// Top-level build file where you can add configuration options common to all sub-projects/modules.
// Co-authored-by: Gemini AI Agent
// Co-authored-by: Claude Opus 5.5
// Co-authored-by: OpenAI Codex
plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.jetbrainsKotlinAndroid) apply false
    alias(libs.plugins.kotlinCompose) apply false
    alias(libs.plugins.ktfmt) apply false
    alias(libs.plugins.googleServices) apply false
    alias(libs.plugins.sonar)
}

sonar {
    properties {
        property("sonar.organization", "didyoustudy")
        property("sonar.projectKey", "DidYouStudy_App")
        property("sonar.host.url", "https://sonarcloud.io")
        property("sonar.sourceEncoding", "UTF-8")
        // Override the server's directory-only value with the generated XML report.
        property(
            "sonar.coverage.jacoco.xmlReportPaths",
            "${project(":app").layout.buildDirectory.get().asFile.absolutePath}/reports/jacoco/jacocoTestReport/jacocoTestReport.xml",
        )
        property("sonar.exclusions", "**/res/**/*.webp,**/res/**/*.png,**/res/**/*.jpg,**/build/**")
    }
}

tasks.named("sonar") { dependsOn(":app:jacocoTestReport") }
