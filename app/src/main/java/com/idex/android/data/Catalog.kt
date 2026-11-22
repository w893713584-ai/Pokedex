package com.idex.android.data

import android.content.Context
import org.json.JSONArray
import java.util.Locale

/** Controls both interface copy and the voice used for Pokédex readings. */
enum class AppLanguage(val code: String) {
    EN("en"), ZH("zh-Hans"), JA("ja");

    val title: String get() = text("English", "简体中文", "日本語")
    val speechLocale: Locale
        get() = when (this) {
            EN -> Locale.US
            ZH -> Locale.SIMPLIFIED_CHINESE
            JA -> Locale.JAPAN
        }

    fun text(english: String, chinese: String, japanese: String): String = when (this) {
        EN -> english
        ZH -> chinese
        JA -> japanese
    }

    companion object {
        fun fromCode(code: String?): AppLanguage? = entries.firstOrNull { it.code == code }

        fun systemDefault(): AppLanguage {
            val locales = android.content.res.Resources.getSystem().configuration.locales
            for (i in 0 until locales.size()) {
                when (locales[i].language) {
                    "en" -> return EN
                    "zh" -> return ZH
                    "ja" -> return JA
                }
            }
            return EN
        }
    }
}

data class PokemonStat(val name: String, val value: Int) {
    fun title(language: AppLanguage): String = when (name) {
        "hp" -> language.text("HP", "体力", "HP")
        "attack" -> language.text("ATTACK", "攻击", "こうげき")
        "defense" -> language.text("DEFENSE", "防御", "ぼうぎょ")
        "special-attack" -> language.text("SP. ATK", "特攻", "とくこう")
        "special-defense" -> language.text("SP. DEF", "特防", "とくぼう")
        "speed" -> language.text("SPEED", "速度", "すばやさ")
        else -> name.uppercase(Locale.ROOT)
    }
}

data class Pokemon(
    val id: Int,
    val generation: Int = 1,
    val englishName: String,
    val chineseName: String,
    val japaneseName: String,
    /** Stable English type identifiers such as `grass`. */
    val types: List<String>,
    /** Metres. */
    val height: Double,
    /** Kilograms. */
    val weight: Double,
    val englishDescription: String,
    val chineseDescription: String,
    val japaneseDescription: String,
    val englishGenus: String,
    val chineseGenus: String,
    val japaneseGenus: String,
    val stats: List<PokemonStat>,
) {
    val number: String get() = String.format(Locale.ROOT, "%04d", id)
    val spriteAsset: String get() = "sprites/pokemon-$number.png"
    val cryAsset: String get() = "cries/pokemon-cry-$number.ogg"
    val totalStats: Int get() = stats.sumOf { it.value }

    fun name(l: AppLanguage) = l.text(englishName, chineseName, japaneseName)
    fun description(l: AppLanguage) = l.text(englishDescription, chineseDescription, japaneseDescription)
    fun genus(l: AppLanguage) = l.text(englishGenus, chineseGenus, japaneseGenus)
    fun typeNames(l: AppLanguage) = types.map { typeName(it, l) }

    fun region(l: AppLanguage): String = when (generation) {
        1 -> l.text("KANTO", "关都", "カントー")
        2 -> l.text("JOHTO", "城都", "ジョウト")
        3 -> l.text("HOENN", "丰缘", "ホウエン")
        4 -> l.text("SINNOH", "神奥", "シンオウ")
        5 -> l.text("UNOVA", "合众", "イッシュ")
        6 -> l.text("KALOS", "卡洛斯", "カロス")
        7 -> l.text("ALOLA", "阿罗拉", "アローラ")
        8 -> l.text("GALAR", "伽勒尔", "ガラール")
        9 -> l.text("PALDEA", "帕底亚", "パルデア")
        else -> l.text("UNKNOWN", "未知", "未知")
    }

    fun spokenEntry(l: AppLanguage): String {
        val types = typeNames(l)
        return when (l) {
            AppLanguage.ZH -> "${chineseName}。全国图鉴编号${id}。${chineseGenus}。属性，${types.joinToString("，")}。${chineseDescription}"
            AppLanguage.JA -> "${japaneseName}。全国図鑑番号${id}。${japaneseGenus}。${types.joinToString("、")}タイプ。${japaneseDescription}"
            AppLanguage.EN -> "$englishName. Pokédex number $id. $englishGenus. ${types.joinToString(" and ")} type. $englishDescription"
        }
    }

    companion object {
        fun typeName(type: String, l: AppLanguage): String = when (l) {
            AppLanguage.EN -> type.uppercase(Locale.ROOT)
            AppLanguage.ZH -> chineseTypes[type] ?: type
            AppLanguage.JA -> japaneseTypes[type] ?: type
        }

        private val chineseTypes = mapOf(
            "normal" to "一般", "fighting" to "格斗", "flying" to "飞行", "poison" to "毒",
            "ground" to "地面", "rock" to "岩石", "bug" to "虫", "ghost" to "幽灵",
            "steel" to "钢", "fire" to "火", "water" to "水", "grass" to "草",
            "electric" to "电", "psychic" to "超能力", "ice" to "冰", "dragon" to "龙",
            "dark" to "恶", "fairy" to "妖精",
        )
        private val japaneseTypes = mapOf(
            "normal" to "ノーマル", "fighting" to "かくとう", "flying" to "ひこう", "poison" to "どく",
            "ground" to "じめん", "rock" to "いわ", "bug" to "むし", "ghost" to "ゴースト",
            "steel" to "はがね", "fire" to "ほのお", "water" to "みず", "grass" to "くさ",
            "electric" to "でんき", "psychic" to "エスパー", "ice" to "こおり", "dragon" to "ドラゴン",
            "dark" to "あく", "fairy" to "フェアリー",
        )
    }
}

/** The bundled 1025-entry National catalogue. Loaded once from assets. */
class PokemonCatalog private constructor(val entries: List<Pokemon>) {
    private val byId = entries.associateBy { it.id }

    fun pokemon(id: Int): Pokemon? = byId[id]

    /**
     * Filter the catalogue by search query, optional generation, and optional discovered filter.
     */
    fun filter(
        query: String,
        language: AppLanguage,
        generation: Int? = null,
        seenOnly: Boolean = false,
        seenIds: Set<Int> = emptySet(),
    ): List<Pokemon> {
        val trimmed = query.trim()
        val strippedHash = trimmed.trimStart('#')
        val numQuery = strippedHash.trimStart('0')
        return entries.filter { p ->
            val matchesSeen = !seenOnly || seenIds.contains(p.id)
            val matchesGen = generation == null || p.generation == generation
            val matchesQuery = trimmed.isEmpty() ||
                p.englishName.contains(trimmed, ignoreCase = true) ||
                p.chineseName.contains(trimmed) ||
                p.japaneseName.contains(trimmed) ||
                (numQuery.isNotEmpty() && p.id.toString() == numQuery) ||
                (strippedHash.isNotEmpty() && p.number.contains(strippedHash)) ||
                p.typeNames(language).any { it.contains(trimmed, ignoreCase = true) }
            matchesSeen && matchesGen && matchesQuery
        }
    }

    fun search(query: String, language: AppLanguage, generation: Int? = null): List<Pokemon> =
        filter(query = query, language = language, generation = generation)

    companion object {
        @Volatile private var instance: PokemonCatalog? = null

        fun get(context: Context): PokemonCatalog = instance ?: synchronized(this) {
            instance ?: load(context).also { instance = it }
        }

        private fun load(context: Context): PokemonCatalog {
            val text = context.assets.open("pokemon-national.json").bufferedReader().use { it.readText() }
            return fromJson(text)
        }

        fun fromJson(text: String): PokemonCatalog {
            val array = JSONArray(text)
            val seenIds = HashSet<Int>(array.length())
            val list = (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                val id = o.getInt("id")
                require(id > 0 && seenIds.add(id)) { "Invalid or duplicate Pokemon id: $id" }
                val types = o.getJSONArray("types")
                val stats = o.getJSONArray("stats")
                Pokemon(
                    id = id,
                    generation = o.optInt("generation", 1),
                    englishName = o.getString("englishName"),
                    chineseName = o.getString("chineseName"),
                    japaneseName = o.getString("japaneseName"),
                    types = (0 until types.length()).map { types.getString(it) },
                    height = o.getDouble("height"),
                    weight = o.getDouble("weight"),
                    englishDescription = o.getString("englishDescription"),
                    chineseDescription = o.getString("chineseDescription"),
                    japaneseDescription = o.getString("japaneseDescription"),
                    englishGenus = o.getString("englishGenus"),
                    chineseGenus = o.getString("chineseGenus"),
                    japaneseGenus = o.getString("japaneseGenus"),
                    stats = (0 until stats.length()).map {
                        val s = stats.getJSONObject(it)
                        PokemonStat(s.getString("name"), s.getInt("value"))
                    },
                )
            }.sortedBy { it.id }
            return PokemonCatalog(list)
        }
    }
}
