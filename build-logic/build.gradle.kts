import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

group = "xyz.jpenilla"
version = "1.1.0-Magnet"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// Magnet - Toothpick logs its implementation version from the jar manifest at configure time.
tasks.jar {
    manifest {
        attributes("Implementation-Version" to project.version)
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("com.gradleup.shadow:shadow-gradle-plugin:9.6.1")
    implementation("org.spongepowered:configurate-hocon:4.2.0")
    implementation("org.spongepowered:configurate-extra-kotlin:4.2.0")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.22.4")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.22.4")
}

gradlePlugin {
    plugins {
        create("toothpick") {
            id = "xyz.jpenilla.toothpick"
            implementationClass = "xyz.jpenilla.toothpick.Toothpick"
        }
        create("toothpickSettings") {
            id = "xyz.jpenilla.toothpick.settings"
            implementationClass = "xyz.jpenilla.toothpick.ToothpickSettingsPlugin"
        }
    }
}
