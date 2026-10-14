import java.io.File

// Magnet - Dionysus (Paper 1.12.2) depends on org.spigotmc:minecraft-server, which the
// upstream build installs into the local Maven repository (Paper/scripts/remap.sh).
// Toothpick only whitelists io.papermc:minecraft-server for mavenLocal, so add an
// explicit, unfiltered local repository here.
//
// Resolve the local repository the same way Maven does: -Dmaven.repo.local, then
// <localRepository> in the user/global settings.xml, then ~/.m2/repository.
fun magnetLocalMavenRepo(): File {
    System.getProperty("maven.repo.local")?.let { return File(it) }
    System.getenv("MAVEN_OPTS")?.split(" ")?.firstOrNull { it.startsWith("-Dmaven.repo.local=") }
        ?.substringAfter("=")?.let { return File(it) }
    val userSettings = File(System.getProperty("user.home"), ".m2/settings.xml")
    val mavenHome = System.getenv("M2_HOME") ?: System.getenv("MAVEN_HOME")
    val globalSettings = mavenHome?.let { File(it, "conf/settings.xml") }
    for (settings in listOfNotNull(userSettings.takeIf { it.isFile }, globalSettings?.takeIf { it.isFile })) {
        try {
            val value = javax.xml.parsers.DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(settings)
                .getElementsByTagName("localRepository")
            if (value.length > 0) {
                val text = value.item(0).textContent?.trim().orEmpty()
                if (text.isNotEmpty()) return File(text)
            }
        } catch (_: Exception) {
            // Ignore malformed settings files and fall through to the default location.
        }
    }
    return File(System.getProperty("user.home"), ".m2/repository")
}

repositories {
    maven {
        name = "MagnetLocalMaven"
        url = uri(magnetLocalMavenRepo())
    }
}

// Magnet - Dionysus' server sources also reference jdk.incubator.vector.
tasks.withType<org.gradle.api.tasks.compile.JavaCompile> {
    options.compilerArgs.add("--add-modules=jdk.incubator.vector")
}

// Magnet - only the server jar is required; the old 1.12.2 sources do not Javadoc cleanly on JDK 17.
tasks.withType<org.gradle.api.tasks.javadoc.Javadoc> {
    enabled = false
}

// Magnet - shade log4j ahead of org.spigotmc:minecraft-server, whose bundled legacy log4j would
// otherwise win (Shadow keeps the first copy of a duplicate entry). Keep in sync with
// <log4j2.version> in Magnet-Server/pom.xml.
val pinnedLog4j = configurations.create("magnetPinnedLog4j") {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

dependencies {
    add(pinnedLog4j.name, "org.apache.logging.log4j:log4j-core:2.26.1")
    add(pinnedLog4j.name, "org.apache.logging.log4j:log4j-api:2.26.1")
}

tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    configurations = listOf(pinnedLog4j) + configurations
}

