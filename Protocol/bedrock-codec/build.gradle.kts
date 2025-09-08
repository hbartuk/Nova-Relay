plugins {
    id("java-library")
    alias(libs.plugins.lombok)
    alias(libs.plugins.checkerframework)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://repo.opencollab.dev/maven-releases")
    maven("https://repo.opencollab.dev/maven-snapshots")
}

dependencies {
    api(project(":Protocol:common"))
    api(libs.adventure.text.serializer.legacy)
    api(libs.adventure.text.serializer.json)
    api(platform(libs.fastutil.bom))
    api(libs.netty.buffer)
    api(libs.fastutil.long.common)
    api(libs.fastutil.long.obj.maps)
    api(libs.jose4j)
    api(libs.nbt)
    api(libs.jackson.annotations)
    api(libs.jackson.databind)

    // Tests
    testImplementation(libs.junit)
}