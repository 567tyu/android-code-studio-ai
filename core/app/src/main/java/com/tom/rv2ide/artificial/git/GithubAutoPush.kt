/*
 *  This file is part of AndroidCodeStudio.
 *
 *  AndroidCodeStudio is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidCodeStudio is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidCodeStudio.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.tom.rv2ide.artificial.git

import com.tom.rv2ide.preferences.internal.prefManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.transport.PushResult
import org.eclipse.jgit.transport.RefSpec
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.eclipse.jgit.transport.URIish
import java.io.File

/**
 * Pushes AI-modified files to a configured GitHub repository.
 *
 * Configuration is read from shared preferences so the user can set it from
 * the AI settings screen. When enabled, the AI agent flow triggers a commit +
 * push after a run succeeds.
 */
object GithubAutoPush {

  private const val TAG = "GithubAutoPush"
  private const val PREF_ENABLED = "ai_autopush_enabled"
  private const val PREF_REPO = "ai_autopush_repo"
  private const val PREF_BRANCH = "ai_autopush_branch"
  private const val PREF_PAT = "ai_autopush_pat"

  // --- preference accessors (used by the AI settings UI) ---

  fun isEnabled(): Boolean = prefManager.getBoolean(PREF_ENABLED, false)
  fun setEnabled(enabled: Boolean) { prefManager.putBoolean(PREF_ENABLED, enabled) }

  fun getRepo(): String = prefManager.getString(PREF_REPO, "")
  fun setRepo(repo: String) { prefManager.putString(PREF_REPO, repo.trim()) }

  fun getBranch(): String = prefManager.getString(PREF_BRANCH, "main")
  fun setBranch(branch: String) { prefManager.putString(PREF_BRANCH, branch.trim()) }

  fun getPat(): String = prefManager.getString(PREF_PAT, "")
  fun setPat(pat: String) { prefManager.putString(PREF_PAT, pat.trim()) }

  fun isConfigured(): Boolean = getRepo().isNotBlank()

  /**
   * Commits [files] (absolute paths) and pushes them to the configured GitHub
   * repository. Best-effort: returns a Result describing the outcome rather than
   * throwing, so callers can surface it to the user.
   */
  suspend fun pushAfterAi(files: List<String>, commitMessage: String): Result<String> =
    withContext(Dispatchers.IO) {
      try {
        if (!isEnabled()) {
          return@withContext Result.failure(
            IllegalStateException("Auto-push is disabled in AI settings"),
          )
        }

        val repo = getRepo().trim()
        if (repo.isBlank()) {
          return@withContext Result.failure(
            IllegalStateException("Auto-push not configured: missing GitHub repo (owner/name)"),
          )
        }

        val pat = getPat().trim()
        val branch = getBranch().trim().ifBlank { "main" }

        val repoDir = files.mapNotNull { gitRootOf(it) }.firstOrNull() ?: commonParent(files)
        if (repoDir == null || !repoDir.isDirectory) {
          return@withContext Result.failure(
            IllegalStateException("Could not locate a project directory to push from"),
          )
        }

        runGit(repoDir, repo, branch, pat, commitMessage)
      } catch (e: Exception) {
        android.util.Log.e(TAG, "Auto-push failed", e)
        Result.failure(e)
      }
    }

  private fun runGit(
    repoDir: File,
    repo: String,
    branch: String,
    pat: String,
    commitMessage: String,
  ): Result<String> = runCatching {
    val hadGitDir = gitRootOf(repoDir.absolutePath) != null
    val git: Git = if (hadGitDir) {
      Git.open(repoDir)
    } else {
      Git.init().setDirectory(repoDir).setInitialBranch(branch).call()
    }

    try {
      // On an existing repo, make sure we are on the target branch (create it if missing).
      if (hadGitDir && git.repository.findRef("refs/heads/$branch") == null) {
        git.checkout().setName(branch).setCreateBranch(true).call()
      }

      git.add().addFilepattern(".").call()
      val ident = PersonIdent("Android Code Studio AI", "acssai@users.noreply.github.com")
      git.commit()
        .setMessage("chore(ai): ${commitMessage.trim().take(80)}")
        .setAuthor(ident)
        .setCommitter(ident)
        .call()

      val remoteNames = git.remoteList().call().map { it.name }.toSet()
      val remoteUrl = URIish("https://github.com/$repo.git")
      if ("origin" in remoteNames) {
        git.remoteSetUrl().setRemoteName("origin").setRemoteUri(remoteUrl).call()
      } else {
        git.remoteAdd().setName("origin").setUri(remoteUrl).call()
      }

      val push = git.push()
        .setRemote("origin")
        .setRefSpecs(RefSpec("refs/heads/$branch:refs/heads/$branch"))
        .setForce(true)
      if (pat.isNotBlank()) {
        push.setCredentialsProvider(UsernamePasswordCredentialsProvider("x-access-token", pat))
      }

      val pushResults: List<PushResult> = push.call().toList()
      val statuses: List<RemoteRefUpdate.Status> = pushResults
        .flatMap { it.remoteUpdates.toList() }
        .map { it.status }
      val allOk = statuses.isNotEmpty() && statuses.all {
        it == RemoteRefUpdate.Status.OK || it == RemoteRefUpdate.Status.UP_TO_DATE
      }
      if (allOk) {
        "Pushed to https://github.com/$repo (branch $branch)"
      } else {
        "Push reported status: ${statuses.joinToString()}"
      }
    } finally {
      git.close()
    }
  }

  /** Walks up from [path] to find the nearest directory containing a .git entry. */
  private fun gitRootOf(path: String): File? {
    var dir = File(path).let { if (it.isDirectory) it else it.parentFile }
    while (dir != null) {
      if (File(dir, ".git").exists()) return dir
      dir = dir.parentFile
    }
    return null
  }

  private fun commonParent(files: List<String>): File? {
    val dirs = files
      .map { File(it) }
      .filter { it.exists() }
      .map { if (it.isDirectory) it else it.parentFile }
      .filterNotNull()
    if (dirs.isEmpty()) return null

    var result = dirs.first()
    for (d in dirs.drop(1)) {
      val merged = commonDir(result, d) ?: return result
      result = merged
    }
    return result
  }

  private fun commonDir(a: File, b: File): File? {
    val sep = java.io.File.separator
    val ap = a.absolutePath.split(sep)
    val bp = b.absolutePath.split(sep)
    var i = 0
    while (i < ap.size && i < bp.size && ap[i] == bp[i]) i++
    return if (i == 0) null else File(ap.subList(0, i).joinToString(sep))
  }
}
