package com.idex.android

import com.idex.android.data.AppLanguage
import com.idex.android.data.PokemonCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.util.Locale

class CatalogTest {

    companion object {
        private lateinit var catalog: PokemonCatalog

        @BeforeClass
        @JvmStatic
        fun setUp() {
            val assetFile = File("src/main/assets/pokemon-national.json")
            assertTrue("pokemon-national.json must exist in assets", assetFile.exists())
            val jsonText = assetFile.readText()
            catalog = PokemonCatalog.fromJson(jsonText)
        }
    }

    @Test
    fun testAll1025PokemonLoaded() {
        assertEquals(1025, catalog.entries.size)
        val ids = catalog.entries.map { it.id }.toSet()
        assertEquals((1..1025).toSet(), ids)

        // All generations 1..9 must be present
        val gens = catalog.entries.map { it.generation }.toSet()
        assertEquals((1..9).toSet(), gens)
    }

    @Test
    fun testPikachuData() {
        val pikachu = catalog.pokemon(25)
        assertNotNull("Pikachu (#25) must exist", pikachu)
        assertEquals("Pikachu", pikachu!!.englishName)
        assertEquals("皮卡丘", pikachu.chineseName)
        assertEquals("ピカチュウ", pikachu.japaneseName)
        assertEquals(1, pikachu.generation)
        assertEquals("0025", pikachu.number)
        assertEquals("sprites/pokemon-0025.png", pikachu.spriteAsset)
        assertEquals("cries/pokemon-cry-0025.ogg", pikachu.cryAsset)
        assertTrue(pikachu.types.contains("electric"))
    }

    @Test
    fun testSearchFunctionality() {
        // Search by English name
        val pikaEn = catalog.search("pika", AppLanguage.EN)
        assertTrue(pikaEn.any { it.id == 25 })

        // Search by Chinese name
        val pikaZh = catalog.search("皮卡", AppLanguage.ZH)
        assertTrue(pikaZh.any { it.id == 25 })

        // Search by Japanese name
        val pikaJa = catalog.search("ピカ", AppLanguage.JA)
        assertTrue(pikaJa.any { it.id == 25 })

        // Search by Dex number: #0025, #025, 25
        val search4 = catalog.search("#0025", AppLanguage.EN)
        assertTrue("Searching #0025 should find Pikachu", search4.any { it.id == 25 })

        val search3 = catalog.search("#025", AppLanguage.EN)
        assertTrue("Searching #025 should find Pikachu", search3.any { it.id == 25 })

        val searchRaw = catalog.search("25", AppLanguage.EN)
        assertTrue("Searching 25 should find Pikachu", searchRaw.any { it.id == 25 })

        // Search by type in English
        val electricEn = catalog.search("electric", AppLanguage.EN)
        assertTrue(electricEn.any { it.id == 25 })

        // Search by type in Chinese
        val electricZh = catalog.search("电", AppLanguage.ZH)
        assertTrue(electricZh.any { it.id == 25 })

        // Search with generation filter
        val gen9List = catalog.search("", AppLanguage.EN, generation = 9)
        assertTrue(gen9List.isNotEmpty())
        assertTrue(gen9List.all { it.generation == 9 })
        assertTrue(gen9List.any { it.id == 1025 })
    }

    @Test
    fun testRandomSampleChineseNamesNonEmpty() {
        val sampleIds = listOf(1, 151, 251, 386, 493, 649, 721, 809, 905, 1025)
        for (id in sampleIds) {
            val p = catalog.pokemon(id)
            assertNotNull("Pokemon #$id must exist", p)
            assertTrue("Pokemon #$id Chinese name must not be empty", p!!.chineseName.isNotBlank())
            assertTrue("Pokemon #$id Japanese name must not be empty", p.japaneseName.isNotBlank())
            assertTrue("Pokemon #$id English description must not be empty", p.englishDescription.isNotBlank())
        }
    }

    @Test
    fun testStepWrapAround() {
        val total = 1025
        // Step forward from 1025 wraps to 1
        val nextAfter1025 = ((1025 - 1 + 1 + total) % total) + 1
        assertEquals(1, nextAfter1025)

        // Step backward from 1 wraps to 1025
        val prevBefore1 = ((1 - 1 - 1 + total) % total) + 1
        assertEquals(1025, prevBefore1)
    }

    @Test
    fun testLabelsMatchAll1025Pokemon() {
        val labelsFile = File("src/main/assets/pokemon_labels.json")
        assertTrue("pokemon_labels.json must exist in assets", labelsFile.exists())
        val text = labelsFile.readText()
        val jsonArray = org.json.JSONArray(text)
        assertEquals("Labels count must be 1025", 1025, jsonArray.length())
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val id = obj.getInt("id")
            assertEquals("Labels must be ordered 1..1025", i + 1, id)
            assertNotNull("Each label id must exist in catalog", catalog.pokemon(id))
        }
    }

    @Test
    fun testAll1025ChineseDescriptionsArePureChinese() {
        val englishWordRegex = Regex("[a-zA-Z]{3,}")
        val chineseCharRegex = Regex("[\\u4e00-\\u9fff]")
        for (p in catalog.entries) {
            assertTrue("Pokemon #${p.id} must have Chinese characters in description", chineseCharRegex.containsMatchIn(p.chineseDescription))
            assertTrue("Pokemon #${p.id} description must NOT contain English words: ${p.chineseDescription}", !englishWordRegex.containsMatchIn(p.chineseDescription))
        }
    }

    @Test
    fun testAssetPathsIgnoreDeviceLocale() {
        val prev = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-EG"))
            val pikachu = catalog.pokemon(25)!!
            assertEquals("0025", pikachu.number)
            assertEquals("sprites/pokemon-0025.png", pikachu.spriteAsset)
            assertEquals("cries/pokemon-cry-0025.ogg", pikachu.cryAsset)
        } finally {
            Locale.setDefault(prev)
        }
    }

    @Test
    fun testCombinedFilterAndBareHashQuery() {
        val seen = setOf(1, 25, 152)
        val gen1Seen = catalog.filter(query = "", language = AppLanguage.EN, generation = 1, seenOnly = true, seenIds = seen)
        assertEquals(listOf(1, 25), gen1Seen.map { it.id })

        val bareHash = catalog.filter(query = "#", language = AppLanguage.EN)
        assertEquals("Bare '#' should not match all species", 0, bareHash.size)
    }
}
