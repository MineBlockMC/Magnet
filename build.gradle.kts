import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.external.javadoc.StandardJavadocDocletOptions

plugins {
    `java-library`
    `maven-publish`
    id("xyz.jpenilla.toothpick")
}

toothpick {
    forkName = "Magnet"
    groupId = "org.mineblock.magnet"
    forkUrl = "https://github.com/MineBlockMC/Magnet"
    // Magnet - providers.exec 替代配置期 ProcessBuilder，Gradle 9 已弃用旧写法
    val versionTag = System.getenv("BUILD_NUMBER")
        ?: providers.exec { commandLine("git", "rev-parse", "--short=7", "HEAD") }.standardOutput.asText.get().trim()
    forkVersion = "git-$forkName-$versionTag"
    paperclipName = "magnetclip"

    minecraftVersion = "1.16.5"
    nmsPackage = "1_16_R3"
    nmsRevision = "R0.1-SNAPSHOT"

    upstream = "Purpur"
    upstreamBranch = "origin/ver/1.16.5"

    server {
        project = rootProject.project(":magnet-server")
        patchesDir = rootProject.projectDir.resolve("patches/server")
    }
    api {
        project = rootProject.project(":magnet-api")
        patchesDir = rootProject.projectDir.resolve("patches/api")
    }
}

subprojects {
    repositories {
        // Magnet - Velocity 旧 Nexus 已停用，统一使用 PaperMC 仓库
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://oss.sonatype.org/content/repositories/snapshots/") {
            name = "sonatype-oss-snapshots"
        }
    }

    java {
        sourceCompatibility = JavaVersion.toVersion(21)
        targetCompatibility = JavaVersion.toVersion(21)
    }

    publishing.repositories.maven {
        url = uri("https://repo.mineblock.cc/snapshots")
        credentials(PasswordCredentials::class)
    }
}

// Magnet - JDK 21 的 javadoc 会把外部链接抓取失败视为错误，构建时不再添加外部链接
subprojects {
    afterEvaluate {
        tasks.withType<Javadoc> {
            (options as? StandardJavadocDocletOptions)?.links?.clear()
        }
    }
}

// Magnet - 固定 Jar 时间戳与条目顺序，避免 pom.properties 写入时间导致 shadowJar 每次都重跑
subprojects {
    tasks.withType<Jar> {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}
