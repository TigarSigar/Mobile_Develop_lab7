package com.example.buhlograf.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.buhlograf.domain.AuthProvider
import com.example.buhlograf.domain.AuthService
import com.example.buhlograf.domain.UserSession

class SecureAuthService(context: Context) : AuthService {
    private val preferences: SharedPreferences = createSecurePreferences(context)

    override fun getSavedSession(): UserSession? {
        val token = preferences.getString(KEY_TOKEN, null) ?: return null
        val userName = preferences.getString(KEY_USER_NAME, null) ?: return null
        val provider = preferences.getString(KEY_PROVIDER, null)
            ?.let { runCatching { AuthProvider.valueOf(it) }.getOrNull() }
            ?: return null
        val userId = preferences.getString(KEY_USER_ID, null) ?: return null
        val photoUrl = preferences.getString(KEY_PHOTO_URL, null)
        val email = preferences.getString(KEY_EMAIL, null)

        return UserSession(
            token = token,
            userName = userName,
            provider = provider,
            userId = userId,
            photoUrl = photoUrl,
            email = email
        )
    }

    override fun saveSession(session: UserSession) {
        preferences.edit()
            .putString(KEY_TOKEN, session.token)
            .putString(KEY_USER_NAME, session.userName)
            .putString(KEY_PROVIDER, session.provider.name)
            .putString(KEY_USER_ID, session.userId)
            .putString(KEY_PHOTO_URL, session.photoUrl)
            .putString(KEY_EMAIL, session.email)
            .apply()
    }

    override fun clearSession() {
        preferences.edit().clear().apply()
    }

    override fun createDemoSession(): UserSession =
        UserSession(
            token = "demo-token",
            userName = "Демо Выпивоха",
            provider = AuthProvider.Demo,
            userId = "DEMO-00000001"
        )

    private fun createSecurePreferences(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private companion object {
        const val FILE_NAME = "secure_session"
        const val KEY_TOKEN = "access_token"
        const val KEY_USER_NAME = "user_name"
        const val KEY_PROVIDER = "provider"
        const val KEY_USER_ID = "user_id"
        const val KEY_PHOTO_URL = "photo_url"
        const val KEY_EMAIL = "email"
    }
}
