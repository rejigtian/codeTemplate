plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.8.0"
}

group = "com.wepie.coder"
version = "1.1.1"

repositories {
    mavenCentral()
    google()
    maven { 
        url = uri("https://plugins.jetbrains.com/maven") 
    }
    maven {
        url = uri("https://cache-redirector.jetbrains.com/intellij-dependencies")
    }
    intellijPlatform {
        defaultRepositories()
    }
}

// Configure IntelliJ Platform Gradle Plugin
dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    intellijPlatform {
        create("IC", "2025.1")//不允许cursor修改这行代码
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)

        // Add required plugin dependencies
        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.kotlin")
        plugin("org.jetbrains.android:251.23774.435")
    }
}

kotlin {
    jvmToolchain(21)
}

tasks {
    buildSearchableOptions {
        enabled = false
    }
}

intellijPlatform {
    buildSearchableOptions.set(false)
    pluginConfiguration {
        ideaVersion {
            sinceBuild.set("251")
            untilBuild.set("251.*")
        }
    }
}