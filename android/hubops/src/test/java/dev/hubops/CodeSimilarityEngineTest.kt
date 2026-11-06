package dev.hubops

import dev.hubops.despertar.project.CodeSimilarityEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeSimilarityEngineTest {
    @Test fun detectsExactTextAndStructureMatch() {
        val code = "package sample\nclass Store { fun load(value: Int) = value + 1 }"
        val report = CodeSimilarityEngine.compare(mapOf("src/Store.kt" to code), mapOf("src/Store.kt" to code))
        assertEquals(100, report.textualPercent)
        assertEquals(100, report.structuralPercent)
        assertEquals(1, report.comparedFiles)
    }

    @Test fun structuralScoreIgnoresIdentifierAndLiteralChanges() {
        val original = "package alpha\nclass Store { fun load(item: Int) = item + 10 }"
        val replica = "package beta\nclass Depot { fun fetch(value: Int) = value + 99 }"
        val report = CodeSimilarityEngine.compare(mapOf("Store.kt" to original), mapOf("Store.kt" to replica))
        assertTrue(report.structuralPercent > report.textualPercent)
    }

    @Test fun recognizesPythonJavaAndKotlinAndSkipsTests() {
        val a = mapOf("src/Main.kt" to "class Main", "src/Legacy.java" to "class Legacy {}", "src/tool.py" to "def run(): return 1", "src/test/MainTest.kt" to "class MainTest")
        val b = a - "src/test/MainTest.kt"
        val report = CodeSimilarityEngine.compare(a, b)
        assertEquals(3, report.originalSourceFiles)
        assertEquals(3, report.replicaSourceFiles)
        assertEquals(3, report.comparedFiles)
    }

    @Test fun reportsUnmatchedFilesInsteadOfPretendingTheyMatch() {
        val report = CodeSimilarityEngine.compare(mapOf("A.kt" to "class A"), mapOf("B.kt" to "class B"))
        assertEquals(0, report.comparedFiles)
        assertEquals(1, report.unmatchedOriginal)
        assertEquals(1, report.unmatchedReplica)
    }
    @Test fun unmatchedFilesReduceProjectLevelSimilarity() {
        val original = mapOf("A.kt" to "class A", "B.kt" to "class B", "C.kt" to "class C", "D.kt" to "class D")
        val replica = mapOf("A.kt" to "class A", "X.kt" to "class X", "Y.kt" to "class Y", "Z.kt" to "class Z")
        val report = CodeSimilarityEngine.compare(original, replica)
        assertEquals(1, report.comparedFiles)
        assertTrue("Unmatched files must lower the aggregate score", report.textualPercent < 100)
        assertEquals(3, report.unmatchedOriginal)
        assertEquals(3, report.unmatchedReplica)
    }

    @Test fun pythonFloorDivisionIsAnOperatorNotAComment() {
        val original = "def divide(value):\n    return value // 2\n"
        val replica = "def divide(value):\n    return value / 2\n"
        val report = CodeSimilarityEngine.compare(mapOf("calc.py" to original), mapOf("calc.py" to replica))
        assertTrue(report.textualPercent < 100)
    }

    @Test fun multilineTripleQuotedStringsAreConsumedAsSingleLiteral() {
        val original = "def message():\n    return \"\"\"first\n# not a comment\nlast\"\"\"\n"
        val replica = "def message():\n    return \"\"\"other\n# still a string\ntext\"\"\"\n"
        val report = CodeSimilarityEngine.compare(mapOf("message.py" to original), mapOf("message.py" to replica))
        assertEquals(100, report.structuralPercent)
        assertTrue(report.textualPercent < 100)
    }


    @Test fun matchesMovedOrRenamedFilesByStructure() {
        val original = mapOf("src/data/Store.kt" to "package sample\nclass Store { fun load(value: Int) = value + 1 }")
        val replica = mapOf("core/persistence/Depot.kt" to "package sample\nclass Depot { fun fetch(item: Int) = item + 2 }")
        val report = CodeSimilarityEngine.compare(original, replica)
        assertEquals(1, report.comparedFiles)
        assertEquals(1, report.movedOrRenamedFiles)
        assertEquals("src/data/Store.kt", report.files.single().path)
        assertEquals("core/persistence/Depot.kt", report.files.single().replicaPath)
        assertEquals(0, report.unmatchedOriginal)
        assertEquals(0, report.unmatchedReplica)
    }

    @Test fun orderedMetricDetectsReorderedStatements() {
        val original = "fun calculate() { val first = 1; val second = 2; return first + second }"
        val replica = "fun calculate() { val second = 2; val first = 1; return first + second }"
        val report = CodeSimilarityEngine.compare(mapOf("Calc.kt" to original), mapOf("Calc.kt" to replica))
        assertTrue("Token order must affect similarity", report.textualPercent < 100)
    }

    @Test fun kotlinNestedCommentsAndRawStringsDoNotLeakTokens() {
        val original = "class Sample { /* outer /* inner */ end */ val text = \"\"\"hello\nworld\"\"\" }"
        val replica = "class Sample { /* other /* nested */ comment */ val text = \"\"\"different\ncontent\"\"\" }"
        val report = CodeSimilarityEngine.compare(mapOf("Sample.kt" to original), mapOf("Sample.kt" to replica))
        assertEquals(100, report.structuralPercent)
        assertTrue(report.textualPercent < 100)
    }

    @Test fun reportExposesMoveMatchingAndTruncationLimits() {
        val report = CodeSimilarityEngine.compare(mapOf("src/A.kt" to "class A"), mapOf("src/A.kt" to "class A"))
        val markdown = report.toMarkdown()
        assertTrue(markdown.contains("Ficheiros movidos/renomeados"))
        assertTrue(markdown.contains("limite"))
    }


    @Test fun doesNotGuessPairsForTinyGenericFiles() {
        val report = CodeSimilarityEngine.compare(mapOf("one/A.kt" to "class A"), mapOf("two/B.kt" to "class B"))
        assertEquals(0, report.comparedFiles)
        assertEquals(1, report.unmatchedOriginal)
        assertEquals(1, report.unmatchedReplica)
    }

    @Test fun rejectsAmbiguousDuplicatePathsAfterSlashNormalization() {
        try {
            CodeSimilarityEngine.compare(
                mapOf("src\\main\\A.kt" to "class A", "src/main/A.kt" to "class A"),
                mapOf("src/main/A.kt" to "class A")
            )
            throw AssertionError("Expected duplicate normalized path to be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("caminhos duplicados"))
        }
    }

}
