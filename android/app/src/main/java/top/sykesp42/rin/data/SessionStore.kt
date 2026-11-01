package top.sykesp42.rin.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The only credentials persisted: the session cookie and the JWXT account
 * password (needed for silent renewal). Both go through AndroidKeyStore
 * AES-GCM; no plaintext ever reaches disk.
 */
data class SessionCookie(val name: String, val value: String)

interface SessionStore {
    fun save(cookie: SessionCookie)
    fun load(): SessionCookie?
    fun clear()
}

class MemorySessionStore : SessionStore {
    private var cookie: SessionCookie? = null
    override fun save(cookie: SessionCookie) { this.cookie = cookie }
    override fun load(): SessionCookie? = cookie
    override fun clear() { cookie = null }
}

/**
 * AES-GCM with a key held by AndroidKeyStore; ciphertext (IV + payload) is
 * written to app-private storage. Not unit-testable without the Android
 * runtime; the interface is exercised through MemorySessionStore in JVM tests.
 */
class KeystoreSessionStore(private val file: File) : SessionStore {
    @Serializable
    private data class Payload(val name: String, val value: String)

    private val json = Json { ignoreUnknownKeys = true }

    override fun save(cookie: SessionCookie) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val plain = json.encodeToString(Payload.serializer(), Payload(cookie.name, cookie.value)).toByteArray()
        val cipherText = cipher.doFinal(plain)
        val iv = cipher.iv
        file.parentFile?.mkdirs()
        // Atomic swap: a process death mid-write must not corrupt the only
        // copy of the credential.
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeBytes(byteArrayOf(iv.size.toByte()) + iv + cipherText)
        if (!tmp.renameTo(file)) {
            file.writeBytes(tmp.readBytes())
            tmp.delete()
        }
    }

    override fun load(): SessionCookie? {
        if (!file.exists() || file.length() < 2) return null
        return runCatching {
            val bytes = file.readBytes()
            val ivLen = bytes[0].toInt()
            val iv = bytes.copyOfRange(1, 1 + ivLen)
            val cipherText = bytes.copyOfRange(1 + ivLen, bytes.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            val payload = json.decodeFromString(Payload.serializer(), cipher.doFinal(cipherText).toString(Charsets.UTF_8))
            SessionCookie(payload.name, payload.value)
        }.getOrNull()
    }

    override fun clear() {
        file.delete()
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "suisign_session"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
