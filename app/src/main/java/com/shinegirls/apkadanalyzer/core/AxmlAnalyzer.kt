package com.shinegirls.apkadanalyzer.core

/**
 * AXML（Android Binary XML）解析工具。
 *
 * APK 内的 AndroidManifest.xml 与 res/layout 布局文件均为二进制 XML（AXML）格式，
 * 无法直接用文本解析。本类提供只读解析能力，供广告特征分析引擎复用：
 * - 解析字符串池（String Pool），将字符串引用解析为可读文本
 * - 从清单字节中读取包名与已有 application 类名
 *
 * 仅做读取分析，不涉及任何清单改写操作。
 */
object AxmlAnalyzer {

    // ========== AXML chunk 类型 ==========
    private const val CHUNK_START_ELEMENT = 0x0102

    /** String Pool 标志位：UTF-8 编码（否则为 UTF-16LE） */
    private const val FLAG_UTF8 = 0x100

    /**
     * AXML 字符串池解析器。
     */
    private class StringPool(private val data: ByteArray, private val chunkStart: Int) {

        private val stringCount: Int = readU32(data, chunkStart + 8).toInt()
        private val flags: Int = readU32(data, chunkStart + 16).toInt()
        private val stringsStart: Int = readU32(data, chunkStart + 20).toInt()
        private val isUtf8: Boolean = (flags and FLAG_UTF8) != 0
        private val offsets: IntArray = IntArray(stringCount)

        init {
            // 字符串偏移表位于 chunk 头部之后（chunkStart + 28）
            for (i in 0 until stringCount) {
                offsets[i] = readU32(data, chunkStart + 28 + i * 4).toInt()
            }
        }

        operator fun get(index: Int): String? {
            if (index < 0 || index >= stringCount) return null
            val pos = chunkStart + stringsStart + offsets[index]
            return if (isUtf8) decodeUtf8(pos) else decodeUtf16(pos)
        }

        /** 解码 UTF-16LE 字符串（Android 默认）。 */
        private fun decodeUtf16(pos: Int): String? {
            var p = pos
            if (p + 2 > data.size) return null
            var len = readU16(data, p)
            p += 2
            if (len and 0x8000 != 0) {
                // 高位置位表示长度需要扩展到 32 位
                if (p + 2 > data.size) return null
                len = ((len and 0x7fff) shl 16) or readU16(data, p)
                p += 2
            }
            val byteLen = len * 2
            if (p + byteLen > data.size) return null
            val bytes = ByteArray(byteLen)
            System.arraycopy(data, p, bytes, 0, byteLen)
            return String(bytes, Charsets.UTF_16LE)
        }

        /** 解码 UTF-8 字符串。 */
        private fun decodeUtf8(pos: Int): String? {
            var p = pos
            if (p >= data.size) return null
            // 第一个变长整数：UTF-16 字符数（解码时不需要）
            p = skipVarint(p)
            if (p >= data.size) return null
            // 第二个变长整数：字节长度
            var byteLen = data[p].toInt() and 0xff
            p++
            if (p >= data.size) return null
            if (byteLen and 0x80 != 0) {
                byteLen = ((byteLen and 0x7f) shl 8) or (data[p].toInt() and 0xff)
                p++
            }
            if (p + byteLen > data.size) return null
            val bytes = ByteArray(byteLen)
            System.arraycopy(data, p, bytes, 0, byteLen)
            return String(bytes, Charsets.UTF_8)
        }

        /** 跳过 1 或 2 字节的变长整数。 */
        private fun skipVarint(p: Int): Int {
            val b = data[p].toInt() and 0xff
            return if (b and 0x80 != 0) p + 2 else p + 1
        }
    }

    /** 清单基础信息：包名与已有 application 名（可能为 null）。 */
    data class ManifestInfo(val packageName: String?, val applicationName: String?)

    /**
     * 提取 AXML 二进制文件字符串池中的所有字符串。
     *
     * 供广告特征分析引擎复用：对 AndroidManifest.xml、res/layout 布局文件等
     * 二进制 AXML，直接读取其字符串池，即可对其中的类名、属性名、权限名等进行
     * 子串匹配，无需自行实现完整的 AXML 解析。
     *
     * @return 字符串池中的全部字符串；非 AXML 或解析失败返回空列表
     */
    fun extractAllStrings(data: ByteArray): List<String> {
        if (data.size < 8) return emptyList()
        // AXML 魔数校验
        if (readU16(data, 0) != 0x0003) return emptyList()
        return try {
            val pool = StringPool(data, 8)
            val count = readU32(data, 8 + 8).toInt()
            (0 until count).mapNotNull { pool[it] }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * 从 AXML 字节数据中读取包名与已有 application 类名。
     * 供广告特征分析引擎直接解析内存中的 AndroidManifest.xml 字节使用，无需落盘。
     */
    fun readManifestInfoBytes(data: ByteArray): ManifestInfo? {
        if (data.size < 8 || readU16(data, 0) != 0x0003) return null
        val fileSize = readU32(data, 4).toInt().coerceAtMost(data.size)
        val pool = StringPool(data, 8)
        var packageName: String? = null
        var appName: String? = null
        var offset = 8
        while (offset + 8 <= fileSize) {
            val type = readU16(data, offset)
            val chunkSize = readU32(data, offset + 4).toInt()
            if (chunkSize < 8 || offset + chunkSize > fileSize) break
            if (type == CHUNK_START_ELEMENT) {
                val elem = pool[readU32(data, offset + 20)]?.lowercase()
                when (elem) {
                    "manifest" -> if (packageName == null) {
                        readAllAttrs(data, offset, pool)["package"]?.let { packageName = it }
                    }
                    "application" -> if (appName == null) {
                        readAllAttrs(data, offset, pool)["name"]?.let { appName = it }
                    }
                }
            }
            offset += chunkSize
        }
        return ManifestInfo(packageName, appName)
    }

    /**
     * 读取某 start element 的所有字符串属性（用于解析 manifest/application 的信息）。
     * 仅收集 rawValue 以字符串类型存储的属性，资源引用天然跳过。
     */
    private fun readAllAttrs(data: ByteArray, chunkStart: Int, pool: StringPool): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        val attributeStart = readU16(data, chunkStart + 24)
        val attributeCount = readU16(data, chunkStart + 28)
        val attributeSize = readU16(data, chunkStart + 26)
        if (attributeCount <= 0 || attributeSize < 20) return result
        var attrOff = chunkStart + 16 + attributeStart
        for (i in 0 until attributeCount) {
            if (attrOff + 20 > data.size) break
            val value = if (readU32(data, attrOff + 8).toInt() >= 0) {
                pool[readU32(data, attrOff + 8).toInt()]
            } else {
                null
            }
            if (value != null && value.isNotBlank()) {
                val name = pool[readU32(data, attrOff + 4).toInt()].orEmpty()
                result[name] = value
            }
            attrOff += attributeSize
        }
        return result
    }

    // ========== 字节读取辅助 ==========

    private fun readU16(data: ByteArray, offset: Int): Int {
        return (data[offset].toInt() and 0xff) or ((data[offset + 1].toInt() and 0xff) shl 8)
    }

    private fun readU32(data: ByteArray, offset: Int): Int {
        return (data[offset].toInt() and 0xff) or
            ((data[offset + 1].toInt() and 0xff) shl 8) or
            ((data[offset + 2].toInt() and 0xff) shl 16) or
            ((data[offset + 3].toInt() and 0xff) shl 24)
    }
}