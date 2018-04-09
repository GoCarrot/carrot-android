#
# This ProGuard configuration file illustrates how to process a program
# library, such that it remains usable as a library.
# Usage:
#     java -jar proguard.jar @library.pro
#

# Save the obfuscation mapping to a file, so we can de-obfuscate any stack
# traces later on. Keep a fixed source file attribute and all line number
# tables to get line numbers in the stack traces.
# You can comment this out if you're not interested in stack traces.

-printmapping out.map
-keepparameternames
-renamesourcefileattribute SourceFile
-keepattributes Exceptions,InnerClasses,Signature,Deprecated,SourceFile,LineNumberTable,EnclosingMethod

# Preserve all annotations.

-keepattributes *Annotation*

# Preserve all public classes, and their public and protected fields and
# methods.

# -keep public class * {
#     public protected *;
# }

# Preserve all .class method names.

-keepclassmembernames class * {
    java.lang.Class class$(java.lang.String);
    java.lang.Class class$(java.lang.String, boolean);
}

# Preserve all native method names and the names of their classes.

-keepclasseswithmembernames class * {
    native <methods>;
}

# Preserve the special static methods that are required in all enumeration
# classes.

-keepclassmembers class * extends java.lang.Enum {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Explicitly preserve all serialization members. The Serializable interface
# is only a marker interface, so it wouldn't save them.
# You can comment this out if your library doesn't use serialization.
# If your code contains serializable classes that have to be backward
# compatible, please refer to the manual.

-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Your library may contain more items that need to be preserved; 
# typically classes that are dynamically created using Class.forName:

# -keep public class mypackage.MyClass
# -keep public interface mypackage.MyInterface
# -keep public class * implements mypackage.MyInterface

### Sentry ###
-keepattributes LineNumberTable,SourceFile
-dontwarn org.slf4j.**
-dontwarn javax.**

### Teak ###

# Public interface
-keep public class io.teak.sdk.Teak { public *; }
-keep public class io.teak.sdk.TeakNotification { public *; }

# Receivers
-keep public class io.teak.sdk.ADMPushProvider { public *; }
-keep public class io.teak.sdk.ADMPushProvider$MessageAlertReceiver { public *; }
-keep public class io.teak.sdk.InstanceIDListenerService { public *; }
-keep public class io.teak.sdk.InstallReferrerReceiver { public *; }

# Services
-keep public class io.teak.sdk.service.RavenService { public *; }

# Wrapper Classes
-keep public class io.teak.sdk.wrapper.Application { public *; }
-keep public class io.teak.sdk.wrapper.TeakInterface { public *; }

# Adobe AIR
-keep public class io.teak.sdk.wrapper.air.Extension { public *; }
-keep public class io.teak.sdk.wrapper.air.ExtensionContext { public *; }

# Unity
-keep public class io.teak.sdk.wrapper.unity.TeakUnity { public *; }
-keep public class io.teak.sdk.wrapper.unity.TeakUnityPlayerActivity { public *; }
-keep public class io.teak.sdk.wrapper.unity.TeakUnityPlayerNativeActivity { public *; }
