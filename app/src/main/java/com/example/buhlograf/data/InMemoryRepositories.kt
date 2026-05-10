package com.example.buhlograf.data

import com.example.buhlograf.domain.DrinkEntry
import com.example.buhlograf.domain.DrinkRepository
import com.example.buhlograf.domain.DrinkType
import com.example.buhlograf.domain.AlcoholCategory
import com.example.buhlograf.domain.AlcoholProduct
import com.example.buhlograf.domain.CatalogLoadState
import com.example.buhlograf.domain.CatalogRepository
import com.example.buhlograf.domain.DailyStats
import com.example.buhlograf.domain.DailyStatsRepository
import com.example.buhlograf.domain.DrinkEntryPreview
import com.example.buhlograf.domain.FriendProgress
import com.example.buhlograf.domain.FriendRelationStatus
import com.example.buhlograf.domain.FriendsRepository
import com.example.buhlograf.domain.ProductSource
import com.example.buhlograf.domain.ProductSuggestion
import com.example.buhlograf.domain.ProductSuggestionStatus
import com.example.buhlograf.domain.PublicIdGenerator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

class InMemoryDrinkRepository : DrinkRepository {
    private val ids = AtomicLong(1)
    private val entries = mutableListOf<DrinkEntry>()

    override fun getEntries(): List<DrinkEntry> =
        entries.sortedByDescending { it.timestampMillis }

    override fun addEntry(
        type: DrinkType,
        volumeMl: Int,
        strengthPercent: Double,
        note: String
    ): DrinkEntry {
        val entry = DrinkEntry(
            id = ids.getAndIncrement(),
            type = type,
            volumeMl = volumeMl,
            strengthPercent = strengthPercent,
            note = note.trim(),
            timestampMillis = System.currentTimeMillis()
        )
        entries += entry
        return entry
    }

    override fun addEntry(
        product: AlcoholProduct,
        volumeMl: Int,
        strengthPercent: Double,
        note: String
    ): DrinkEntry {
        val entry = DrinkEntry(
            id = ids.getAndIncrement(),
            type = product.category.defaultType,
            volumeMl = volumeMl,
            strengthPercent = strengthPercent,
            note = note.trim(),
            timestampMillis = System.currentTimeMillis(),
            productId = product.id,
            barcode = product.barcode,
            productName = product.name,
            brand = product.brand,
            category = product.category.storageKey,
            imageUrl = product.imageUrl,
            dayKey = formatDayKey(System.currentTimeMillis())
        )
        entries += entry
        return entry
    }

    override fun clearToday() {
        entries.clear()
    }
}

class LocalCatalogRepository : CatalogRepository {
    private val products = mutableListOf(
        AlcoholProduct(
            id = "demo-beer",
            name = "Пенное учебное",
            brand = "BuhloSoft",
            description = "Демо-пиво для офлайн-режима.",
            category = AlcoholCategory.Beer,
            volumeMl = 500,
            strengthPercent = 5.0,
            source = ProductSource.Manual,
            isVerified = true
        ),
        AlcoholProduct(
            id = "demo-liqueur",
            name = "Ликер лабораторный",
            brand = "BuhloSoft",
            description = "Демо-ликер, чтобы каталог не был пустым.",
            category = AlcoholCategory.Liqueur,
            volumeMl = 700,
            strengthPercent = 33.0,
            source = ProductSource.Manual,
            isVerified = true
        )
    )
    private val suggestions = mutableListOf<ProductSuggestion>()

    override fun getProducts(): List<AlcoholProduct> =
        products.sortedWith(compareByDescending<AlcoholProduct> { it.ratingCount > 0 }
            .thenByDescending { it.averageRating }
            .thenByDescending { it.ratingCount }
            .thenBy { it.name.lowercase() })

    override fun getSuggestions(): List<ProductSuggestion> =
        suggestions.filter { it.status == ProductSuggestionStatus.Pending }

    override fun getLoadState(): CatalogLoadState = CatalogLoadState.Ready(fromCache = true)

    override fun startListening(onChanged: () -> Unit) {
        onChanged()
    }

    override fun startSuggestionsListening(onChanged: () -> Unit) {
        onChanged()
    }

    override fun addProduct(product: AlcoholProduct, createdBy: String, isAdmin: Boolean): Boolean {
        if (!isAdmin) return false
        products.removeAll { it.id == product.id }
        products += product.copy(createdBy = createdBy, updatedBy = createdBy, updatedAtMillis = System.currentTimeMillis())
        return true
    }

    override fun updateProduct(product: AlcoholProduct, isAdmin: Boolean): Boolean =
        addProduct(product, product.createdBy, isAdmin)

    override fun setProductActive(productId: String, isActive: Boolean, adminId: String, isAdmin: Boolean): Boolean {
        if (!isAdmin) return false
        products.replaceAll {
            if (it.id == productId) {
                it.copy(
                    isActive = isActive,
                    deletedAtMillis = if (isActive) 0L else System.currentTimeMillis(),
                    updatedBy = adminId,
                    updatedAtMillis = System.currentTimeMillis()
                )
            } else {
                it
            }
        }
        return true
    }

    override fun rateProduct(productId: String, userId: String, value: Int): Boolean {
        val rating = value.coerceIn(1, 10)
        products.replaceAll { product ->
            if (product.id != productId) return@replaceAll product
            val old = product.myRating
            val newCount = if (old == null) product.ratingCount + 1 else product.ratingCount
            val newSum = product.ratingSum - (old ?: 0) + rating
            product.copy(
                ratingSum = newSum,
                ratingCount = newCount,
                averageRating = if (newCount > 0) newSum.toDouble() / newCount else 0.0,
                myRating = rating
            )
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
        suggestions += ProductSuggestion(
            id = "local-${System.currentTimeMillis()}",
            product = product.copy(source = ProductSource.UserSuggested, createdBy = createdBy),
            authorId = createdBy,
            authorName = authorName,
            createdAtMillis = System.currentTimeMillis(),
            updatedAtMillis = System.currentTimeMillis()
        )
        onResult(true, null)
        return true
    }

    override fun approveSuggestion(suggestionId: String, product: AlcoholProduct, adminId: String): Boolean {
        suggestions.replaceAll {
            if (it.id == suggestionId) it.copy(status = ProductSuggestionStatus.Approved) else it
        }
        return addProduct(product, adminId, true)
    }

    override fun rejectSuggestion(suggestionId: String, adminId: String): Boolean {
        suggestions.replaceAll {
            if (it.id == suggestionId) it.copy(status = ProductSuggestionStatus.Rejected) else it
        }
        return true
    }

    override fun hideSuggestionAuthor(authorId: String) = Unit

}

class LocalDailyStatsRepository(
    private val drinkRepository: DrinkRepository
) : DailyStatsRepository {
    override fun getDailyStats(ownerPublicId: String): List<DailyStats> =
        drinkRepository.getEntries()
            .groupBy { it.dayKey.ifBlank { formatDayKey(it.timestampMillis) } }
            .map { (key, entries) ->
                DailyStats(
                    dayKey = key,
                    totalVolumeMl = entries.sumOf { it.volumeMl },
                    totalDrinkMl = entries.sumOf { it.volumeMl },
                    totalPureAlcoholMl = entries.sumOf { it.pureAlcoholMl },
                    entriesCount = entries.size,
                    moodFace = moodFace(entries.sumOf { it.pureAlcoholMl }),
                    moodTitle = moodTitle(entries.sumOf { it.pureAlcoholMl }),
                    updatedAtMillis = entries.maxOfOrNull { it.timestampMillis } ?: 0L
                )
            }

    override fun getDayStats(ownerPublicId: String, dayKey: String): DailyStats? =
        getDailyStats(ownerPublicId).firstOrNull { it.dayKey == dayKey }

    override fun getDayEntries(ownerPublicId: String, dayKey: String): List<DrinkEntryPreview> =
        drinkRepository.getEntries()
            .filter { it.dayKey.ifBlank { formatDayKey(it.timestampMillis) } == dayKey }
            .map {
                DrinkEntryPreview(
                    productName = it.productName,
                    brand = it.brand,
                    imageUrl = it.imageUrl,
                    volumeMl = it.volumeMl,
                    strengthPercent = it.strengthPercent,
                    pureAlcoholMl = it.pureAlcoholMl,
                    timestampMillis = it.timestampMillis
                )
            }
}

class LocalFriendsRepository : FriendsRepository {
    private val friends = mutableListOf<FriendProgress>()

    override fun getFriends(): List<FriendProgress> = friends.toList()

    override fun addFriendById(id: String): FriendProgress? {
        val normalized = PublicIdGenerator.normalize(id)
        if (!PublicIdGenerator.isValid(normalized)) return null
        if (normalized == "DEMO01") return null
        friends.firstOrNull { it.publicId == normalized }?.let { return it }

        val friend = FriendProgress(
            id = normalized,
            publicId = normalized,
            name = "Друг $normalized",
            status = "заявка ожидает подтверждения",
            totalDrinkMl = 0,
            pureAlcoholMl = 0.0,
            streakDays = 0,
            relationStatus = FriendRelationStatus.OutgoingRequest
        )
        friends += friend
        return friend
    }

    override fun acceptFriend(publicId: String) {
        val normalized = PublicIdGenerator.normalize(publicId)
        friends.replaceAll {
            if (it.publicId == normalized) {
                it.copy(status = "друг подтвержден", relationStatus = FriendRelationStatus.Accepted)
            } else {
                it
            }
        }
    }

    override fun rejectFriend(publicId: String) {
        val normalized = PublicIdGenerator.normalize(publicId)
        friends.removeAll { it.publicId == normalized }
    }

    override fun removeFriend(publicId: String) {
        rejectFriend(publicId)
    }
}

fun formatDayKey(timestampMillis: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestampMillis))

fun moodFace(pureAlcoholMl: Double): String = when {
    pureAlcoholMl >= 140.0 -> "💀"
    pureAlcoholMl >= 115.0 -> "😡"
    pureAlcoholMl >= 95.0 -> "🤯"
    pureAlcoholMl >= 80.0 -> "😵‍💫"
    pureAlcoholMl >= 65.0 -> "🤨"
    pureAlcoholMl >= 50.0 -> "🥴"
    pureAlcoholMl >= 35.0 -> "😄"
    pureAlcoholMl >= 25.0 -> "😎"
    pureAlcoholMl >= 15.0 -> "🙂"
    pureAlcoholMl >= 5.0 -> "😐"
    pureAlcoholMl > 0.0 -> "🥺"
    else -> "😭"
}

fun moodTitle(pureAlcoholMl: Double): String = when {
    pureAlcoholMl >= 140.0 -> "завтра будет отчет"
    pureAlcoholMl >= 115.0 -> "режим совести"
    pureAlcoholMl >= 95.0 -> "критично"
    pureAlcoholMl >= 80.0 -> "опасная зона"
    pureAlcoholMl >= 65.0 -> "режим вопросов"
    pureAlcoholMl >= 50.0 -> "шумновато"
    pureAlcoholMl >= 35.0 -> "вечер пошел"
    pureAlcoholMl >= 25.0 -> "график оживает"
    pureAlcoholMl >= 15.0 -> "культурный режим"
    pureAlcoholMl >= 5.0 -> "почти академично"
    pureAlcoholMl > 0.0 -> "совсем чуть-чуть"
    else -> "пусто и драматично"
}
