# Model files

`melspectrogram.onnx`, `embedding_model.onnx`, and `hey_jarvis_v0.1.onnx` are
bundled here, pulled directly from openWakeWord's GitHub releases
(https://github.com/dscripka/openWakeWord/releases/tag/v0.5.1). These let you
test the full wake-word pipeline immediately using the stock "Hey Jarvis"
phrase, before training your own custom phrase.

**Still missing:** `speaker_embedding.onnx` — no pretrained speaker-embedding
model is bundled. Source or convert one (Apache/MIT-licensed) and place it
here before enrollment/voice-auth will work — see the main README's
"Speaker verification model" section.

To swap in your own trained wake-word phrase later: keep
`melspectrogram.onnx` and `embedding_model.onnx` as-is (they're the shared
preprocessing stages, reusable for any phrase), and replace only the
classifier — update the `classifierAsset` default in
`WakeWordEngine.loadModels()` to point at your new file.
