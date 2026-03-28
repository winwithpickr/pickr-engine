plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    `maven-publish`
}

group   = "dev.pickrtweet"
version = "0.2.0"

repositories {
    mavenCentral()
}

kotlin {
    jvm()
    js(IR) {
        browser {
            webpackTask {
                mainOutputFileName = "pickr-parser.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":engine"))
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmTest.dependencies {
            implementation(libs.mockk)
        }
    }
}

tasks.register<Copy>("assembleNpm") {
    group = "build"
    description = "Assemble npm package into packages/pickr-verify/"
    dependsOn("jsBrowserProductionWebpack")
    from(layout.buildDirectory.file("kotlin-webpack/js/productionExecutable/pickr-parser.js")) {
        into("lib")
    }
    from(rootProject.layout.projectDirectory.dir("packages/pickr-verify")) {
        include("package.json", "README.md", "bin/**")
    }
    into(layout.buildDirectory.dir("npm-package"))
}

publishing {
    publications.withType<MavenPublication> {
        pom {
            name.set("pickr-twitter")
            description.set("Twitter/X integration for the pickr verifiable selection engine")
            url.set("https://github.com/bmcreations/winwithpickr")
            licenses {
                license {
                    name.set("MIT License")
                    url.set("https://opensource.org/licenses/MIT")
                }
            }
        }
    }
}
