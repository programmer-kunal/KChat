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

# Suppress warnings for optional SLF4J bindings referenced by transitive dependencies
-dontwarn org.slf4j.impl.StaticLoggerBinder

# Preserve Firebase Realtime Database model classes and no-arg constructors
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

-keep class com.example.kchat.model.** { *; }

-keepclassmembers class * {
    @com.google.firebase.database.PropertyName <fields>;
    @com.google.firebase.database.PropertyName <methods>;
}

-keepclassmembers class * {
    @com.google.firebase.database.IgnoreExtraProperties *;
}