package com.example.data

import android.content.Context
import android.os.SystemClock
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

import android.util.Base64
import java.io.BufferedOutputStream
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

data class GitHubRepoSpec(
  val owner: String,
  val repo: String,
  val branch: String?,
)

data class GitHubWorkspaceMeta(
  val owner: String,
  val repo: String,
  val branch: String,
  val importedSha: String,
  val importedMessage: String,
  val lastCheckedAtMs: Long,
  val latestReleaseTag: String?,
  val latestApkName: String?,
  val latestApkUrl: String?,
)

data class GitHubUpdateResult(
  val meta: GitHubWorkspaceMeta,
  val remoteSha: String,
  val remoteMessage: String,
  val remoteAuthor: String,
  val remoteDate: String,
  val hasNewCommit: Boolean,
  val latestReleaseTag: String?,
  val latestApkName: String?,
  val latestApkUrl: String?,
)

data class GitHubWorkflowInfo(
  val id: Long,
  val name: String,
  val path: String,
  val state: String,
)

data class GitHubWorkflowRun(
  val id: Long,
  val name: String,
  val runNumber: Int,
  val status: String,
  val conclusion: String?,
  val branch: String,
  val headSha: String,
  val createdAt: String,
  val htmlUrl: String,
)

data class GitHubArtifactInfo(
  val id: Long,
  val name: String,
  val sizeBytes: Long,
  val expired: Boolean,
  val downloadUrl: String,
)

data class GitHubSearchRepo(
  val fullName: String,
  val description: String,
  val language: String?,
  val stars: Int,
  val defaultBranch: String,
  val sizeKb: Long,
  val htmlUrl: String,
  val updatedAt: String,
)

data class GitHubRemoteSnapshot(
  val owner: String,
  val repo: String,
  val branch: String,
  val remoteSha: String,
  val remoteMessage: String,
  val remoteAuthor: String,
  val remoteDate: String,
  val latestReleaseTag: String?,
  val latestApkName: String?,
  val latestApkUrl: String?,
)

enum class ImportPhase {
  RESOLVING_METADATA,
  DOWNLOADING_ARCHIVE,
  EXTRACTING_FILES,
  DOWNLOADING_APK,
  IMPORTING_LOCAL,
}

data class ImportProgress(
  val phase: ImportPhase,
  val label: String,
  val bytesDone: Long = 0L,
  val bytesTotal: Long = -1L,
  val streamHintBytes: Long = -1L,
  val filesDone: Int = 0,
  val filesTotal: Int = -1,
  val bytesPerSec: Long = 0L,
  val avgBytesPerSec: Long = 0L,
  val peakBytesPerSec: Long = 0L,
  val elapsedMs: Long = 0L,
  val etaSeconds: Long? = null,
) {
  val fraction: Float?
    get() =
      when {
        phase == ImportPhase.EXTRACTING_FILES && bytesTotal > 0L ->
          (bytesDone.toFloat() / bytesTotal.toFloat()).coerceIn(0f, 1f)
        phase == ImportPhase.EXTRACTING_FILES && filesTotal > 0 ->
          (filesDone.toFloat() / filesTotal.toFloat()).coerceIn(0f, 1f)
        (phase == ImportPhase.DOWNLOADING_ARCHIVE ||
          phase == ImportPhase.DOWNLOADING_APK ||
          phase == ImportPhase.IMPORTING_LOCAL) && bytesTotal > 0L ->
          (bytesDone.toFloat() / bytesTotal.toFloat()).coerceIn(0f, 1f)
        else -> null
      }
}

class ProgressSpeedTracker(private val phaseStartMs: Long = SystemClock.elapsedRealtime()) {
  private var windowStartMs: Long = phaseStartMs
  private var windowBytes: Long = 0L
  private var lastWindowSpeed: Long = 0L
  var peakBytesPerSec: Long = 0L
    private set

  fun sample(totalBytesDone: Long, bytesTotal: Long, nowMs: Long = SystemClock.elapsedRealtime()): Sample {
    val totalElapsedMs = (nowMs - phaseStartMs).coerceAtLeast(1L)
    val avgSpeed = (totalBytesDone * 1000L) / totalElapsedMs

    val windowElapsed = nowMs - windowStartMs
    val bytesInWindow = (totalBytesDone - windowBytes).coerceAtLeast(0L)
    val currentSpeed =
      if (windowElapsed >= 450L) {
        val measured = (bytesInWindow * 1000L) / windowElapsed.coerceAtLeast(1L)
        lastWindowSpeed = measured
        windowStartMs = nowMs
        windowBytes = totalBytesDone
        measured
      } else if (lastWindowSpeed > 0L) {
        lastWindowSpeed
      } else {
        avgSpeed
      }

    if (currentSpeed > peakBytesPerSec) {
      peakBytesPerSec = currentSpeed
    }
    if (avgSpeed > peakBytesPerSec) {
      peakBytesPerSec = avgSpeed
    }

    val etaSpeed = if (currentSpeed > 0L) currentSpeed else avgSpeed
    val etaSeconds =
      if (bytesTotal > 0L && etaSpeed > 0L) {
        (bytesTotal - totalBytesDone).coerceAtLeast(0L) / etaSpeed
      } else {
        null
      }

    return Sample(
      currentBytesPerSec = currentSpeed,
      avgBytesPerSec = avgSpeed,
      peakBytesPerSec = peakBytesPerSec,
      etaSeconds = etaSeconds,
    )
  }

  data class Sample(
    val currentBytesPerSec: Long,
    val avgBytesPerSec: Long,
    val peakBytesPerSec: Long,
    val etaSeconds: Long?,
  )
}

class GitHubRepository(
  private val context: Context,
  private val projectRepository: ProjectRepository,
) {
  private val prefs = context.getSharedPreferences("termforge_github", Context.MODE_PRIVATE)

  private val client: OkHttpClient =
    OkHttpClient.Builder()
      .connectTimeout(20, TimeUnit.SECONDS)
      .readTimeout(60, TimeUnit.SECONDS)
      .followRedirects(true)
      .build()

  fun getSavedToken(): String = prefs.getString("github_pat", "") ?: ""

  fun saveToken(token: String) {
    prefs.edit().putString("github_pat", token.trim()).apply()
  }

  fun parseRepoInput(rawInput: String): GitHubRepoSpec {
    val cleaned = rawInput.trim().removeSuffix("/").removeSuffix(".git")
    if (cleaned.isBlank()) {
      throw IllegalArgumentException("Enter a GitHub repository URL or owner/repo.")
    }

    val treeRegex =
      Regex("""(?:https?://)?github\.com/([^/]+)/([^/]+)/tree/(.+)""", RegexOption.IGNORE_CASE)
    treeRegex.matchEntire(cleaned)?.let { m ->
      return GitHubRepoSpec(
        owner = m.groupValues[1],
        repo = m.groupValues[2],
        branch = m.groupValues[3],
      )
    }

    val urlRegex =
      Regex("""(?:https?://)?github\.com/([^/]+)/([^/]+)(?:/.*)?""", RegexOption.IGNORE_CASE)
    urlRegex.matchEntire(cleaned)?.let { m ->
      return GitHubRepoSpec(
        owner = m.groupValues[1],
        repo = m.groupValues[2],
        branch = null,
      )
    }

    val shortRegex = Regex("""^([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+)$""")
    shortRegex.matchEntire(cleaned)?.let { m ->
      return GitHubRepoSpec(
        owner = m.groupValues[1],
        repo = m.groupValues[2],
        branch = null,
      )
    }

    throw IllegalArgumentException(
      "Invalid GitHub format. Use owner/repo or https://github.com/owner/repo."
    )
  }

  suspend fun importFromGitHub(
    rawInput: String,
    tokenOverride: String? = null,
    existingTargetDir: File? = null,
    onProgress: (ImportProgress) -> Unit = {},
  ): AndroidProjectSummary =
    withContext(Dispatchers.IO) {
      val opStart = SystemClock.elapsedRealtime()
      val spec = parseRepoInput(rawInput)
      val token = (tokenOverride ?: getSavedToken()).trim()

      onProgress(
        ImportProgress(
          phase = ImportPhase.RESOLVING_METADATA,
          label = "Fetching ${spec.owner}/${spec.repo} metadata…",
          elapsedMs = 0L,
        )
      )

      val repoJson = getJson("https://api.github.com/repos/${spec.owner}/${spec.repo}", token)
      val resolvedBranch =
        spec.branch?.takeIf { it.isNotBlank() }
          ?: repoJson.optString("default_branch", "main").ifBlank { "main" }
      val repoSizeKb = repoJson.optLong("size", -1L)

      val commitJson =
        getJson(
          "https://api.github.com/repos/${spec.owner}/${spec.repo}/commits/$resolvedBranch",
          token,
        )
      val commitSha = commitJson.optString("sha", "")
      val commitInner = commitJson.optJSONObject("commit")
      val commitMessage =
        commitInner?.optString("message", "")?.lineSequence()?.firstOrNull().orEmpty()

      val releaseInfo = fetchLatestReleaseApk(spec.owner, spec.repo, token)

      val tempZip = File.createTempFile("gh_${spec.repo}_", ".zip", context.cacheDir)
      val targetDir =
        existingTargetDir?.apply {
          if (exists()) deleteRecursively()
          mkdirs()
        } ?: projectRepository.allocateWorkspaceDir(spec.repo)

      try {
        val zipUrl =
          "https://api.github.com/repos/${spec.owner}/${spec.repo}/zipball/$resolvedBranch"
        val zipRequest = buildRequest(zipUrl, token)
        client.newCall(zipRequest).execute().use { response ->
          if (!response.isSuccessful) {
            throw IllegalStateException(
              "GitHub download failed (HTTP ${response.code}): ${response.message}"
            )
          }
          val body =
            response.body
              ?: throw IllegalStateException("GitHub returned an empty archive body.")
          val contentLength = body.contentLength()
          val repoHintBytes = if (repoSizeKb > 0L) repoSizeKb * 1024L else -1L
          val dlTracker = ProgressSpeedTracker()
          var bytesReadTotal = 0L
          var lastEmitAt = 0L
          val buffer = ByteArray(128 * 1024)

          onProgress(
            ImportProgress(
              phase = ImportPhase.DOWNLOADING_ARCHIVE,
              label = "Downloading ${spec.repo} ($resolvedBranch)…",
              bytesDone = 0L,
              bytesTotal = contentLength,
              streamHintBytes = repoHintBytes,
              elapsedMs = SystemClock.elapsedRealtime() - opStart,
            )
          )

          body.byteStream().use { input ->
            BufferedOutputStream(FileOutputStream(tempZip), 128 * 1024).use { output ->
              while (true) {
                coroutineContext.ensureActive()
                val read = input.read(buffer)
                if (read == -1) break
                output.write(buffer, 0, read)
                bytesReadTotal += read

                val now = SystemClock.elapsedRealtime()
                if (now - lastEmitAt >= 120L) {
                  lastEmitAt = now
                  val sample = dlTracker.sample(bytesReadTotal, contentLength, now)
                  onProgress(
                    ImportProgress(
                      phase = ImportPhase.DOWNLOADING_ARCHIVE,
                      label = "Downloading ${spec.repo} ($resolvedBranch)…",
                      bytesDone = bytesReadTotal,
                      bytesTotal = contentLength,
                      streamHintBytes = repoHintBytes,
                      bytesPerSec = sample.currentBytesPerSec,
                      avgBytesPerSec = sample.avgBytesPerSec,
                      peakBytesPerSec = sample.peakBytesPerSec,
                      elapsedMs = now - opStart,
                      etaSeconds = sample.etaSeconds,
                    )
                  )
                }
              }
            }
          }
        }

        val extracted =
          extractZipFileWithProgress(
            zipFile = tempZip,
            targetDir = targetDir,
            opStartMs = opStart,
            onProgress = onProgress,
          )
        if (extracted == 0) {
          targetDir.deleteRecursively()
          throw IllegalStateException("GitHub archive contained no files.")
        }

        unwrapSingleDirectory(targetDir)

        val meta =
          GitHubWorkspaceMeta(
            owner = spec.owner,
            repo = spec.repo,
            branch = resolvedBranch,
            importedSha = commitSha,
            importedMessage = commitMessage,
            lastCheckedAtMs = System.currentTimeMillis(),
            latestReleaseTag = releaseInfo?.first,
            latestApkName = releaseInfo?.second,
            latestApkUrl = releaseInfo?.third,
          )
        writeMeta(targetDir, meta)

        projectRepository.inspectProject(targetDir)
      } catch (t: Throwable) {
        if (existingTargetDir == null && targetDir.exists()) {
          targetDir.deleteRecursively()
        }
        throw t
      } finally {
        tempZip.delete()
      }
    }

  internal suspend fun extractZipFileWithProgress(
    zipFile: File,
    targetDir: File,
    opStartMs: Long = SystemClock.elapsedRealtime(),
    onProgress: (ImportProgress) -> Unit = {},
  ): Int =
    withContext(Dispatchers.IO) {
      val canonicalRoot = targetDir.canonicalFile
      val extractTracker = ProgressSpeedTracker()
      val extractedCount = AtomicInteger(0)
      val extractedBytes = AtomicLong(0L)
      val lastEmitAt = AtomicLong(0L)
      val emitLock = Any()

      val fileEntries = mutableListOf<Pair<String, Long>>()
      var totalUncompressedBytes = 0L

      ZipFile(zipFile).use { zip ->
        val scanEnum = zip.entries()
        while (scanEnum.hasMoreElements()) {
          val e = scanEnum.nextElement()
          val outFile = File(canonicalRoot, e.name)
          val canonicalOut = outFile.canonicalFile
          if (!canonicalOut.path.startsWith(canonicalRoot.path + File.separator) &&
            canonicalOut != canonicalRoot
          ) {
            throw SecurityException("Blocked unsafe zip path: ${e.name}")
          }
          if (e.isDirectory) {
            canonicalOut.mkdirs()
          } else {
            canonicalOut.parentFile?.mkdirs()
            fileEntries += e.name to e.size
            if (e.size > 0L) totalUncompressedBytes += e.size
          }
        }
      }

      if (fileEntries.isEmpty()) return@withContext 0

      val safeTotalFiles = fileEntries.size
      val safeTotalBytes = if (totalUncompressedBytes > 0L) totalUncompressedBytes else -1L
      val workerCount =
        if (safeTotalFiles >= 32) {
          Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
        } else {
          1
        }

      fun maybeEmitProgress(entryName: String, force: Boolean = false) {
        val doneCount = extractedCount.get().coerceAtMost(safeTotalFiles)
        val now = SystemClock.elapsedRealtime()
        val prev = lastEmitAt.get()
        if (force || now - prev >= 80L || doneCount == safeTotalFiles) {
          synchronized(emitLock) {
            val nowSync = SystemClock.elapsedRealtime()
            if (force || nowSync - lastEmitAt.get() >= 80L || doneCount == safeTotalFiles) {
              lastEmitAt.set(nowSync)
              val bytesDoneNow = extractedBytes.get()
              val sample =
                if (safeTotalBytes > 0L) {
                  extractTracker.sample(bytesDoneNow.coerceAtMost(safeTotalBytes), safeTotalBytes, nowSync)
                } else {
                  extractTracker.sample(doneCount.toLong(), safeTotalFiles.toLong(), nowSync)
                }
              onProgress(
                ImportProgress(
                  phase = ImportPhase.EXTRACTING_FILES,
                  label = "Extracting ${entryName.substringAfterLast('/')} (${workerCount}x cores)",
                  bytesDone = bytesDoneNow,
                  bytesTotal = safeTotalBytes,
                  filesDone = doneCount,
                  filesTotal = safeTotalFiles,
                  bytesPerSec = if (safeTotalBytes > 0L) sample.currentBytesPerSec else 0L,
                  avgBytesPerSec = if (safeTotalBytes > 0L) sample.avgBytesPerSec else 0L,
                  peakBytesPerSec = if (safeTotalBytes > 0L) sample.peakBytesPerSec else 0L,
                  elapsedMs = (nowSync - opStartMs).coerceAtLeast(1L),
                  etaSeconds = sample.etaSeconds,
                )
              )
            }
          }
        }
      }

      coroutineScope {
        val chunkSize = ((fileEntries.size + workerCount - 1) / workerCount).coerceAtLeast(1)
        val batches = fileEntries.chunked(chunkSize)
        batches.map { batch ->
          async(Dispatchers.IO) {
            val copyBuf = ByteArray(128 * 1024)
            ZipFile(zipFile).use { workerZip ->
              for ((entryName, _) in batch) {
                coroutineContext.ensureActive()
                val entry = workerZip.getEntry(entryName) ?: continue
                val canonicalOut = File(canonicalRoot, entryName).canonicalFile
                canonicalOut.parentFile?.mkdirs()
                workerZip.getInputStream(entry).use { input ->
                  BufferedOutputStream(FileOutputStream(canonicalOut), 128 * 1024).use { fos ->
                    while (true) {
                      val r = input.read(copyBuf)
                      if (r == -1) break
                      fos.write(copyBuf, 0, r)
                      extractedBytes.addAndGet(r.toLong())
                    }
                  }
                }
                extractedCount.incrementAndGet()
                maybeEmitProgress(entryName)
              }
            }
          }
        }.awaitAll()
      }

      maybeEmitProgress(fileEntries.last().first, force = true)
      extractedCount.get()
    }

  suspend fun fetchRepoRemoteSnapshot(
    rawInputOrFullName: String,
    branchOverride: String? = null,
    tokenOverride: String? = null,
  ): GitHubRemoteSnapshot =
    withContext(Dispatchers.IO) {
      val spec = parseRepoInput(rawInputOrFullName)
      val token = (tokenOverride ?: getSavedToken()).trim()
      val resolvedBranch =
        branchOverride?.takeIf { it.isNotBlank() }
          ?: spec.branch?.takeIf { it.isNotBlank() }
          ?: getJson("https://api.github.com/repos/${spec.owner}/${spec.repo}", token)
            .optString("default_branch", "main")
            .ifBlank { "main" }

      val commitJson =
        getJson(
          "https://api.github.com/repos/${spec.owner}/${spec.repo}/commits/$resolvedBranch",
          token,
        )
      val remoteSha = commitJson.optString("sha", "")
      val commitInner = commitJson.optJSONObject("commit")
      val remoteMsg =
        commitInner?.optString("message", "")?.lineSequence()?.firstOrNull().orEmpty()
      val authorObj = commitInner?.optJSONObject("author")
      val remoteAuthor = authorObj?.optString("name", "") ?: ""
      val remoteDate = authorObj?.optString("date", "") ?: ""
      val releaseInfo = fetchLatestReleaseApk(spec.owner, spec.repo, token)

      GitHubRemoteSnapshot(
        owner = spec.owner,
        repo = spec.repo,
        branch = resolvedBranch,
        remoteSha = remoteSha,
        remoteMessage = remoteMsg,
        remoteAuthor = remoteAuthor,
        remoteDate = remoteDate,
        latestReleaseTag = releaseInfo?.first,
        latestApkName = releaseInfo?.second,
        latestApkUrl = releaseInfo?.third,
      )
    }

  suspend fun checkForUpdates(
    workspaceDir: File,
    tokenOverride: String? = null,
  ): GitHubUpdateResult =
    withContext(Dispatchers.IO) {
      val meta =
        readMeta(workspaceDir)
          ?: throw IllegalStateException("This project was not imported from GitHub.")
      val token = (tokenOverride ?: getSavedToken()).trim()

      val commitJson =
        getJson(
          "https://api.github.com/repos/${meta.owner}/${meta.repo}/commits/${meta.branch}",
          token,
        )
      val remoteSha = commitJson.optString("sha", "")
      val commitInner = commitJson.optJSONObject("commit")
      val remoteMsg =
        commitInner?.optString("message", "")?.lineSequence()?.firstOrNull().orEmpty()
      val authorObj = commitInner?.optJSONObject("author")
      val remoteAuthor = authorObj?.optString("name", "") ?: ""
      val remoteDate = authorObj?.optString("date", "") ?: ""

      val releaseInfo = fetchLatestReleaseApk(meta.owner, meta.repo, token)
      val updatedMeta =
        meta.copy(
          lastCheckedAtMs = System.currentTimeMillis(),
          latestReleaseTag = releaseInfo?.first ?: meta.latestReleaseTag,
          latestApkName = releaseInfo?.second ?: meta.latestApkName,
          latestApkUrl = releaseInfo?.third ?: meta.latestApkUrl,
        )
      writeMeta(workspaceDir, updatedMeta)

      GitHubUpdateResult(
        meta = updatedMeta,
        remoteSha = remoteSha,
        remoteMessage = remoteMsg,
        remoteAuthor = remoteAuthor,
        remoteDate = remoteDate,
        hasNewCommit = remoteSha.isNotBlank() && remoteSha != meta.importedSha,
        latestReleaseTag = releaseInfo?.first,
        latestApkName = releaseInfo?.second,
        latestApkUrl = releaseInfo?.third,
      )
    }

  suspend fun pullLatest(
    workspaceDir: File,
    tokenOverride: String? = null,
    onProgress: (ImportProgress) -> Unit = {},
  ): AndroidProjectSummary =
    withContext(Dispatchers.IO) {
      val meta =
        readMeta(workspaceDir)
          ?: throw IllegalStateException("This project was not imported from GitHub.")
      importFromGitHub(
        rawInput = "https://github.com/${meta.owner}/${meta.repo}/tree/${meta.branch}",
        tokenOverride = tokenOverride,
        existingTargetDir = workspaceDir,
        onProgress = onProgress,
      )
    }

  suspend fun downloadReleaseApk(
    downloadUrl: String,
    fileName: String,
    tokenOverride: String? = null,
    onProgress: (ImportProgress) -> Unit = {},
  ): File =
    withContext(Dispatchers.IO) {
      val apksDir = File(context.filesDir, "apks").apply { mkdirs() }
      val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "release.apk" }
      val destFile = File(apksDir, safeName)
      downloadBinaryToFile(
        url = downloadUrl,
        destFile = destFile,
        label = "Downloading $safeName…",
        phase = ImportPhase.DOWNLOADING_APK,
        tokenOverride = tokenOverride,
        onProgress = onProgress,
      )
      destFile
    }

  suspend fun listWorkflows(
    owner: String,
    repo: String,
    tokenOverride: String? = null,
  ): List<GitHubWorkflowInfo> =
    withContext(Dispatchers.IO) {
      val token = (tokenOverride ?: getSavedToken()).trim()
      val json = getJson("https://api.github.com/repos/$owner/$repo/actions/workflows", token)
      val arr = json.optJSONArray("workflows") ?: return@withContext emptyList()
      buildList {
        for (i in 0 until arr.length()) {
          val obj = arr.getJSONObject(i)
          add(
            GitHubWorkflowInfo(
              id = obj.getLong("id"),
              name = obj.optString("name", "Workflow"),
              path = obj.optString("path", ""),
              state = obj.optString("state", "unknown"),
            )
          )
        }
      }
    }

  suspend fun listWorkflowRuns(
    owner: String,
    repo: String,
    tokenOverride: String? = null,
  ): List<GitHubWorkflowRun> =
    withContext(Dispatchers.IO) {
      val token = (tokenOverride ?: getSavedToken()).trim()
      val json = getJson("https://api.github.com/repos/$owner/$repo/actions/runs?per_page=10", token)
      val arr = json.optJSONArray("workflow_runs") ?: return@withContext emptyList()
      buildList {
        for (i in 0 until arr.length()) {
          val obj = arr.getJSONObject(i)
          add(
            GitHubWorkflowRun(
              id = obj.getLong("id"),
              name = obj.optString("name", "Build"),
              runNumber = obj.optInt("run_number", 0),
              status = obj.optString("status", "unknown"),
              conclusion = obj.optString("conclusion", "").ifBlank { null },
              branch = obj.optString("head_branch", ""),
              headSha = obj.optString("head_sha", ""),
              createdAt = obj.optString("created_at", ""),
              htmlUrl = obj.optString("html_url", ""),
            )
          )
        }
      }
    }

  suspend fun dispatchWorkflow(
    owner: String,
    repo: String,
    workflowId: Long,
    branch: String,
    tokenOverride: String? = null,
  ) =
    withContext(Dispatchers.IO) {
      val token = (tokenOverride ?: getSavedToken()).trim()
      if (token.isBlank()) {
        throw IllegalStateException("GitHub token with 'workflow' / 'actions:write' scope is required to trigger cloud builds.")
      }
      val payload = JSONObject().put("ref", branch).toString()
      val request =
        Request.Builder()
          .url("https://api.github.com/repos/$owner/$repo/actions/workflows/$workflowId/dispatches")
          .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
          .header("Accept", "application/vnd.github+json")
          .header("User-Agent", "TermForge-Android")
          .header("X-GitHub-Api-Version", "2022-11-28")
          .header("Authorization", "Bearer $token")
          .build()

      client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
          val raw = response.body?.string().orEmpty()
          val msg = runCatching { JSONObject(raw).optString("message", "") }.getOrDefault("")
          val detail = if (msg.isNotBlank()) ": $msg" else ""
          throw IllegalStateException("Workflow trigger failed (HTTP ${response.code})$detail")
        }
      }
    }

  suspend fun createOrUpdateAndroidWorkflow(
    owner: String,
    repo: String,
    branch: String,
    tokenOverride: String? = null,
  ) =
    withContext(Dispatchers.IO) {
      val token = (tokenOverride ?: getSavedToken()).trim()
      if (token.isBlank()) {
        throw IllegalStateException("Enter a GitHub token with 'workflow' & 'contents:write' scope to add CI workflow.")
      }
      val workflowPath = ".github/workflows/termforge-android.yml"
      val contentUrl = "https://api.github.com/repos/$owner/$repo/contents/$workflowPath"
      val existingSha =
        runCatching {
          getJson("$contentUrl?ref=$branch", token).optString("sha", "").ifBlank { null }
        }.getOrNull()

      val yaml =
        """
        name: APK Builder Android APK

        on:
          workflow_dispatch:

        jobs:
          build:
            runs-on: ubuntu-latest
            steps:
              - uses: actions/checkout@v4
              - uses: actions/setup-java@v4
                with:
                  distribution: temurin
                  java-version: '21'
              - uses: gradle/actions/setup-gradle@v4
                with:
                  gradle-version: '9.3.1'
              - name: Prepare Debug Keystore
                run: |
                  if [ ! -f debug.keystore ]; then
                    if [ -f debug.keystore.base64 ]; then
                      base64 -d debug.keystore.base64 > debug.keystore
                    else
                      keytool -genkeypair -v -keystore debug.keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
                    fi
                  fi
              - name: Build Debug APK
                run: |
                  if [ -f "./gradlew" ]; then
                    chmod +x ./gradlew
                    ./gradlew assembleDebug --parallel --build-cache --max-workers=4
                  else
                    gradle assembleDebug --parallel --build-cache --max-workers=4
                  fi
              - uses: actions/upload-artifact@v4
                with:
                  name: termforge-debug-apk
                  path: "**/*.apk"
        """
          .trimIndent() + "\n"

      val encoded = Base64.encodeToString(yaml.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
      val bodyJson =
        JSONObject().apply {
          put("message", "Add TermForge online Android APK workflow")
          put("content", encoded)
          put("branch", branch)
          if (existingSha != null) put("sha", existingSha)
        }

      val request =
        Request.Builder()
          .url(contentUrl)
          .put(bodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
          .header("Accept", "application/vnd.github+json")
          .header("User-Agent", "TermForge-Android")
          .header("X-GitHub-Api-Version", "2022-11-28")
          .header("Authorization", "Bearer $token")
          .build()

      client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
          val raw = response.body?.string().orEmpty()
          val msg = runCatching { JSONObject(raw).optString("message", "") }.getOrDefault("")
          val detail = if (msg.isNotBlank()) ": $msg" else ""
          throw IllegalStateException("Could not push workflow (HTTP ${response.code})$detail")
        }
      }
    }

  suspend fun listRunArtifacts(
    owner: String,
    repo: String,
    runId: Long,
    tokenOverride: String? = null,
  ): List<GitHubArtifactInfo> =
    withContext(Dispatchers.IO) {
      val token = (tokenOverride ?: getSavedToken()).trim()
      val json =
        getJson("https://api.github.com/repos/$owner/$repo/actions/runs/$runId/artifacts", token)
      val arr = json.optJSONArray("artifacts") ?: return@withContext emptyList()
      buildList {
        for (i in 0 until arr.length()) {
          val obj = arr.getJSONObject(i)
          add(
            GitHubArtifactInfo(
              id = obj.getLong("id"),
              name = obj.optString("name", "artifact"),
              sizeBytes = obj.optLong("size_in_bytes", 0L),
              expired = obj.optBoolean("expired", false),
              downloadUrl = obj.optString("archive_download_url", ""),
            )
          )
        }
      }
    }

  suspend fun downloadAndExtractArtifactApk(
    artifact: GitHubArtifactInfo,
    tokenOverride: String? = null,
    onProgress: (ImportProgress) -> Unit = {},
  ): List<File> =
    withContext(Dispatchers.IO) {
      val token = (tokenOverride ?: getSavedToken()).trim()
      if (token.isBlank()) {
        throw IllegalStateException("GitHub token required to download GitHub Actions build artifacts.")
      }
      val opStart = SystemClock.elapsedRealtime()
      val tempZip = File.createTempFile("gh_artifact_", ".zip", context.cacheDir)
      val extractTempDir = File(context.cacheDir, "artifact_${artifact.id}_${opStart}").apply { mkdirs() }
      val apksDir = File(context.filesDir, "apks").apply { mkdirs() }

      try {
        downloadBinaryToFile(
          url = artifact.downloadUrl,
          destFile = tempZip,
          label = "Downloading ${artifact.name}.zip…",
          phase = ImportPhase.DOWNLOADING_APK,
          tokenOverride = token,
          hintTotalBytes = artifact.sizeBytes,
          opStartMs = opStart,
          onProgress = onProgress,
        )
        extractZipFileWithProgress(
          zipFile = tempZip,
          targetDir = extractTempDir,
          opStartMs = opStart,
          onProgress = onProgress,
        )
        val extractedApks =
          extractTempDir
            .walkTopDown()
            .filter { it.isFile && it.extension.equals("apk", ignoreCase = true) }
            .toList()
        if (extractedApks.isEmpty()) {
          throw IllegalStateException("Artifact '${artifact.name}' contained no .apk files.")
        }
        extractedApks.map { src ->
          val dest = File(apksDir, src.name)
          src.copyTo(dest, overwrite = true)
          dest
        }
      } finally {
        tempZip.delete()
        extractTempDir.deleteRecursively()
      }
    }

  private suspend fun downloadBinaryToFile(
    url: String,
    destFile: File,
    label: String,
    phase: ImportPhase,
    tokenOverride: String? = null,
    hintTotalBytes: Long = -1L,
    opStartMs: Long = SystemClock.elapsedRealtime(),
    onProgress: (ImportProgress) -> Unit = {},
  ) =
    withContext(Dispatchers.IO) {
      val token = (tokenOverride ?: getSavedToken()).trim()
      val request = buildRequest(url, token, accept = "application/octet-stream")
      client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
          throw IllegalStateException("Download failed (HTTP ${response.code}): ${response.message}")
        }
        val body = response.body ?: throw IllegalStateException("Empty download response.")
        val headerTotal = body.contentLength()
        val effectiveTotal = if (headerTotal > 0L) headerTotal else -1L
        val tracker = ProgressSpeedTracker()
        val buffer = ByteArray(128 * 1024)
        var done = 0L
        var lastEmitAt = 0L

        onProgress(
          ImportProgress(
            phase = phase,
            label = label,
            bytesDone = 0L,
            bytesTotal = effectiveTotal,
            streamHintBytes = hintTotalBytes,
            elapsedMs = SystemClock.elapsedRealtime() - opStartMs,
          )
        )

        body.byteStream().use { input ->
          BufferedOutputStream(FileOutputStream(destFile), 128 * 1024).use { fos ->
            while (true) {
              coroutineContext.ensureActive()
              val read = input.read(buffer)
              if (read == -1) break
              fos.write(buffer, 0, read)
              done += read
              val now = SystemClock.elapsedRealtime()
              if (now - lastEmitAt >= 100L) {
                lastEmitAt = now
                val sample = tracker.sample(done, effectiveTotal, now)
                onProgress(
                  ImportProgress(
                    phase = phase,
                    label = label,
                    bytesDone = done,
                    bytesTotal = effectiveTotal,
                    streamHintBytes = hintTotalBytes,
                    bytesPerSec = sample.currentBytesPerSec,
                    avgBytesPerSec = sample.avgBytesPerSec,
                    peakBytesPerSec = sample.peakBytesPerSec,
                    elapsedMs = now - opStartMs,
                    etaSeconds = sample.etaSeconds,
                  )
                )
              }
            }
          }
        }
      }
    }

  fun readMeta(workspaceDir: File): GitHubWorkspaceMeta? {
    val file = File(workspaceDir, META_FILE_NAME)
    if (!file.exists()) return null
    return runCatching {
      val obj = JSONObject(file.readText())
      GitHubWorkspaceMeta(
        owner = obj.getString("owner"),
        repo = obj.getString("repo"),
        branch = obj.getString("branch"),
        importedSha = obj.getString("importedSha"),
        importedMessage = obj.optString("importedMessage", ""),
        lastCheckedAtMs = obj.optLong("lastCheckedAtMs", 0L),
        latestReleaseTag = obj.optString("latestReleaseTag", "").ifBlank { null },
        latestApkName = obj.optString("latestApkName", "").ifBlank { null },
        latestApkUrl = obj.optString("latestApkUrl", "").ifBlank { null },
      )
    }.getOrNull()
  }

  private fun writeMeta(workspaceDir: File, meta: GitHubWorkspaceMeta) {
    val obj =
      JSONObject().apply {
        put("owner", meta.owner)
        put("repo", meta.repo)
        put("branch", meta.branch)
        put("importedSha", meta.importedSha)
        put("importedMessage", meta.importedMessage)
        put("lastCheckedAtMs", meta.lastCheckedAtMs)
        put("latestReleaseTag", meta.latestReleaseTag ?: "")
        put("latestApkName", meta.latestApkName ?: "")
        put("latestApkUrl", meta.latestApkUrl ?: "")
      }
    File(workspaceDir, META_FILE_NAME).writeText(obj.toString(2))
  }

  private fun fetchLatestReleaseApk(
    owner: String,
    repo: String,
    token: String,
  ): Triple<String, String, String>? {
    return runCatching {
      val rel = getJson("https://api.github.com/repos/$owner/$repo/releases/latest", token)
      val tag = rel.optString("tag_name", "")
      val assets = rel.optJSONArray("assets") ?: return null
      for (i in 0 until assets.length()) {
        val asset = assets.getJSONObject(i)
        val name = asset.optString("name", "")
        if (name.endsWith(".apk", ignoreCase = true)) {
          val url =
            asset.optString("url", "").ifBlank { asset.optString("browser_download_url", "") }
          if (url.isNotBlank()) {
            return Triple(tag, name, url)
          }
        }
      }
      null
    }.getOrNull()
  }

  private fun getJson(url: String, token: String): JSONObject {
    val request = buildRequest(url, token)
    client.newCall(request).execute().use { response ->
      val raw = response.body?.string().orEmpty()
      if (!response.isSuccessful) {
        val apiMsg = runCatching { JSONObject(raw).optString("message", "") }.getOrDefault("")
        val detail = if (apiMsg.isNotBlank()) ": $apiMsg" else ""
        throw IllegalStateException("GitHub API HTTP ${response.code}$detail")
      }
      return JSONObject(raw)
    }
  }

  private fun buildRequest(
    url: String,
    token: String,
    accept: String = "application/vnd.github+json",
  ): Request {
    val builder =
      Request.Builder()
        .url(url)
        .header("Accept", accept)
        .header("User-Agent", "TermForge-Android")
        .header("X-GitHub-Api-Version", "2022-11-28")
    if (token.isNotBlank()) {
      builder.header("Authorization", "Bearer $token")
    }
    return builder.build()
  }

  private fun unwrapSingleDirectory(targetDir: File) {
    val children = targetDir.listFiles()?.filter { it.name != "__MACOSX" } ?: return
    if (children.size == 1 && children.first().isDirectory) {
      val inner = children.first()
      inner.listFiles()?.forEach { f ->
        f.renameTo(File(targetDir, f.name))
      }
      inner.deleteRecursively()
    }
  }

  suspend fun searchRepositories(
    query: String,
    androidFilter: Boolean = true,
    tokenOverride: String? = null,
  ): List<GitHubSearchRepo> =
    withContext(Dispatchers.IO) {
      val trimmed = query.trim()
      if (trimmed.isBlank()) {
        throw IllegalArgumentException("Enter keywords to search on GitHub.")
      }
      val fullQuery =
        if (androidFilter && !trimmed.contains("android", ignoreCase = true) && !trimmed.contains("topic:", ignoreCase = true)) {
          "$trimmed android"
        } else {
          trimmed
        }
      val encoded = URLEncoder.encode(fullQuery, "UTF-8")
      val token = (tokenOverride ?: getSavedToken()).trim()
      val json =
        getJson(
          "https://api.github.com/search/repositories?q=$encoded&sort=stars&order=desc&per_page=15",
          token,
        )
      val items = json.optJSONArray("items") ?: return@withContext emptyList()
      buildList {
        for (i in 0 until items.length()) {
          val obj = items.getJSONObject(i)
          add(
            GitHubSearchRepo(
              fullName = obj.optString("full_name", ""),
              description = obj.optString("description", "").takeIf { it != "null" }.orEmpty(),
              language = obj.optString("language", "").takeIf { it.isNotBlank() && it != "null" },
              stars = obj.optInt("stargazers_count", 0),
              defaultBranch = obj.optString("default_branch", "main").ifBlank { "main" },
              sizeKb = obj.optLong("size", 0L),
              htmlUrl = obj.optString("html_url", ""),
              updatedAt = obj.optString("updated_at", "").take(10),
            )
          )
        }
      }
    }

  fun buildBrowserSearchUrl(query: String, androidFilter: Boolean): String {
    val trimmed = query.trim()
    if (trimmed.isBlank()) {
      return if (androidFilter) "https://github.com/topics/android" else "https://github.com/search?type=repositories"
    }
    val q =
      if (androidFilter && !trimmed.contains("android", ignoreCase = true)) {
        "$trimmed android"
      } else {
        trimmed
      }
    return "https://github.com/search?q=${URLEncoder.encode(q, "UTF-8")}&type=repositories"
  }

  companion object {
    const val META_FILE_NAME = ".termforge_github.json"
  }
}
