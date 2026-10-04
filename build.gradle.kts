// Top-level build file where you can add configuration options common to all sub-projects/modules.
// Co-authored-by: Gemini AI Agent
// Co-authored-by: Codex AI Agent
plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.jetbrainsKotlinAndroid) apply false
    alias(libs.plugins.kotlinCompose) apply false
    alias(libs.plugins.ktfmt) apply false
    alias(libs.plugins.sonar)

    id("com.google.gms.google-services") version "4.5.0" apply false
}

sonar {
    properties {
        property("sonar.organization", "didyoustudy")
        property("sonar.projectKey", "DidYouStudy_App")
        property("sonar.host.url", "https://sonarcloud.io")
        property("sonar.sourceEncoding", "UTF-8")
        property("sonar.exclusions", "**/res/**/*.webp,**/res/**/*.png,**/res/**/*.jpg,**/build/**")
    }
}

tasks.named("sonar") { dependsOn(":app:jacocoTestReport") }
