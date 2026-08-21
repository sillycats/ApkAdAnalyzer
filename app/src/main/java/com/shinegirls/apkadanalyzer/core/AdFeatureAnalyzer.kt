package com.shinegirls.apkadanalyzer.core

import com.shinegirls.apkadanalyzer.utils.Format
import java.io.File
import java.util.ArrayDeque
import java.util.Arrays
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/** 分析过程中的日志回调类型，接收一段文本消息。 */
typealias Logger = (String) -> Unit

/**
 * APK 广告特征分析引擎。
 *
 * 直接读取 APK（ZIP）的各个条目，依据 [AdPatternConfig] 中各分类的广告特征
 * （sdk 包名 / 类名关键词 / 方法名 / URL / 布局 / assets / 原生库 / Flutter 等），
 * 逐项检测该 APK 命中了哪些广告特征，最后按分类聚合为 [AdAnalysisResult]。
 *
 * 命中结果经 [AdAnalysisResult.toConfig] 可序列化为与 ad_patterns.json 完全一致
 * 的格式，用户可将其保存/复制为针对该 APK 的广告特征配置文件。
 *
 * 检测策略：
 * - DEX 条目：使用 Aho-Corasick 多模式自动机做单遍字节扫描，覆盖 sdk 包名、类名关键词、
 *   方法名、URL、字符串特征、View/Activity/Service/Receiver 类名等 DEX 内分类。
 * - AndroidManifest.xml：解析 AXML 字符串池，匹配组件类名与 ad_permissions。
 * - res/layout：解析 AXML 字符串池，匹配 res_layout_keywords。
 * - assets / 根目录：按路径匹配 asset_keywords / ad_asset_paths / root_file_keywords。
 * - lib (ABI) 目录：按 .so 文件名匹配 lib_file_keywords。
 * - lib/(ABI)/libapp.so：Aho-Corasick 字节扫描匹配 flutter_string_patterns（Flutter 应用）。
 *
 * 性能说明：传统实现对所有特征各自做一次 O(n) 朴素扫描，特征是数百上千条时约等于
 * O(特征数 * n) 次全量遍历。本实现将全部特征编译进一台 Aho-Corasick 自动机，
 * 对每个条目只扫描一遍即同时命中所有特征（O(n + 命中数)），大幅提升分析速度。
 * 匹配对 ASCII 大小写不敏感，且无需复制输入字节（在遍历时动态做小写折叠）。
 */
object AdFeatureAnalyzer {

    /** 单次读入内存的字节数上限（64MB），超过则用分块读取截断，防止 OOM。 */
    private const val MAX_IN_MEMORY_BYTES = 64L * 1024 * 1024

    /** 字母表大小（0~255）。 */
    private const val ALPHA = 256

    /**
     * 分析 APK 广告特征。
     *
     * @param apkFile APK 文件
     * @param config  广告特征配置（用于提取各分类的检测特征）
     * @param logger  进度日志回调（可选）
     * @param progress 实时进度回调（可选）：(已处理条目数, 总条目数, 当前文件名)，用于 UI 展示进度条。
     * @return 分析结果
     */
    fun analyze(
        apkFile: File,
        config: AdPatternConfig.AdPatterns,
        logger: Logger? = null,
        progress: ((done: Int, total: Int, fileName: String) -> Unit)? = null
    ): AdAnalysisResult {
        val log = logger ?: {}
        val result = AdAnalysisResult(apkName = apkFile.name)
        val matches = mutableMapOf<AdPatternConfig.Category, MutableSet<String>>()

        // 预编译各扫描上下文的多模式自动机（全部特征一次编译，扫描时单遍命中）
        val dexMatcher = buildDexMatcher(config)
        val manifestMatcher = buildListMatcher(linkedMapOf(
            AdPatternConfig.Category.SDK_PACKAGES to config.sdkPackages,
            AdPatternConfig.Category.CLASS_KEYWORDS to config.classKeywords,
            AdPatternConfig.Category.AD_ACTIVITIES to config.adActivities,
            AdPatternConfig.Category.AD_SERVICES to config.adServices,
            AdPatternConfig.Category.AD_RECEIVERS to config.adReceivers,
            AdPatternConfig.Category.AD_PERMISSIONS to config.adPermissions,
            AdPatternConfig.Category.STRING_PATTERNS to config.stringPatterns
        ))
        val layoutMatcher = buildListMatcher(linkedMapOf(
            AdPatternConfig.Category.RES_LAYOUT_KEYWORDS to config.resLayoutKeywords
        ))
        val flutterMatcher = buildListMatcher(linkedMapOf(
            AdPatternConfig.Category.FLUTTER_PATTERNS to config.flutterPatterns
        ))

        val libKeywords = config.libFileKeywords
        val assetKeywords = config.assetKeywords
        val adAssetPaths = config.adAssetPaths
        val rootKeywords = config.rootFileKeywords

        var dexCount = 0
        var fileCount = 0
        var classHit = 0
        var methodHit = 0
        var libappCount = 0
        var flutterStringCount = 0

        if (!apkFile.exists()) {
            log("  ✗ APK 文件不存在: ${apkFile.absolutePath}")
            return result.copy(matches = emptyMap())
        }

        log("  · 开始扫描: ${apkFile.name} (${Format.formatSize(apkFile.length())})")

        // 单遍遍历：同时完成包名提取、文件计数与全部特征扫描
        try {
            ZipFile(apkFile).use { zip ->
                val entries = zip.entries().asSequence().filter { !it.isDirectory }.toList()
                var processed = 0
                for (entry in entries) {
                    val name = entry.name
                    fileCount++
                    processed++
                    progress?.invoke(processed, entries.size, name)
                    // 每 50 条记一次日志，显示百分比与当前文件，避免高频日志拖慢分析
                    if (processed % 50 == 0) {
                        val pct = if (entries.isEmpty()) 100 else (processed * 100 / entries.size)
                        log("  · 进度: $processed/${entries.size} ($pct%) 当前: ${shortName(name)}")
                    }

                    when {
                        // Flutter libapp.so：自动机字节扫描 + 命中计数
                        name.startsWith("lib/") && name.endsWith("libapp.so") -> {
                            libappCount++
                            val bytes = readEntry(zip, entry)
                            val fl = flutterMatcher.scan(bytes)[AdPatternConfig.Category.FLUTTER_PATTERNS].orEmpty()
                            if (fl.isNotEmpty()) {
                                addAll(matches, AdPatternConfig.Category.FLUTTER_PATTERNS, fl)
                                flutterStringCount += fl.sumOf { countSubstring(bytes, it) }
                            }
                        }
                        // DEX：自动机单遍扫描 DEX 内分类
                        name.endsWith(".dex") -> {
                            dexCount++
                            val bytes = readEntry(zip, entry)
                            val hits = dexMatcher.scan(bytes)
                            hits.forEach { (cat, values) ->
                                val before = matches[cat].orEmpty().size
                                addAll(matches, cat, values)
                                val added = matches[cat].orEmpty().size - before
                                when (cat) {
                                    AdPatternConfig.Category.CLASS_KEYWORDS,
                                    AdPatternConfig.Category.AD_VIEW_NAMES,
                                    AdPatternConfig.Category.AD_ACTIVITIES,
                                    AdPatternConfig.Category.AD_SERVICES,
                                    AdPatternConfig.Category.AD_RECEIVERS -> classHit += added
                                    AdPatternConfig.Category.METHOD_PATTERNS -> methodHit += added
                                    else -> {}
                                }
                            }
                        }
                        // AndroidManifest.xml：AXML 字符串池匹配（同时提取包名）
                        name == "AndroidManifest.xml" -> {
                            val bytes = readEntry(zip, entry)
                            AxmlAnalyzer.readManifestInfoBytes(bytes)?.packageName
                                ?.ifBlank { null }?.let { result.packageName = it }
                            val strings = AxmlAnalyzer.extractAllStrings(bytes)
                            manifestMatcher.scanStrings(strings)
                                .forEach { (cat, values) ->
                                    val before = matches[cat].orEmpty().size
                                    addAll(matches, cat, values)
                                    if (cat == AdPatternConfig.Category.AD_ACTIVITIES) {
                                        classHit += matches[cat].orEmpty().size - before
                                    }
                                }
                        }
                        // res/layout 布局文件：AXML 字符串池匹配
                        name.startsWith("res/layout") && name.endsWith(".xml") -> {
                            val bytes = readEntry(zip, entry)
                            val strings = AxmlAnalyzer.extractAllStrings(bytes)
                            layoutMatcher.scanStrings(strings)
                                .forEach { (cat, values) -> addAll(matches, cat, values) }
                        }
                        // lib 原生库：按文件名匹配 lib_file_keywords
                        name.startsWith("lib/") && name.endsWith(".so") -> {
                            val fileName = name.substringAfterLast('/')
                            matchNamePatterns(fileName, libKeywords)
                                .forEach { addAll(matches, AdPatternConfig.Category.LIB_FILE_KEYWORDS, listOf(it)) }
                        }
                        // assets：路径匹配 asset_keywords 与 ad_asset_paths
                        name.startsWith("assets/") -> {
                            val rel = name.removePrefix("assets/")
                            matchNamePatterns(name, assetKeywords)
                                .forEach { addAll(matches, AdPatternConfig.Category.ASSET_KEYWORDS, listOf(it)) }
                            adAssetPaths.filter { ap ->
                                val target = ap.removePrefix("assets/")
                                target.isNotBlank() && (rel == target || rel.startsWith("$target/") || name == ap)
                            }.forEach { addAll(matches, AdPatternConfig.Category.AD_ASSET_PATHS, listOf(it)) }
                        }
                        // 根目录文件：按文件名匹配 root_file_keywords
                        !name.contains('/') -> {
                            matchNamePatterns(name, rootKeywords)
                                .forEach { addAll(matches, AdPatternConfig.Category.ROOT_FILE_KEYWORDS, listOf(it)) }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            log("  ✗ 分析过程异常: ${e.message}")
            log("  · ${e.stackTraceToString().take(200)}")
        }

        result.dexCount = dexCount
        result.fileCount = fileCount
        result.classCount = classHit
        result.methodCount = methodHit
        result.isFlutter = libappCount > 0
        result.flutterLibappCount = libappCount
        result.flutterStringCount = flutterStringCount
        result.matches = matches.mapValues { (_, v) -> v.toList() }

        val totalHits = result.totalHitCount
        log("  · DEX=${dexCount} 条目=${fileCount} Flutter=${if (result.isFlutter) "是" else "否"}")
        log("  · 命中特征总条数: $totalHits 个")
        if (totalHits == 0) {
            log("  ⚠ 该 APK 未命中任何已配置的广告特征")
        }
        log("  ⚠ 提示: 分析出的广告特征仅供参考，请结合 APK 实际功能与人工复核后使用")
        return result
    }

    /**
     * 构建 DEX 扫描用的多模式自动机。
     *
     * sdk 包名同时生成点号与斜杠（DEX 类路径 Lcom/foo/bar;）两种匹配形式，
     * 但命中后回显仍使用配置原文（点号形式），保证生成的配置与 ad_patterns.json 一致。
     */
    private fun buildDexMatcher(config: AdPatternConfig.AdPatterns): AcMatcher {
        val m = AcMatcher()
        config.sdkPackages
            .filter { it.isNotBlank() }
            .forEach { pkg -> m.add(listOf(asciiLower(pkg), asciiLower(pkg.replace('.', '/'))), AdPatternConfig.Category.SDK_PACKAGES, pkg) }
        m.addAll(AdPatternConfig.Category.CLASS_KEYWORDS, config.classKeywords)
        m.addAll(AdPatternConfig.Category.AD_VIEW_NAMES, config.adViewNames)
        m.addAll(AdPatternConfig.Category.AD_ACTIVITIES, config.adActivities)
        m.addAll(AdPatternConfig.Category.AD_SERVICES, config.adServices)
        m.addAll(AdPatternConfig.Category.AD_RECEIVERS, config.adReceivers)
        m.addAll(AdPatternConfig.Category.METHOD_PATTERNS, config.methodPatterns)
        m.addAll(AdPatternConfig.Category.URL_PATTERNS, config.urlPatterns)
        m.addAll(AdPatternConfig.Category.STRING_PATTERNS, config.stringPatterns)
        m.addAll(AdPatternConfig.Category.FORCE_TRUE_METHODS, config.forceTrueMethodNames)
        m.addAll(AdPatternConfig.Category.FORCE_FALSE_METHODS, config.forceFalseMethodNames)
        m.addAll(AdPatternConfig.Category.METHOD_NEUTRALIZE_KEYWORDS, config.methodNeutralizeKeywords)
        m.build()
        return m
    }

    /** 构建面向字符串列表（AXML 字符串池等）的多模式自动机。 */
    private fun buildListMatcher(groups: Map<AdPatternConfig.Category, List<String>>): AcMatcher {
        val m = AcMatcher()
        for ((cat, list) in groups) m.addAll(cat, list)
        m.build()
        return m
    }

    /** 将条目名简化为末尾文件名，便于日志展示。 */
    private fun shortName(entryName: String): String {
        val s = entryName.substringAfterLast('/')
        return if (s.length > 48) s.take(22) + "…" + s.takeLast(24) else s
    }

    /** 按条目名匹配文件名/路径类特征（子串匹配，大小写不敏感；列表通常较小）。 */
    private fun matchNamePatterns(entryName: String, keywords: List<String>): List<String> {
        if (keywords.isEmpty()) return emptyList()
        val n = entryName.lowercase()
        return keywords.filter { k -> k.isNotBlank() && n.contains(k.lowercase()) }
    }

    private fun addAll(
        matches: MutableMap<AdPatternConfig.Category, MutableSet<String>>,
        category: AdPatternConfig.Category,
        values: Collection<String>
    ) {
        matches.getOrPut(category) { linkedSetOf() }.addAll(values)
    }

    /** 读取 zip 中某条目的字节（超限则仅读入前 MAX_IN_MEMORY_BYTES，防止 OOM）。 */
    private fun readEntry(zip: ZipFile, entry: ZipEntry): ByteArray {
        return try {
            zip.getInputStream(entry).use { ins ->
                if (entry.isDirectory) return@use ByteArray(0)
                val buffer = java.io.ByteArrayOutputStream((entry.size.coerceAtMost(MAX_IN_MEMORY_BYTES)).toInt().coerceAtLeast(0))
                val chunk = ByteArray(64 * 1024)
                var total = 0L
                while (total < MAX_IN_MEMORY_BYTES) {
                    val read = ins.read(chunk)
                    if (read < 0) break
                    val toWrite = (read.toLong().coerceAtMost(MAX_IN_MEMORY_BYTES - total)).toInt()
                    buffer.write(chunk, 0, toWrite)
                    total += toWrite.toLong()
                    if (total >= MAX_IN_MEMORY_BYTES) break
                }
                buffer.toByteArray()
            }
        } catch (_: Exception) {
            ByteArray(0)
        }
    }

    /** 统计特征在字节数组中出现的次数（仅对已命中的少量特征调用，开销可忽略）。 */
    private fun countSubstring(data: ByteArray, needle: String): Int {
        val n = needle.lowercase()
        if (n.isEmpty()) return 0
        val ndl = asciiLower(n)
        var count = 0
        var from = 0
        while (true) {
            val idx = indexOfIgnoreCase(data, ndl, from)
            if (idx < 0) break
            count++
            from = idx + 1
        }
        return count.coerceAtMost(Int.MAX_VALUE / 4)
    }

    private fun indexOfIgnoreCase(data: ByteArray, needle: ByteArray, from: Int = 0): Int {
        if (needle.isEmpty() || needle.size > data.size) return -1
        val last = data.size - needle.size
        var i = from
        while (i <= last) {
            if (equalsIgnoreCaseAt(data, i, needle)) return i
            i++
        }
        return -1
    }

    /** 判断 [data] 从 [offset] 起的字节是否与 [needle] 相等（忽略 ASCII 大小写）。 */
    private fun equalsIgnoreCaseAt(data: ByteArray, offset: Int, needle: ByteArray): Boolean {
        for (j in needle.indices) {
            if (lowerAscii(data, offset + j) != needle[j]) return false
        }
        return true
    }

    private fun lowerAscii(data: ByteArray, index: Int): Byte {
        val b = data[index]
        return if (b >= 0x41.toByte() && b <= 0x5A.toByte()) (b.toInt() + 32).toByte() else b
    }

    /** 将字符串转为 ASCII 小写字节数组（支持非 ASCII 用 UTF-8 字节兜底）。 */
    private fun asciiLower(s: String): ByteArray {
        val utf8 = s.toByteArray(Charsets.UTF_8)
        for (i in utf8.indices) {
            val b = utf8[i]
            utf8[i] = if (b in 0x41..0x5A) (b + 32).toByte() else b
        }
        return utf8
    }
}

/**
 * Aho-Corasick 多模式匹配自动机（字节字母表，256 进）。
 *
 * 将多条特征一次性编译进自动机，随后对任意字节流/字符串做单遍扫描，
 * 即可同时命中所有特征，返回 (分类 -> 去重后的特征原文) 映射。
 * 匹配对 ASCII 大小写不敏感：模式在加入时转为小写，扫描输入时对 ASCII 大写动态折叠，
 * 因此无需复制输入字节，内存占用更低。特征原文用于回显，保证生成的配置与 ad_patterns.json 一致。
 */
private class AcMatcher {
    /** 字母表大小（0~255），转移表 flat 编码的基数。 */
    private val ALPHA = 256

    private class Out(val cat: AdPatternConfig.Category, val original: String)
    private var go: IntArray
    private val outputs = ArrayList<ArrayList<Out>>()
    private val fail = ArrayList<Int>()
    private var built = false

    private val root = 0

    init {
        go = IntArray(ALPHA)
        Arrays.fill(go, -1)
        outputs.add(ArrayList())
        fail.add(0)
    }

    /** 添加一条特征：needles 是匹配用的一个或多个小写字节形式，original 是配置原文。 */
    fun add(needles: List<ByteArray>, cat: AdPatternConfig.Category, original: String) {
        require(original.isNotBlank()) { "空白特征不应被加入" }
        for (needle in needles) addSingle(needle, cat, original)
    }

    fun addAll(cat: AdPatternConfig.Category, list: List<String>) {
        for (s in list) if (s.isNotBlank()) add(listOf(asciiLower(s)), cat, s)
    }

    /** 将字符串转为 ASCII 小写字节数组（非 ASCII 用 UTF-8 字节兜底，仅折叠 A-Z）。 */
    private fun asciiLower(s: String): ByteArray {
        val utf8 = s.toByteArray(Charsets.UTF_8)
        for (i in utf8.indices) {
            val b = utf8[i]
            utf8[i] = if (b in 0x41..0x5A) (b + 32).toByte() else b
        }
        return utf8
    }

    private fun addSingle(needle: ByteArray, cat: AdPatternConfig.Category, original: String) {
        var state = root
        for (b in needle) {
            val idx = b.toInt() and (ALPHA - 1)
            val key = state * ALPHA + idx
            if (key >= go.size) grow(key + 1)
            var next = go[key]
            if (next < 0) {
                next = newState()
                go[key] = next
            }
            state = next
        }
        outputs[state].add(Out(cat, original))
    }

    private fun newState(): Int {
        if (outputs.size * ALPHA > go.size) {
            // 保险：确保容量足够容纳新增状态的整行转移
            grow(outputs.size * ALPHA + ALPHA + 16)
        }
        outputs.add(ArrayList())
        fail.add(0)
        return outputs.size - 1
    }

    private fun grow(minKey: Int) {
        if (go.size >= minKey) return
        var newSize = go.size * 2
        while (newSize < minKey) newSize *= 2
        // 保持容量为 ALPHA 的整倍数，便于按状态索引（非必需，但更规整）
        if (newSize % ALPHA != 0) newSize += ALPHA - (newSize % ALPHA)
        val ng = IntArray(newSize)
        System.arraycopy(go, 0, ng, 0, go.size)
        Arrays.fill(ng, go.size, newSize, -1)
        go = ng
    }

    /** 计算失败跳转并完善转移表（一次性预处理，之后扫描为常数级跳转）。 */
    fun build() {
        if (built) return
        built = true

        // 根节点缺失的转移全部指向自身（已就绪）
        for (b in 0 until ALPHA) {
            if (go[b] < 0) go[b] = root
        }

        val queue = ArrayDeque<Int>()
        for (b in 0 until ALPHA) {
            val v = go[b]
            if (v != root) {
                fail[v] = root
                queue.addLast(v)
            }
        }

        while (queue.isNotEmpty()) {
            val r = queue.removeFirst()
            val base = r * ALPHA
            val failBase = fail[r] * ALPHA
            for (b in 0 until ALPHA) {
                val key = base + b
                var u = go[key]
                if (u < 0) {
                    // 转移缺失：直接跟随失败节点的转移（完成真转移表）
                    go[key] = go[failBase + b]
                } else {
                    fail[u] = go[failBase + b]
                    // 将失败链上的输出并入本节点，扫描时只需检查当前节点
                    outputs[u].addAll(outputs[fail[u]])
                    queue.addLast(u)
                }
            }
        }
    }

    /** 扫描一段字节流，返回命中的 (分类 -> 特征原文集合)。 */
    fun scan(bytes: ByteArray): Map<AdPatternConfig.Category, MutableSet<String>> {
        val hits = linkedMapOf<AdPatternConfig.Category, MutableSet<String>>()
        if (!built || bytes.isEmpty()) return hits
        var state = root
        for (byte in bytes) {
            val b = byte.toInt() and 0xFF
            // ASCII 大写折叠为小写，模式已小写，从而大小写不敏感
            val nb = if (b in 0x41..0x5A) b + 32 else b
            state = go[state * ALPHA + nb]
            val list = outputs[state]
            if (list.isNotEmpty()) {
                for (o in list) hits.getOrPut(o.cat) { linkedSetOf() }.add(o.original)
            }
        }
        return hits
    }

    /** 扫描一组字符串（如 AXML 字符串池），返回命中的 (分类 -> 特征原文集合)。 */
    fun scanStrings(strings: List<String>): Map<AdPatternConfig.Category, MutableSet<String>> {
        if (!built || strings.isEmpty()) return emptyMap()
        // 拼接为单段字节流（用 NUL 分隔），一次扫描
        val size = strings.sumOf { it.length * 2 + 1 }
        val bb = java.io.ByteArrayOutputStream(size.coerceAtLeast(16))
        for (i in strings.indices) {
            if (i > 0) bb.write(0)
            bb.write(strings[i].toByteArray(Charsets.UTF_8))
        }
        return scan(bb.toByteArray())
    }
}