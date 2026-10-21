/*
 * This file is part of Toothpick, licensed under the MIT License.
 *
 * Copyright (c) 2020-2021 Jason Penilla & Contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package xyz.jpenilla.toothpick

import com.github.jengelman.gradle.plugins.shadow.ShadowPlugin
import com.github.jengelman.gradle.plugins.shadow.relocation.SimpleRelocator
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.logging.Logger
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.publish.maven.tasks.GenerateMavenPom
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.api.tasks.testing.Test
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.attributes
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.getByName
import org.gradle.kotlin.dsl.getValue
import org.gradle.kotlin.dsl.getting
import org.gradle.kotlin.dsl.withType
import xyz.jpenilla.toothpick.data.JavadocPlugin
import xyz.jpenilla.toothpick.data.ShadePlugin
import xyz.jpenilla.toothpick.shadow.ModifiedLog4j2PluginsCacheFileTransformer
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

internal fun Project.commonSubprojectConfiguration() {
  apply<JavaLibraryPlugin>()
  apply<MavenPublishPlugin>()

  tasks.withType<JavaCompile> {
    options.encoding = Charsets.UTF_8.name()
  }
  tasks.withType<Javadoc> {
    options.encoding = Charsets.UTF_8.name()
  }
}

private fun Project.setupPublication(subproject: ToothpickSubproject) {
  extensions.configure<PublishingExtension> {
    publications {
      create<MavenPublication>("mavenJava") {
        groupId = project.group as String
        version = project.version as String
        pom {
          name.set(project.name)
          url.set(subproject.toothpick.forkUrl)
        }
      }
    }
  }
}

private fun Project.configurePublication(configurer: MavenPublication.() -> Unit) {
  extensions.configure<PublishingExtension> {
    publications {
      getByName<MavenPublication>("mavenJava").apply(configurer)
    }
  }
}

internal fun Project.configureServerProject(subproject: ToothpickSubproject) {
  apply<ShadowPlugin>()
  setupPublication(subproject)

  val generatePomFileForMavenJavaPublication = tasks.getByName<GenerateMavenPom>("generatePomFileForMavenJavaPublication") {
    destinationFile.set(project.layout.buildDirectory.file("tmp/pom.xml"))
  }

  tasks.withType<Test> {
    // didn't bother to look into why these fail. paper excludes them in paperweight as well though
    exclude("org/bukkit/craftbukkit/inventory/ItemStack*Test.class")
  }

  tasks.getByName("jar", Jar::class) {
    archiveClassifier.set("unshaded")
  }

  val shadowLogger = project.logger
  val shadowJar = tasks.getByName<ShadowJar>("shadowJar") {
    archiveClassifier.set("") // ShadowJar is the main server artifact
    dependsOn(generatePomFileForMavenJavaPublication)
    transform(ModifiedLog4j2PluginsCacheFileTransformer::class.java)
    mergeServiceFiles()
    // Magnet - Shadow 9 默认的 EXCLUDE 会在 transformer 之前丢弃重复资源，
    // 导致 Log4j2Plugins.dat 与 META-INF/services 合并后缺少其他 jar 中的条目。
    filesMatching("**/Log4j2Plugins.dat") {
      duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
    filesMatching("META-INF/services/**") {
      duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
    manifest {
      attributes(
        "Main-Class" to "org.bukkit.craftbukkit.Main",
        "Implementation-Title" to "CraftBukkit",
        "Implementation-Version" to subproject.toothpick.forkVersion,
        "Implementation-Vendor" to SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").format(Date()),
        "Specification-Title" to "Bukkit",
        "Specification-Version" to project.version.toString(),
        "Specification-Vendor" to "Bukkit Team",
        "Multi-Release" to true,
        // Magnet - 让 java -jar 直接携带 add-opens
        "Add-Opens" to Constants.JVM_ADD_OPENS.joinToString(" ")
      )
    }

    from(project.layout.buildDirectory.file("tmp/pom.xml")) {
      // dirty hack to make "java -Dpaperclip.install=true -jar paperclip.jar" work without forking paperclip
      into("META-INF/maven/io.papermc.paper/paper")
    }

    if (hasProperty(Constants.Properties.NO_RELOCATE)) return@getByName
    // Import relocations from server pom
    val pom = subproject.pom ?: return@getByName
    val shadePlugin = pom.build.plugins.filterIsInstance<ShadePlugin>().firstOrNull() ?: error("Could not find shade plugin in server pom!")
    for (relocation in shadePlugin.executions.first().configuration.relocations) {
      val (pattern, shadedPattern, rawString, includes, excludes) = relocation
      val modifiedExcludes = excludes.toMutableList()
      if (rawString) modifiedExcludes.add("net/minecraft/data/Main*")
      relocate(SimpleRelocator(pattern, shadedPattern, includes, modifiedExcludes, rawString))
    }
  }

  // Magnet start - minecraft-server 自带旧版 Log4j 类，先把该依赖移出自动打包，
  // 再排除其中的 Log4j 类重新加入其余类，避免覆盖 log4j-core 2.26.1
  val minecraftServerJar = project.configurations.named("runtimeClasspath").map { configuration ->
    configuration.files.single { it.name.startsWith("minecraft-server-") && it.extension == "jar" }
  }
  shadowJar.dependencies {
    exclude { it.moduleGroup == "io.papermc" && it.moduleName == "minecraft-server" }
  }
  shadowJar.from(minecraftServerJar.map { project.zipTree(it) }) {
    exclude("org/apache/logging/log4j/**")
  }
  // Magnet end

  // Magnet - minecraft-server 依赖里的 vanilla Main 重定位后会与项目 Main 重名，
  // 保留带 main(OptionSet) 的项目版本，移除 vanilla 版本
  shadowJar.doLast {
    removeVanillaMain(shadowJar.archiveFile.get().asFile, shadowLogger)
  }

  tasks.getByName("build") {
    dependsOn(shadowJar)
  }

  extensions.configure<JavaPluginExtension> {
    withSourcesJar()
  }

  configurePublication {
    artifactId = subproject.toothpick.forkNameLowercase
    artifact(tasks["shadowJar"])
    artifact(tasks["sourcesJar"])
  }
}

internal fun Project.configureApiProject(subproject: ToothpickSubproject) {
  tasks.withType<Jar> {
    val pomProperties = project.layout.buildDirectory.file("tmp/pom.properties")
    // Magnet - capture these at configuration time; Task.project is deprecated at execution time.
    val projectVersion = project.version.toString()
    doFirst {
      pomProperties.get().asFile.apply { parentFile.mkdirs() }
        .writeText("version=$projectVersion")
    }
    from(pomProperties) {
      into("META-INF/maven/${project.group}/${project.name}")
    }
    manifest {
      attributes("Automatic-Module-Name" to "org.bukkit")
    }
  }

  tasks.withType<Javadoc> {
    options {
      if (this !is StandardJavadocDocletOptions) return@options

      val currentVersion = JavaVersion.current().majorVersion.toInt()

      links(jdkApiDocs(currentVersion))

      if (currentVersion in 9..11) {
        // Apply workaround for https://bugs.openjdk.java.net/browse/JDK-8215291
        // Fixes search links, but breaks external doc links which use modules. Fixed in JDK 12+.
        val noModuleDirectories = addBooleanOption("-no-module-directories")
        noModuleDirectories.value = true
      }

      val pom = subproject.pom ?: return@options
      val javadocPlugin = pom.build.plugins.filterIsInstance<JavadocPlugin>().firstOrNull() ?: return@options
      for (link in javadocPlugin.configuration.links) {
        links(link)
      }
    }
  }

  extensions.configure<JavaPluginExtension> {
    withSourcesJar()
    withJavadocJar()
  }

  setupPublication(subproject)
  configurePublication {
    artifactId = project.name
    from(components["java"])
  }
}

private fun jdkApiDocs(javaVersion: Int): String = if (javaVersion >= 11) {
  "https://docs.oracle.com/en/java/javase/$javaVersion/docs/api"
} else {
  "https://docs.oracle.com/javase/$javaVersion/docs/api"
}

private fun removeVanillaMain(jar: File, logger: Logger) {
  if (!jar.isFile) return
  val temp = File(jar.parentFile, "${jar.name}.tmp")
  var keptProjectMain = false
  JarFile(jar).use { input ->
    val manifest = input.manifest
    val output = if (manifest != null) {
      JarOutputStream(temp.outputStream().buffered(), manifest)
    } else {
      JarOutputStream(temp.outputStream().buffered())
    }
    output.use { out ->
      for (entry in input.entries()) {
        if (entry.name.equals("META-INF/MANIFEST.MF", ignoreCase = true)) continue
        if (entry.name == "net/minecraft/server/v1_16_R3/Main.class") {
          val bytes = input.getInputStream(entry).use { it.readBytes() }
          val isProjectMain = String(bytes, Charsets.ISO_8859_1).contains("(Ljoptsimple/OptionSet;)V")
          if (!isProjectMain || keptProjectMain) {
            logger.lifecycle(">>> Removed duplicate vanilla Main class from ${jar.name}")
            continue
          }
          keptProjectMain = true
          out.putNextEntry(JarEntry(entry.name))
          out.write(bytes)
          out.closeEntry()
          continue
        }
        out.putNextEntry(JarEntry(entry.name))
        input.getInputStream(entry).use { it.copyTo(out) }
        out.closeEntry()
      }
    }
  }
  temp.copyTo(jar, overwrite = true)
  temp.delete()
}
