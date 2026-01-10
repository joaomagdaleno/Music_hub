#!/usr/bin/env kotlin

import java.io.File
import java.time.Instant
import kotlin.system.exitProcess

// Configuration
val MAX_APK_SIZE_MB = 100
val METRICS_DIR = "metrics"

// Expected Args: <apk_path> <version> <commit_hash>
if (args.size < 3) {
    println("Usage: build_health.main.kts <apk_path> <version> <commit_hash>")
    exitProcess(1)
}

val apkPath = args[0]
val version = args[1]
val commit = args[2]
val apkFile = File(apkPath)

println("🔍 Hermes Build Health Check")
println("============================")

// 1. Verify Artifact Existence
if (!apkFile.exists()) {
    println("❌ Error: APK artifact not found at '$apkPath'")
    exitProcess(1)
}

// 2. Size Analysis
val sizeBytes = apkFile.length()
val sizeMb = sizeBytes / (1024.0 * 1024.0)

println("📊 Artifact: ${apkFile.name}")
println("   Version:  $version")
println("   Commit:   $commit")
println("   Size:     %.2f MB".format(sizeMb))

// 3. Performance Budget Check
if (sizeMb > MAX_APK_SIZE_MB) {
    println("❌ FAILURE: APK size (%.2f MB) exceeds performance budget of $MAX_APK_SIZE_MB MB!".format(sizeMb))
    println("   Action: Optimize assets, enable resource shrinking, or use R8 more aggressively.")
    exitProcess(1)
} else {
    println("✅ SUCCESS: Size within budget (< $MAX_APK_SIZE_MB MB)")
}

// 4. Generate Telemetry
val metricsDir = File(METRICS_DIR)
if (!metricsDir.exists()) {
    metricsDir.mkdirs()
}

val json = """
{
  "platform": "android",
  "version": "$version",
  "apk_size_bytes": $sizeBytes,
  "timestamp": "${Instant.now()}",
  "commit": "$commit"
}
""".trimIndent()

val telemetryFile = File(metricsDir, "android_telemetry.json")
telemetryFile.writeText(json)
println("✅ Telemetry generated: ${telemetryFile.path}")
println("============================")
