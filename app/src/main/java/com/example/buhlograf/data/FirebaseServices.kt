package com.example.buhlograf.data

import android.util.Log
import com.example.buhlograf.domain.CloudSyncService
import com.example.buhlograf.domain.DrinkEntry
import com.example.buhlograf.domain.DrinkRepository
import com.example.buhlograf.domain.DrinkType
import com.example.buhlograf.domain.FriendProgress
import com.example.buhlograf.domain.FriendsRepository
import com.example.buhlograf.domain.RemoteConfigService
import com.example.buhlograf.domain.RemoteConfigState
import com.example.buhlograf.domain.UserProfile
import com.example.buhlograf.domain.UserSession
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import java.util.concurrent.atomic.AtomicLong

class FirebaseBuhlografRepository(
    private val firestore: FirebaseFirestore
) : DrinkRepository, FriendsRepository, CloudSyncService {
    private val ids = AtomicLong(System.currentTimeMillis())
    private val entries = mutableListOf<DrinkEntry>()
    private val friends = mutableListOf<FriendProgress>()
    private val listeners = mutableListOf<ListenerRegistration>()
    private var currentUserId: String? = null

    override val isEnabled: Boolean = true

    override fun getEntries(): List<DrinkEntry> =
        entries.sortedByDescending { it.timestampMillis }

    override fun addEntry(
        type: DrinkType,
        volumeMl: Int,
        strengthPercent: Double,
        note: String
    ): DrinkEntry {
        val entry = DrinkEntry(
            id = ids.incrementAndGet(),
            type = type,
            volumeMl = volumeMl,
            strengthPercent = strengthPercent,
            note = note.trim(),
            timestampMillis = System.currentTimeMillis()
        )
        entries += entry
        currentUserId?.let { userId ->
            firestore.collection(USERS)
                .document(userId)
                .collection(DRINKS)
                .document(entry.id.toString())
                .set(entry.toFirestore())
        }
        return entry
    }

    override fun clearToday() {
        val userId = currentUserId
        val idsToDelete = entries.map { it.id.toString() }
        entries.clear()
        if (userId != null) {
            idsToDelete.forEach { id ->
                firestore.collection(USERS)
                    .document(userId)
                    .collection(DRINKS)
                    .document(id)
                    .delete()
            }
        }
    }

    override fun getFriends(): List<FriendProgress> = friends.toList()

    override fun addFriendById(id: String): FriendProgress? {
        val normalized = id.trim().uppercase()
        if (normalized.isBlank()) return null
        friends.firstOrNull { it.id == normalized }?.let { return it }

        val friend = FriendProgress(
            id = normalized,
            name = "Друг $normalized",
            status = "ожидает профиль из Firestore",
            pureAlcoholMl = 0.0,
            streakDays = 0
        )
        friends += friend
        currentUserId?.let { userId ->
            firestore.collection(USERS)
                .document(userId)
                .collection(FRIENDS)
                .document(normalized)
                .set(friend.toFirestore())
        }
        return friend
    }

    override fun bindSession(
        session: UserSession,
        onDataChanged: () -> Unit,
        onProfileChanged: (UserProfile) -> Unit
    ) {
        unbind()
        currentUserId = session.userId
        saveProfile(session, FcmTokenStore.lastToken)

        val userDocument = firestore.collection(USERS).document(session.userId)
        listeners += userDocument.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "Profile listen failed", error)
                return@addSnapshotListener
            }
            snapshot?.toObject(UserProfile::class.java)?.let(onProfileChanged)
        }
        listeners += userDocument.collection(DRINKS).addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "Drinks listen failed", error)
                return@addSnapshotListener
            }
            entries.clear()
            snapshot?.documents
                ?.mapNotNull { it.toDrinkEntry() }
                ?.let(entries::addAll)
            onDataChanged()
        }
        listeners += userDocument.collection(FRIENDS).addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "Friends listen failed", error)
                return@addSnapshotListener
            }
            friends.clear()
            snapshot?.documents
                ?.mapNotNull { it.toFriendProgress() }
                ?.let(friends::addAll)
            onDataChanged()
        }
    }

    override fun unbind() {
        listeners.forEach { it.remove() }
        listeners.clear()
        currentUserId = null
    }

    override fun saveProfile(session: UserSession, fcmToken: String?) {
        val profile = UserProfile(
            userId = session.userId,
            name = session.userName,
            email = session.email.orEmpty(),
            provider = session.provider.analyticsName,
            photoUrl = session.photoUrl.orEmpty(),
            fcmToken = fcmToken.orEmpty(),
            updatedAtMillis = System.currentTimeMillis()
        )
        firestore.collection(USERS)
            .document(session.userId)
            .set(profile, SetOptions.merge())
    }

    override fun updateFcmToken(userId: String, token: String) {
        firestore.collection(USERS)
            .document(userId)
            .set(
                mapOf(
                    "fcmToken" to token,
                    "updatedAtMillis" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            )
    }

    private fun DrinkEntry.toFirestore(): Map<String, Any> = mapOf(
        "id" to id,
        "type" to type.name,
        "volumeMl" to volumeMl,
        "strengthPercent" to strengthPercent,
        "note" to note,
        "timestampMillis" to timestampMillis
    )

    private fun FriendProgress.toFirestore(): Map<String, Any> = mapOf(
        "id" to id,
        "name" to name,
        "status" to status,
        "pureAlcoholMl" to pureAlcoholMl,
        "streakDays" to streakDays
    )

    private fun com.google.firebase.firestore.DocumentSnapshot.toDrinkEntry(): DrinkEntry? {
        val type = getString("type")?.let { runCatching { DrinkType.valueOf(it) }.getOrNull() }
            ?: return null
        return DrinkEntry(
            id = getLong("id") ?: id.toLongOrNull() ?: return null,
            type = type,
            volumeMl = getLong("volumeMl")?.toInt() ?: 0,
            strengthPercent = getDouble("strengthPercent") ?: 0.0,
            note = getString("note").orEmpty(),
            timestampMillis = getLong("timestampMillis") ?: 0L
        )
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toFriendProgress(): FriendProgress? =
        FriendProgress(
            id = getString("id") ?: id,
            name = getString("name") ?: "Друг $id",
            status = getString("status") ?: "ожидает профиль из Firestore",
            pureAlcoholMl = getDouble("pureAlcoholMl") ?: 0.0,
            streakDays = getLong("streakDays")?.toInt() ?: 0
        )

    private companion object {
        const val TAG = "FirebaseBuhlografRepo"
        const val USERS = "users"
        const val DRINKS = "drinks"
        const val FRIENDS = "friends"
    }
}

class NoOpCloudSyncService : CloudSyncService {
    override val isEnabled: Boolean = false
    override fun bindSession(
        session: UserSession,
        onDataChanged: () -> Unit,
        onProfileChanged: (UserProfile) -> Unit
    ) = Unit

    override fun unbind() = Unit
    override fun saveProfile(session: UserSession, fcmToken: String?) = Unit
    override fun updateFcmToken(userId: String, token: String) = Unit
}

class FirebaseRemoteConfigService(
    private val remoteConfig: FirebaseRemoteConfig
) : RemoteConfigService {
    init {
        remoteConfig.setDefaultsAsync(
            mapOf(
                KEY_BANNER to "Бухлограф синхронизирует записи через Firebase.",
                KEY_EXPERIMENTAL_FRIENDS to true
            )
        )
        remoteConfig.setConfigSettingsAsync(
            FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(0)
                .build()
        )
    }

    override fun getState(): RemoteConfigState = RemoteConfigState(
        welcomeBanner = remoteConfig.getString(KEY_BANNER).ifBlank {
            "Бухлограф синхронизирует записи через Firebase."
        },
        experimentalFriendsEnabled = remoteConfig.getBoolean(KEY_EXPERIMENTAL_FRIENDS)
    )

    override fun fetch(onUpdated: (RemoteConfigState) -> Unit) {
        remoteConfig.fetchAndActivate()
            .addOnCompleteListener { onUpdated(getState()) }
    }

    private companion object {
        const val KEY_BANNER = "welcome_banner"
        const val KEY_EXPERIMENTAL_FRIENDS = "experimental_friends_enabled"
    }
}

class StaticRemoteConfigService : RemoteConfigService {
    private val state = RemoteConfigState()
    override fun getState(): RemoteConfigState = state
    override fun fetch(onUpdated: (RemoteConfigState) -> Unit) {
        onUpdated(state)
    }
}

object FcmTokenStore {
    @Volatile
    var lastToken: String? = null

    fun save(context: android.content.Context, token: String) {
        lastToken = token
        context.getSharedPreferences("firebase_push", android.content.Context.MODE_PRIVATE)
            .edit()
            .putString("fcm_token", token)
            .apply()
    }

    fun load(context: android.content.Context): String? {
        val token = context.getSharedPreferences("firebase_push", android.content.Context.MODE_PRIVATE)
            .getString("fcm_token", null)
        lastToken = token
        return token
    }
}
