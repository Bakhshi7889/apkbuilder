package com.example.data

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class WatchedAppEntry(
  val id: String,
  val owner: String,
  val repo: String,
  val branch: String,
  val baselineSha: String,
  val baselineMessage: String,
  val baselineReleaseTag: String?,
  val remoteSha: String? = null,
  val remoteMessage: String? = null,
  val remoteReleaseTag: String? = null,
  val remoteApkName: String? = null,
  val remoteApkUrl: String? = null,
  val lastCheckedAtMs: Long = 0L,
  val lastNotifiedKey: String? = null,
  val lastError: String? = null,
  val workspacePath: String? = null,
) {
  val fullName: String
    get() = "$owner/$repo"

  val hasNewCommit: Boolean
    get() = !remoteSha.isNullOrBlank() && baselineSha.isNotBlank() && remoteSha != baselineSha

  val hasNewRelease: Boolean
    get() =
      !remoteReleaseTag.isNullOrBlank() &&
        remoteReleaseTag != baselineReleaseTag

  val hasUpdate: Boolean
    get() = hasNewCommit || hasNewRelease

  val currentUpdateKey: String
    get() = "${remoteSha.orEmpty()}|${remoteReleaseTag.orEmpty()}"

  val needsNotification: Boolean
    get() = hasUpdate && currentUpdateKey.isNotBlank() && currentUpdateKey != lastNotifiedKey
}

class WatchedAppsRepository(context: Context) {
  private val storeFile = File(context.filesDir, "watched_apps.json")
  private val prefs = context.getSharedPreferences("termforge_watchlist", Context.MODE_PRIVATE)
  private val lock = Any()

  fun isAutoCheckOnAppOpenEnabled(): Boolean =
    prefs.getBoolean(KEY_AUTO_CHECK_ON_OPEN, true)

  fun setAutoCheckOnAppOpen(enabled: Boolean) {
    prefs.edit().putBoolean(KEY_AUTO_CHECK_ON_OPEN, enabled).apply()
  }

  fun getLastCheckAllAtMs(): Long = prefs.getLong(KEY_LAST_CHECK_ALL_MS, 0L)

  fun setLastCheckAllAtMs(timestampMs: Long) {
    prefs.edit().putLong(KEY_LAST_CHECK_ALL_MS, timestampMs).apply()
  }

  suspend fun loadAll(): List<WatchedAppEntry> =
    withContext(Dispatchers.IO) {
      synchronized(lock) { readListUnlocked() }
    }

  suspend fun syncWithWorkspaces(
    workspaces: List<AndroidProjectSummary>,
    gitHubRepository: GitHubRepository,
  ): List<WatchedAppEntry> =
    withContext(Dispatchers.IO) {
      synchronized(lock) {
        val current = readListUnlocked().toMutableList()
        val workspaceById = mutableMapOf<String, Pair<AndroidProjectSummary, GitHubWorkspaceMeta>>()
        for (ws in workspaces) {
          val meta = gitHubRepository.readMeta(File(ws.rootPath)) ?: continue
          val id = makeId(meta.owner, meta.repo)
          workspaceById[id] = ws to meta
        }

        // Update existing entries with live workspace linkage and newer pulled baselines
        for (i in current.indices) {
          val entry = current[i]
          val matched = workspaceById.remove(entry.id)
          if (matched != null) {
            val (ws, meta) = matched
            val pulledUpdatedCommit =
              meta.importedSha.isNotBlank() && meta.importedSha != entry.baselineSha
            current[i] =
              entry.copy(
                branch = meta.branch.ifBlank { entry.branch },
                baselineSha = if (pulledUpdatedCommit) meta.importedSha else entry.baselineSha,
                baselineMessage =
                  if (pulledUpdatedCommit) meta.importedMessage else entry.baselineMessage,
                baselineReleaseTag = meta.latestReleaseTag ?: entry.baselineReleaseTag,
                remoteApkName = entry.remoteApkName ?: meta.latestApkName,
                remoteApkUrl = entry.remoteApkUrl ?: meta.latestApkUrl,
                workspacePath = ws.rootPath,
              )
          } else if (entry.workspacePath != null && !File(entry.workspacePath).exists()) {
            current[i] = entry.copy(workspacePath = null)
          }
        }

        // Auto-register any GitHub-imported workspace not yet in the watchlist
        workspaceById.forEach { (id, pair) ->
          val (ws, meta) = pair
          current.add(
            0,
            WatchedAppEntry(
              id = id,
              owner = meta.owner,
              repo = meta.repo,
              branch = meta.branch,
              baselineSha = meta.importedSha,
              baselineMessage = meta.importedMessage,
              baselineReleaseTag = meta.latestReleaseTag,
              remoteSha = meta.importedSha,
              remoteMessage = meta.importedMessage,
              remoteReleaseTag = meta.latestReleaseTag,
              remoteApkName = meta.latestApkName,
              remoteApkUrl = meta.latestApkUrl,
              lastCheckedAtMs = meta.lastCheckedAtMs,
              workspacePath = ws.rootPath,
            ),
          )
        }

        writeListUnlocked(current)
        current
      }
    }

  suspend fun upsertInitialSnapshot(
    snapshot: GitHubRemoteSnapshot,
    workspacePath: String? = null,
    checkedAtMs: Long = System.currentTimeMillis(),
  ): WatchedAppEntry =
    withContext(Dispatchers.IO) {
      synchronized(lock) {
        val current = readListUnlocked().toMutableList()
        val id = makeId(snapshot.owner, snapshot.repo)
        val existingIdx = current.indexOfFirst { it.id == id }
        val entry =
          if (existingIdx >= 0) {
            val old = current[existingIdx]
            old.copy(
              owner = snapshot.owner,
              repo = snapshot.repo,
              branch = snapshot.branch,
              remoteSha = snapshot.remoteSha,
              remoteMessage = snapshot.remoteMessage,
              remoteReleaseTag = snapshot.latestReleaseTag,
              remoteApkName = snapshot.latestApkName,
              remoteApkUrl = snapshot.latestApkUrl,
              lastCheckedAtMs = checkedAtMs,
              lastError = null,
              workspacePath = workspacePath ?: old.workspacePath,
            )
          } else {
            WatchedAppEntry(
              id = id,
              owner = snapshot.owner,
              repo = snapshot.repo,
              branch = snapshot.branch,
              baselineSha = snapshot.remoteSha,
              baselineMessage = snapshot.remoteMessage,
              baselineReleaseTag = snapshot.latestReleaseTag,
              remoteSha = snapshot.remoteSha,
              remoteMessage = snapshot.remoteMessage,
              remoteReleaseTag = snapshot.latestReleaseTag,
              remoteApkName = snapshot.latestApkName,
              remoteApkUrl = snapshot.latestApkUrl,
              lastCheckedAtMs = checkedAtMs,
              workspacePath = workspacePath,
            )
          }
        if (existingIdx >= 0) current[existingIdx] = entry else current.add(0, entry)
        writeListUnlocked(current)
        entry
      }
    }

  suspend fun recordRemoteCheckSuccess(
    id: String,
    snapshot: GitHubRemoteSnapshot,
    checkedAtMs: Long = System.currentTimeMillis(),
  ): WatchedAppEntry? =
    withContext(Dispatchers.IO) {
      synchronized(lock) {
        val current = readListUnlocked().toMutableList()
        val idx = current.indexOfFirst { it.id == id }
        if (idx < 0) return@withContext null
        val old = current[idx]
        val updated =
          old.copy(
            branch = snapshot.branch,
            baselineSha = old.baselineSha.ifBlank { snapshot.remoteSha },
            remoteSha = snapshot.remoteSha,
            remoteMessage = snapshot.remoteMessage,
            remoteReleaseTag = snapshot.latestReleaseTag,
            remoteApkName = snapshot.latestApkName ?: old.remoteApkName,
            remoteApkUrl = snapshot.latestApkUrl ?: old.remoteApkUrl,
            lastCheckedAtMs = checkedAtMs,
            lastError = null,
          )
        current[idx] = updated
        writeListUnlocked(current)
        updated
      }
    }

  suspend fun recordRemoteCheckFailure(
    id: String,
    errorMessage: String,
    checkedAtMs: Long = System.currentTimeMillis(),
  ): WatchedAppEntry? =
    withContext(Dispatchers.IO) {
      synchronized(lock) {
        val current = readListUnlocked().toMutableList()
        val idx = current.indexOfFirst { it.id == id }
        if (idx < 0) return@withContext null
        val updated =
          current[idx].copy(
            lastCheckedAtMs = checkedAtMs,
            lastError = errorMessage,
          )
        current[idx] = updated
        writeListUnlocked(current)
        updated
      }
    }

  suspend fun markAsSeen(id: String): List<WatchedAppEntry> =
    withContext(Dispatchers.IO) {
      synchronized(lock) {
        val current = readListUnlocked().toMutableList()
        val idx = current.indexOfFirst { it.id == id }
        if (idx >= 0) {
          val e = current[idx]
          current[idx] =
            e.copy(
              baselineSha = e.remoteSha?.takeIf { it.isNotBlank() } ?: e.baselineSha,
              baselineMessage = e.remoteMessage ?: e.baselineMessage,
              baselineReleaseTag = e.remoteReleaseTag ?: e.baselineReleaseTag,
              lastNotifiedKey = e.currentUpdateKey,
            )
          writeListUnlocked(current)
        }
        current
      }
    }

  suspend fun markNotified(ids: Set<String>): List<WatchedAppEntry> =
    withContext(Dispatchers.IO) {
      synchronized(lock) {
        val current = readListUnlocked().toMutableList()
        var changed = false
        for (i in current.indices) {
          val e = current[i]
          if (e.id in ids) {
            current[i] = e.copy(lastNotifiedKey = e.currentUpdateKey)
            changed = true
          }
        }
        if (changed) writeListUnlocked(current)
        current
      }
    }

  suspend fun remove(id: String): List<WatchedAppEntry> =
    withContext(Dispatchers.IO) {
      synchronized(lock) {
        val next = readListUnlocked().filterNot { it.id == id }
        writeListUnlocked(next)
        next
      }
    }

  private fun readListUnlocked(): List<WatchedAppEntry> {
    if (!storeFile.exists()) return emptyList()
    return runCatching {
      val arr = JSONArray(storeFile.readText())
      buildList {
        for (i in 0 until arr.length()) {
          val obj = arr.getJSONObject(i)
          add(
            WatchedAppEntry(
              id = obj.getString("id"),
              owner = obj.getString("owner"),
              repo = obj.getString("repo"),
              branch = obj.optString("branch", "main").ifBlank { "main" },
              baselineSha = obj.optString("baselineSha", ""),
              baselineMessage = obj.optString("baselineMessage", ""),
              baselineReleaseTag = obj.optString("baselineReleaseTag", "").ifBlank { null },
              remoteSha = obj.optString("remoteSha", "").ifBlank { null },
              remoteMessage = obj.optString("remoteMessage", "").ifBlank { null },
              remoteReleaseTag = obj.optString("remoteReleaseTag", "").ifBlank { null },
              remoteApkName = obj.optString("remoteApkName", "").ifBlank { null },
              remoteApkUrl = obj.optString("remoteApkUrl", "").ifBlank { null },
              lastCheckedAtMs = obj.optLong("lastCheckedAtMs", 0L),
              lastNotifiedKey = obj.optString("lastNotifiedKey", "").ifBlank { null },
              lastError = obj.optString("lastError", "").ifBlank { null },
              workspacePath = obj.optString("workspacePath", "").ifBlank { null },
            )
          )
        }
      }
    }.getOrDefault(emptyList())
  }

  private fun writeListUnlocked(entries: List<WatchedAppEntry>) {
    val arr = JSONArray()
    entries.forEach { e ->
      arr.put(
        JSONObject().apply {
          put("id", e.id)
          put("owner", e.owner)
          put("repo", e.repo)
          put("branch", e.branch)
          put("baselineSha", e.baselineSha)
          put("baselineMessage", e.baselineMessage)
          put("baselineReleaseTag", e.baselineReleaseTag ?: "")
          put("remoteSha", e.remoteSha ?: "")
          put("remoteMessage", e.remoteMessage ?: "")
          put("remoteReleaseTag", e.remoteReleaseTag ?: "")
          put("remoteApkName", e.remoteApkName ?: "")
          put("remoteApkUrl", e.remoteApkUrl ?: "")
          put("lastCheckedAtMs", e.lastCheckedAtMs)
          put("lastNotifiedKey", e.lastNotifiedKey ?: "")
          put("lastError", e.lastError ?: "")
          put("workspacePath", e.workspacePath ?: "")
        }
      )
    }
    storeFile.writeText(arr.toString(2))
  }

  companion object {
    private const val KEY_AUTO_CHECK_ON_OPEN = "auto_check_on_open"
    private const val KEY_LAST_CHECK_ALL_MS = "last_check_all_ms"

    fun makeId(owner: String, repo: String): String =
      "${owner.trim().lowercase()}/${repo.trim().lowercase()}"
  }
}
