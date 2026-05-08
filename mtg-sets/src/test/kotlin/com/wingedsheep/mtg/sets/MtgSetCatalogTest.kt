package com.wingedsheep.mtg.sets

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.name
import kotlin.io.path.readText

class MtgSetCatalogTest : FunSpec({

    test("every <Name>Set.kt under definitions is registered in MtgSetCatalog.all") {
        val definitionsDir = Paths.get(
            "src/main/kotlin/com/wingedsheep/mtg/sets/definitions"
        ).toAbsolutePath()

        val setFilesInTree = Files.walk(definitionsDir).use { stream ->
            stream
                .filter { it.name.endsWith("Set.kt") }
                .filter { it.readText().contains(": MtgSet") }
                .map { it.name.removeSuffix(".kt") }
                .toList()
                .sorted()
        }

        val registeredNames = MtgSetCatalog.all
            .map { it::class.simpleName!! }
            .sorted()

        setFilesInTree.shouldContainExactlyInAnyOrder(registeredNames)
    }

    test("set codes are unique") {
        val duplicates = MtgSetCatalog.all
            .groupBy { it.code }
            .filterValues { it.size > 1 }
            .keys
        duplicates.shouldBeEmpty()
    }
})
