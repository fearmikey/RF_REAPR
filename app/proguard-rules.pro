# Add project specific ProGuard/R8 rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   https://developer.android.com/studio/build/shrink-code

# ---------------------------------------------------------------------------
# Gson
# ---------------------------------------------------------------------------
# Gson uses generic type information stored in a class file when working with
# fields. R8/ProGuard removes such information by default; keep it so
# TypeToken-based deserialization (used throughout ReportRepositoryImpl,
# ShodanRepositoryImpl, InternetDbRepositoryImpl, RoomConverters, etc.)
# keeps working.
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**

-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# Our own Gson-serialized model/DTO classes: keep fields (including names)
# since Gson maps JSON keys to field names/@SerializedName reflectively and
# there is no generated TypeAdapter.
-keepclassmembers class com.fearmikey.rf_reapr.data.remote.dto.** {
    <fields>;
}
-keepclassmembers class com.fearmikey.rf_reapr.domain.model.** {
    <fields>;
}
-keep class com.fearmikey.rf_reapr.data.remote.dto.** { *; }
-keep class com.fearmikey.rf_reapr.domain.model.** { *; }

# ---------------------------------------------------------------------------
# Room
# ---------------------------------------------------------------------------
# Room's own consumer ProGuard rules (bundled in the androidx.room AAR)
# already keep generated DAO/database implementations. Entities are
# referenced directly by that generated code, so no extra rules are needed
# here as long as the generated *_Impl classes remain reachable.

# ---------------------------------------------------------------------------
# WorkManager
# ---------------------------------------------------------------------------
# WorkManager instantiates Worker subclasses by class name via reflection
# (WorkerFactory), so their class name and public constructor must survive
# shrinking/obfuscation.
-keep public class * extends androidx.work.Worker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep public class * extends androidx.work.CoroutineWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class com.fearmikey.rf_reapr.data.worker.** { *; }

# ---------------------------------------------------------------------------
# iperf JNI bridge
# ---------------------------------------------------------------------------
# Native code looks up this class/its methods by name; the default Android
# rules already keep classes with native methods, but keep the whole object
# explicitly for clarity and safety.
-keep class com.fearmikey.rf_reapr.iperf.IperfNative { *; }

# ---------------------------------------------------------------------------
# Apache POI / OOXML (report generation) and SNMP4J
# ---------------------------------------------------------------------------
# Both are large, reflection-heavy libraries not written with Android/R8 in
# mind. They reference optional dependencies (logging frameworks, javax.*,
# AWT, etc.) that are not present at runtime on Android. Keep them intact
# rather than risk breaking report generation / SNMP parsing, and silence
# the resulting "missing class" warnings for their optional dependencies.
-keep class org.apache.poi.** { *; }
-keep interface org.apache.poi.** { *; }
-dontwarn org.apache.poi.**
-dontwarn org.apache.xmlbeans.**
-dontwarn org.apache.commons.compress.**
-dontwarn org.openxmlformats.**
-dontwarn org.etsi.uri.**
-dontwarn org.w3.x2000.x09.xmldsig.**
-dontwarn javax.xml.**
-dontwarn org.ietf.jgss.**
-dontwarn org.osgi.**
-dontwarn org.apache.logging.log4j.**
-dontwarn aQute.bnd.**
-dontwarn edu.umd.cs.findbugs.annotations.**
-dontwarn com.graphbuilder.**
-dontwarn java.awt.**

-keep class org.snmp4j.** { *; }
-dontwarn org.snmp4j.**

# ---------------------------------------------------------------------------
# Retrofit / OkHttp
# ---------------------------------------------------------------------------
# Retrofit and OkHttp ship their own consumer rules; nothing extra required
# here for the single Retrofit service interface (VulnerabilityApiService).
