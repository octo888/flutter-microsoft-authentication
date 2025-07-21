# Keep Tink classes
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

# Keep BouncyCastle
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# Nimbus JOSE + JWT
-keep class com.nimbusds.** { *; }
-dontwarn com.nimbusds.**

# FindBugs annotations
-keep class edu.umd.cs.findbugs.annotations.** { *; }
-dontwarn edu.umd.cs.findbugs.annotations.**