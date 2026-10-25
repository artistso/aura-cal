# Aura — R8 / ProGuard rules
#
# Release builds currently ship with `isMinifyEnabled = false`, so these rules are inert.
# They are written and kept in sync so that turning shrinking on is a one-line change:
#
#     buildTypes {
#         release {
#             isMinifyEnabled = true
#             isShrinkResources = true
#             ...
#         }
#     }
#
# Verify an optimised build on a physical device (and preferably against the Play
# pre-launch report) before promoting it to closed testing.

# Keep line numbers so crash reports from the Play Console stay actionable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Room: the generated _Impl classes are referenced by name from the runtime.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# The kernel is pure Kotlin and used directly; nothing reflective, so only the
# entry points that the in-app verification panel reflects over need keeping.
-keep class com.example.aura.kernel.Seeds { *; }
-keepclassmembers class com.example.aura.kernel.KernelTests {
    public static *** TEST_CASES;
}

# Compose keeps its own rules; this silences the reflective-composable warning.
-dontwarn androidx.compose.**

# Moshi codegen (currently unused, kept for future model types).
-keepclasseswithmembers class * {
    @com.squareup.moshi.* <methods>;
}
-keep @com.squareup.moshi.JsonQualifier @interface *
