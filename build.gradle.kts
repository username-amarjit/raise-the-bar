plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.ktlint) apply false
}

// Milestone 14: Phase 1 needs no secrets, so a key, token or password anywhere in the repo fails the build. The app's
// own code is checked too: no logging or printing (personal data stays on the phone), no SQL built from strings, no
// dynamic code and no network APIs. Runs as part of every module's `check`.
val secretScan by tasks.registering {
    group = "verification"
    description = "Fails on keys, tokens or passwords in the repo, and on logging, raw SQL, dynamic code or network APIs in app code."
    val root = layout.projectDirectory
    val repoFiles =
        fileTree(root) {
            exclude("**/build/**", "**/.gradle/**", ".git/**", ".idea/**", "**/.kotlin/**", "scripts/.cache/**", "local.properties")
        }
    val codeFiles = fileTree(root) { include("app/src/main/**/*.kt", "engine/src/main/**/*.kt") }
    val report = layout.buildDirectory.file("reports/secret-scan.txt")
    inputs.files(repoFiles)
    outputs.file(report)
    doLast {
        val binary = setOf("png", "webp", "jpg", "jpeg", "gif", "ttf", "otf", "jar", "apk", "aab", "dex", "class", "so", "zip")
        val keyFiles = setOf("jks", "keystore", "p12", "pfx", "pem", "key")
        val secrets =
            listOf(
                "a private key" to Regex("-----BEGIN [A-Z ]*PRIVATE KEY-----"),
                "an AWS access key" to Regex("AKIA[0-9A-Z]{16}"),
                "a Google API key" to Regex("AIza[0-9A-Za-z_\\-]{35}"),
                "a GitHub token" to Regex("gh[pousr]_[0-9A-Za-z]{36}|github_pat_[0-9A-Za-z_]{40,}"),
                "a Slack token" to Regex("xox[abprs]-[0-9A-Za-z-]{10,}"),
                "a secret API key" to Regex("\\bsk-[A-Za-z0-9_\\-]{20,}"),
                "a hard-coded credential" to
                    Regex("(?i)\\b(api[_-]?key|client[_-]?secret|secret|token|password|passwd)\\b\\s*[:=]\\s*[\"'][^\"'\\s]{8,}[\"']"),
            )
        val codeRules =
            listOf(
                "logging" to Regex("\\bandroid\\.util\\.Log\\b|\\bLog\\.(v|d|i|w|e|wtf)\\(|\\bprintln\\(|\\bprint\\(|printStackTrace\\(|System\\.(out|err)\\b"),
                "SQL built from strings" to Regex("\\brawQuery\\(|SimpleSQLiteQuery|@RawQuery"),
                "dynamic code" to Regex("ScriptEngine|DexClassLoader|Runtime\\.getRuntime\\(\\)\\.exec|ProcessBuilder\\("),
                "a network API" to Regex("java\\.net\\.(URL|HttpURLConnection|Socket)\\b|\\bokhttp3\\b|\\bio\\.ktor\\b|\\bretrofit2\\b|\\bWebView\\b"),
            )
        val base = root.asFile
        val problems = mutableListOf<String>()
        repoFiles.forEach { f ->
            val path = f.relativeTo(base).invariantSeparatorsPath
            val ext = f.extension.lowercase()
            when (ext) {
                in keyFiles -> problems += "$path: a key file is in the repo"
                in binary -> Unit
                else ->
                    f.useLines { lines ->
                        lines.forEachIndexed { i, line ->
                            secrets.filter { it.second.containsMatchIn(line) }.forEach { problems += "$path:${i + 1}: looks like ${it.first}" }
                        }
                    }
            }
        }
        codeFiles.forEach { f ->
            val path = f.relativeTo(base).invariantSeparatorsPath
            f.readLines().forEachIndexed { i, line ->
                codeRules.filter { it.second.containsMatchIn(line) }.forEach { problems += "$path:${i + 1}: ${it.first}" }
            }
        }
        val out = report.get().asFile
        out.parentFile.mkdirs()
        out.writeText(if (problems.isEmpty()) "No problems found.\n" else problems.joinToString("\n", postfix = "\n"))
        if (problems.isNotEmpty()) throw GradleException("Secret scan found ${problems.size} problem(s):\n" + problems.joinToString("\n"))
    }
}
