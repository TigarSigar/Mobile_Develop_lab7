package com.example.buhlograf.domain

class CalculateMascotMoodUseCase {
    operator fun invoke(entries: List<DrinkEntry>): MascotMood {
        val totalPureAlcohol = entries.sumOf { it.pureAlcoholMl }
        return when {
            entries.isEmpty() -> MascotMood.Empty
            totalPureAlcohol >= 90.0 -> MascotMood.Warning
            totalPureAlcohol >= 35.0 -> MascotMood.Party
            else -> MascotMood.Calm
        }
    }
}

class BuildDashboardUseCase(
    private val drinkRepository: DrinkRepository,
    private val friendsRepository: FriendsRepository,
    private val calculateMascotMood: CalculateMascotMoodUseCase
) {
    operator fun invoke(): DrinkDashboard {
        val entries = drinkRepository.getEntries()
        return DrinkDashboard(
            entries = entries,
            totalVolumeMl = entries.sumOf { it.volumeMl },
            totalPureAlcoholMl = entries.sumOf { it.pureAlcoholMl },
            mood = calculateMascotMood(entries),
            friendProgress = friendsRepository.getFriends()
        )
    }
}

class BuildUserIdUseCase {
    operator fun invoke(provider: AuthProvider, token: String): String {
        val hash = token.hashCode().toUInt().toString(16).uppercase().padStart(8, '0')
        return "${provider.analyticsName.uppercase()}-${hash.take(8)}"
    }
}
