# Preserve kotlinx.serialization models
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.aistudio.vortexpc.core.model.**$$serializer { *; }
-keepclassmembers class com.aistudio.vortexpc.core.model.** { *** Companion; }
