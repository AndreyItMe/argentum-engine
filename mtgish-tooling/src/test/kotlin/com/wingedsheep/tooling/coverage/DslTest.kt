package com.wingedsheep.tooling.coverage

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Pins the emitter output AST renderer ([render]) — the ONE place a [Dsl] node becomes pre-wrap source
 * text. Each spec locks a node shape to the exact string the emitter historically hand-stitched, so the
 * leaf/effect/ability handlers can build nodes instead of interpolating strings with no risk of a
 * whitespace/comma/paren drift. (Long-line reflow stays downstream in `wrapLine`; these are pre-wrap.)
 */
class DslTest : StringSpec({

    "a literal renders verbatim" {
        render(Lit("DynamicAmount.XValue")) shouldBe "DynamicAmount.XValue"
        render(Lit("3")) shouldBe "3"
    }

    "a no-arg call renders as callee()" {
        render(call("DynamicAmounts.sacrificedPower")) shouldBe "DynamicAmounts.sacrificedPower()"
    }

    "positional args join with ', '" {
        render(call("DealDamageEffect", arg("2"), arg("t"))) shouldBe "DealDamageEffect(2, t)"
    }

    "a named arg renders as 'name = value'" {
        render(call("DynamicAmount.Fixed", arg("2"))) shouldBe "DynamicAmount.Fixed(2)"
        render(call("AddCountersEffect", arg("count", "1"), arg("target", "t"))) shouldBe
            "AddCountersEffect(count = 1, target = t)"
    }

    "nested calls render inline (downstream wrapLine handles width)" {
        val node = call(
            "DynamicAmount.Divide",
            arg(Lit("DynamicAmount.XValue")), arg(call("DynamicAmount.Fixed", arg("2"))), arg("roundUp", "true"),
        )
        render(node) shouldBe "DynamicAmount.Divide(DynamicAmount.XValue, DynamicAmount.Fixed(2), roundUp = true)"
    }

    "a chain renders base.method(args).method(args)" {
        val node = Lit("GameObjectFilter.Creature").dot("withSubtype", arg("\"Goblin\"")).dot("tapped")
        render(node) shouldBe "GameObjectFilter.Creature.withSubtype(\"Goblin\").tapped()"
    }

    "an infix joins with the operator, parenthesized when asked" {
        render(Infix("or", listOf(Lit("A"), Lit("B")))) shouldBe "A or B"
        render(Infix("or", listOf(Lit("A"), Lit("B")), parenthesized = true)) shouldBe "(A or B)"
    }

    "a Composite lays each element at 12 spaces with an 8-space close (matches the old composite())" {
        val node = Composite(listOf(Lit("Effects.ModifyStats(3, 3, t)"), Lit("Effects.GrantKeyword(Keyword.FLYING, t)")))
        render(node) shouldBe
            "Effects.Composite(\n" +
            "            Effects.ModifyStats(3, 3, t),\n" +
            "            Effects.GrantKeyword(Keyword.FLYING, t)\n" +
            "        )"
    }

    "Raw passes its text through untouched" {
        render(Raw("anything\n    indented")) shouldBe "anything\n    indented"
    }
})
