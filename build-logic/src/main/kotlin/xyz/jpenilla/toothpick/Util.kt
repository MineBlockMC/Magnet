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

import org.gradle.api.logging.Logging
import java.io.File
import java.util.LinkedList
import kotlin.streams.asSequence

public data class CmdResult(val exitCode: Int, val output: String?)

public fun cmd(
  vararg args: String,
  dir: File,
  printOut: Boolean = false,
  environment: Map<String, String> = emptyMap()
): CmdResult =
  cmdImpl(*args, dir = dir, printOut = printOut, environment = environment)

private fun cmdImpl(
  vararg args: String,
  dir: File,
  printOut: Boolean = false,
  environment: Map<String, String> = emptyMap()
): CmdResult {
  val process = ProcessBuilder().apply {
    command(*args)
    redirectErrorStream(true)
    directory(dir)
    environment().putAll(environment)
  }.start()
  val output = process.inputStream.bufferedReader().use { reader ->
    val logger = Logging.getLogger(Toothpick::class.java)
    reader.lines().asSequence()
      .onEach {
        if (printOut) {
          logger.lifecycle(it)
        } else {
          logger.debug(it)
        }
      }
      .toCollection(LinkedList())
      .joinToString(separator = "\n")
  }
  val exit = process.waitFor()
  return CmdResult(exit, output)
}

internal fun ensureSuccess(
  cmd: CmdResult,
  errorHandler: CmdResult.() -> Unit = {}
): String? {
  val (exit, output) = cmd
  if (exit != 0) {
    errorHandler(cmd)
    error("Failed to run command, exit code is $exit")
  }
  return output
}

public fun gitCmd(
  vararg args: String,
  dir: File,
  printOut: Boolean = false
): CmdResult =
  cmd("git", *args, dir = dir, printOut = printOut)

public fun bashCmd(
  vararg args: String,
  dir: File,
  printOut: Boolean = false,
  environment: Map<String, String> = emptyMap()
): CmdResult =
  cmd(bashExecutable(), "-c", *args, dir = dir, printOut = printOut, environment = environment)

// Magnet - Windows 的 PATH 通常只有 Git\cmd，不能直接找到 bash.exe
private val cachedBashExecutable: String by lazy { findBashExecutable() }

private fun bashExecutable(): String = cachedBashExecutable

private fun findBashExecutable(): String {
  if (!System.getProperty("os.name").lowercase().contains("win")) return "bash"

  val programFiles = System.getenv("ProgramFiles") ?: "C:\\Program Files"
  val programFilesX86 = System.getenv("ProgramFiles(x86)") ?: "C:\\Program Files (x86)"
  val candidates = listOf(
    File(programFiles, "Git\\bin\\bash.exe"),
    File(programFiles, "Git\\usr\\bin\\bash.exe"),
    File(programFilesX86, "Git\\bin\\bash.exe"),
    File(programFilesX86, "Git\\usr\\bin\\bash.exe")
  )
  candidates.firstOrNull { it.isFile }?.let { return it.absolutePath }

  val gitExecutable = System.getenv("PATH")
    ?.split(System.getProperty("path.separator"))
    ?.map { File(it, "git.exe") }
    ?.firstOrNull { it.isFile }
  if (gitExecutable != null) {
    val gitRoot = gitExecutable.parentFile.parentFile
    listOf(File(gitRoot, "bin\\bash.exe"), File(gitRoot, "usr\\bin\\bash.exe"))
      .firstOrNull { it.isFile }?.let { return it.absolutePath }
  }

  error("Unable to locate Git Bash (bash.exe). Install Git for Windows and ensure Git Bash is available.")
}

private fun gitSigningEnabled(repo: File): Boolean =
  gitCmd("config", "commit.gpgsign", dir = repo).output?.toBoolean() == true

internal fun temporarilyDisableGitSigning(repo: File): Boolean {
  val isCurrentlyEnabled = gitSigningEnabled(repo)
  if (isCurrentlyEnabled) {
    gitCmd("config", "commit.gpgsign", "false", dir = repo)
  }
  return isCurrentlyEnabled
}

internal fun reEnableGitSigning(repo: File) {
  gitCmd("config", "commit.gpgsign", "true", dir = repo)
}

internal fun gitHash(repo: File): String =
  gitCmd("rev-parse", "HEAD", dir = repo).output ?: ""

internal val jenkins = System.getenv("JOB_NAME") != null
