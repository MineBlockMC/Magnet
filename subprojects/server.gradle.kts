import java.io.File

// Magnet - Dionysus (Paper 1.12.2) depends on org.spigotmc:minecraft-server, which the
// upstream build installs into the local Maven repository (Paper/scripts/remap.sh).
// Toothpick only whitelists io.papermc:minecraft-server for mavenLocal, so add an
// explicit, unfiltered local repository here.
repositories {
    maven {
        name = "MagnetLocalMaven"
        url = uri(File(System.getProperty("user.home"), ".m2/repository"))
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
