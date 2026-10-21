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

import org.gradle.api.logging.Logger
import xyz.jpenilla.toothpick.ensureSuccess
import xyz.jpenilla.toothpick.gitCmd
import xyz.jpenilla.toothpick.reEnableGitSigning
import xyz.jpenilla.toothpick.temporarilyDisableGitSigning
import java.io.File
import java.nio.file.Files

internal fun patchFiles(patchesDir: File?): List<File> {
  if (patchesDir == null || !patchesDir.isDirectory) return emptyList()
  return Files.newDirectoryStream(patchesDir.toPath()).use { stream ->
    stream.map { it.toFile() }
      .filter { it.name.endsWith(".patch") }
      .sortedBy { it.name }
  }
}

/**
 * 将 [projectDir] 重置为 [baseDir] 的 `origin/master`，再按文件名顺序应用 [patchesDir] 下的补丁。
 */
internal fun applyPatchSet(
  baseDir: File,
  projectDir: File,
  patchesDir: File?,
  applyCommand: List<String>,
  logger: Logger,
  workingDir: File
) {
  val name = projectDir.name

  // Reset or initialize subproject
  logger.lifecycle(">>> Resetting subproject $name")
  if (projectDir.resolve(".git").exists()) {
    ensureSuccess(gitCmd("fetch", "origin", dir = projectDir))
    ensureSuccess(gitCmd("reset", "--hard", "origin/master", dir = projectDir))
  } else {
    // Magnet - 兼容 Gradle 9 预创建的空目录或只有 bin/ 的残留目录
    if (projectDir.exists()) {
      projectDir.deleteRecursively()
    }
    ensureSuccess(gitCmd("clone", baseDir.absolutePath, projectDir.absolutePath, dir = workingDir))
  }
  logger.lifecycle(">>> Done resetting subproject $name")

  val patches = patchFiles(patchesDir)
  if (patches.isEmpty()) {
    logger.lifecycle(">>> Patches directory for $name does not exist or is empty, skipping")
    return
  }

  val wasGitSigningEnabled = temporarilyDisableGitSigning(projectDir)

  logger.lifecycle(">>> Applying patches to $name")

  val gitCommand = ArrayList<String>(applyCommand.size + patches.size)
  gitCommand.addAll(applyCommand)
  gitCommand.addAll(patches.map { it.absolutePath })
  ensureSuccess(gitCmd(*gitCommand.toTypedArray(), dir = projectDir, printOut = true)) {
    if (wasGitSigningEnabled) reEnableGitSigning(projectDir)
  }

  if (wasGitSigningEnabled) reEnableGitSigning(projectDir)
  logger.lifecycle(">>> Done applying patches to $name")
}
