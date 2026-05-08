package com.example.buhlograf.domain

interface AnalyticsService {
    fun trackEvent(name: String, params: Map<String, Any> = emptyMap())
    fun trackError(message: String, error: Throwable? = null)
}

interface AuthService {
    fun getSavedSession(): UserSession?
    fun saveSession(session: UserSession)
    fun clearSession()
    fun createDemoSession(): UserSession
}

interface DrinkRepository {
    fun getEntries(): List<DrinkEntry>
    fun addEntry(type: DrinkType, volumeMl: Int, strengthPercent: Double, note: String): DrinkEntry
    fun clearToday()
}

interface FriendsRepository {
    fun getFriends(): List<FriendProgress>
    fun addFriendById(id: String): FriendProgress?
}

interface CloudSyncService {
    val isEnabled: Boolean
    fun bindSession(
        session: UserSession,
        onDataChanged: () -> Unit,
        onProfileChanged: (UserProfile) -> Unit
    )
    fun unbind()
    fun saveProfile(session: UserSession, fcmToken: String?)
    fun updateFcmToken(userId: String, token: String)
}

interface RemoteConfigService {
    fun getState(): RemoteConfigState
    fun fetch(onUpdated: (RemoteConfigState) -> Unit)
}
