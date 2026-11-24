# Правила R8 для release-сборки. Свой код и LuaJ (рефлексия в загрузчике библиотек) целиком оставляем как есть:
# выигрыш по скорости даёт оптимизация Compose / Media3 / OkHttp / Coil, а не обфускация приложения.
-keep class app.ymusic.m3e.** { *; }
-keep class org.luaj.** { *; }
-dontwarn org.luaj.**
-dontwarn javax.script.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
