plugins {
    kotlin("jvm") version "2.0.0"
}

group = "dev.the_nerd2.notebook"
version = "0.1"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    implementation("com.formdev:flatlaf:3.4.1")
    implementation("com.miglayout:miglayout-swing:11.3")
    implementation(platform("dev.whyoleg.cryptography:cryptography-bom:0.3.1"))
    implementation("dev.whyoleg.cryptography:cryptography-core")
    implementation("dev.whyoleg.cryptography:cryptography-provider-jdk")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}