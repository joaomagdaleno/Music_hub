#!/usr/bin/env kotlin

import java.io.File
import kotlin.system.exitProcess
import java.util.concurrent.TimeUnit

// Configuration
val OUTPUT_FILE = "CHANGELOG_BODY.md"

fun main(args: Array<String>) {
    // Expected: <current_version>
    // We will dynamically find the previous tag.
    
    println("📜 Hermes Changelog Generator")
    println("============================")

    val currentTag = if (args.isNotEmpty()) args[0] else "HEAD"
    val previousTag = getPreviousTag(currentTag)

    println("Generating changelog: $previousTag -> $currentTag")

    val commits = getCommits(previousTag, currentTag)
    val changelog = formatChangelog(commits, previousTag, currentTag)

    File(OUTPUT_FILE).writeText(changelog)
    println("✅ Changelog generated at: $OUTPUT_FILE")
    println("============================")
}

fun getPreviousTag(current: String): String {
    val process = ProcessBuilder("git", "describe", "--tags", "--abbrev=0", "$current^")
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .start()
    
    process.waitFor(5, TimeUnit.SECONDS)
    val tag = process.inputReader().readText().trim()
    
    return if (process.exitValue() == 0 && tag.isNotEmpty()) tag else {
        // Fallback for first release or no prev tag
        println("⚠️ No previous tag found, getting initial commit.")
        val firstCommitProcess = ProcessBuilder("git", "rev-list", "--max-parents=0", "HEAD")
             .start()
        firstCommitProcess.inputReader().readText().trim()
    }
}

fun getCommits(from: String, to: String): List<String> {
    val range = if (from == to) "HEAD" else "$from..$to"
    val process = ProcessBuilder("git", "log", "--pretty=format:%s", range)
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .start()
    
    process.waitFor(5, TimeUnit.SECONDS)
    return process.inputReader().readLines()
}

fun formatChangelog(commits: List<String>, from: String, to: String): String {
    val features = mutableListOf<String>()
    val fixes = mutableListOf<String>()
    val performance = mutableListOf<String>()
    val chores = mutableListOf<String>()
    val others = mutableListOf<String>()

    commits.forEach { msg ->
        val line = msg.trim()
        when {
            line.startsWith("feat", ignoreCase = true) || line.contains("Added", ignoreCase = true) -> features.add("- " + clean(line))
            line.startsWith("fix", ignoreCase = true) || line.contains("Fixed", ignoreCase = true) -> fixes.add("- " + clean(line))
            line.startsWith("perf", ignoreCase = true) || line.contains("Optimize", ignoreCase = true) -> performance.add("- " + clean(line))
            line.startsWith("chore", ignoreCase = true) || line.startsWith("docs", ignoreCase = true) || line.startsWith("refactor", ignoreCase = true) -> chores.add("- " + clean(line))
            else -> others.add("- " + clean(line))
        }
    }

    val sb = StringBuilder()
    sb.append("# 🎵 Change Log ($to)\n\n")

    if (features.isNotEmpty()) {
        sb.append("### ✨ Features\n")
        features.forEach { sb.append("$it\n") }
        sb.append("\n")
    }

    if (fixes.isNotEmpty()) {
        sb.append("### 🐛 Fixes\n")
        fixes.forEach { sb.append("$it\n") }
        sb.append("\n")
    }

    if (performance.isNotEmpty()) {
        sb.append("### ⚡ Performance\n")
        performance.forEach { sb.append("$it\n") }
        sb.append("\n")
    }

    if (chores.isNotEmpty()) {
        sb.append("### 🧹 Maintenance\n")
        chores.forEach { sb.append("$it\n") }
        sb.append("\n")
    }
    
    if (others.isNotEmpty()) {
        sb.append("### 📦 General\n")
        others.forEach { sb.append("$it\n") }
        sb.append("\n")
    }

    sb.append("\n_Since [$from]_")
    return sb.toString()
}

fun clean(msg: String): String {
    // Remove prefixes like "feat:" or "fix(ui):"
    return msg.replace(Regex("^(feat|fix|perf|chore|docs|refactor)(\\(.*?\\))?:\\s*", RegexOption.IGNORE_CASE), "").trim()
}
