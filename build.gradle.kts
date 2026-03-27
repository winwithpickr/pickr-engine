plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    `maven-publish`
}

group   = "dev.pickrtweet"
version = "0.1.0"

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

tasks.register<JavaExec>("verifyCli") {
    group = "application"
    description = "Verify a giveaway pick result from the command line"
    mainClass.set("dev.pickrtweet.core.VerifyCliKt")
    val jvmMain = kotlin.jvm().compilations["main"]
    classpath = files(jvmMain.output.allOutputs, jvmMain.runtimeDependencyFiles)
}

tasks.register<Jar>("verifyJar") {
    group = "build"
    description = "Build standalone pickr-verify.jar"
    archiveBaseName.set("pickr-verify")
    archiveClassifier.set("")
    archiveVersion.set("")
    manifest { attributes["Main-Class"] = "dev.pickrtweet.core.VerifyCliKt" }
    val jvmMain = kotlin.jvm().compilations["main"]
    from(jvmMain.output.allOutputs)
    from(configurations.named("jvmRuntimeClasspath").map { config ->
        config.map { if (it.isDirectory) it else zipTree(it) }
    })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.register<Copy>("assembleNpm") {
    group = "build"
    description = "Assemble npm package into packages/pickr-verify/"
    dependsOn("jsBrowserProductionWebpack")
    from(layout.buildDirectory.file("kotlin-webpack/js/productionExecutable/pickr-parser.js")) {
        into("lib")
    }
    from(layout.projectDirectory.dir("packages/pickr-verify")) {
        include("package.json", "README.md", "bin/**")
    }
    into(layout.buildDirectory.dir("npm-package"))
}

publishing {
    publications.withType<MavenPublication> {
        pom {
            name.set("pickr")
            description.set("Verifiable random giveaway winner selection engine")
            url.set("https://github.com/bmcreations/pickr")
            licenses {
                license {
                    name.set("MIT License")
                    url.set("https://opensource.org/licenses/MIT")
                }
            }
        }
    }
}
