// Top-level build file where you can add configuration options common to all sub-projects/modules.
// Co-authored-by: Gemini AI Agent
plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.jetbrainsKotlinAndroid) apply false
    alias(libs.plugins.kotlinCompose) apply false
    alias(libs.plugins.ktfmt) apply false
    alias(libs.plugins.sonar)
}

sonar {
    properties {
        property("sonar.organization", "didyoustudy")
        property("sonar.projectKey", "DidYouStudy_App")
        property("sonar.host.url", "https://sonarcloud.io")
        property("sonar.sourceEncoding", "UTF-8")
        property("sonar.exclusions", "**/*.webp,**/*.png,**/*.jpg,**/*.keystore,**/build/**")
    }
}

tasks.named("sonar") { dependsOn(":app:jacocoTestReport") }