# Правила R8 для release-сборки.

# NewPipeExtractor расшифровывает подписи потоков через Rhino (SPEC 4.1).
# Без этого правила в release-сборке видео не играют.
-keep class org.mozilla.javascript.** { *; }
-dontwarn org.mozilla.javascript.**
-dontwarn javax.script.**
-dontwarn java.beans.**
-dontwarn jdk.dynalink.**
