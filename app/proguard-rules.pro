# Add project-specific ProGuard rules here.
# Keep ONNX Runtime and Hilt-generated classes from being stripped.
-keep class ai.onnxruntime.** { *; }
-keep class dagger.hilt.** { *; }
-keep class com.voicelock.app.** { *; }
