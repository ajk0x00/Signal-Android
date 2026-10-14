# The google-cloud-storage SDK pulls in references to classes from
# protobuf-java (we use protobuf-javalite), Google App Engine, OpenTelemetry,
# and a dnsjava SPI that are not on our runtime classpath. The code paths
# that touch them are never exercised, so it's safe to silence R8.

-dontwarn com.google.appengine.api.urlfetch.**
-dontwarn com.google.protobuf.AbstractMessage
-dontwarn com.google.protobuf.AbstractMessage$Builder
-dontwarn com.google.protobuf.AbstractMessage$BuilderParent
-dontwarn com.google.protobuf.DescriptorProtos
-dontwarn com.google.protobuf.Descriptors$Descriptor
-dontwarn com.google.protobuf.Descriptors$FieldDescriptor
-dontwarn com.google.protobuf.Descriptors$FileDescriptor
-dontwarn com.google.protobuf.ExtensionRegistry
-dontwarn com.google.protobuf.GeneratedMessage
-dontwarn com.google.protobuf.GeneratedMessage$GeneratedExtension
-dontwarn com.google.protobuf.GeneratedMessageV3
-dontwarn com.google.protobuf.GeneratedMessageV3$Builder
-dontwarn com.google.protobuf.GeneratedMessageV3$BuilderParent
-dontwarn com.google.protobuf.GeneratedMessageV3$FieldAccessorTable
-dontwarn com.google.protobuf.MapEntry
-dontwarn com.google.protobuf.MapField
-dontwarn com.google.protobuf.Message
-dontwarn com.google.protobuf.MessageOrBuilder
-dontwarn com.google.protobuf.ProtocolMessageEnum
-dontwarn com.google.protobuf.RepeatedFieldBuilderV3
-dontwarn com.google.protobuf.SingleFieldBuilderV3
-dontwarn com.google.protobuf.UnknownFieldSet

-dontwarn io.opentelemetry.**
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
