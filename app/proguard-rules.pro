# The hook side runs inside system_server and is reached by name from META-INF/xposed/java_init.list
-keep class dev.fadalias.core.** { *; }
-keep class dev.fadalias.state.** { *; }
-keep class dev.fadalias.data.** { *; }
-keep class dev.fadalias.Identity { *; }
-dontwarn io.github.libxposed.annotation.**
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}
-keepattributes SourceFile,LineNumberTable
# ContentProvider and Application are referenced from the manifest (kept by AGP); the provider is called by system_server
-keep class dev.fadalias.app.RulesProvider { *; }
