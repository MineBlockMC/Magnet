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
package xyz.jpenilla.toothpick.task

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import xyz.jpenilla.toothpick.Constants
import xyz.jpenilla.toothpick.cmd
import xyz.jpenilla.toothpick.ensureSuccess
import xyz.jpenilla.toothpick.jenkins
import java.io.File
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

public open class Paperclip : ToothpickTask() {
  @InputFile
  public val patchedJar: RegularFileProperty = project.objects.fileProperty()

  @TaskAction
  public fun paperclip() {
    val workDir = toothpick.paperDir.resolve("work")
    val paperclipDir = workDir.resolve("Paperclip")
    val vanillaJarPath = workDir.resolve("Minecraft/${toothpick.minecraftVersion}/${toothpick.minecraftVersion}.jar").absolutePath
    val patchedJarPath = patchedJar.get().asFile.absolutePath
    val assemblyPom = paperclipDir.resolve("assembly/pom.xml")
    val originalPom = assemblyPom.takeIf { it.isFile }?.readText()
    // Magnet - papermc.io 旧仓库已停用，构建前修正 Paperclip 的插件仓库地址
    patchPaperclipRepository(paperclipDir)
    logger.lifecycle(">>> Building paperclip")
    val paperclipCmd = arrayListOf(
      toothpick.mavenCommand, "clean", "package",
      "-Dmcver=${toothpick.minecraftVersion}",
      "-Dpaperjar=$patchedJarPath",
      "-Dvanillajar=$vanillaJarPath"
    )
    if (jenkins) paperclipCmd.add("-Dstyle.color=never")
    try {
      ensureSuccess(cmd(*paperclipCmd.toTypedArray(), dir = paperclipDir, printOut = true))
    } finally {
      // Magnet - 还原临时修改，保持 Paperclip 子模块干净
      if (originalPom != null) assemblyPom.writeText(originalPom)
    }
    val paperClip = paperclipDir.resolve("assembly/target/paperclip-${toothpick.minecraftVersion}.jar")
    val destination = toothpick.project.projectDir.resolve(toothpick.paperclipName)
    paperClip.copyTo(destination, overwrite = true)
    addAddOpens(destination)
    logger.lifecycle(">>> ${toothpick.paperclipName} saved to root project directory")
  }

  // Magnet - Paperclip 在同一 JVM 内启动服务端，清单里的 Add-Opens 会被 java -jar 读取
  private fun addAddOpens(jar: File) {
    val temp = File(jar.parentFile, "${jar.name}.tmp")
    JarFile(jar).use { input ->
      val manifest = input.manifest ?: error("Missing manifest in ${jar.absolutePath}")
      manifest.mainAttributes[Attributes.Name("Add-Opens")] = Constants.JVM_ADD_OPENS.joinToString(" ")
      JarOutputStream(temp.outputStream().buffered(), manifest).use { output ->
        for (entry in input.entries()) {
          if (entry.name.equals("META-INF/MANIFEST.MF", ignoreCase = true)) continue
          output.putNextEntry(JarEntry(entry.name))
          input.getInputStream(entry).use { it.copyTo(output) }
          output.closeEntry()
        }
      }
    }
    temp.copyTo(jar, overwrite = true)
    temp.delete()
  }

  private fun patchPaperclipRepository(paperclipDir: File) {
    val assemblyPom = paperclipDir.resolve("assembly/pom.xml")
    if (!assemblyPom.isFile) return
    val oldUrl = "https://papermc.io/repo/repository/maven-releases/"
    val newUrl = "https://repo.papermc.io/repository/maven-public/"
    val content = assemblyPom.readText()
    if (content.contains(oldUrl)) {
      assemblyPom.writeText(content.replace(oldUrl, newUrl))
    }
  }
}
