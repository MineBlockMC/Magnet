plugins {
    `java-library`
    `maven-publish`
    id("xyz.jpenilla.toothpick")
}

toothpick {
    forkName = "Magnet"
    groupId = "org.mineblock.magnet"
    forkUrl = "https://github.com/MineBlockMC/Magnet"
    val versionTag = System.getenv("BUILD_NUMBER")
        ?: "\"${commitHash() ?: error("Could not obtain git hash")}\""
    forkVersion = "git-$forkName-$versionTag"

    minecraftVersion = "1.16.5"
    nmsPackage = "1_16_R3"
    nmsRevision = "R0.1-SNAPSHOT"

    upstream = "Purpur"
    upstreamBranch = "origin/ver/1.16.5"

    server {
        project = projects.magnetServer.dependencyProject
        patchesDir = rootProject.projectDir.resolve("patches/server")
    }
    api {
        project = projects.magnetApi.dependencyProject
        patchesDir = rootProject.projectDir.resolve("patches/api")
    }
}

subprojects {
    repositories {
        maven("https://nexus.velocitypowered.com/repository/velocity-artifacts-snapshots/")
        maven("https://oss.sonatype.org/content/repositories/snapshots/") {
            name = "sonatype-oss-snapshots"
        }
    }

    java {
        sourceCompatibility = JavaVersion.toVersion(8)
        targetCompatibility = JavaVersion.toVersion(8)
    }

    publishing.repositories.maven {
        url = uri("https://repo.mineblock.cc/snapshots")
        credentials(PasswordCredentials::class)
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}
