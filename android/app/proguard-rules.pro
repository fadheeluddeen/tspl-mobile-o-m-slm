# Apache POI
-dontwarn org.apache.poi.**
-dontwarn org.apache.xmlbeans.**
-dontwarn org.apache.commons.**
-dontwarn org.openxmlformats.**
-dontwarn com.microsoft.schemas.**
-dontwarn org.etsi.**
-dontwarn org.w3.**
-keep class org.apache.poi.** { *; }
-keep class org.apache.xmlbeans.** { *; }
-keep class org.openxmlformats.** { *; }

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.technavious.om15.data.model.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
