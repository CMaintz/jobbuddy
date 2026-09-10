plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    java
    id("com.diffplug.spotless") version "6.25.0"
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
