package com.example.buhlograf.data

import com.example.buhlograf.domain.DrinkEntry
import com.example.buhlograf.domain.DrinkRepository
import com.example.buhlograf.domain.DrinkType
import com.example.buhlograf.domain.FriendProgress
import com.example.buhlograf.domain.FriendsRepository
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

    override fun clearToday() {
        entries.clear()
    }
}

class LocalFriendsRepository : FriendsRepository {
    private val friends = mutableListOf<FriendProgress>()

    override fun getFriends(): List<FriendProgress> = friends.toList()

    override fun addFriendById(id: String): FriendProgress? {
        val normalized = id.trim().uppercase()
        if (normalized.isBlank()) return null
        friends.firstOrNull { it.id == normalized }?.let { return it }

        val friend = FriendProgress(
            id = normalized,
            name = "Друг $normalized",
            status = "ожидает синхронизацию Firebase",
            pureAlcoholMl = 0.0,
            streakDays = 0
        )
        friends += friend
        return friend
    }
}
