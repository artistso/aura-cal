package com.example

import com.example.aura.kernel.KernelTests
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun auraKernelExecutableSpecificationsPass() {
        val report = KernelTests.runKernelSuite()
        val failureDetails = report.outcomes
            .filter { !it.ok }
            .joinToString("\n") { "[${it.suite}] ${it.name}: ${it.error}" }
        assertEquals("Kernel spec failures:\n$failureDetails", 0, report.failed)
        assertTrue("Expected at least 60 kernel specs, got ${report.total}", report.total >= 60)
    }
}
