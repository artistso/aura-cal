/*
 * Run the kernel specification suite on a plain JVM, with nothing but a JDK and kotlinc.
 *
 * The kernel lives inside the Android app module, so `gradle testDebugUnitTest` needs the Android
 * SDK even though none of the specs touch an Android type. That made the correctness gate depend on
 * a toolchain download that has nothing to do with correctness. This harness removes the
 * dependency: the same SuiteReport the in-app runner displays is produced here and the process
 * exits non-zero if anything fails.
 *
 * Build and run from the repository root ([asterisk] stands for the glob star; Kotlin block
 * comments nest, so the literal token cannot appear here):
 *
 *   kotlinc app/src/main/java/com/example/aura/kernel/[asterisk].kt tools/RunKernelSpecs.kt \
 *       -include-runtime -d build/kernel-specs.jar
 *   java -cp build/kernel-specs.jar kernel.specs.KernelSpecs
 */

@file:JvmName("KernelSpecs")

package kernel.specs

import com.example.aura.kernel.KernelTests
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val verbose = "--verbose" in args
    val report = KernelTests.runKernelSuite()

    report.suites.forEach { suite ->
        val status = if (suite.failed == 0) "PASS" else "FAIL"
        println("$status  ${suite.suite} (${suite.passed}/${suite.passed + suite.failed})")
    }

    val failures = report.outcomes.filter { !it.ok }
    if (failures.isNotEmpty()) {
        println()
        failures.forEach { failure ->
            println("  x ${failure.suite} / ${failure.name}")
            failure.error?.lineSequence()?.take(6)?.forEach { println("      $it") }
        }
    }

    if (verbose) {
        println()
        report.outcomes.forEach { println("${if (it.ok) "ok  " else "FAIL"} ${it.suite} / ${it.name}") }
    }

    println()
    println(
        "kernel specs: ${report.passed}/${report.total} passed, ${report.failed} failed " +
            "in ${report.durationMs} ms (${report.librarySymbols} library symbols)"
    )
    exitProcess(if (report.failed == 0) 0 else 1)
}
