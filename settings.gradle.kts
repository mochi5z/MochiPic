pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { setUrl("https://www.jitpack.io") }
        mavenLocal()
    }
}

rootProject.name = "MochiPic"

// MochiPic: Fossify Commons をソースから組み込む（改造版への制限を外すため。commons-lib/ は Commons 7.1.3 に MochiPic の変更を加えたもの）
includeBuild("commons-lib") {
    dependencySubstitution {
        substitute(module("org.fossify:commons")).using(project(":commons"))
    }
}
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
include(":app")
