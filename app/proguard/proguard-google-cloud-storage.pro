# The google-cloud-storage SDK references classes that are not on the Android
# runtime classpath: full protobuf-java (this app uses protobuf-javalite, and
# protobuf-java is excluded), the Google App Engine API, optional gRPC modules
# (alts, auth, opentelemetry, protobuf, s2a), OpenTelemetry, Apache HttpClient's
# Kerberos/GSS authenticator, and a dnsjava SPI. The keep rules below retain
# those libraries, so R8 can no longer drop the unused references and reports
# them as missing classes. None of these paths run in the app.

-dontwarn com.google.appengine.**
-dontwarn com.google.protobuf.**

-dontwarn io.grpc.alts.**
-dontwarn io.grpc.auth.**
-dontwarn io.grpc.opentelemetry.**
-dontwarn io.grpc.protobuf.**
-dontwarn io.grpc.s2a.**
-dontwarn io.opentelemetry.**

-dontwarn org.ietf.jgss.**
-dontwarn org.xbill.DNS.spi.DnsjavaInetAddressResolverProvider

# =================================================================
# Google Cloud Storage & Google Auth / API Client Keep Rules
# =================================================================

# Retain reflection attributes and annotations
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep Google Auth library
-keep class com.google.auth.** { *; }

# Keep Google Cloud Storage and Cloud Core
-keep class com.google.cloud.** { *; }

# Keep Google API Client classes, JSON parsing, and HTTP transport
-keep class com.google.api.client.** { *; }
-keep class com.google.api.services.** { *; }

# Keep GenericJson, GenericData, and all subclasses (DTOs / response models) with constructors
-keep class com.google.api.client.json.GenericJson {
    <init>();
    *;
}
-keep class com.google.api.client.util.GenericData {
    <init>();
    *;
}
-keep class * extends com.google.api.client.json.GenericJson {
    <init>();
    <fields>;
    <methods>;
}
-keep class * extends com.google.api.client.util.GenericData {
    <init>();
    <fields>;
    <methods>;
}

# Preserve fields and methods annotated with @Key used by Google JSON parser reflection
-keepclassmembers class * {
    @com.google.api.client.util.Key <fields>;
    @com.google.api.client.util.Key <methods>;
    @com.google.api.client.util.Value <fields>;
}
