package com.example.buhlograf.domain

class CalculateMascotMoodUseCase {
    operator fun invoke(entries: List<DrinkEntry>): MascotMood {
        val totalPureAlcohol = entries.sumOf { it.pureAlcoholMl }
        return when {
            entries.isEmpty() -> MascotMood.Empty
            totalPureAlcohol >= 140.0 -> MascotMood.Aftermath
            totalPureAlcohol >= 115.0 -> MascotMood.Warning
            totalPureAlcohol >= 95.0 -> MascotMood.Critical
            totalPureAlcohol >= 80.0 -> MascotMood.Risky
            totalPureAlcohol >= 65.0 -> MascotMood.Questionable
            totalPureAlcohol >= 50.0 -> MascotMood.Loud
            totalPureAlcohol >= 35.0 -> MascotMood.Party
            totalPureAlcohol >= 25.0 -> MascotMood.Alive
            totalPureAlcohol >= 15.0 -> MascotMood.Calm
            totalPureAlcohol >= 5.0 -> MascotMood.AlmostAcademic
            else -> MascotMood.Tiny
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
        val mood = calculateMascotMood(entries)
        return DrinkDashboard(
            entries = entries,
            totalVolumeMl = entries.sumOf { it.volumeMl },
            totalPureAlcoholMl = entries.sumOf { it.pureAlcoholMl },
            mood = mood,
            mascot = MascotCatalog.variantFor(mood, entries),
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
