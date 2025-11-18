# Add project specific ProGuard rules here.

# Keep Garmin Connect IQ SDK
-keep class com.garmin.android.** { *; }
-dontwarn com.garmin.android.**

# Keep OSMDroid
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**
