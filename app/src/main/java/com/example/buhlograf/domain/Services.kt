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
    fun addEntry(product: AlcoholProduct, volumeMl: Int, strengthPercent: Double, note: String): DrinkEntry
    fun clearToday()
}

interface FriendsRepository {
    fun getFriends(): List<FriendProgress>
    fun addFriendById(id: String): FriendProgress?
    fun acceptFriend(publicId: String)
    fun rejectFriend(publicId: String)
    fun removeFriend(publicId: String)
}

interface CloudSyncService {
    val isEnabled: Boolean
    fun resolveSessionByEmail(session: UserSession, onResolved: (UserSession) -> Unit) {
        onResolved(session)
    }
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

interface CatalogRepository {
    fun getProducts(): List<AlcoholProduct>
    fun getSuggestions(): List<ProductSuggestion>
    fun getLoadState(): CatalogLoadState
    fun startListening(onChanged: () -> Unit)
    fun startSuggestionsListening(onChanged: () -> Unit)
    fun addProduct(product: AlcoholProduct, createdBy: String, isAdmin: Boolean): Boolean
    fun updateProduct(product: AlcoholProduct, isAdmin: Boolean): Boolean
    fun setProductActive(productId: String, isActive: Boolean, adminId: String, isAdmin: Boolean): Boolean
    fun submitProduct(
        product: AlcoholProduct,
        createdBy: String,
        authorName: String,
        isAdmin: Boolean,
        onResult: (Boolean, String?) -> Unit = { _, _ -> }
    ): Boolean
    fun approveSuggestion(suggestionId: String, product: AlcoholProduct, adminId: String): Boolean
    fun rejectSuggestion(suggestionId: String, adminId: String): Boolean
    fun hideSuggestionAuthor(authorId: String)
}

interface DailyStatsRepository {
    fun getDailyStats(ownerPublicId: String): List<DailyStats>
    fun getDayStats(ownerPublicId: String, dayKey: String): DailyStats?
    fun getDayEntries(ownerPublicId: String, dayKey: String): List<DrinkEntryPreview>
}
