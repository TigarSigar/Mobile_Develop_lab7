package com.example.buhlograf.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.buhlograf.push.BuhloNotificationCenter
import com.example.buhlograf.domain.AlcoholCategory
import com.example.buhlograf.domain.AlcoholProduct
import com.example.buhlograf.domain.CatalogLoadState
import com.example.buhlograf.domain.CatalogRepository
import com.example.buhlograf.domain.CloudSyncService
import com.example.buhlograf.domain.DailyStats
import com.example.buhlograf.domain.DailyStatsRepository
import com.example.buhlograf.domain.DrinkEntry
import com.example.buhlograf.domain.DrinkEntryPreview
import com.example.buhlograf.domain.DrinkRepository
import com.example.buhlograf.domain.DrinkType
import com.example.buhlograf.domain.FriendProgress
import com.example.buhlograf.domain.FriendRelationStatus
import com.example.buhlograf.domain.FriendsRepository
import com.example.buhlograf.domain.ProductSource
import com.example.buhlograf.domain.ProductSuggestion
import com.example.buhlograf.domain.ProductSuggestionStatus
import com.example.buhlograf.domain.ProductTag
import com.example.buhlograf.domain.PublicIdGenerator
import com.example.buhlograf.domain.RemoteConfigService
import com.example.buhlograf.domain.RemoteConfigState
import com.example.buhlograf.domain.UserProfile
import com.example.buhlograf.domain.UserSession
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import java.util.concurrent.atomic.AtomicLong

class FirebaseBuhlografRepository(
    context: Context,
    private val firestore: FirebaseFirestore,
    private val realtimeDatabase: FirebaseDatabase
) : DrinkRepository, FriendsRepository, CloudSyncService, CatalogRepository, DailyStatsRepository {
    private val notifications = BuhloNotificationCenter(context.applicationContext)
    private val ids = AtomicLong(System.currentTimeMillis())
    private val entries = mutableListOf<DrinkEntry>()
    private val products = mutableListOf<AlcoholProduct>()
    private val suggestions = mutableListOf<ProductSuggestion>()
    private val hiddenSuggestionAuthors = mutableSetOf<String>()
    private val dailyStatsByPublicId = mutableMapOf<String, MutableList<DailyStats>>()
    private val publicEntriesByPublicId = mutableMapOf<String, MutableList<DrinkEntryPreview>>()
    private val acceptedFriends = mutableListOf<FriendProgress>()
    private val incomingRequests = mutableMapOf<String, IncomingFriendRequest>()
    private val incomingFriends = mutableListOf<FriendProgress>()
    private val outgoingFriends = mutableListOf<FriendProgress>()
    private val publicProfiles = mutableMapOf<String, PublicFriendProfile>()
    private val listeners = mutableListOf<ListenerRegistration>()
    private var currentSession: UserSession? = null
    private var onDataChanged: (() -> Unit)? = null
    private var catalogLoadState: CatalogLoadState = CatalogLoadState.Loading
    private var onCatalogChanged: (() -> Unit)? = null
    private var catalogListener: ListenerRegistration? = null
    private var suggestionsListener: ValueEventListener? = null
    private var currentIsAdmin: Boolean = false
    private val shownNotifications = mutableSetOf<String>()
    private var suggestionsSnapshotInitialized = false
    private var friendsSnapshotInitialized = false
    private var requestsSnapshotInitialized = false

    override val isEnabled: Boolean = true

    override fun resolveSessionByEmail(session: UserSession, onResolved: (UserSession) -> Unit) {
        val normalizedEmail = session.email?.trim()?.lowercase().orEmpty()
        if (normalizedEmail.isBlank()) {
            onResolved(session)
            return
        }

        firestore.collection(USERS)
            .whereEqualTo("email", normalizedEmail)
            .get()
            .addOnSuccessListener { snapshot ->
                val profile = snapshot.documents
                    .filter { it.id != session.userId || it.getString("publicId").isNullOrBlank().not() }
                    .sortedWith(
                        compareByDescending<DocumentSnapshot> { it.getBoolean("isAdmin") == true }
                            .thenByDescending { it.getLong("updatedAtMillis") ?: 0L }
                    )
                    .firstOrNull()
                if (profile == null) {
                    onResolved(session.copy(email = normalizedEmail))
                    return@addOnSuccessListener
                }

                val resolvedUserId = profile.getString("userId").orEmpty().ifBlank { profile.id }
                val resolvedPublicId = profile.getString("publicId").orEmpty()
                    .ifBlank { PublicIdGenerator.fromUserId(resolvedUserId) }
                onResolved(
                    session.copy(
                        userId = resolvedUserId,
                        publicId = resolvedPublicId,
                        email = normalizedEmail,
                        userName = session.userName.ifBlank { profile.getString("name").orEmpty() },
                        photoUrl = session.photoUrl ?: profile.getString("photoUrl")
                    )
                )
            }
            .addOnFailureListener { error ->
                Log.w(TAG, "Profile email lookup failed", error)
                onResolved(session.copy(email = normalizedEmail))
            }
    }

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
        currentSession?.let { session ->
            userDocument(session.userId)
                .collection(DRINKS)
                .document(entry.id.toString())
                .set(entry.toFirestore())
            updateStatsAndProfile(session)
        }
        return entry
    }

    override fun addEntry(
        product: AlcoholProduct,
        volumeMl: Int,
        strengthPercent: Double,
        note: String
    ): DrinkEntry {
        val now = System.currentTimeMillis()
        val entry = DrinkEntry(
            id = ids.incrementAndGet(),
            type = product.category.defaultType,
            volumeMl = volumeMl,
            strengthPercent = strengthPercent,
            note = note.trim(),
            timestampMillis = now,
            productId = product.id,
            barcode = product.barcode,
            productName = product.name,
            brand = product.brand,
            category = product.category.storageKey,
            imageUrl = product.imageUrl,
            dayKey = formatDayKey(now)
        )
        entries += entry
        currentSession?.let { session ->
            userDocument(session.userId)
                .collection(DRINKS)
                .document(entry.id.toString())
                .set(entry.toFirestore())
            updateStatsAndProfile(session)
        }
        return entry
    }

    override fun clearToday() {
        val session = currentSession
        val idsToDelete = entries.map { it.id.toString() }
        entries.clear()
        if (session != null) {
            idsToDelete.forEach { id ->
                userDocument(session.userId)
                    .collection(DRINKS)
                    .document(id)
                    .delete()
            }
            updateStatsAndProfile(session)
        }
    }

    override fun getProducts(): List<AlcoholProduct> =
        products.ifEmpty { defaultProducts() }.sortedWith(
            compareByDescending<AlcoholProduct> { it.ratingCount > 0 }
                .thenByDescending { it.averageRating }
                .thenByDescending { it.ratingCount }
                .thenBy { it.name.lowercase() }
        )

    override fun getSuggestions(): List<ProductSuggestion> =
        suggestions
            .filter { it.status == ProductSuggestionStatus.Pending }
            .filterNot { it.authorId in hiddenSuggestionAuthors }
            .sortedByDescending { it.createdAtMillis }

    override fun getLoadState(): CatalogLoadState = catalogLoadState

    override fun startListening(onChanged: () -> Unit) {
        onCatalogChanged = onChanged
        if (catalogListener != null) {
            onCatalogChanged?.invoke()
            return
        }
        Handler(Looper.getMainLooper()).postDelayed({
            if (catalogLoadState is CatalogLoadState.Loading && products.isEmpty()) {
                products += defaultProducts()
                catalogLoadState = CatalogLoadState.Ready(fromCache = true)
                onCatalogChanged?.invoke()
            }
        }, 7000)
        catalogListener = firestore.collection(PRODUCTS).addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "Products listen failed", error)
                catalogLoadState = if (products.isEmpty()) {
                    products += defaultProducts()
                    CatalogLoadState.Error(error.localizedMessage ?: "Не удалось загрузить каталог")
                } else {
                    CatalogLoadState.Ready(fromCache = true)
                }
                onCatalogChanged?.invoke()
                return@addSnapshotListener
            }
            products.clear()
            snapshot?.documents
                ?.mapNotNull { it.toAlcoholProduct() }
                ?.let(products::addAll)
            if (products.isEmpty()) {
                products += defaultProducts()
            }
            loadMyRatings()
            catalogLoadState = CatalogLoadState.Ready(fromCache = snapshot?.metadata?.isFromCache == true)
            onCatalogChanged?.invoke()
        }
    }

    override fun startSuggestionsListening(onChanged: () -> Unit) {
        onCatalogChanged = {
            onChanged()
        }
        if (suggestionsListener != null) {
            onChanged()
            return
        }
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                suggestions.clear()
            snapshot.children.mapNotNull { it.toProductSuggestion() }.let(suggestions::addAll)
                suggestions.forEach { suggestion ->
                    val session = currentSession
                    if (suggestionsSnapshotInitialized && suggestion.status == ProductSuggestionStatus.Pending && currentIsAdmin) {
                        showOnce(
                            key = "admin_suggestion_${suggestion.id}",
                            channel = BuhloNotificationCenter.Channel.Admin,
                            title = "Новая заявка на напиток",
                            body = "${suggestion.product.name} ждёт модерации",
                            screen = "catalog"
                        )
                    }
                    if (suggestionsSnapshotInitialized && session != null &&
                        suggestion.authorId == session.userId &&
                        suggestion.status == ProductSuggestionStatus.Approved
                    ) {
                        showOnce(
                            key = "suggestion_approved_${suggestion.id}",
                            channel = BuhloNotificationCenter.Channel.Catalog,
                            title = "Напиток одобрен",
                            body = "${suggestion.product.name} появился в ассортименте",
                            screen = "catalog"
                        )
                    }
                }
                suggestionsSnapshotInitialized = true
                onChanged()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Product suggestions listen failed", error.toException())
                onChanged()
            }
        }
        suggestionsListener = listener
        suggestionsReference().addValueEventListener(listener)
    }

    override fun addProduct(product: AlcoholProduct, createdBy: String, isAdmin: Boolean): Boolean {
        if (!isAdmin) return false
        val now = System.currentTimeMillis()
        val id = product.id.ifBlank {
            firestore.collection(PRODUCTS).document().id
        }
        val saved = product.copy(
            id = id,
            source = ProductSource.Admin,
            isVerified = true,
            isActive = product.isActive,
            deletedAtMillis = product.deletedAtMillis,
            updatedBy = createdBy,
            createdBy = createdBy,
            createdAtMillis = product.createdAtMillis.takeIf { it > 0L } ?: now,
            updatedAtMillis = now
        )
        products.removeAll { it.id == saved.id }
        products += saved
        firestore.collection(PRODUCTS).document(saved.id).set(saved.toFirestore(), SetOptions.merge())
        onCatalogChanged?.invoke()
        return true
    }

    override fun updateProduct(product: AlcoholProduct, isAdmin: Boolean): Boolean =
        addProduct(product, product.createdBy, isAdmin)

    override fun setProductActive(
        productId: String,
        isActive: Boolean,
        adminId: String,
        isAdmin: Boolean
    ): Boolean {
        if (!isAdmin) return false
        val now = System.currentTimeMillis()
        products.replaceAll {
            if (it.id == productId) {
                it.copy(
                    isActive = isActive,
                    deletedAtMillis = if (isActive) 0L else now,
                    updatedBy = adminId,
                    updatedAtMillis = now
                )
            } else {
                it
            }
        }
        firestore.collection(PRODUCTS).document(productId).set(
            mapOf(
                "isActive" to isActive,
                "deletedAtMillis" to if (isActive) 0L else now,
                "updatedBy" to adminId,
                "updatedAtMillis" to now
            ),
            SetOptions.merge()
        )
        onCatalogChanged?.invoke()
        return true
    }

    override fun rateProduct(productId: String, userId: String, value: Int): Boolean {
        if (productId.isBlank() || userId.isBlank()) return false
        val rating = value.coerceIn(1, 10)
        val productRef = firestore.collection(PRODUCTS).document(productId)
        val ratingRef = productRef.collection(RATINGS).document(userId)
        firestore.runTransaction { transaction ->
            val productSnapshot = transaction.get(productRef)
            val previous = transaction.get(ratingRef).getLong("value")?.toInt()
            val oldSum = productSnapshot.getLong("ratingSum")?.toInt() ?: 0
            val oldCount = productSnapshot.getLong("ratingCount")?.toInt() ?: 0
            val newCount = if (previous == null) oldCount + 1 else oldCount
            val newSum = oldSum - (previous ?: 0) + rating
            transaction.set(
                ratingRef,
                mapOf(
                    "userId" to userId,
                    "value" to rating,
                    "updatedAtMillis" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            )
            transaction.set(
                productRef,
                mapOf(
                    "ratingSum" to newSum,
                    "ratingCount" to newCount,
                    "averageRating" to if (newCount > 0) newSum.toDouble() / newCount else 0.0,
                    "updatedAtMillis" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            )
        }.addOnSuccessListener {
            products.replaceAll { product ->
                if (product.id == productId) {
                    val old = product.myRating
                    val newCount = if (old == null) product.ratingCount + 1 else product.ratingCount
                    val newSum = product.ratingSum - (old ?: 0) + rating
                    product.copy(
                        ratingSum = newSum,
                        ratingCount = newCount,
                        averageRating = if (newCount > 0) newSum.toDouble() / newCount else 0.0,
                        myRating = rating
                    )
                } else {
                    product
                }
            }
            onCatalogChanged?.invoke()
        }.addOnFailureListener { error ->
            Log.w(TAG, "Product rating failed", error)
        }
        return true
    }

    override fun submitProduct(
        product: AlcoholProduct,
        createdBy: String,
        authorName: String,
        isAdmin: Boolean,
        onResult: (Boolean, String?) -> Unit
    ): Boolean {
        if (isAdmin) {
            val saved = addProduct(product, createdBy, true)
            onResult(saved, null)
            return saved
        }
        val now = System.currentTimeMillis()
        val reference = suggestionsReference().push()
        val suggestionId = reference.key ?: "suggestion-$now"
        val suggestion = ProductSuggestion(
            id = suggestionId,
            product = product.copy(
                id = suggestionId,
                source = ProductSource.UserSuggested,
                isVerified = false,
                createdBy = createdBy,
                createdAtMillis = now,
                updatedAtMillis = now
            ),
            authorId = createdBy,
            authorName = authorName,
            createdAtMillis = now,
            updatedAtMillis = now
        )
        reference
            .setValue(suggestion.toRealtimeDatabase())
            .addOnSuccessListener {
                onResult(true, null)
            }
            .addOnFailureListener { error ->
                Log.w(TAG, "Product suggestion write failed", error)
                onResult(false, error.localizedMessage ?: "Не удалось отправить заявку")
            }
        return true
    }

    override fun approveSuggestion(suggestionId: String, product: AlcoholProduct, adminId: String): Boolean {
        val now = System.currentTimeMillis()
        val saved = product.copy(
            id = product.id.ifBlank { "admin-$now" },
            source = ProductSource.Admin,
            isVerified = true,
            createdBy = adminId,
            createdAtMillis = product.createdAtMillis.takeIf { it > 0L } ?: now,
            updatedAtMillis = now
        )
        addProduct(saved, adminId, isAdmin = true)
        suggestionsReference().child(suggestionId).updateChildren(
            mapOf(
                "status" to ProductSuggestionStatus.Approved.name,
                "updatedAtMillis" to now,
                "moderatedBy" to adminId
            )
        )
        return true
    }

    override fun rejectSuggestion(suggestionId: String, adminId: String): Boolean {
        suggestionsReference().child(suggestionId).updateChildren(
            mapOf(
                "status" to ProductSuggestionStatus.Rejected.name,
                "updatedAtMillis" to System.currentTimeMillis(),
                "moderatedBy" to adminId
            )
        )
        return true
    }

    override fun hideSuggestionAuthor(authorId: String) {
        hiddenSuggestionAuthors += authorId
        onCatalogChanged?.invoke()
    }

    override fun getDailyStats(ownerPublicId: String): List<DailyStats> =
        dailyStatsByPublicId[ownerPublicId]?.toList().orEmpty()

    override fun getDayStats(ownerPublicId: String, dayKey: String): DailyStats? =
        dailyStatsByPublicId[ownerPublicId]?.firstOrNull { it.dayKey == dayKey }

    override fun getDayEntries(ownerPublicId: String, dayKey: String): List<DrinkEntryPreview> =
        publicEntriesByPublicId["$ownerPublicId/$dayKey"]?.toList().orEmpty()

    override fun getFriends(): List<FriendProgress> =
        (incomingFriends + outgoingFriends + acceptedFriends)
            .map { it.withPublicProfile(publicProfiles[it.publicId]) }
            .distinctBy { it.publicId }

    override fun addFriendById(id: String): FriendProgress? {
        val session = currentSession ?: return null
        val normalized = PublicIdGenerator.normalize(id)
        if (!PublicIdGenerator.isValid(normalized)) return null

        if (normalized == session.publicId) {
            return null
        }

        getFriends().firstOrNull { it.publicId == normalized }?.let { return it }

        val pending = FriendProgress(
            id = normalized,
            publicId = normalized,
            name = "Заявка $normalized",
            status = "ищем профиль",
            pureAlcoholMl = 0.0,
            streakDays = 0,
            relationStatus = FriendRelationStatus.OutgoingRequest
        )
        outgoingFriends += pending
        notifyDataChanged()

        firestore.collection(PUBLIC_PROFILES)
            .document(normalized)
            .get()
            .addOnSuccessListener { snapshot ->
                val targetUid = snapshot.getString("userId")
                if (!snapshot.exists() || targetUid.isNullOrBlank()) {
                    replaceOutgoing(
                        normalized,
                        pending.copy(status = "профиль не найден")
                    )
                    return@addOnSuccessListener
                }
                if (targetUid == session.userId) {
                    replaceOutgoing(
                        normalized,
                        pending.copy(name = "Это ваш ID", status = "себя добавить нельзя")
                    )
                    return@addOnSuccessListener
                }

                val targetName = snapshot.getString("name") ?: "Друг $normalized"
                val targetPhoto = snapshot.getString("photoUrl").orEmpty()
                val batch = firestore.batch()
                batch.set(
                    userDocument(targetUid).collection(FRIEND_REQUESTS).document(session.publicId),
                    mapOf(
                        "fromUid" to session.userId,
                        "fromPublicId" to session.publicId,
                        "fromName" to session.userName,
                        "fromPhotoUrl" to session.photoUrl.orEmpty(),
                        "targetUid" to targetUid,
                        "targetPublicId" to normalized,
                        "createdAtMillis" to System.currentTimeMillis(),
                        "status" to "pending"
                    ),
                    SetOptions.merge()
                )
                batch.set(
                    userDocument(session.userId).collection(SENT_REQUESTS).document(normalized),
                    mapOf(
                        "targetUid" to targetUid,
                        "targetPublicId" to normalized,
                        "targetName" to targetName,
                        "targetPhotoUrl" to targetPhoto,
                        "createdAtMillis" to System.currentTimeMillis(),
                        "status" to "pending"
                    ),
                    SetOptions.merge()
                )
                batch.commit().addOnFailureListener { error ->
                    Log.w(TAG, "Friend request failed", error)
                    replaceOutgoing(normalized, pending.copy(status = "не удалось отправить заявку"))
                }
            }
            .addOnFailureListener { error ->
                Log.w(TAG, "Friend lookup failed", error)
                replaceOutgoing(normalized, pending.copy(status = "не удалось найти профиль"))
            }

        return pending
    }

    override fun acceptFriend(publicId: String) {
        val session = currentSession ?: return
        val normalized = PublicIdGenerator.normalize(publicId)
        val request = incomingRequests[normalized] ?: return

        val batch = firestore.batch()
        batch.set(
            userDocument(session.userId).collection(FRIENDS).document(normalized),
            mapOf(
                "id" to normalized,
                "publicId" to normalized,
                "userId" to request.fromUid,
                "name" to request.fromName,
                "photoUrl" to request.fromPhotoUrl,
                "status" to "друг подтвержден",
                "totalDrinkMl" to 0,
                "pureAlcoholMl" to 0.0,
                "streakDays" to 0,
                "relationStatus" to FriendRelationStatus.Accepted.name,
                "updatedAtMillis" to System.currentTimeMillis()
            ),
            SetOptions.merge()
        )
        batch.set(
            userDocument(request.fromUid).collection(FRIENDS).document(session.publicId),
            mapOf(
                "id" to session.publicId,
                "publicId" to session.publicId,
                "userId" to session.userId,
                "name" to session.userName,
                "photoUrl" to session.photoUrl.orEmpty(),
                "status" to "друг подтвержден",
                "totalDrinkMl" to entries.sumOf { it.volumeMl },
                "pureAlcoholMl" to entries.sumOf { it.pureAlcoholMl },
                "streakDays" to 0,
                "relationStatus" to FriendRelationStatus.Accepted.name,
                "updatedAtMillis" to System.currentTimeMillis()
            ),
            SetOptions.merge()
        )
        batch.delete(userDocument(session.userId).collection(FRIEND_REQUESTS).document(normalized))
        batch.delete(userDocument(request.fromUid).collection(SENT_REQUESTS).document(session.publicId))
        batch.commit().addOnFailureListener { error ->
            Log.w(TAG, "Accept friend failed", error)
        }.addOnSuccessListener {
            incomingRequests.remove(normalized)
            incomingFriends.removeAll { it.publicId == normalized }
            acceptedFriends.removeAll { it.publicId == normalized }
            acceptedFriends += FriendProgress(
                id = normalized,
                publicId = normalized,
                name = request.fromName,
                status = "друг подтвержден",
                pureAlcoholMl = 0.0,
                streakDays = 0,
                photoUrl = request.fromPhotoUrl,
                relationStatus = FriendRelationStatus.Accepted
            ).withPublicProfile(publicProfiles[normalized])
            notifyDataChanged()
        }
    }

    override fun rejectFriend(publicId: String) {
        val session = currentSession ?: return
        val normalized = PublicIdGenerator.normalize(publicId)
        val request = incomingRequests[normalized]
        val batch = firestore.batch()
        batch.delete(userDocument(session.userId).collection(FRIEND_REQUESTS).document(normalized))
        if (request != null) {
            batch.delete(userDocument(request.fromUid).collection(SENT_REQUESTS).document(session.publicId))
        }
        batch.commit().addOnFailureListener { error ->
            Log.w(TAG, "Reject friend failed", error)
        }
    }

    override fun removeFriend(publicId: String) {
        val session = currentSession ?: return
        val normalized = PublicIdGenerator.normalize(publicId)
        val friend = acceptedFriends.firstOrNull { it.publicId == normalized }
        val batch = firestore.batch()
        batch.delete(userDocument(session.userId).collection(FRIENDS).document(normalized))
        val friendUid = publicProfiles[normalized]?.userId ?: friend?.id.orEmpty()
        if (friendUid.isNotBlank() && friendUid != normalized) {
            batch.delete(userDocument(friendUid).collection(FRIENDS).document(session.publicId))
        }
        batch.commit().addOnFailureListener { error ->
            Log.w(TAG, "Remove friend failed", error)
        }.addOnSuccessListener {
            acceptedFriends.removeAll { it.publicId == normalized }
            notifyDataChanged()
        }
    }

    override fun bindSession(
        session: UserSession,
        onDataChanged: () -> Unit,
        onProfileChanged: (UserProfile) -> Unit
    ) {
        unbind()
        currentSession = session
        this.onDataChanged = onDataChanged
        saveProfile(session, FcmTokenStore.lastToken)

        val userDocument = userDocument(session.userId)
        listeners += userDocument.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "Profile listen failed", error)
                return@addSnapshotListener
            }
            snapshot?.toUserProfile()?.let { profile ->
                currentIsAdmin = profile.isAdmin
                if (profile.publicId.isNotBlank() && currentSession?.userId == profile.userId) {
                    currentSession = currentSession?.copy(publicId = profile.publicId)
                }
                onProfileChanged(profile)
            }
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
            updateStatsAndProfile(session)
            notifyDataChanged()
        }
        listeners += userDocument.collection(FRIENDS).addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "Friends listen failed", error)
                return@addSnapshotListener
            }
            acceptedFriends.clear()
            snapshot?.documents
                ?.mapNotNull { it.toFriendProgress() }
                ?.let(acceptedFriends::addAll)
            acceptedFriends.forEach { friend ->
                if (friendsSnapshotInitialized) showOnce(
                    key = "accepted_friend_${friend.publicId}",
                    channel = BuhloNotificationCenter.Channel.Friends,
                    title = "Заявка принята",
                    body = "${friend.name} теперь у тебя в друзьях",
                    screen = "friends"
                )
            }
            friendsSnapshotInitialized = true
            notifyDataChanged()
        }
        listeners += firestore.collection(PUBLIC_PROFILES).addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "Public profiles listen failed", error)
                return@addSnapshotListener
            }
            publicProfiles.clear()
            snapshot?.documents
                ?.mapNotNull { it.toPublicFriendProfile() }
                ?.forEach {
                    publicProfiles[it.publicId] = it
                    if (it.publicId != currentSession?.publicId) {
                        dailyStatsByPublicId[it.publicId] = mutableListOf(
                            DailyStats(
                                dayKey = formatDayKey(System.currentTimeMillis()),
                                totalVolumeMl = it.totalDrinkMl,
                                totalDrinkMl = it.totalDrinkMl,
                                totalPureAlcoholMl = it.pureAlcoholMl,
                                entriesCount = if (it.pureAlcoholMl > 0.0) 1 else 0,
                                moodFace = moodFace(it.pureAlcoholMl),
                                moodTitle = moodTitle(it.pureAlcoholMl),
                                updatedAtMillis = System.currentTimeMillis()
                            )
                        )
                    }
                }
            notifyDataChanged()
        }
        listeners += userDocument.collection(FRIEND_REQUESTS).addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "Incoming requests listen failed", error)
                return@addSnapshotListener
            }
            incomingRequests.clear()
            incomingFriends.clear()
            snapshot?.documents?.forEach { document ->
                document.toIncomingRequest()?.let { request ->
                    incomingRequests[request.fromPublicId] = request
                    incomingFriends += request.toFriendProgress()
                    if (requestsSnapshotInitialized) showOnce(
                        key = "friend_request_${request.fromPublicId}",
                        channel = BuhloNotificationCenter.Channel.Friends,
                        title = "Новая заявка в друзья",
                        body = "${request.fromName} хочет добавить тебя в друзья",
                        screen = "friends"
                    )
                }
            }
            requestsSnapshotInitialized = true
            notifyDataChanged()
        }
        listeners += userDocument.collection(SENT_REQUESTS).addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "Sent requests listen failed", error)
                return@addSnapshotListener
            }
            outgoingFriends.clear()
            snapshot?.documents
                ?.mapNotNull { it.toOutgoingFriend() }
                ?.let { outgoing ->
                    outgoingFriends.addAll(outgoing)
                    outgoing.filter { it.relationStatus == FriendRelationStatus.Accepted }.forEach { friend ->
                        showOnce(
                            key = "friend_accepted_${friend.publicId}",
                            channel = BuhloNotificationCenter.Channel.Friends,
                            title = "Заявка принята",
                            body = "${friend.name} теперь у тебя в друзьях",
                            screen = "friends"
                        )
                    }
                }
            notifyDataChanged()
        }
    }

    override fun unbind() {
        listeners.forEach { it.remove() }
        listeners.clear()
        catalogListener?.remove()
        catalogListener = null
        suggestionsListener?.let { suggestionsReference().removeEventListener(it) }
        suggestionsListener = null
        currentSession = null
        onDataChanged = null
        entries.clear()
        acceptedFriends.clear()
        incomingRequests.clear()
        incomingFriends.clear()
        outgoingFriends.clear()
        publicProfiles.clear()
        suggestions.clear()
        hiddenSuggestionAuthors.clear()
        dailyStatsByPublicId.clear()
        publicEntriesByPublicId.clear()
        currentIsAdmin = false
        shownNotifications.clear()
        suggestionsSnapshotInitialized = false
        friendsSnapshotInitialized = false
        requestsSnapshotInitialized = false
    }

    override fun saveProfile(session: UserSession, fcmToken: String?) {
        val email = session.email?.trim()?.lowercase().orEmpty()
        val pureAlcoholMl = entries.sumOf { it.pureAlcoholMl }

        userDocument(session.userId).get().addOnSuccessListener { snapshot ->
            val publicId = snapshot.getString("publicId").orEmpty()
                .ifBlank { session.publicId }
                .ifBlank { PublicIdGenerator.fromUserId(session.userId) }
            if (currentSession?.userId == session.userId && currentSession?.publicId != publicId) {
                currentSession = currentSession?.copy(publicId = publicId)
            }

            val profileData = mutableMapOf<String, Any>(
                "userId" to session.userId,
                "publicId" to publicId,
                "name" to session.userName,
                "email" to email,
                "provider" to session.provider.analyticsName,
                "photoUrl" to session.photoUrl.orEmpty(),
                "fcmToken" to fcmToken.orEmpty(),
                "updatedAtMillis" to System.currentTimeMillis()
            )
            if (!snapshot.contains("isAdmin")) {
                profileData["isAdmin"] = false
            }

            userDocument(session.userId).set(profileData, SetOptions.merge())
            firestore.collection(PUBLIC_PROFILES)
                .document(publicId)
                .set(
                    mapOf(
                    "userId" to session.userId,
                    "publicId" to publicId,
                    "name" to session.userName,
                    "email" to email,
                    "provider" to session.provider.analyticsName,
                    "photoUrl" to session.photoUrl.orEmpty(),
                    "pureAlcoholMl" to pureAlcoholMl,
                    "totalDrinkMl" to entries.sumOf { it.volumeMl },
                    "moodFace" to FriendProgress(
                        id = publicId,
                        publicId = publicId,
                        name = session.userName,
                        status = "",
                        totalDrinkMl = entries.sumOf { it.volumeMl },
                        pureAlcoholMl = pureAlcoholMl,
                        streakDays = 0
                    ).moodFace,
                    "moodTitle" to FriendProgress(
                        id = publicId,
                        publicId = publicId,
                        name = session.userName,
                        status = "",
                        totalDrinkMl = entries.sumOf { it.volumeMl },
                        pureAlcoholMl = pureAlcoholMl,
                        streakDays = 0
                    ).moodTitle,
                    "updatedAtMillis" to System.currentTimeMillis()
                    ),
                    SetOptions.merge()
                )
        }
    }

    private fun updateStatsAndProfile(session: UserSession) = runCatching {
        val activeSession = currentSession?.takeIf { it.userId == session.userId } ?: session
        val publicId = activeSession.publicId.ifBlank { PublicIdGenerator.fromUserId(activeSession.userId) }
        val stats = buildStats(entries)
        dailyStatsByPublicId[publicId] = stats.toMutableList()
        val batch = firestore.batch()
        stats.forEach { stat ->
            batch.set(
                userDocument(activeSession.userId).collection(DAILY_STATS).document(stat.dayKey),
                stat.toFirestore(),
                SetOptions.merge()
            )
            batch.set(
                firestore.collection(PUBLIC_PROFILES)
                    .document(publicId)
                    .collection(DAILY_STATS)
                    .document(stat.dayKey),
                stat.toFirestore(),
                SetOptions.merge()
            )
            val previews = entries
                .filter { it.dayKey.ifBlank { formatDayKey(it.timestampMillis) } == stat.dayKey }
                .map { it.toPreview() }
            publicEntriesByPublicId["$publicId/${stat.dayKey}"] = previews.toMutableList()
            batch.set(
                firestore.collection(PUBLIC_PROFILES)
                    .document(publicId)
                    .collection(PUBLIC_DAY_ENTRIES)
                    .document(stat.dayKey),
                mapOf("entries" to previews.map { it.toFirestore() }),
                SetOptions.merge()
            )
        }
        if (stats.isNotEmpty()) {
            batch.commit().addOnFailureListener { error ->
                Log.w(TAG, "Daily stats update failed", error)
            }
        }
        saveProfile(activeSession, FcmTokenStore.lastToken)
    }.onFailure { error ->
        Log.w(TAG, "Stats/profile update failed", error)
    }

    override fun updateFcmToken(userId: String, token: String) {
        userDocument(userId)
            .set(
                mapOf(
                    "fcmToken" to token,
                    "updatedAtMillis" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            )
    }

    private fun userDocument(userId: String) =
        firestore.collection(USERS).document(userId)

    private fun suggestionsReference() =
        realtimeDatabase.reference.child(PRODUCT_SUGGESTIONS)

    private fun notifyDataChanged() {
        onDataChanged?.invoke()
    }

    private fun loadMyRatings() {
        val userId = currentSession?.userId ?: return
        products.forEach { product ->
            firestore.collection(PRODUCTS)
                .document(product.id)
                .collection(RATINGS)
                .document(userId)
                .get()
                .addOnSuccessListener { snapshot ->
                    val rating = snapshot.getLong("value")?.toInt()?.coerceIn(1, 10) ?: return@addOnSuccessListener
                    products.replaceAll {
                        if (it.id == product.id) it.copy(myRating = rating) else it
                    }
                    onCatalogChanged?.invoke()
                }
        }
    }

    private fun showOnce(
        key: String,
        channel: BuhloNotificationCenter.Channel,
        title: String,
        body: String,
        screen: String
    ) {
        if (!shownNotifications.add(key)) return
        notifications.show(channel, key, title, body, screen)
    }

    private fun replaceOutgoing(publicId: String, friend: FriendProgress) {
        outgoingFriends.removeAll { it.publicId == publicId }
        outgoingFriends += friend
        notifyDataChanged()
    }

    private fun DrinkEntry.toFirestore(): Map<String, Any> = mapOf(
        "id" to id,
        "type" to type.name,
        "volumeMl" to volumeMl,
        "strengthPercent" to strengthPercent,
        "note" to note,
        "timestampMillis" to timestampMillis,
        "productId" to productId,
        "barcode" to barcode,
        "productName" to productName,
        "brand" to brand,
        "category" to category,
        "imageUrl" to imageUrl,
        "dayKey" to dayKey.ifBlank { formatDayKey(timestampMillis) }
    )

    private fun DocumentSnapshot.toDrinkEntry(): DrinkEntry? {
        val type = getString("type")?.let { runCatching { DrinkType.valueOf(it) }.getOrNull() }
            ?: return null
        return DrinkEntry(
            id = getLong("id") ?: id.toLongOrNull() ?: return null,
            type = type,
            volumeMl = getLong("volumeMl")?.toInt() ?: 0,
            strengthPercent = getDouble("strengthPercent") ?: 0.0,
            note = getString("note").orEmpty(),
            timestampMillis = getLong("timestampMillis") ?: 0L,
            productId = getString("productId").orEmpty(),
            barcode = getString("barcode").orEmpty(),
            productName = getString("productName") ?: getString("type").orEmpty(),
            brand = getString("brand").orEmpty(),
            category = getString("category").orEmpty(),
            imageUrl = getString("imageUrl").orEmpty(),
            dayKey = getString("dayKey").orEmpty()
        )
    }

    private fun AlcoholProduct.toFirestore(): Map<String, Any> = mapOf(
        "id" to id,
        "barcode" to barcode,
        "name" to name,
        "brand" to brand,
        "description" to description,
        "category" to category.storageKey,
        "volumeMl" to volumeMl,
        "strengthPercent" to strengthPercent,
        "imageUrl" to imageUrl,
        "recommendedPriceRub" to (recommendedPriceRub ?: 0),
        "ratingSum" to ratingSum,
        "ratingCount" to ratingCount,
        "averageRating" to averageRating,
        "source" to source.name,
        "isVerified" to isVerified,
        "tags" to tags,
        "isActive" to isActive,
        "deletedAtMillis" to deletedAtMillis,
        "updatedBy" to updatedBy,
        "createdBy" to createdBy,
        "createdAtMillis" to createdAtMillis,
        "updatedAtMillis" to updatedAtMillis
    )

    private fun DocumentSnapshot.toAlcoholProduct(): AlcoholProduct? {
        val name = getString("name") ?: return null
        val ratingSum = getLong("ratingSum")?.toInt() ?: 0
        val ratingCount = getLong("ratingCount")?.toInt() ?: 0
        return AlcoholProduct(
            id = getString("id") ?: id,
            barcode = getString("barcode").orEmpty(),
            name = name,
            brand = getString("brand").orEmpty(),
            description = getString("description").orEmpty(),
            category = AlcoholCategory.fromStorageKey(getString("category").orEmpty()),
            volumeMl = getLong("volumeMl")?.toInt() ?: 0,
            strengthPercent = getDouble("strengthPercent") ?: 0.0,
            imageUrl = getString("imageUrl").orEmpty(),
            recommendedPriceRub = getLong("recommendedPriceRub")?.toInt()?.takeIf { it > 0 },
            ratingSum = ratingSum,
            ratingCount = ratingCount,
            averageRating = getDouble("averageRating") ?: if (ratingCount > 0) ratingSum.toDouble() / ratingCount else 0.0,
            source = getString("source")?.let { runCatching { ProductSource.valueOf(it) }.getOrNull() }
                ?: ProductSource.Imported,
            isVerified = getBoolean("isVerified") ?: false,
            tags = (get("tags") as? List<*>)?.mapNotNull { it?.toString() }
                ?.ifEmpty { ProductTag.defaultFor(AlcoholCategory.fromStorageKey(getString("category").orEmpty())) }
                ?: ProductTag.defaultFor(AlcoholCategory.fromStorageKey(getString("category").orEmpty())),
            isActive = getBoolean("isActive") ?: true,
            deletedAtMillis = getLong("deletedAtMillis") ?: 0L,
            updatedBy = getString("updatedBy").orEmpty(),
            createdBy = getString("createdBy").orEmpty(),
            createdAtMillis = getLong("createdAtMillis") ?: 0L,
            updatedAtMillis = getLong("updatedAtMillis") ?: 0L
        )
    }

    private fun DocumentSnapshot.toUserProfile(): UserProfile? {
        if (!exists()) return null
        val userId = getString("userId").orEmpty().ifBlank { id }
        return UserProfile(
            userId = userId,
            publicId = getString("publicId").orEmpty().ifBlank { PublicIdGenerator.fromUserId(userId) },
            name = getString("name").orEmpty(),
            email = getString("email").orEmpty(),
            provider = getString("provider").orEmpty(),
            photoUrl = getString("photoUrl").orEmpty(),
            fcmToken = getString("fcmToken").orEmpty(),
            isAdmin = getBoolean("isAdmin") == true,
            updatedAtMillis = getLong("updatedAtMillis") ?: 0L
        )
    }

    private fun ProductSuggestion.toRealtimeDatabase(): Map<String, Any> = mapOf(
        "id" to id,
        "product" to product.toRealtimeDatabase(),
        "authorId" to authorId,
        "authorName" to authorName,
        "status" to status.name,
        "createdAtMillis" to createdAtMillis,
        "updatedAtMillis" to updatedAtMillis
    )

    private fun AlcoholProduct.toRealtimeDatabase(): Map<String, Any> =
        toFirestore()

    private fun DataSnapshot.toProductSuggestion(): ProductSuggestion? {
        val productSnapshot = child("product")
        val name = productSnapshot.childString("name").ifBlank { return null }
        val category = AlcoholCategory.fromStorageKey(productSnapshot.childString("category"))
        val product = AlcoholProduct(
            id = productSnapshot.childString("id").ifBlank { key.orEmpty() },
            barcode = productSnapshot.childString("barcode"),
            name = name,
            brand = productSnapshot.childString("brand"),
            description = productSnapshot.childString("description"),
            category = category,
            volumeMl = productSnapshot.childLong("volumeMl").toInt().takeIf { it > 0 } ?: 500,
            strengthPercent = productSnapshot.childDouble("strengthPercent"),
            imageUrl = productSnapshot.childString("imageUrl"),
            recommendedPriceRub = productSnapshot.childLong("recommendedPriceRub").toInt().takeIf { it > 0 },
            ratingSum = productSnapshot.childLong("ratingSum").toInt(),
            ratingCount = productSnapshot.childLong("ratingCount").toInt(),
            averageRating = productSnapshot.childDouble("averageRating"),
            source = productSnapshot.childString("source")
                .let { runCatching { ProductSource.valueOf(it) }.getOrDefault(ProductSource.UserSuggested) },
            isVerified = productSnapshot.childBool("isVerified"),
            tags = productSnapshot.child("tags").children.mapNotNull { it.value?.toString() }
                .ifEmpty { ProductTag.defaultFor(category) },
            isActive = productSnapshot.child("isActive").value?.let { productSnapshot.childBool("isActive") } ?: true,
            deletedAtMillis = productSnapshot.childLong("deletedAtMillis"),
            updatedBy = productSnapshot.childString("updatedBy"),
            createdBy = productSnapshot.childString("createdBy"),
            createdAtMillis = productSnapshot.childLong("createdAtMillis"),
            updatedAtMillis = productSnapshot.childLong("updatedAtMillis")
        )
        return ProductSuggestion(
            id = childString("id").ifBlank { key.orEmpty() },
            product = product,
            authorId = childString("authorId"),
            authorName = childString("authorName").ifBlank { "Пользователь" },
            status = childString("status")
                .let { runCatching { ProductSuggestionStatus.valueOf(it) }.getOrDefault(ProductSuggestionStatus.Pending) },
            createdAtMillis = childLong("createdAtMillis"),
            updatedAtMillis = childLong("updatedAtMillis")
        )
    }

    private fun DataSnapshot.childString(name: String): String =
        child(name).value?.toString().orEmpty()

    private fun DataSnapshot.childLong(name: String): Long =
        when (val value = child(name).value) {
            is Long -> value
            is Int -> value.toLong()
            is Double -> value.toLong()
            is String -> value.toLongOrNull() ?: 0L
            else -> 0L
        }

    private fun DataSnapshot.childDouble(name: String): Double =
        when (val value = child(name).value) {
            is Double -> value
            is Long -> value.toDouble()
            is Int -> value.toDouble()
            is String -> value.toDoubleOrNull() ?: 0.0
            else -> 0.0
        }

    private fun DataSnapshot.childBool(name: String): Boolean =
        when (val value = child(name).value) {
            is Boolean -> value
            is String -> value.toBooleanStrictOrNull() ?: false
            else -> false
        }

    private fun DocumentSnapshot.toFriendProgress(): FriendProgress? {
        val publicId = getString("publicId") ?: getString("id") ?: id
        return FriendProgress(
            id = publicId,
            publicId = publicId,
            name = getString("name") ?: "Друг $publicId",
            status = getString("status") ?: "друг подтвержден",
            pureAlcoholMl = getDouble("pureAlcoholMl") ?: 0.0,
            totalDrinkMl = getLong("totalDrinkMl")?.toInt()
                ?: getLong("totalVolumeMl")?.toInt()
                ?: 0,
            streakDays = getLong("streakDays")?.toInt() ?: 0,
            photoUrl = getString("photoUrl").orEmpty(),
            relationStatus = FriendRelationStatus.Accepted
        )
    }

    private fun DocumentSnapshot.toIncomingRequest(): IncomingFriendRequest? {
        val fromUid = getString("fromUid") ?: return null
        val fromPublicId = getString("fromPublicId") ?: id
        return IncomingFriendRequest(
            fromUid = fromUid,
            fromPublicId = fromPublicId,
            fromName = getString("fromName") ?: "Друг $fromPublicId",
            fromPhotoUrl = getString("fromPhotoUrl").orEmpty()
        )
    }

    private fun IncomingFriendRequest.toFriendProgress(): FriendProgress =
        FriendProgress(
            id = fromPublicId,
            publicId = fromPublicId,
            name = fromName,
            status = "хочет добавить вас в друзья",
            totalDrinkMl = 0,
            pureAlcoholMl = 0.0,
            streakDays = 0,
            photoUrl = fromPhotoUrl,
            relationStatus = FriendRelationStatus.IncomingRequest
        )

    private fun DocumentSnapshot.toOutgoingFriend(): FriendProgress? {
        val targetPublicId = getString("targetPublicId") ?: id
        return FriendProgress(
            id = targetPublicId,
            publicId = targetPublicId,
            name = getString("targetName") ?: "Друг $targetPublicId",
            status = "заявка отправлена, ждем подтверждения",
            totalDrinkMl = 0,
            pureAlcoholMl = 0.0,
            streakDays = 0,
            photoUrl = getString("targetPhotoUrl").orEmpty(),
            relationStatus = FriendRelationStatus.OutgoingRequest
        )
    }

    private fun DocumentSnapshot.toPublicFriendProfile(): PublicFriendProfile? {
        val publicId = getString("publicId") ?: id
        return PublicFriendProfile(
            userId = getString("userId").orEmpty(),
            publicId = publicId,
            name = getString("name") ?: "Друг $publicId",
            photoUrl = getString("photoUrl").orEmpty(),
            pureAlcoholMl = getDouble("pureAlcoholMl") ?: 0.0,
            totalDrinkMl = getLong("totalDrinkMl")?.toInt()
                ?: getLong("totalVolumeMl")?.toInt()
                ?: 0
        )
    }

    private fun DrinkEntry.toPreview(): DrinkEntryPreview =
        DrinkEntryPreview(
            productName = productName.ifBlank { type.title },
            brand = brand,
            imageUrl = imageUrl,
            volumeMl = volumeMl,
            strengthPercent = strengthPercent,
            pureAlcoholMl = pureAlcoholMl,
            timestampMillis = timestampMillis
        )

    private fun DrinkEntryPreview.toFirestore(): Map<String, Any> = mapOf(
        "productName" to productName,
        "brand" to brand,
        "imageUrl" to imageUrl,
        "volumeMl" to volumeMl,
        "strengthPercent" to strengthPercent,
        "pureAlcoholMl" to pureAlcoholMl,
        "timestampMillis" to timestampMillis
    )

    private fun buildStats(sourceEntries: List<DrinkEntry>): List<DailyStats> =
        sourceEntries
            .groupBy { it.dayKey.ifBlank { formatDayKey(it.timestampMillis) } }
            .map { (key, dayEntries) ->
                val pure = dayEntries.sumOf { it.pureAlcoholMl }
                val total = dayEntries.sumOf { it.volumeMl }
                DailyStats(
                    dayKey = key,
                    totalVolumeMl = total,
                    totalDrinkMl = total,
                    totalPureAlcoholMl = pure,
                    entriesCount = dayEntries.size,
                    moodFace = moodFace(pure),
                    moodTitle = moodTitle(pure),
                    updatedAtMillis = dayEntries.maxOfOrNull { it.timestampMillis } ?: System.currentTimeMillis()
                )
            }

    private fun DailyStats.toFirestore(): Map<String, Any> = mapOf(
        "dayKey" to dayKey,
        "totalVolumeMl" to totalVolumeMl,
        "totalDrinkMl" to totalDrinkMl,
        "totalPureAlcoholMl" to totalPureAlcoholMl,
        "entriesCount" to entriesCount,
        "moodFace" to moodFace,
        "moodTitle" to moodTitle,
        "updatedAtMillis" to updatedAtMillis
    )

    private fun defaultProducts(): List<AlcoholProduct> {
        val now = System.currentTimeMillis()
        return listOf(
            AlcoholProduct(
                id = "seed-beer",
                name = "Пенное учебное",
                brand = "BuhloSoft",
                description = "Базовое пиво для первого запуска.",
                category = AlcoholCategory.Beer,
                volumeMl = 500,
                strengthPercent = 5.0,
                source = ProductSource.Imported,
                isVerified = true,
                tags = listOf(ProductTag.Beer.title),
                createdBy = "seed",
                createdAtMillis = now,
                updatedAtMillis = now
            ),
            AlcoholProduct(
                id = "seed-wine",
                name = "Вино лабораторное",
                brand = "BuhloSoft",
                description = "Демо-вино для каталога.",
                category = AlcoholCategory.Wine,
                volumeMl = 150,
                strengthPercent = 12.0,
                source = ProductSource.Imported,
                isVerified = true,
                tags = listOf(ProductTag.Wine.title),
                createdBy = "seed",
                createdAtMillis = now,
                updatedAtMillis = now
            ),
            AlcoholProduct(
                id = "seed-liqueur",
                name = "Ликер апельсиновый",
                brand = "BuhloSoft",
                description = "Учебная настойка в стиле Jagermeister Orange.",
                category = AlcoholCategory.Liqueur,
                volumeMl = 700,
                strengthPercent = 33.0,
                source = ProductSource.Imported,
                isVerified = true,
                tags = listOf(ProductTag.Liqueur.title),
                createdBy = "seed",
                createdAtMillis = now,
                updatedAtMillis = now
            )
        )
    }

    private fun FriendProgress.withPublicProfile(profile: PublicFriendProfile?): FriendProgress {
        if (profile == null || relationStatus != FriendRelationStatus.Accepted) return this
        val merged = copy(
            name = profile.name.ifBlank { name },
            photoUrl = profile.photoUrl.ifBlank { photoUrl },
            pureAlcoholMl = profile.pureAlcoholMl,
            totalDrinkMl = profile.totalDrinkMl,
        )
        return merged.copy(status = merged.moodTitle)
    }

    private data class IncomingFriendRequest(
        val fromUid: String,
        val fromPublicId: String,
        val fromName: String,
        val fromPhotoUrl: String
    )

    private data class PublicFriendProfile(
        val userId: String,
        val publicId: String,
        val name: String,
        val photoUrl: String,
        val pureAlcoholMl: Double,
        val totalDrinkMl: Int
    )

    private companion object {
        const val TAG = "FirebaseBuhlografRepo"
        const val USERS = "users"
        const val PUBLIC_PROFILES = "public_profiles"
        const val PRODUCTS = "products"
        const val PRODUCT_SUGGESTIONS = "product_suggestions"
        const val DRINKS = "drinks"
        const val DAILY_STATS = "daily_stats"
        const val PUBLIC_DAY_ENTRIES = "public_day_entries"
        const val FRIENDS = "friends"
        const val FRIEND_REQUESTS = "friend_requests"
        const val SENT_REQUESTS = "sent_requests"
        const val RATINGS = "ratings"
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
                KEY_BANNER to "",
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
            ""
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
