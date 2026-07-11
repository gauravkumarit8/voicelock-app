# Model files go here

This directory is intentionally empty in source control.

Place your trained/converted ONNX models here before building:
- `wakeword_phrase.onnx` — trained via the free openWakeWord + Piper-TTS
  pipeline (see main README, "Training your wake word model").
- `speaker_embedding.onnx` — a pretrained, permissively-licensed
  (Apache/MIT) speaker-embedding model converted to ONNX.

Without these two files, WakeWordEngine.loadModel() and
SpeakerVerificationEngine.loadModel() will throw a FileNotFoundException
at runtime — the app will still compile and install, but voice detection
won't function until the models are added.
