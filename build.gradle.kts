// Plugins go on the root classpath so AGP and the Kotlin plugin share one
// classloader. AGP is only added when the :app module is included (it needs
// the Android SDK), so `:core` can be built on machines without one.
buildscript {
    val kotlinVersion = "2.1.20"
    val agpVersion = "8.9.1"
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
        classpath("org.jetbrains.kotlin:kotlin-serialization:$kotlinVersion")
        classpath("org.jetbrains.kotlin:compose-compiler-gradle-plugin:$kotlinVersion")
        if (findProject(":app") != null) {
            classpath("com.android.tools.build:gradle:$agpVersion")
        }
    }
}
