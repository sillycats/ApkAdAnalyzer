# ProGuard rules for ApkAdAnalyzer v1.0

# ===== Kotlin =====
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembernames class * {
    native <methods>;
}

# ===== App main classes =====
-keep class com.shinegirls.apkadanalyzer.** { *; }

# ===== AndroidX / Material =====
-keep class androidx.** { *; }
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**

# ===== JSON serialization =====
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# ===== AdPatternConfig (JSON serialization) =====
-keep class com.shinegirls.apkadanalyzer.core.AdPatternConfig { *; }
-keep class com.shinegirls.apkadanalyzer.core.AdPatternConfig$** { *; }

# ===== R8 full mode =====
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile