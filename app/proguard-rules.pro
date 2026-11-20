-keep class org.lsposed.hidexposed.core.** { *; }
-keep class org.lsposed.hidexposed.data.** { *; }
-dontwarn io.github.libxposed.annotation.**
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}
-keepattributes SourceFile,LineNumberTable
