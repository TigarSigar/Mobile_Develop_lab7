package com.example.buhlograf.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs

data class MascotVariant(
    val face: String,
    val title: String,
    val colorHex: Long
)

object MascotCatalog {
    private val variants = mapOf(
        MascotMood.Empty to listOf(
            "😶" to "пустой эфир", "🫥" to "график в отпуске", "🙃" to "тишина в стакане",
            "😐" to "без происшествий", "🫡" to "дневник стоит смирно", "😴" to "сонный режим",
            "🤔" to "подозрительно чисто", "🙂" to "нулевой движ", "🫠" to "пока без сюжета", "😭" to "пусто и драматично"
        ),
        MascotMood.Tiny to listOf(
            "🥺" to "символический плеск", "🤏" to "почти гомеопатия", "😌" to "один уважительный глоток",
            "🙂" to "микро-режим", "😇" to "почти минералка", "🧐" to "статистика заметила каплю",
            "😏" to "аккуратный старт", "🤭" to "чуть-чуть для графика", "😶‍🌫️" to "пар над бокалом", "🙄" to "маскот не впечатлён"
        ),
        MascotMood.AlmostAcademic to listOf(
            "😐" to "почти академично", "🤓" to "лабораторная дегустация", "🧐" to "аналитик включён",
            "😌" to "культурная проба", "🙂" to "спокойный отчёт", "😎" to "данные появились",
            "🤨" to "есть что записать", "📊" to "график проснулся", "😏" to "первый аргумент", "🫡" to "контрольная отметка"
        ),
        MascotMood.Calm to listOf(
            "🙂" to "культурный режим", "😌" to "бархатный вечер", "😇" to "всё под контролем",
            "😎" to "спокойный стиль", "🤝" to "договорились без хаоса", "🫡" to "чинная запись",
            "🧐" to "социальная аналитика", "😏" to "нормальный темп", "🍷" to "мягкий график", "😊" to "маскот доволен"
        ),
        MascotMood.Alive to listOf(
            "😎" to "график оживает", "😄" to "разговор пошёл", "🤠" to "уверенность растёт",
            "🕺" to "статистика танцует", "😏" to "вечер набирает форму", "🙂" to "тепло внутри",
            "🥂" to "социальный режим", "😋" to "вкусно и записано", "🤌" to "почти красиво", "😺" to "маскот ожил"
        ),
        MascotMood.Party to listOf(
            "😄" to "вечер ожил", "🥳" to "микро-праздник", "🕺" to "танцы в таблице",
            "😎" to "легенда разогревается", "🤩" to "график улыбается", "🍻" to "социальный максимум",
            "😆" to "шутки пошли быстрее", "🎉" to "режим конфетти", "🤝" to "дружеский отчёт", "😏" to "маскот подмигнул"
        ),
        MascotMood.Loud to listOf(
            "🥴" to "шумновато", "🫠" to "голосовой режим", "😵‍💫" to "график качнулся",
            "📢" to "история пошла по кругу", "🤪" to "таблица смеётся", "😆" to "громкий аргумент",
            "🕺" to "танцует даже отчёт", "🍺" to "пенное эхо", "😎" to "слишком уверенно", "🤨" to "маскот насторожился"
        ),
        MascotMood.Questionable to listOf(
            "🤨" to "режим вопросов", "🧐" to "что это было", "😬" to "график напрягся",
            "🙃" to "логика на паузе", "😵‍💫" to "маршрут поплыл", "🤔" to "маскот считает воду",
            "🫠" to "почти сюжет", "😶‍🌫️" to "туманная аналитика", "🤯" to "подозрительный лимонад", "😐" to "внутренний аудит"
        ),
        MascotMood.Risky to listOf(
            "😵‍💫" to "опасная зона", "🥴" to "график штормит", "🫨" to "таблица дрожит",
            "😬" to "воды бы", "🤯" to "данные перегрелись", "🫠" to "маскот плавится",
            "🙃" to "реальность наклонилась", "🚨" to "мягкая тревога", "😵" to "система просит паузу", "🧯" to "пора тушить вечер"
        ),
        MascotMood.Critical to listOf(
            "🤯" to "критично", "😵" to "экран качает", "🚨" to "график кричит",
            "🫠" to "статистика расплавилась", "😬" to "режим спасения", "🧯" to "вода обязательна",
            "😵‍💫" to "маскот потерял курс", "📉" to "акции организма падают", "🥴" to "слишком смело", "⚠️" to "жёлтая карточка"
        ),
        MascotMood.Warning to listOf(
            "😡" to "режим совести", "⚠️" to "маскот ругается", "🚑" to "план эвакуации",
            "😤" to "воды и еды", "🫨" to "перебор близко", "🤕" to "организм в чате",
            "📋" to "акт почти готов", "🧯" to "вечер надо тушить", "😬" to "не геройствовать", "🚨" to "красная зона"
        ),
        MascotMood.Aftermath to listOf(
            "💀" to "завтра будет отчёт", "🪦" to "легенда района", "🚑" to "график вызвал помощь",
            "🤕" to "маскот просит тишину", "😵" to "последствия онлайн", "📉" to "рынок здоровья просел",
            "🧯" to "вечер потушен", "⚰️" to "данные мрачные", "🥴" to "финальный босс", "🚨" to "максимальная стадия"
        )
    )

    fun variantFor(
        mood: MascotMood,
        entries: List<DrinkEntry>,
        userSeed: String = ""
    ): MascotVariant {
        if (hasSquirrelEasterEgg(entries)) {
            return MascotVariant("🐿️", "белочка пришла сверить отчёт", 0xFFECE7DF)
        }
        val list = variants.getValue(mood)
        val dayKey = entries.maxByOrNull { it.timestampMillis }?.dayKey
            ?.ifBlank { null }
            ?: LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val index = abs("$userSeed-$dayKey-${mood.name}".hashCode()) % list.size
        val (face, title) = list[index]
        return MascotVariant(face, title, colorFor(mood, index))
    }

    fun colorFor(mood: MascotMood, variantIndex: Int = 0): Long {
        val base = when (mood) {
            MascotMood.Empty -> 0xFF5A5068
            MascotMood.Tiny -> 0xFF7A6C8E
            MascotMood.AlmostAcademic -> 0xFF597B8F
            MascotMood.Calm -> 0xFF4D8D73
            MascotMood.Alive -> 0xFF4F9B61
            MascotMood.Party -> 0xFF9EA13C
            MascotMood.Loud -> 0xFFC18B2E
            MascotMood.Questionable -> 0xFFD2792F
            MascotMood.Risky -> 0xFFD45F33
            MascotMood.Critical -> 0xFFD04747
            MascotMood.Warning -> 0xFFB83E50
            MascotMood.Aftermath -> 0xFF7D3242
        }
        val bump = (variantIndex % 4) * 0x00040404
        return (base + bump).coerceAtMost(0xFFFFFFFF)
    }

    private fun hasSquirrelEasterEgg(entries: List<DrinkEntry>): Boolean {
        if (entries.isEmpty()) return false
        val formatter = DateTimeFormatter.ISO_LOCAL_DATE
        val byDay = entries.groupBy {
            it.dayKey.ifBlank {
                java.time.Instant.ofEpochMilli(it.timestampMillis)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate()
                    .format(formatter)
            }
        }
        val latestDay = byDay.keys.mapNotNull { runCatching { LocalDate.parse(it, formatter) }.getOrNull() }
            .maxOrNull() ?: return false
        return (0..3).all { offset ->
            val key = latestDay.minusDays(offset.toLong()).format(formatter)
            val pure = byDay[key]?.sumOf { it.pureAlcoholMl } ?: 0.0
            pure >= 140.0
        }
    }
}
