plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    java
    id("com.diffplug.spotless") version "6.25.0"
    id("com.github.spotbugs") version "6.5.11"
}

// SpotBugs bug-pattern analysis — BLOCKING (fails the build on findings).
// config/spotbugs/exclude.xml carries the two accepted noise categories (EI/EI2
// mutable-exposure on JPA/records, and prompt-string \n); individual false
// positives are suppressed at the method with @SuppressFBWarnings + justification.
// Every other (real) finding fails the build. Runs only because commons-lang3 is
// overridden to 3.18.0 above — SpotBugs needs org.apache.commons.lang3.Strings,
// which Spring's managed 3.17.0 lacked.
spotbugs {
    ignoreFailures.set(false)
    excludeFilter.set(file("config/spotbugs/exclude.xml"))
}

// Foundry `lint`/`fix` verbs. Deliberately minimal — no full reformat, just
// import hygiene and whitespace — so enabling it doesn't churn the codebase.
// ratchetFrom means only files changed vs origin/main are enforced: green on day one.
spotless {
    java {
        target("src/**/*.java")
        ratchetFrom("origin/main")
        removeUnusedImports()
        importOrder()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

// Enables `--write-locks` so osv-scanner (the audit verb / CI job) can read a
// gradle.lockfile. The lockfile is NOT committed and is generated on demand:
// with locking enabled but no lockfile present, normal builds resolve freely
// (no enforcement), so this never breaks the gate. Regenerate for a scan with:
//   ./gradlew :backend:dependencies --write-locks
dependencyLocking {
    lockAllConfigurations()
}

// Security bumps: override Spring-managed transitive versions to clear known CVEs
// surfaced by the osv-scanner audit. These are patch/minor bumps within the
// versions Spring Boot 3.5 supports. commons-lang3 3.18.0 also unblocks SpotBugs
// (it needs org.apache.commons.lang3.Strings). Re-check with `mise run backend:audit`.
extra["tomcat.version"] = "10.1.59"        // GHSA-9xv2/gcx9/h3x4 (incl. 9.8); 10.1.58 was skipped
extra["jackson-bom.version"] = "2.21.5"    // GHSA-5gvw/5jmj/mhm7
extra["netty.version"] = "4.1.137.Final"   // 11 netty-codec/http/handler CVEs
extra["log4j2.version"] = "2.25.5"         // GHSA-qv9r
extra["postgresql.version"] = "42.7.12"    // GHSA-j92g
extra["commons-lang3.version"] = "3.18.0"  // GHSA-j288 + unblocks SpotBugs
extra["httpclient5.version"] = "5.6.3"     // GHSA-hjcp
extra["httpcore5.version"] = "5.4.3"       // GHSA-hf6x/v3jc (7.5)
extra["opentelemetry.version"] = "1.63.0"  // GHSA-rcgg

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.quartz)
    implementation(libs.spring.boot.starter.mail)
    implementation(libs.springdoc.openapi)

    // Database
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)
    implementation(libs.hypersistence.utils)
    implementation(libs.pgvector)

    // Firebase Admin
    implementation(libs.firebase.admin)

    // AI
    implementation(libs.openai.java)

    // SpotBugs annotations — compile-only; used to suppress specific bug patterns
    // (e.g. REC_CATCH_EXCEPTION) on methods whose try blocks genuinely throw a
    // checked exception, so catching Exception is required.
    compileOnly("com.github.spotbugs:spotbugs-annotations:4.9.8")

    // Utilities
    implementation(libs.jsoup)
    implementation(libs.pdfbox)
    implementation(libs.openhtmltopdf.pdfbox)

    // Test
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.security.test)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Run the crawler as a one-shot CLI command (no HTTP server starts):
//   ./gradlew :backend:crawl
//   ./gradlew :backend:crawl --args="--crawler.source=JOBINDEX"
tasks.register<org.springframework.boot.gradle.tasks.run.BootRun>("crawl") {
    group = "application"
    description = "Trigger job crawl via CLI (uses crawler Spring profile, no web server)"
    val bootRun = tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun").get()
    classpath = bootRun.classpath
    mainClass.set(bootRun.mainClass)
    args("--spring.profiles.active=crawler")
}
