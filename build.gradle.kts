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

    paperclipName = "magnetclip"

    minecraftVersion = "1.12.2"
    nmsPackage = "1_12_R1"
    nmsRevision = "R0.1-SNAPSHOT"

    upstream = "Dionysus"
    upstreamBranch = "origin/dev"

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
        // Magnet - nexus.velocitypowered.com is decommissioned (HTTP 522); Velocity and
        // Paper artifacts are served by repo.papermc.io now.
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://oss.sonatype.org/content/repositories/snapshots/") {
            name = "sonatype-oss-snapshots"
        }
    }

    java {
        sourceCompatibility = JavaVersion.toVersion(17)
        targetCompatibility = JavaVersion.toVersion(17)
    }

    publishing.repositories.maven {
        url = uri("https://repo.mineblock.cc/snapshots")
        credentials(PasswordCredentials::class)
    }
}

// Magnet - Paper 1.12.2 uses the old decompile layout, which has no paperweight-style
// `libraries/` directory. Toothpick's importMCDev task lists that directory and would fail
// with "Unable to list library groups", so make sure it exists before the task runs.
val toothpickExtension = extensions.getByType(xyz.jpenilla.toothpick.ToothpickExtension::class.java)
val decompileLibrariesDir = projectDir.resolve(
    "${toothpickExtension.upstream}/Paper/work/Minecraft/${toothpickExtension.minecraftVersion}/libraries"
)
tasks.named("importMCDev") {
    doFirst {
        decompileLibrariesDir.mkdirs()
    }
}

// Magnet - Replace http to https due to Maven 3.8+ blocked it.
val paperclipDir = projectDir.resolve("${toothpickExtension.upstream}/Paper/work/Paperclip")
val paperclipRepoRewrites = listOf(
    "java8/pom.xml" to ("http://clojars.org/repo" to "https://repo.clojars.org"),
    "assembly/pom.xml" to (
        "https://papermc.io/repo/repository/maven-releases/" to
            "https://repo.papermc.io/repository/maven-public/"
        )
)

// useModernUrls = true -> patch the POMs for the build; false -> restore the original URLs.
val rewritePaperclipRepos: (Boolean) -> Unit = { useModernUrls ->
    paperclipRepoRewrites.forEach { (relativePath, urls) ->
        val pom = paperclipDir.resolve(relativePath)
        if (pom.exists()) {
            val (upstream, modern) = urls
            val from = if (useModernUrls) upstream else modern
            val to = if (useModernUrls) modern else upstream
            val text = pom.readText()
            val fixed = text.replace(from, to)
            if (fixed != text) {
                pom.writeText(fixed)
                logger.lifecycle(">>> ${if (useModernUrls) "Rewrote" else "Restored"} repository URL in $relativePath")
            }
        }
    }
}

tasks.named("paperclip") {
    doFirst { rewritePaperclipRepos(true) }
}

// Always restore the upstream POMs afterwards.
val restorePaperclipPoms = tasks.register("restorePaperclipPoms") {
    group = "toothpick"
    description = "Restores the Paperclip POMs patched during the paperclip build."
    doLast { rewritePaperclipRepos(false) }
}
tasks.named("paperclip") { finalizedBy(restorePaperclipPoms) }

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}
