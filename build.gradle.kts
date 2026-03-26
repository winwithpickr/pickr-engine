plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    `maven-publish`
}

group   = "dev.pickrtweet"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.coroutines.core)
    // No Ktor, no Exposed, no Stripe, no Redis — zero service dependencies
    testImplementation(libs.kotlin.test)
    testImplementation(libs.mockk)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
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
}
