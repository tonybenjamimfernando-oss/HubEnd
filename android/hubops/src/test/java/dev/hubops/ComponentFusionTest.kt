package dev.hubops

import dev.hubops.despertar.project.ComponentFusion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComponentFusionTest {
    @Test fun detectsSameSourcePathAndPackageOverlap() {
        val primary = mapOf(
            "main/app/src/main/java/com/example/Home.kt" to "package com.example\nclass Home",
            "main/app/build.gradle.kts" to "dependencies { implementation(\"androidx.core:core-ktx:1.13.1\") }",
            "main/app/src/main/AndroidManifest.xml" to "<manifest><uses-permission android:name=\"android.permission.INTERNET\"/></manifest>",
        )
        val reference = mapOf(
            "reference/app/src/main/java/com/example/Home.kt" to "package com.example\nclass HomeReference",
            "reference/app/build.gradle.kts" to "dependencies { implementation(\"androidx.core:core-ktx:1.12.0\") }",
            "reference/app/src/main/AndroidManifest.xml" to "<manifest><uses-permission android:name=\"android.permission.CAMERA\"/></manifest>",
        )
        val report = ComponentFusion.analyze(primary, reference)
        assertEquals(listOf("app/src/main/java/com/example/Home.kt"), report.exactPathConflicts)
        assertTrue(report.packageOverlaps.contains("com.example"))
        assertTrue(report.dependencyConflicts.any { it.contains("1.13.1") && it.contains("1.12.0") })
        assertTrue(report.permissionDifferences.any { it.contains("android.permission.CAMERA") })
    }

    @Test fun reportsNoBasicCollisionWhenNamesAndPackagesDiffer() {
        val primary = mapOf("one/src/main/java/a/One.kt" to "package a\nclass One")
        val reference = mapOf("two/src/main/java/b/Two.kt" to "package b\nclass Two")
        val report = ComponentFusion.analyze(primary, reference)
        assertTrue(report.exactPathConflicts.isEmpty())
        assertTrue(report.packageOverlaps.isEmpty())
        assertTrue(report.findings.any { it.title == "Sem colisões básicas identificadas" })
    }

    @Test fun markdownClearlyStatesThatNoMergeWasPerformed() {
        val report = ComponentFusion.analyze(
            mapOf("a/A.kt" to "package a"), mapOf("b/B.kt" to "package b"),
        )
        assertTrue(report.toMarkdown().contains("nenhum código foi copiado ou modificado"))
        assertTrue(report.integrationPlan.isNotEmpty())
    }
}
