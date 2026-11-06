# LiteRT-LM uses JNI and reflection into its Kotlin classes
-keep class com.google.ai.edge.litertlm.** { *; }
-dontwarn com.google.ai.edge.litertlm.**
# kotlinx.serialization models
-keep,includedescriptorclasses class app.lumi.**$$serializer { *; }
-keepclassmembers class app.lumi.** { *** Companion; kotlinx.serialization.KSerializer serializer(...); }
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
