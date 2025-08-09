plugins {
    id("java")
    id("application")
}

group = "com.kayar.yetanotherlabyrinth"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.github.almasb:fxgl:21.1")

    // JavaFX 21 for Windows
    implementation("org.openjfx:javafx-base:21:win")
    implementation("org.openjfx:javafx-graphics:21:win")
    implementation("org.openjfx:javafx-controls:21:win")
    implementation("org.openjfx:javafx-media:21:win")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

application {
    mainClass.set("com.kayar.yetanotherlabyrinth.LabyrinthApp")
}

tasks.test {
    useJUnitPlatform()
}