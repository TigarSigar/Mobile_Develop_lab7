package com.example.buhlograf.domain

import java.security.MessageDigest

object PublicIdGenerator {
    private const val CODE_LENGTH = 6
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

    fun fromUserId(userId: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(userId.toByteArray(Charsets.UTF_8))
        val chars = CharArray(CODE_LENGTH) { index ->
            ALPHABET[(digest[index].toInt() and 0xFF) % ALPHABET.length]
        }
        if (chars.none { it.isDigit() }) {
            chars[CODE_LENGTH - 1] = ('0'.code + ((digest[CODE_LENGTH].toInt() and 0xFF) % 10)).toChar()
        }
        return chars.concatToString()
    }

    fun normalize(input: String): String =
        input
            .filter { it.isLetterOrDigit() }
            .uppercase()
            .take(CODE_LENGTH)

    fun isValid(code: String): Boolean =
        code.length == CODE_LENGTH && code.all { it in ALPHABET }
}
