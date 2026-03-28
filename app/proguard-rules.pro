# ProGuard rules for Sentinoid
# Optimized for security and build speed

# ------------------------------------------------------------------------------
# R8 Optimization Settings
# ------------------------------------------------------------------------------

# Enable aggressive optimizations
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-dontpreverify
-verbose

# Optimizations to apply
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*,!code/allocation/variable

# Faster obfuscation with dictionary
-obfuscationdictionary proguard-dictionary.txt
-classobfuscationdictionary proguard-dictionary.txt
-packageobfuscationdictionary proguard-dictionary.txt

# ------------------------------------------------------------------------------
# Keep Rules (Security Critical)
# ------------------------------------------------------------------------------

# Keep crypto classes
-keep class com.sentinoid.app.security.** { *; }
-keepclassmembers class com.sentinoid.app.security.** { *; }

# Keep BouncyCastle
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# Keep USB serial
-keep class com.hoho.android.usbserial.** { *; }
-dontwarn com.hoho.android.usbserial.**

# Keep ZXing
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# Keep biometric classes
-keep class androidx.biometric.** { *; }

# Keep Keystore and security classes
-keep class android.security.** { *; }
-keepclassmembers class android.security.** { *; }

# Keep accessibility service
-keep class com.sentinoid.app.service.FPMInterceptorService { *; }
-keepclassmembers class com.sentinoid.app.service.FPMInterceptorService { *; }

# Keep device admin
-keep class com.sentinoid.app.receiver.SentinoidDeviceAdminReceiver { *; }

# Keep application class
-keep class com.sentinoid.app.SentinoidApp { *; }

# ------------------------------------------------------------------------------
# Kotlin Specific
# ------------------------------------------------------------------------------

-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}

# Keep Kotlin coroutines
-keepclassmembernames class kotlinx.coroutines.** {
    volatile <fields>;
}

# ------------------------------------------------------------------------------
# General Attributes
# ------------------------------------------------------------------------------

-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-keepattributes SourceFile
-keepattributes LineNumberTable
-keepattributes MethodParameters

# For reflection
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes RuntimeInvisibleParameterAnnotations

# ------------------------------------------------------------------------------
# Android Framework
# ------------------------------------------------------------------------------

# Keep Android components
-keep public class * extends android.app.Application
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.preference.Preference

# Keep lifecycle components
-keep class * implements androidx.lifecycle.LifecycleObserver { *; }

# ------------------------------------------------------------------------------
# Faster Build Options
# ------------------------------------------------------------------------------

# Don't warn about missing dependencies (faster builds)
-dontwarn javax.naming.**
-dontwarn javax.security.**
-dontwarn java.awt.**
-dontwarn java.beans.**

# Skip unnecessary processing
-dontnote **

# Remove logging code in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

# Remove print statements
-assumenosideeffects class java.io.PrintStream {
    public void println(...);
    public void print(...);
}

# ------------------------------------------------------------------------------
# Security Hardening
# ------------------------------------------------------------------------------

# Prevent string decryption attacks
-repackageclasses 's'
-flattenpackagehierarchy
-allowaccessmodification

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep serializable for crypto
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}
