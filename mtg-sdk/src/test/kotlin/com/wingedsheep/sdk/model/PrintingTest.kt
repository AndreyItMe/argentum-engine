package com.wingedsheep.sdk.model

import com.wingedsheep.sdk.core.ManaCost
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

/**
 * Pins the multi-printing data-model surface introduced in Phase 1: the [Printing] /
 * [PrintingRef] pair, their JSON round-trip, and the derived
 * [CardDefinition.defaultPrintingRef] getter that lets existing card definitions
 * advertise a default printing without per-card edits.
 */
class PrintingTest : DescribeSpec({

    val json = Json { ignoreUnknownKeys = true }

    describe("PrintingRef") {
        it("formats identifier as SET-CN") {
            PrintingRef("M10", "146").identifier() shouldBe "M10-146"
        }

        it("round-trips through JSON") {
            val ref = PrintingRef("2X2", "117")
            val encoded = json.encodeToString(PrintingRef.serializer(), ref)
            val decoded = json.decodeFromString(PrintingRef.serializer(), encoded)
            decoded shouldBe ref
        }
    }

    describe("Printing") {
        it("exposes a PrintingRef view") {
            val printing = Printing(
                oracleId = "abc-123",
                name = "Lightning Bolt",
                setCode = "M10",
                collectorNumber = "146",
            )
            printing.ref shouldBe PrintingRef("M10", "146")
        }

        it("round-trips through JSON with optional metadata") {
            val printing = Printing(
                oracleId = "abc-123",
                name = "Lightning Bolt",
                setCode = "M10",
                collectorNumber = "146",
                artist = "Christopher Moeller",
                imageUri = "https://example.test/m10/146.jpg",
                rarity = Rarity.COMMON,
            )
            val encoded = json.encodeToString(Printing.serializer(), printing)
            val decoded = json.decodeFromString(Printing.serializer(), encoded)
            decoded shouldBe printing
        }
    }

    describe("CardDefinition.defaultPrintingRef") {
        it("derives from setCode + metadata.collectorNumber") {
            val card = CardDefinition.creature(
                name = "Bear",
                manaCost = ManaCost.parse("{1}{G}"),
                subtypes = emptySet(),
                power = 2,
                toughness = 2,
                metadata = ScryfallMetadata(collectorNumber = "146"),
            ).copy(setCode = "M10")

            card.defaultPrintingRef shouldBe PrintingRef("M10", "146")
        }

        it("is null when setCode missing") {
            val card = CardDefinition.creature(
                name = "Bear",
                manaCost = ManaCost.parse("{1}{G}"),
                subtypes = emptySet(),
                power = 2,
                toughness = 2,
                metadata = ScryfallMetadata(collectorNumber = "146"),
            )
            card.defaultPrintingRef.shouldBeNull()
        }

        it("is null when collector number missing") {
            val card = CardDefinition.creature(
                name = "Bear",
                manaCost = ManaCost.parse("{1}{G}"),
                subtypes = emptySet(),
                power = 2,
                toughness = 2,
            ).copy(setCode = "M10")
            card.defaultPrintingRef.shouldBeNull()
        }
    }
})
