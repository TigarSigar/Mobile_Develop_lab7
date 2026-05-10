package com.example.buhlograf.domain

import kotlin.math.roundToInt

enum class DrinkType(
    val title: String,
    val emoji: String,
    val defaultVolumeMl: Int,
    val defaultStrength: Double
) {
    Beer("Пиво", "🍺", 500, 5.0),
    Wine("Вино", "🍷", 150, 12.0),
    Cocktail("Коктейль", "🍹", 250, 10.0),
    Strong("Крепкое", "🥃", 50, 40.0),
    Other("Другое", "🧃", 200, 4.0)
}

data class DrinkEntry(
    val id: Long,
    val type: DrinkType,
    val volumeMl: Int,
    val strengthPercent: Double,
    val note: String,
    val timestampMillis: Long,
    val productId: String = "",
    val barcode: String = "",
    val productName: String = type.title,
    val brand: String = "",
    val category: String = type.name.lowercase(),
    val imageUrl: String = "",
    val dayKey: String = ""
) {
    val pureAlcoholMl: Double
        get() = volumeMl * (strengthPercent / 100.0)
}

enum class AlcoholCategory(val title: String, val defaultType: DrinkType) {
    Beer("Пиво", DrinkType.Beer),
    Wine("Вино", DrinkType.Wine),
    SparklingWine("Игристое", DrinkType.Wine),
    Vodka("Водка", DrinkType.Strong),
    Whiskey("Виски", DrinkType.Strong),
    Rum("Ром", DrinkType.Strong),
    Gin("Джин", DrinkType.Strong),
    Tequila("Текила", DrinkType.Strong),
    Liqueur("Ликер", DrinkType.Strong),
    Cider("Сидр", DrinkType.Beer),
    Cocktail("Коктейль", DrinkType.Cocktail),
    LowAlcohol("Слабоалкогольное", DrinkType.Cocktail),
    Other("Другое", DrinkType.Other);

    val storageKey: String
        get() = name.replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase()

    companion object {
        fun fromStorageKey(value: String): AlcoholCategory =
            entries.firstOrNull { it.storageKey == value || it.name.equals(value, ignoreCase = true) } ?: Other
    }
}

enum class ProductSource(val title: String) {
    Admin("Админ"),
    UserSuggested("Предложено пользователем"),
    Manual("Вручную"),
    Imported("Импорт")
}

enum class ProductTag(val title: String) {
    Beer("#ПИВО"),
    Wine("#ВИНО"),
    Strong("#КРЕПКОЕ"),
    Cocktail("#КОКТЕЙЛЬ"),
    Liqueur("#ЛИКЕР"),
    Cider("#СИДР"),
    LowAlcohol("#СЛАБОЕ"),
    Other("#ДРУГОЕ");

    companion object {
        fun defaultFor(category: AlcoholCategory): List<String> = when (category) {
            AlcoholCategory.Beer -> listOf(Beer.title)
            AlcoholCategory.Wine, AlcoholCategory.SparklingWine -> listOf(Wine.title)
            AlcoholCategory.Vodka,
            AlcoholCategory.Whiskey,
            AlcoholCategory.Rum,
            AlcoholCategory.Gin,
            AlcoholCategory.Tequila -> listOf(Strong.title)
            AlcoholCategory.Liqueur -> listOf(Liqueur.title)
            AlcoholCategory.Cider -> listOf(Cider.title)
            AlcoholCategory.Cocktail -> listOf(Cocktail.title)
            AlcoholCategory.LowAlcohol -> listOf(LowAlcohol.title)
            AlcoholCategory.Other -> listOf(Other.title)
        }
    }
}

data class AlcoholProduct(
    val id: String,
    val barcode: String = "",
    val name: String,
    val brand: String = "",
    val description: String = "",
    val category: AlcoholCategory = AlcoholCategory.Other,
    val volumeMl: Int,
    val strengthPercent: Double,
    val imageUrl: String = "",
    val source: ProductSource = ProductSource.Manual,
    val isVerified: Boolean = false,
    val tags: List<String> = ProductTag.defaultFor(category),
    val isActive: Boolean = true,
    val deletedAtMillis: Long = 0L,
    val updatedBy: String = "",
    val createdBy: String = "",
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L
) {
    val pureAlcoholPerBottleMl: Double
        get() = volumeMl * (strengthPercent / 100.0)
}

enum class ProductSuggestionStatus {
    Pending,
    Approved,
    Rejected
}

data class ProductSuggestion(
    val id: String,
    val product: AlcoholProduct,
    val authorId: String,
    val authorName: String,
    val status: ProductSuggestionStatus = ProductSuggestionStatus.Pending,
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L
)

sealed interface CatalogLoadState {
    data object Loading : CatalogLoadState
    data class Ready(val fromCache: Boolean) : CatalogLoadState
    data class Error(val message: String) : CatalogLoadState
}

data class DailyStats(
    val dayKey: String = "",
    val totalVolumeMl: Int = 0,
    val totalPureAlcoholMl: Double = 0.0,
    val entriesCount: Int = 0,
    val moodFace: String = "😭",
    val moodTitle: String = "пока тихо",
    val updatedAtMillis: Long = 0L
)

data class DrinkEntryPreview(
    val productName: String = "",
    val brand: String = "",
    val imageUrl: String = "",
    val volumeMl: Int = 0,
    val strengthPercent: Double = 0.0,
    val pureAlcoholMl: Double = 0.0,
    val timestampMillis: Long = 0L
)

data class CalendarDay(
    val dayKey: String,
    val dayOfMonth: Int,
    val hasEntries: Boolean,
    val pureAlcoholMl: Double
)

data class CalendarUiState(
    val ownerPublicId: String,
    val ownerName: String,
    val selectedDayKey: String,
    val monthDays: List<CalendarDay>,
    val selectedDayStats: DailyStats?,
    val selectedDayEntries: List<DrinkEntryPreview>,
    val isLoading: Boolean,
    val error: String? = null
)

enum class MascotMood(
    val title: String,
    val face: String,
    val phrase: String
) {
    Empty(
        title = "Дневник пустует",
        face = "😭",
        phrase = "Пусто и драматично. График требует хоть какой-то сюжет."
    ),
    Tiny(
        title = "Совсем чуть-чуть",
        face = "🥺",
        phrase = "Маскот смотрит так, будто его позвали на вечеринку и дали минералку."
    ),
    AlmostAcademic(
        title = "Почти академично",
        face = "😐",
        phrase = "Статистика есть, но легенда пока держит лицо."
    ),
    Calm(
        title = "Культурный режим",
        face = "🙂",
        phrase = "Аккуратно, спокойно, почти как в методичке."
    ),
    Alive(
        title = "График оживает",
        face = "😎",
        phrase = "Маскот надел очки и сделал вид, что это аналитика."
    ),
    Party(
        title = "Вечер ожил",
        face = "😄",
        phrase = "Веселье зафиксировано, график начал улыбаться."
    ),
    Loud(
        title = "Шумновато",
        face = "🥴",
        phrase = "Маскот уже рассказывает одну историю второй раз."
    ),
    Questionable(
        title = "Режим вопросов",
        face = "🤨",
        phrase = "График подозревает, что завтра будет нужна вода."
    ),
    Risky(
        title = "Опасная зона",
        face = "😵‍💫",
        phrase = "Тут уже не мем, а мягкое предупреждение."
    ),
    Critical(
        title = "Критично",
        face = "🤯",
        phrase = "Маскот открыл таблицу последствий."
    ),
    Warning(
        title = "Режим совести",
        face = "😡",
        phrase = "Похоже на перебор. Лучше вода, еда и никаких героических решений."
    ),
    Aftermath(
        title = "Завтра будет отчет",
        face = "💀",
        phrase = "Маскот уже подготовил акт внутреннего расследования."
    )
}

data class FriendProgress(
    val id: String,
    val publicId: String = id,
    val name: String,
    val status: String,
    val pureAlcoholMl: Double,
    val streakDays: Int,
    val photoUrl: String = "",
    val relationStatus: FriendRelationStatus = FriendRelationStatus.Accepted
) {
    val glasses: Int
        get() = (pureAlcoholMl / 20.0).roundToInt().coerceAtLeast(0)

    val moodFace: String
        get() = when {
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

    val moodTitle: String
        get() = when {
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
}

enum class FriendRelationStatus {
    IncomingRequest,
    OutgoingRequest,
    Accepted
}

data class UserSession(
    val token: String,
    val userName: String,
    val provider: AuthProvider,
    val userId: String,
    val publicId: String = PublicIdGenerator.fromUserId(userId),
    val photoUrl: String? = null,
    val email: String? = null
)

data class UserProfile(
    val userId: String = "",
    val publicId: String = "",
    val name: String = "",
    val email: String = "",
    val provider: String = "",
    val photoUrl: String = "",
    val fcmToken: String = "",
    val isAdmin: Boolean = false,
    val updatedAtMillis: Long = 0L
)

data class RemoteConfigState(
    val welcomeBanner: String = "Бухлограф готов к синхронизации.",
    val experimentalFriendsEnabled: Boolean = true
)

enum class AuthProvider(val analyticsName: String, val label: String) {
    Yandex("yandex", "Яндекс ID"),
    VK("vk", "VK ID"),
    Google("google", "Google"),
    Demo("demo", "Демо-вход")
}

sealed interface AuthResult {
    data class Success(val session: UserSession) : AuthResult
    data class Error(val message: String) : AuthResult
    data object Cancelled : AuthResult
}

data class DrinkDashboard(
    val entries: List<DrinkEntry>,
    val totalVolumeMl: Int,
    val totalPureAlcoholMl: Double,
    val mood: MascotMood,
    val friendProgress: List<FriendProgress>
)
