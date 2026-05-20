plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    java
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
