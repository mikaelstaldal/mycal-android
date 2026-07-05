package nu.staldal.mycal.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.IOException
import java.security.GeneralSecurityException

class CredentialStore(context: Context) {
    private val prefs = createPrefs(context)

    val baseUrl: String?
        get() = prefs.getString(KEY_BASE_URL, null)

    val username: String?
        get() = prefs.getString(KEY_USERNAME, null)

    val password: String?
        get() = prefs.getString(KEY_PASSWORD, null)

    fun save(baseUrl: String, username: String, password: String) {
        prefs.edit {
            putString(KEY_BASE_URL, baseUrl)
            putString(KEY_USERNAME, username)
            putString(KEY_PASSWORD, password)
        }
    }

    fun clear() {
        prefs.edit {
            remove(KEY_BASE_URL)
            remove(KEY_USERNAME)
            remove(KEY_PASSWORD)
        }
    }

    fun hasCredentials(): Boolean =
        baseUrl != null && username != null && password != null

    private companion object {
        const val PREFS_FILE_NAME = "secret_shared_prefs"
        const val KEY_BASE_URL = "base_url"
        const val KEY_USERNAME = "username"
        const val KEY_PASSWORD = "password"

        /**
         * Opens the encrypted credential store, recovering from decryption failures.
         *
         * After a device-to-device migration the AndroidKeyStore master key does not
         * come across (Keystore keys never leave the device), so the transferred
         * keysets can no longer be unwrapped and [EncryptedSharedPreferences.create]
         * fails (e.g. `AEADBadTagException`). Rather than crash on first launch, we
         * delete the unreadable file and start fresh, degrading to "please log in
         * again". Excluding the file from device transfer (see
         * `data_extraction_rules.xml`) normally prevents this; the catch is a
         * belt-and-braces guard.
         */
        private fun createPrefs(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return try {
                buildEncryptedPrefs(context, masterKey)
            } catch (e: GeneralSecurityException) {
                context.deleteSharedPreferences(PREFS_FILE_NAME)
                buildEncryptedPrefs(context, masterKey)
            } catch (e: IOException) {
                context.deleteSharedPreferences(PREFS_FILE_NAME)
                buildEncryptedPrefs(context, masterKey)
            }
        }

        private fun buildEncryptedPrefs(
            context: Context,
            masterKey: MasterKey,
        ): SharedPreferences =
            EncryptedSharedPreferences.create(
                context,
                PREFS_FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
    }
}
