# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

## Rhino
-keep class javax.script.** { *; }
-keep class com.sun.script.javascript.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class com.script.javascript.** { *; }

## 插件相关
-keep class com.github.jing332.tts_server_android.model.rhino.core.** { *; }
-keep class cn.hutool.crypto.** { *; }
-keep class cn.hutool.core.** { *; }

# OKIO
-keep class okio.* { *; }

# 保持 ViewBinding 实现类中的所有名称以 “inflate” 开头的方法不被混淆
-keepclassmembers class * implements androidx.viewbinding.ViewBinding {
    public static ** inflate(...);
}

#-------------- 去掉所有打印 -------------

-assumenosideeffects class android.util.Log {
public static *** d(...);

# public static *** e(...);

public static *** i(...);

public static *** v(...);

public static *** println(...);

public static *** w(...);

public static *** wtf(...);

}

-assumenosideeffects class android.util.Log {
public static *** d(...);

public static *** v(...);

}

-assumenosideeffects class android.util.Log {
# public static *** e(...);

public static *** v(...);

}

-assumenosideeffects class android.util.Log {
public static *** i(...);

public static *** v(...);

}

-assumenosideeffects class android.util.Log {
public static *** w(...);

public static *** v(...);

}

-assumenosideeffects class java.io.PrintStream {
public *** println(...);

public *** print(...);

}

# Keep `Companion` object fields of serializable classes.
# This avoids serializer lookup through `getDeclaredClasses` as done for named companion objects.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

# Keep `serializer()` on companion objects (both default and named) of serializable classes.
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep `INSTANCE.serializer()` of serializable objects.
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# @Serializable and @Polymorphic are used at runtime for polymorphic serialization.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

# Serializer for classes with named companion objects are retrieved using `getDeclaredClasses`.
# If you have any, uncomment and replace classes with those containing named companion objects.
#-keepattributes InnerClasses # Needed for `getDeclaredClasses`.
#-if @kotlinx.serialization.Serializable class
#com.example.myapplication.HasNamedCompanion, # <-- List serializable classes with named companions.
#com.example.myapplication.HasNamedCompanion2
#{
#    static **$* *;
#}
#-keepnames class <1>$$serializer { # -keepnames suffices; class is kept when serializer() is kept.
#    static <1>$$serializer INSTANCE;
#}
