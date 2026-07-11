package com.voicelock.app.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import android.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores the enrolled voice embedding, encrypted via Android Keystore.
 * Never leaves the device, never touches the network (PRD §12 Security).
 */
@Singleton
class VoiceprintStore @Inject constructor(
    private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "voicelock_voiceprint",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveEmbedding(embedding: FloatArray) {
        val bytes = ByteArray(embedding.size * 4)
        java.nio.ByteBuffer.wrap(bytes).asFloatBuffer().put(embedding)
        prefs.edit().putString(KEY_EMBEDDING, Base64.encodeToString(bytes, Base64.NO_WRAP)).apply()
    }

    fun loadEmbedding(): FloatArray? {
        val encoded = prefs.getString(KEY_EMBEDDING, null) ?: return null
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        val floatBuffer = java.nio.ByteBuffer.wrap(bytes).asFloatBuffer()
        val out = FloatArray(floatBuffer.remaining())
        floatBuffer.get(out)
        return out
    }

    /**
     * Blends a newly-verified sample into the stored embedding (PRD §15.2 —
     * silent re-embedding). Weighted average keeps the voiceprint adapting
     * to the user's mic/environment over time without any retraining infra.
     */
    fun reinforceEmbedding(newSample: FloatArray, existingWeight: Float = 0.9f) {
        val existing = loadEmbedding() ?: run { saveEmbedding(newSample); return }
        if (existing.size != newSample.size) return
        val blended = FloatArray(existing.size) { i ->
            existing[i] * existingWeight + newSample[i] * (1f - existingWeight)
        }
        saveEmbedding(blended)
    }

    fun clear() = prefs.edit().remove(KEY_EMBEDDING).apply()
    fun hasEnrollment(): Boolean = prefs.contains(KEY_EMBEDDING)

    companion object {
        private const val KEY_EMBEDDING = "embedding"
    }
}
