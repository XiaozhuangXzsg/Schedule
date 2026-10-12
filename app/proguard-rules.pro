# Room / Kotlin 保留规则
-keep class com.university.timetable.data.entity.** { *; }
-keepclassmembers class com.university.timetable.data.entity.** { *; }

# ML Kit 文字识别
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Glance
-keep class androidx.glance.** { *; }
