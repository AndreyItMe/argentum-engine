package com.wingedsheep.tooling.coverage.emitter

import com.wingedsheep.tooling.coverage.J
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonElement

/**
 * Pins the shared filter-predicate recovery the two filter renderers ([creatureFilterDsl] /
 * [gameObjectFilterDsl]) now compose, so the regex/condition→DSL fragments stay in one place. The
 * power detector reads `compact()`'s whitespace-free encoding; the spaced inputs here confirm the
 * permissive regex matches both forms identically.
 */
class FilterPredicatesTest : StringSpec({

    fun node(json: String): JsonElement = J.parseToJsonElement(json)

    "power bounds recover from both compact and spaced encodings" {
        val compact = """[{"_Permanents":"PowerIs","args":["GreaterThanOrEqualTo",{"_GameNumber":"Integer","args":3}]}]"""
        FilterPredicates.powerAtLeast(compact) shouldBe ".powerAtLeast(3)"
        FilterPredicates.powerAtMost(compact).shouldBeNull()

        val spaced = """[ {"_Permanents": "PowerIs", "args": [ "LessThanOrEqualTo", {"_GameNumber": "Integer", "args": 2} ]} ]"""
        FilterPredicates.powerAtMost(spaced) shouldBe ".powerAtMost(2)"
        FilterPredicates.powerAtLeast(spaced).shouldBeNull()
    }

    "tap / attack state predicates map to their fluent suffixes" {
        FilterPredicates.tapped("""{"_Permanents":"IsTapped"}""") shouldBe ".tapped()"
        FilterPredicates.untapped("""{"_Permanents":"IsUntapped"}""") shouldBe ".untapped()"
        FilterPredicates.attacking("""{"_Permanents":"IsAttacking"}""") shouldBe ".attacking()"
        FilterPredicates.tapped("{}").shouldBeNull()
    }

    "flying recovers as with/without keyword, distinguished by the DoesntHaveAbility marker" {
        val without = node("""{"_Permanents":"DoesntHaveAbility","args":[{"_Keyword":"Flying"}]}""")
        FilterPredicates.withoutFlying(without, J.encodeToString(JsonElement.serializer(), without)) shouldBe
            ".withoutKeyword(Keyword.FLYING)"

        val plain = node("""{"_Permanents":"HasAbility","args":[{"_Keyword":"Flying"}]}""")
        val plainBlob = J.encodeToString(JsonElement.serializer(), plain)
        FilterPredicates.withoutFlying(plain, plainBlob).shouldBeNull()
        FilterPredicates.withFlying(plainBlob) shouldBe ".withKeyword(Keyword.FLYING)"
    }
})
