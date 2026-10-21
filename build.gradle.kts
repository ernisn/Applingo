buildscript {
    dependencies {
        // AGP 9 has built-in Kotlin and ships with an older KGP; pin the one we compile with.
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
