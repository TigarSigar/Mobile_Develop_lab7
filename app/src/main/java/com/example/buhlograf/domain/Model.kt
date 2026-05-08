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
    val timestampMillis: Long
) {
    val pureAlcoholMl: Double
        get() = volumeMl * (strengthPercent / 100.0)
}

enum class MascotMood(
    val title: String,
    val face: String,
    val phrase: String
) {
    Empty(
        title = "Дневник пустует",
        face = "(._.)",
        phrase = "Маскот грустит не из-за трезвости, а потому что графику нечего рисовать."
    ),
    Calm(
        title = "Культурный режим",
        face = "(•‿•)",
        phrase = "Запись есть, легенда спокойна, таблица довольна."
    ),
    Party(
        title = "Вечер ожил",
        face = "(＾▽＾)",
        phrase = "Маскот пляшет, но приложение всё еще ведет себя как взрослое."
    ),
    Warning(
        title = "Режим совести",
        face = "(ಠ_ಠ)",
        phrase = "Похоже на перебор. Лучше вода, еда и никаких героических решений."
    )
}

data class FriendProgress(
    val id: String,
    val name: String,
    val status: String,
    val pureAlcoholMl: Double,
    val streakDays: Int
) {
    val glasses: Int
        get() = (pureAlcoholMl / 20.0).roundToInt().coerceAtLeast(0)
}

data class UserSession(
    val token: String,
    val userName: String,
    val provider: AuthProvider,
    val userId: String,
    val photoUrl: String? = null,
    val email: String? = null
)

data class UserProfile(
    val userId: String = "",
    val name: String = "",
    val email: String = "",
    val provider: String = "",
    val photoUrl: String = "",
    val fcmToken: String = "",
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
