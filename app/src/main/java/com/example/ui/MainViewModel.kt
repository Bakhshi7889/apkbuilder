package com.example.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.AndroidProjectSummary
import com.example.data.ApkSigningService
import com.example.data.ApkVerificationSummary
import com.example.data.BuildOutcomeStatus
import com.example.data.BuildStepEvent
import com.example.data.GitHubArtifactInfo
import com.example.data.GitHubRepository
import com.example.data.GitHubSearchRepo
import com.example.data.GitHubUpdateResult
import com.example.data.GitHubWorkflowInfo
import com.example.data.GitHubWorkflowRun
import com.example.data.GitHubWorkspaceMeta
import com.example.data.ImportProgress
import com.example.data.KeystoreFileInfo
import com.example.data.ProjectRepository
import com.example.data.SelfUpdateConfig
import com.example.data.SelfUpdateRepository
import com.example.data.ShellEngine
import com.example.data.SigningResult
import com.example.data.SystemEnvironmentInfo
import com.example.data.UpdateNotifier
import com.example.data.WatchedAppEntry
import com.example.data.WatchedAppsRepository
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class AppTab {
  APPS,
  BUILD,
  SIGN,
}

enum class BuildMode {
  DEVICE_GRADLE,
  CLOUD_GITHUB_ACTIONS,
}

data class BuilderUiState(
  val workspaces: List<AndroidProjectSummary> = emptyList(),
  val activeProject: AndroidProjectSummary? = null,
  val activeGitHubMeta: GitHubWorkspaceMeta? = null,
  val latestUpdateResult: GitHubUpdateResult? = null,
  val isCheckingUpdates: Boolean = false,
  val isImporting: Boolean = false,
  val importProgress: ImportProgress? = null,
  val statusMessage: String? = null,
  val isErrorStatus: Boolean = false,
  val isGitHubFormExpanded: Boolean = false,
  val gitHubRepoInput: String = "",
  val isTokenFieldExpanded: Boolean = false,
  val gitHubTokenInput: String = "",
  val gitHubSearchResults: List<GitHubSearchRepo> = emptyList(),
  val isSearchingGitHub: Boolean = false,
  val gitHubSearchAndroidFilter: Boolean = true,
  val gitHubSearchError: String? = null,
  val isMoreSourcesExpanded: Boolean = false,
  val isProjectSwitcherExpanded: Boolean = false,
  val isToolchainExpanded: Boolean = false,
  val envInfo: SystemEnvironmentInfo? = null,
  val isFilesExpanded: Boolean = false,
  val expandedFilePath: String? = null,
  val editingFileText: String = "",
  val isFileSaving: Boolean = false,
  val buildMode: BuildMode = BuildMode.DEVICE_GRADLE,
  val gradleOnlineMode: Boolean = false,
  val workflows: List<GitHubWorkflowInfo> = emptyList(),
  val workflowRuns: List<GitHubWorkflowRun> = emptyList(),
  val actionsArtifactsByRun: Map<Long, List<GitHubArtifactInfo>> = emptyMap(),
  val isLoadingActions: Boolean = false,
  val isPollingActionsRun: Boolean = false,
  val actionsError: String? = null,
  val isBuilding: Boolean = false,
  val isBuildPanelExpanded: Boolean = false,
  val buildLogs: List<String> = emptyList(),
  val buildOutcome: BuildStepEvent.Completed? = null,
  val scriptPreview: String? = null,
)

data class SignUiState(
  val availableApks: List<File> = emptyList(),
  val selectedApk: ApkVerificationSummary? = null,
  val isVerifyingApk: Boolean = false,
  val keystores: List<KeystoreFileInfo> = emptyList(),
  val selectedKeystore: KeystoreFileInfo? = null,
  val keystorePassword: String = "",
  val keyAliasInput: String = "",
  val discoveredAliases: List<String> = emptyList(),
  val isCreateKeystoreExpanded: Boolean = false,
  val newKeyFileName: String = "release_key",
  val newKeyAlias: String = "upload",
  val newKeyCommonName: String = "APK Builder Release",
  val newKeyOrg: String = "APK Builder",
  val newKeyPassword: String = "",
  val isGeneratingKeystore: Boolean = false,
  val isSigning: Boolean = false,
  val signingResult: SigningResult? = null,
  val selectedApkMatchesInstalled: Boolean? = null,
  val statusMessage: String? = null,
  val isErrorStatus: Boolean = false,
)

data class SelfUpdateUiState(
  val config: SelfUpdateConfig,
  val repoInput: String = config.repoSlug,
  val isConfigExpanded: Boolean = false,
  val isCiYamlExpanded: Boolean = false,
  val isRunningSelfUpdate: Boolean = false,
  val pipelineStep: String? = null,
  val stagedWorkspacePath: String? = null,
  val stagedScriptName: String = "build_offline.sh",
  val termuxInstalled: Boolean = false,
  val hasNewRemoteUpdate: Boolean = false,
  val remoteSha: String? = null,
  val remoteReleaseTag: String? = null,
  val candidateApk: ApkVerificationSummary? = null,
  val signatureMatchesInstalled: Boolean? = null,
  val statusMessage: String? = null,
  val isErrorStatus: Boolean = false,
)

data class AppsUiState(
  val watchedApps: List<WatchedAppEntry> = emptyList(),
  val isAddFormExpanded: Boolean = false,
  val addRepoInput: String = "",
  val isAddingApp: Boolean = false,
  val isCheckingAll: Boolean = false,
  val checkingAppId: String? = null,
  val autoCheckOnAppOpen: Boolean = true,
  val canPostNotifications: Boolean = false,
  val isBatteryInfoExpanded: Boolean = false,
  val lastCheckAllAtMs: Long = 0L,
  val statusMessage: String? = null,
  val isErrorStatus: Boolean = false,
) {
  val updatesAvailableCount: Int
    get() = watchedApps.count { it.hasUpdate }
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
  private val shellEngine = ShellEngine(application)
  private val projectRepository = ProjectRepository(application, shellEngine)
  private val gitHubRepository = GitHubRepository(application, projectRepository)
  private val watchedAppsRepository = WatchedAppsRepository(application)
  private val signingService = ApkSigningService(application)
  private val selfUpdateRepository = SelfUpdateRepository(application, signingService)
  private val updateNotifier = UpdateNotifier(application)

  private val _activeTab = MutableStateFlow(AppTab.BUILD)
  val activeTab: StateFlow<AppTab> = _activeTab.asStateFlow()

  private val _appsState =
    MutableStateFlow(
      AppsUiState(
        autoCheckOnAppOpen = watchedAppsRepository.isAutoCheckOnAppOpenEnabled(),
        canPostNotifications = updateNotifier.canPostNotifications(),
        lastCheckAllAtMs = watchedAppsRepository.getLastCheckAllAtMs(),
      )
    )
  val appsState: StateFlow<AppsUiState> = _appsState.asStateFlow()

  private val _selfUpdateState =
    MutableStateFlow(
      SelfUpdateUiState(
        config = selfUpdateRepository.loadConfig(),
      )
    )
  val selfUpdateState: StateFlow<SelfUpdateUiState> = _selfUpdateState.asStateFlow()

  private val _builderState =
    MutableStateFlow(
      BuilderUiState(gitHubTokenInput = gitHubRepository.getSavedToken())
    )
  val builderState: StateFlow<BuilderUiState> = _builderState.asStateFlow()

  private val _signState = MutableStateFlow(SignUiState())
  val signState: StateFlow<SignUiState> = _signState.asStateFlow()

  private var buildJob: Job? = null
  private var importJob: Job? = null
  private var actionsPollJob: Job? = null

  init {
    refreshEnvironment()
    refreshWorkspaces(selectFirst = true)
    refreshSigningAssets()
  }

  fun selectTab(tab: AppTab) {
    _activeTab.value = tab
    when (tab) {
      AppTab.APPS -> {
        refreshNotificationPermissionStatus()
        refreshWorkspaces(selectFirst = false)
      }
      AppTab.BUILD -> refreshWorkspaces(selectFirst = false)
      AppTab.SIGN -> refreshSigningAssets()
    }
  }

  fun refreshEnvironment() {
    viewModelScope.launch {
      val info = shellEngine.inspectEnvironment()
      _builderState.update { it.copy(envInfo = info) }
      _selfUpdateState.update { it.copy(termuxInstalled = info.termuxInstalled) }
    }
  }

  fun toggleToolchainExpanded() {
    _builderState.update { it.copy(isToolchainExpanded = !it.isToolchainExpanded) }
  }

  fun refreshWorkspaces(selectFirst: Boolean = false) {
    viewModelScope.launch {
      val list = projectRepository.listWorkspaces()
      val syncedWatched = watchedAppsRepository.syncWithWorkspaces(list, gitHubRepository)
      autoLinkSelfRepoIfDetected(list)
      _builderState.update { state ->
        val currentPath = state.activeProject?.rootPath
        val updatedActive =
          when {
            currentPath != null -> list.firstOrNull { it.rootPath == currentPath } ?: list.firstOrNull()
            selectFirst -> list.firstOrNull()
            else -> state.activeProject
          }
        val meta = updatedActive?.let { gitHubRepository.readMeta(File(it.rootPath)) }
        state.copy(
          workspaces = list,
          activeProject = updatedActive,
          activeGitHubMeta = meta,
        )
      }
      _appsState.update { it.copy(watchedApps = syncedWatched) }
    }
  }

  private fun autoLinkSelfRepoIfDetected(workspaces: List<AndroidProjectSummary>) {
    val currentConfig = selfUpdateRepository.loadConfig()
    if (currentConfig.repoSlug.isNotBlank()) return
    for (ws in workspaces) {
      if (ws.appGradle?.applicationId == BuildConfig.APPLICATION_ID) {
        val meta = gitHubRepository.readMeta(File(ws.rootPath)) ?: continue
        val slug = "${meta.owner}/${meta.repo}"
        selfUpdateRepository.saveRepo(slug, meta.branch)
        val refreshed = selfUpdateRepository.loadConfig()
        _selfUpdateState.update {
          it.copy(
            config = refreshed,
            repoInput = refreshed.repoSlug,
            stagedWorkspacePath = ws.rootPath,
          )
        }
        return
      }
    }
  }

  fun toggleGitHubForm() {
    _builderState.update {
      it.copy(
        isGitHubFormExpanded = !it.isGitHubFormExpanded,
        isMoreSourcesExpanded = false,
      )
    }
  }

  fun updateGitHubRepoInput(value: String) {
    _builderState.update { it.copy(gitHubRepoInput = value) }
  }

  fun toggleTokenField() {
    _builderState.update { it.copy(isTokenFieldExpanded = !it.isTokenFieldExpanded) }
  }

  fun updateGitHubTokenInput(value: String) {
    gitHubRepository.saveToken(value)
    _builderState.update { it.copy(gitHubTokenInput = value) }
  }

  fun toggleGitHubSearchAndroidFilter() {
    _builderState.update { it.copy(gitHubSearchAndroidFilter = !it.gitHubSearchAndroidFilter) }
  }

  fun searchGitHubRepositories() {
    val query = _builderState.value.gitHubRepoInput.trim()
    val androidOnly = _builderState.value.gitHubSearchAndroidFilter
    val token = _builderState.value.gitHubTokenInput.trim()
    if (query.isEmpty()) {
      _builderState.update {
        it.copy(gitHubSearchError = "Enter keywords (e.g. compose music player, termux) to search.")
      }
      return
    }
    viewModelScope.launch {
      _builderState.update {
        it.copy(isSearchingGitHub = true, gitHubSearchError = null)
      }
      runCatching {
        gitHubRepository.searchRepositories(query, androidFilter = androidOnly, tokenOverride = token)
      }.onSuccess { results ->
        _builderState.update {
          it.copy(
            isSearchingGitHub = false,
            gitHubSearchResults = results,
            gitHubSearchError = if (results.isEmpty()) "No matching repositories found." else null,
          )
        }
      }.onFailure { err ->
        _builderState.update {
          it.copy(
            isSearchingGitHub = false,
            gitHubSearchError = err.message ?: "GitHub search failed.",
          )
        }
      }
    }
  }

  fun selectSearchRepoToImport(repo: GitHubSearchRepo) {
    _builderState.update { it.copy(gitHubRepoInput = repo.fullName, gitHubSearchError = null) }
    importFromGitHub()
  }

  fun buildGitHubBrowserSearchUrl(): String {
    return gitHubRepository.buildBrowserSearchUrl(
      query = _builderState.value.gitHubRepoInput,
      androidFilter = _builderState.value.gitHubSearchAndroidFilter,
    )
  }

  fun importFromGitHub() {
    val input = _builderState.value.gitHubRepoInput.trim()
    val token = _builderState.value.gitHubTokenInput.trim()
    if (input.isEmpty()) {
      _builderState.update {
        it.copy(
          statusMessage = "Enter a GitHub repository (owner/repo or URL).",
          isErrorStatus = true,
        )
      }
      return
    }

    importJob?.cancel()
    importJob =
      viewModelScope.launch {
        _builderState.update {
          it.copy(
            isImporting = true,
            importProgress = null,
            statusMessage = null,
            isErrorStatus = false,
          )
        }
        runCatching {
          gitHubRepository.saveToken(token)
          gitHubRepository.importFromGitHub(input, token) { progress ->
            _builderState.update { it.copy(importProgress = progress) }
          }
        }.onSuccess { summary ->
          val list = projectRepository.listWorkspaces()
          val meta = gitHubRepository.readMeta(File(summary.rootPath))
          val syncedWatched = watchedAppsRepository.syncWithWorkspaces(list, gitHubRepository)
          autoLinkSelfRepoIfDetected(list)
          _builderState.update {
            it.copy(
              isImporting = false,
              importProgress = null,
              workspaces = list,
              activeProject = summary,
              activeGitHubMeta = meta,
              latestUpdateResult = null,
              isGitHubFormExpanded = false,
              isMoreSourcesExpanded = false,
              isProjectSwitcherExpanded = false,
              expandedFilePath = null,
              buildLogs = emptyList(),
              buildOutcome = null,
              isBuildPanelExpanded = false,
              statusMessage =
                "Imported ${summary.workspaceName} (${meta?.importedSha?.take(7) ?: "ok"})",
              isErrorStatus = false,
            )
          }
          _appsState.update { it.copy(watchedApps = syncedWatched) }
          refreshSigningAssets()
        }.onFailure { err ->
          if (err is kotlinx.coroutines.CancellationException) return@onFailure
          _builderState.update {
            it.copy(
              isImporting = false,
              importProgress = null,
              statusMessage = err.message ?: "GitHub import failed.",
              isErrorStatus = true,
            )
          }
        }
      }
  }

  fun cancelGitHubImport() {
    importJob?.cancel()
    importJob = null
    _builderState.update {
      it.copy(
        isImporting = false,
        importProgress = null,
        statusMessage = "GitHub import cancelled.",
        isErrorStatus = false,
      )
    }
  }

  fun checkGitHubUpdates(notifyOnUpdate: Boolean = true) {
    val active = _builderState.value.activeProject ?: return
    val token = _builderState.value.gitHubTokenInput.trim()
    viewModelScope.launch {
      _builderState.update { it.copy(isCheckingUpdates = true, statusMessage = null) }
      runCatching {
        gitHubRepository.checkForUpdates(File(active.rootPath), token)
      }.onSuccess { result ->
        _builderState.update {
          it.copy(
            isCheckingUpdates = false,
            activeGitHubMeta = result.meta,
            latestUpdateResult = result,
            statusMessage =
              if (result.hasNewCommit) {
                "New commit available: ${result.remoteSha.take(7)} — ${result.remoteMessage}"
              } else {
                "Up to date (${result.remoteSha.take(7)})"
              },
            isErrorStatus = false,
          )
        }
        if (notifyOnUpdate && result.hasNewCommit) {
          updateNotifier.notifyGitHubUpdate(
            repoName = "${result.meta.owner}/${result.meta.repo}",
            branch = result.meta.branch,
            shortSha = result.remoteSha.take(7),
            commitMessage = result.remoteMessage,
            releaseTag = result.latestReleaseTag,
          )
        }
      }.onFailure { err ->
        _builderState.update {
          it.copy(
            isCheckingUpdates = false,
            statusMessage = err.message ?: "Update check failed.",
            isErrorStatus = true,
          )
        }
      }
    }
  }

  fun pullLatestFromGitHub() {
    val active = _builderState.value.activeProject ?: return
    val token = _builderState.value.gitHubTokenInput.trim()
    importJob?.cancel()
    importJob =
      viewModelScope.launch {
        _builderState.update {
          it.copy(
            isImporting = true,
            importProgress = null,
            statusMessage = null,
            isErrorStatus = false,
          )
        }
        runCatching {
          gitHubRepository.pullLatest(File(active.rootPath), token) { progress ->
            _builderState.update { it.copy(importProgress = progress) }
          }
        }.onSuccess { summary ->
          val list = projectRepository.listWorkspaces()
          val meta = gitHubRepository.readMeta(File(summary.rootPath))
          val syncedWatched = watchedAppsRepository.syncWithWorkspaces(list, gitHubRepository)
          _builderState.update {
            it.copy(
              isImporting = false,
              importProgress = null,
              workspaces = list,
              activeProject = summary,
              activeGitHubMeta = meta,
              latestUpdateResult = null,
              expandedFilePath = null,
              statusMessage = "Updated to ${meta?.importedSha?.take(7) ?: "latest"}",
              isErrorStatus = false,
            )
          }
          _appsState.update { it.copy(watchedApps = syncedWatched) }
        }.onFailure { err ->
          if (err is kotlinx.coroutines.CancellationException) return@onFailure
          _builderState.update {
            it.copy(
              isImporting = false,
              importProgress = null,
              statusMessage = err.message ?: "Failed to pull latest commit.",
              isErrorStatus = true,
            )
          }
        }
      }
  }

  fun downloadLatestReleaseApkToSigner() {
    val meta = _builderState.value.activeGitHubMeta ?: return
    val url = _builderState.value.latestUpdateResult?.latestApkUrl ?: meta.latestApkUrl ?: return
    val name =
      _builderState.value.latestUpdateResult?.latestApkName ?: meta.latestApkName ?: "release.apk"
    val token = _builderState.value.gitHubTokenInput.trim()

    importJob?.cancel()
    importJob =
      viewModelScope.launch {
        _builderState.update {
          it.copy(isImporting = true, importProgress = null, statusMessage = null)
        }
        runCatching {
          gitHubRepository.downloadReleaseApk(url, name, token) { progress ->
            _builderState.update { it.copy(importProgress = progress) }
          }
        }.onSuccess { apkFile ->
          _builderState.update {
            it.copy(
              isImporting = false,
              importProgress = null,
              statusMessage = "Downloaded ${apkFile.name} to Sign tab",
              isErrorStatus = false,
            )
          }
          refreshSigningAssets(selectApkFile = apkFile)
          _activeTab.value = AppTab.SIGN
        }.onFailure { err ->
          if (err is kotlinx.coroutines.CancellationException) return@onFailure
          _builderState.update {
            it.copy(
              isImporting = false,
              importProgress = null,
              statusMessage = err.message ?: "Failed to download release APK.",
              isErrorStatus = true,
            )
          }
        }
      }
  }

  fun toggleMoreSources() {
    _builderState.update { it.copy(isMoreSourcesExpanded = !it.isMoreSourcesExpanded) }
  }

  fun toggleProjectSwitcher() {
    _builderState.update { it.copy(isProjectSwitcherExpanded = !it.isProjectSwitcherExpanded) }
  }

  fun selectWorkspace(project: AndroidProjectSummary) {
    actionsPollJob?.cancel()
    val meta = gitHubRepository.readMeta(File(project.rootPath))
    _builderState.update {
      it.copy(
        activeProject = project,
        activeGitHubMeta = meta,
        latestUpdateResult = null,
        workflows = emptyList(),
        workflowRuns = emptyList(),
        actionsArtifactsByRun = emptyMap(),
        actionsError = null,
        isPollingActionsRun = false,
        buildMode = if (meta != null && it.buildMode == BuildMode.CLOUD_GITHUB_ACTIONS) BuildMode.CLOUD_GITHUB_ACTIONS else BuildMode.DEVICE_GRADLE,
        isProjectSwitcherExpanded = false,
        expandedFilePath = null,
        buildLogs = emptyList(),
        buildOutcome = null,
        isBuildPanelExpanded = false,
        statusMessage = null,
      )
    }
  }

  fun importZip(uri: Uri) {
    importJob?.cancel()
    importJob =
      viewModelScope.launch {
        _builderState.update {
          it.copy(isImporting = true, importProgress = null, statusMessage = null, isErrorStatus = false)
        }
        runCatching {
          projectRepository.importZip(uri) { progress ->
            _builderState.update { it.copy(importProgress = progress) }
          }
        }.onSuccess { summary ->
          val list = projectRepository.listWorkspaces()
          _builderState.update {
            it.copy(
              isImporting = false,
              importProgress = null,
              workspaces = list,
              activeProject = summary,
              activeGitHubMeta = null,
              latestUpdateResult = null,
              workflows = emptyList(),
              workflowRuns = emptyList(),
              actionsArtifactsByRun = emptyMap(),
              buildMode = BuildMode.DEVICE_GRADLE,
              isMoreSourcesExpanded = false,
              isProjectSwitcherExpanded = false,
              expandedFilePath = null,
              buildLogs = emptyList(),
              buildOutcome = null,
              isBuildPanelExpanded = false,
              statusMessage = "Imported ${summary.workspaceName} (${summary.fileCount} files)",
              isErrorStatus = false,
            )
          }
          refreshSigningAssets()
        }.onFailure { err ->
          if (err is kotlinx.coroutines.CancellationException) return@onFailure
          _builderState.update {
            it.copy(
              isImporting = false,
              importProgress = null,
              statusMessage = err.message ?: "Failed to extract zip archive.",
              isErrorStatus = true,
            )
          }
        }
      }
  }

  fun importFolderTree(uri: Uri) {
    importJob?.cancel()
    importJob =
      viewModelScope.launch {
        _builderState.update {
          it.copy(isImporting = true, importProgress = null, statusMessage = null, isErrorStatus = false)
        }
        runCatching {
          projectRepository.importFolderTree(uri) { progress ->
            _builderState.update { it.copy(importProgress = progress) }
          }
        }.onSuccess { summary ->
          val list = projectRepository.listWorkspaces()
          _builderState.update {
            it.copy(
              isImporting = false,
              importProgress = null,
              workspaces = list,
              activeProject = summary,
              activeGitHubMeta = null,
              latestUpdateResult = null,
              workflows = emptyList(),
              workflowRuns = emptyList(),
              actionsArtifactsByRun = emptyMap(),
              buildMode = BuildMode.DEVICE_GRADLE,
              isMoreSourcesExpanded = false,
              isProjectSwitcherExpanded = false,
              expandedFilePath = null,
              buildLogs = emptyList(),
              buildOutcome = null,
              isBuildPanelExpanded = false,
              statusMessage = "Imported folder ${summary.workspaceName} (${summary.fileCount} files)",
              isErrorStatus = false,
            )
          }
          refreshSigningAssets()
        }.onFailure { err ->
          if (err is kotlinx.coroutines.CancellationException) return@onFailure
          _builderState.update {
            it.copy(
              isImporting = false,
              importProgress = null,
              statusMessage = err.message ?: "Failed to import folder.",
              isErrorStatus = true,
            )
          }
        }
      }
  }

  fun createStarterProject() {
    viewModelScope.launch {
      _builderState.update { it.copy(isImporting = true, statusMessage = null, isErrorStatus = false) }
      runCatching { projectRepository.createStarterGradleProject() }
        .onSuccess { summary ->
          val list = projectRepository.listWorkspaces()
          _builderState.update {
            it.copy(
              isImporting = false,
              workspaces = list,
              activeProject = summary,
              activeGitHubMeta = null,
              latestUpdateResult = null,
              isMoreSourcesExpanded = false,
              isProjectSwitcherExpanded = false,
              expandedFilePath = null,
              buildLogs = emptyList(),
              buildOutcome = null,
              isBuildPanelExpanded = false,
              statusMessage = "Created starter project (${summary.fileCount} files)",
              isErrorStatus = false,
            )
          }
        }
        .onFailure { err ->
          _builderState.update {
            it.copy(
              isImporting = false,
              statusMessage = err.message ?: "Failed to create starter project.",
              isErrorStatus = true,
            )
          }
        }
    }
  }

  fun deleteWorkspace(rootPath: String) {
    viewModelScope.launch {
      projectRepository.deleteWorkspace(rootPath)
      val list = projectRepository.listWorkspaces()
      val nextActive = list.firstOrNull()
      val meta = nextActive?.let { gitHubRepository.readMeta(File(it.rootPath)) }
      _builderState.update {
        it.copy(
          workspaces = list,
          activeProject = nextActive,
          activeGitHubMeta = meta,
          latestUpdateResult = null,
          isProjectSwitcherExpanded = false,
          expandedFilePath = null,
          buildLogs = emptyList(),
          buildOutcome = null,
          isBuildPanelExpanded = false,
        )
      }
      refreshSigningAssets()
    }
  }

  fun toggleBuilderFiles() {
    _builderState.update { it.copy(isFilesExpanded = !it.isFilesExpanded) }
  }

  fun openOrCloseFile(filePath: String) {
    if (_builderState.value.expandedFilePath == filePath) {
      _builderState.update { it.copy(expandedFilePath = null, editingFileText = "") }
      return
    }
    viewModelScope.launch {
      runCatching { projectRepository.readFileText(filePath) }
        .onSuccess { content ->
          _builderState.update {
            it.copy(
              expandedFilePath = filePath,
              editingFileText = content,
              statusMessage = null,
            )
          }
        }
        .onFailure { err ->
          _builderState.update {
            it.copy(
              statusMessage = err.message ?: "Unable to read file.",
              isErrorStatus = true,
            )
          }
        }
    }
  }

  fun updateEditingFileText(newText: String) {
    _builderState.update { it.copy(editingFileText = newText) }
  }

  fun saveExpandedFile() {
    val path = _builderState.value.expandedFilePath ?: return
    val text = _builderState.value.editingFileText
    val activeRoot = _builderState.value.activeProject?.rootPath
    viewModelScope.launch {
      _builderState.update { it.copy(isFileSaving = true) }
      runCatching {
        projectRepository.writeFileText(path, text)
        if (activeRoot != null) projectRepository.inspectProject(File(activeRoot)) else null
      }.onSuccess { refreshed ->
        val list = projectRepository.listWorkspaces()
        _builderState.update {
          it.copy(
            isFileSaving = false,
            workspaces = list,
            activeProject = refreshed ?: it.activeProject,
            statusMessage = "Saved ${File(path).name}",
            isErrorStatus = false,
          )
        }
      }.onFailure { err ->
        _builderState.update {
          it.copy(
            isFileSaving = false,
            statusMessage = err.message ?: "Failed to save file.",
            isErrorStatus = true,
          )
        }
      }
    }
  }

  fun setBuildMode(mode: BuildMode) {
    _builderState.update { it.copy(buildMode = mode, actionsError = null) }
    if (mode == BuildMode.CLOUD_GITHUB_ACTIONS && _builderState.value.activeGitHubMeta != null) {
      refreshGitHubActions()
    }
  }

  fun toggleGradleOnlineMode() {
    _builderState.update { it.copy(gradleOnlineMode = !it.gradleOnlineMode) }
  }

  fun refreshGitHubActions() {
    val meta = _builderState.value.activeGitHubMeta ?: return
    val token = _builderState.value.gitHubTokenInput.trim()
    viewModelScope.launch {
      _builderState.update { it.copy(isLoadingActions = true, actionsError = null) }
      runCatching {
        val workflows = gitHubRepository.listWorkflows(meta.owner, meta.repo, token)
        val runs = gitHubRepository.listWorkflowRuns(meta.owner, meta.repo, token)
        workflows to runs
      }.onSuccess { (workflows, runs) ->
        _builderState.update {
          it.copy(
            isLoadingActions = false,
            workflows = workflows,
            workflowRuns = runs,
            actionsError = null,
          )
        }
      }.onFailure { err ->
        _builderState.update {
          it.copy(
            isLoadingActions = false,
            actionsError = err.message ?: "Failed to query GitHub Actions.",
          )
        }
      }
    }
  }

  fun triggerGitHubWorkflow(workflowId: Long) {
    val meta = _builderState.value.activeGitHubMeta ?: return
    val token = _builderState.value.gitHubTokenInput.trim()
    viewModelScope.launch {
      _builderState.update { it.copy(isLoadingActions = true, actionsError = null) }
      runCatching {
        gitHubRepository.dispatchWorkflow(meta.owner, meta.repo, workflowId, meta.branch, token)
      }.onSuccess {
        _builderState.update {
          it.copy(
            isLoadingActions = false,
            statusMessage = "Dispatched GitHub Actions build on ${meta.branch}",
            isErrorStatus = false,
          )
        }
        startActionsPolling(meta.owner, meta.repo, token)
      }.onFailure { err ->
        _builderState.update {
          it.copy(
            isLoadingActions = false,
            actionsError = err.message ?: "Failed to dispatch workflow.",
          )
        }
      }
    }
  }

  fun installTermforgeWorkflowOnGitHub() {
    val meta = _builderState.value.activeGitHubMeta ?: return
    val token = _builderState.value.gitHubTokenInput.trim()
    viewModelScope.launch {
      _builderState.update { it.copy(isLoadingActions = true, actionsError = null) }
      runCatching {
        gitHubRepository.createOrUpdateAndroidWorkflow(meta.owner, meta.repo, meta.branch, token)
        val workflows = gitHubRepository.listWorkflows(meta.owner, meta.repo, token)
        val runs = gitHubRepository.listWorkflowRuns(meta.owner, meta.repo, token)
        workflows to runs
      }.onSuccess { (workflows, runs) ->
        _builderState.update {
          it.copy(
            isLoadingActions = false,
            workflows = workflows,
            workflowRuns = runs,
            statusMessage = "Committed .github/workflows/termforge-android.yml to ${meta.branch}",
            isErrorStatus = false,
          )
        }
      }.onFailure { err ->
        _builderState.update {
          it.copy(
            isLoadingActions = false,
            actionsError = err.message ?: "Could not add workflow file.",
          )
        }
      }
    }
  }

  private fun startActionsPolling(owner: String, repo: String, token: String) {
    actionsPollJob?.cancel()
    actionsPollJob =
      viewModelScope.launch {
        _builderState.update { it.copy(isPollingActionsRun = true) }
        repeat(24) {
          if (!isActive) return@launch
          delay(5000L)
          val runs =
            runCatching { gitHubRepository.listWorkflowRuns(owner, repo, token) }.getOrNull()
          if (runs != null) {
            _builderState.update { it.copy(workflowRuns = runs) }
            val newest = runs.firstOrNull()
            if (newest != null && newest.status == "completed") {
              _builderState.update { it.copy(isPollingActionsRun = false) }
              if (newest.conclusion == "success") {
                fetchRunArtifacts(newest.id)
              }
              return@launch
            }
          }
        }
        _builderState.update { it.copy(isPollingActionsRun = false) }
      }
  }

  fun fetchRunArtifacts(runId: Long) {
    val meta = _builderState.value.activeGitHubMeta ?: return
    val token = _builderState.value.gitHubTokenInput.trim()
    viewModelScope.launch {
      _builderState.update { it.copy(isLoadingActions = true, actionsError = null) }
      runCatching {
        gitHubRepository.listRunArtifacts(meta.owner, meta.repo, runId, token)
      }.onSuccess { list ->
        _builderState.update {
          it.copy(
            isLoadingActions = false,
            actionsArtifactsByRun = it.actionsArtifactsByRun + (runId to list),
          )
        }
      }.onFailure { err ->
        _builderState.update {
          it.copy(
            isLoadingActions = false,
            actionsError = err.message ?: "Failed to load artifacts.",
          )
        }
      }
    }
  }

  fun downloadWorkflowArtifactApk(artifact: GitHubArtifactInfo) {
    val token = _builderState.value.gitHubTokenInput.trim()
    importJob?.cancel()
    importJob =
      viewModelScope.launch {
        _builderState.update {
          it.copy(isImporting = true, importProgress = null, statusMessage = null, isErrorStatus = false)
        }
        runCatching {
          gitHubRepository.downloadAndExtractArtifactApk(artifact, token) { progress ->
            _builderState.update { it.copy(importProgress = progress) }
          }
        }.onSuccess { apks ->
          val primaryApk = apks.first()
          _builderState.update {
            it.copy(
              isImporting = false,
              importProgress = null,
              statusMessage = "Downloaded ${primaryApk.name} from CI artifact to Sign tab",
              isErrorStatus = false,
            )
          }
          refreshSigningAssets(selectApkFile = primaryApk)
          _activeTab.value = AppTab.SIGN
        }.onFailure { err ->
          if (err is kotlinx.coroutines.CancellationException) return@onFailure
          _builderState.update {
            it.copy(
              isImporting = false,
              importProgress = null,
              statusMessage = err.message ?: "Failed to download CI artifact.",
              isErrorStatus = true,
            )
          }
        }
      }
  }

  fun runPrimaryBuildAction() {
    val state = _builderState.value
    if (state.buildMode == BuildMode.CLOUD_GITHUB_ACTIONS && state.activeGitHubMeta != null) {
      val dispatchable = state.workflows.firstOrNull { it.state == "active" }
      if (dispatchable != null && state.gitHubTokenInput.isNotBlank()) {
        triggerGitHubWorkflow(dispatchable.id)
      } else {
        refreshGitHubActions()
      }
      return
    }
    runOfflineBuild()
  }

  fun runOfflineBuild() {
    val project = _builderState.value.activeProject ?: return
    if (_builderState.value.isBuilding) return
    val onlineGradle = _builderState.value.gradleOnlineMode

    _builderState.update {
      it.copy(
        isBuilding = true,
        isBuildPanelExpanded = true,
        buildLogs = emptyList(),
        buildOutcome = null,
        scriptPreview = null,
        statusMessage = null,
      )
    }

    buildJob?.cancel()
    buildJob =
      viewModelScope.launch {
        projectRepository.runOfflineBuild(project, onlineGradle = onlineGradle).collect { event ->
          when (event) {
            is BuildStepEvent.Log -> {
              _builderState.update { it.copy(buildLogs = it.buildLogs + event.line) }
            }
            is BuildStepEvent.Completed -> {
              val scriptText =
                event.scriptPath?.let { path ->
                  runCatching { File(path).readText() }.getOrNull()
                }
              val refreshed =
                runCatching { projectRepository.inspectProject(File(project.rootPath)) }.getOrNull()
              val list = projectRepository.listWorkspaces()
              _builderState.update {
                it.copy(
                  isBuilding = false,
                  buildOutcome = event,
                  scriptPreview = scriptText,
                  activeProject = refreshed ?: it.activeProject,
                  workspaces = list,
                )
              }
              refreshSigningAssets()
            }
          }
        }
      }
  }

  fun toggleBuildPanelExpanded() {
    _builderState.update { it.copy(isBuildPanelExpanded = !it.isBuildPanelExpanded) }
  }

  fun sendBuiltApkToSigner(relativeOrAbsPath: String) {
    val active = _builderState.value.activeProject ?: return
    val file =
      File(relativeOrAbsPath).takeIf { it.isAbsolute && it.exists() }
        ?: File(active.rootPath, relativeOrAbsPath)
    if (file.exists()) {
      refreshSigningAssets(selectApkFile = file)
      _activeTab.value = AppTab.SIGN
    }
  }

  fun exportActiveProjectZip(destinationUri: Uri) {
    val project = _builderState.value.activeProject ?: return
    viewModelScope.launch {
      runCatching { projectRepository.exportWorkspaceZip(project.rootPath, destinationUri) }
        .onSuccess { bytes ->
          _builderState.update {
            it.copy(
              statusMessage = "Exported ${project.workspaceName}.zip (${formatByteCount(bytes)})",
              isErrorStatus = false,
            )
          }
        }
        .onFailure { err ->
          _builderState.update {
            it.copy(
              statusMessage = err.message ?: "Export failed.",
              isErrorStatus = true,
            )
          }
        }
    }
  }

  fun copyGeneratedScriptToClipboard() {
    val text = _builderState.value.scriptPreview ?: return
    val clipboard =
      getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("build_offline.sh", text))
    _builderState.update {
      it.copy(statusMessage = "Copied build_offline.sh to clipboard", isErrorStatus = false)
    }
  }

  fun createTermuxLaunchIntent(): Intent? = shellEngine.createLaunchTermuxIntent()

  fun prepareActiveWorkspaceTermuxIntent(): Intent? {
    val active = _builderState.value.activeProject ?: return null
    val scriptName = if (_builderState.value.gradleOnlineMode) "build_online.sh" else "build_offline.sh"
    val oneLiner =
      selfUpdateRepository.buildOneLineTermuxShellCommand(
        workspaceName = active.workspaceName,
        scriptFileName = scriptName,
      )
    copyTextToClipboard("TermForge Termux command", oneLiner)
    return selfUpdateRepository.buildTermuxRunCommandIntent(
      workspaceDir = File(active.rootPath),
      scriptFileName = scriptName,
    )
  }

  private fun copyTextToClipboard(label: String, text: String) {
    val clipboard =
      getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
  }

  // --- Self-Update Actions (On-Device Gradle Primary + Cloud Actions Fallback) ---

  fun toggleSelfUpdateConfig() {
    _selfUpdateState.update { it.copy(isConfigExpanded = !it.isConfigExpanded) }
  }

  fun updateSelfRepoInput(value: String) {
    _selfUpdateState.update { it.copy(repoInput = value) }
  }

  fun saveSelfUpdateRepo() {
    val raw = _selfUpdateState.value.repoInput.trim()
    if (raw.isEmpty()) {
      selfUpdateRepository.clearRepo()
      val config = selfUpdateRepository.loadConfig()
      _selfUpdateState.update {
        it.copy(
          config = config,
          repoInput = "",
          isConfigExpanded = false,
          statusMessage = "Cleared self-update repository link.",
          isErrorStatus = false,
        )
      }
      return
    }
    runCatching {
      val parsed = gitHubRepository.parseRepoInput(raw)
      val slug = "${parsed.owner}/${parsed.repo}"
      selfUpdateRepository.saveRepo(slug, parsed.branch ?: "main")
      selfUpdateRepository.loadConfig()
    }.onSuccess { config ->
      _selfUpdateState.update {
        it.copy(
          config = config,
          repoInput = config.repoSlug,
          isConfigExpanded = false,
          statusMessage = "Linked self-update repo: ${config.repoSlug} (${config.branch})",
          isErrorStatus = false,
        )
      }
    }.onFailure { err ->
      _selfUpdateState.update {
        it.copy(
          statusMessage = err.message ?: "Invalid GitHub repo (use owner/repo or GitHub URL).",
          isErrorStatus = true,
        )
      }
    }
  }

  fun toggleSelfCiYaml() {
    _selfUpdateState.update { it.copy(isCiYamlExpanded = !it.isCiYamlExpanded) }
  }

  fun getStarterCiWorkflowYaml(): String = selfUpdateRepository.defaultGitHubActionsWorkflowYaml()

  fun copyStarterCiWorkflow() {
    copyTextToClipboard("termforge-android.yml", getStarterCiWorkflowYaml())
    _selfUpdateState.update {
      it.copy(
        statusMessage = "Copied .github/workflows/termforge-android.yml to clipboard",
        isErrorStatus = false,
      )
    }
  }

  fun runSelfUpdateResilient() {
    if (_selfUpdateState.value.isRunningSelfUpdate) return
    val currentConfig = selfUpdateRepository.loadConfig()
    if (currentConfig.repoSlug.isBlank()) {
      _selfUpdateState.update {
        it.copy(
          isConfigExpanded = true,
          statusMessage = "Enter this app's GitHub repository (owner/repo) first.",
          isErrorStatus = true,
        )
      }
      return
    }
    val token = _builderState.value.gitHubTokenInput.trim()

    viewModelScope.launch {
      val env = shellEngine.inspectEnvironment()
      _selfUpdateState.update {
        it.copy(
          config = currentConfig,
          termuxInstalled = env.termuxInstalled,
          isRunningSelfUpdate = true,
          pipelineStep = "Checking ${currentConfig.repoSlug} on GitHub…",
          candidateApk = null,
          signatureMatchesInstalled = null,
          statusMessage = null,
          isErrorStatus = false,
        )
      }

      runCatching {
        val snap =
          gitHubRepository.fetchRepoRemoteSnapshot(
            rawInputOrFullName = currentConfig.repoSlug,
            branchOverride = currentConfig.branch,
            tokenOverride = token,
          )
        _selfUpdateState.update {
          it.copy(
            remoteSha = snap.remoteSha,
            remoteReleaseTag = snap.latestReleaseTag,
            pipelineStep = "Syncing ${snap.owner}/${snap.repo} (${snap.remoteSha.take(7)})…",
          )
        }

        // Find matching local workspace or import it
        val existingWorkspace =
          _builderState.value.workspaces.firstOrNull { ws ->
            val meta = gitHubRepository.readMeta(File(ws.rootPath))
            meta != null &&
              WatchedAppsRepository.makeId(meta.owner, meta.repo) ==
              WatchedAppsRepository.makeId(snap.owner, snap.repo)
          }

        val workspaceSummary =
          if (existingWorkspace != null) {
            val meta = gitHubRepository.readMeta(File(existingWorkspace.rootPath))
            if (meta?.importedSha != snap.remoteSha) {
              gitHubRepository.pullLatest(File(existingWorkspace.rootPath), token) {}
            } else {
              existingWorkspace
            }
          } else {
            val url = "https://github.com/${snap.owner}/${snap.repo}/tree/${snap.branch}"
            gitHubRepository.importFromGitHub(url, token) {}
          }

        _selfUpdateState.update {
          it.copy(
            stagedWorkspacePath = workspaceSummary.rootPath,
            stagedScriptName = "build_offline.sh",
            pipelineStep = "Running hardened on-device Gradle pipeline…",
          )
        }

        var outcome: BuildStepEvent.Completed? = null
        projectRepository.runOfflineBuild(workspaceSummary, onlineGradle = false).collect { ev ->
          if (ev is BuildStepEvent.Completed) outcome = ev
        }

        val list = projectRepository.listWorkspaces()
        _builderState.update {
          it.copy(
            workspaces = list,
            activeProject = workspaceSummary,
            activeGitHubMeta = gitHubRepository.readMeta(File(workspaceSummary.rootPath)),
          )
        }

        Triple(snap, workspaceSummary, outcome)
      }.onSuccess { (snap, workspaceSummary, outcome) ->
        when (outcome?.status) {
          BuildOutcomeStatus.APK_BUILT -> {
            val apkRelOrAbs = outcome.apkPaths.firstOrNull()
            val apkFile =
              apkRelOrAbs?.let { p ->
                File(p).takeIf { it.isAbsolute && it.exists() } ?: File(workspaceSummary.rootPath, p)
              }
            val verified = apkFile?.let { signingService.verifyApk(it) }
            val sigMatch = verified?.let { signingService.isSignatureCompatibleWithInstalled(it) }
            if (verified != null) refreshSigningAssets(selectApkFile = File(verified.apkPath))
            _selfUpdateState.update {
              it.copy(
                isRunningSelfUpdate = false,
                pipelineStep = "On-device Gradle succeeded! Export or Share APK to install update.",
                candidateApk = verified,
                signatureMatchesInstalled = sigMatch,
                hasNewRemoteUpdate = true,
                statusMessage =
                  if (sigMatch == false) {
                    "APK built! Signature differs from installed app — re-sign with matching keystore in Sign tab before installing."
                  } else {
                    "Self-update APK built cleanly (${verified?.apkName ?: "debug APK"})."
                  },
                isErrorStatus = false,
              )
            }
          }
          else -> {
            // Toolchain missing inside Android sandbox or local failure:
            // Try Release APK fallback automatically if one is attached on GitHub, otherwise guide Termux + Cloud CI.
            val apkUrl = snap.latestApkUrl
            val apkName = snap.latestApkName
            if (!apkUrl.isNullOrBlank() && !apkName.isNullOrBlank()) {
              _selfUpdateState.update {
                it.copy(pipelineStep = "Sandbox lacks java/gradle — fetching Release $apkName fallback…")
              }
              val downloaded =
                runCatching { gitHubRepository.downloadReleaseApk(apkUrl, apkName, token) {} }.getOrNull()
              if (downloaded != null) {
                val verified = signingService.verifyApk(downloaded)
                val sigMatch = signingService.isSignatureCompatibleWithInstalled(verified)
                refreshSigningAssets(selectApkFile = downloaded)
                _selfUpdateState.update {
                  it.copy(
                    isRunningSelfUpdate = false,
                    pipelineStep =
                      if (env.termuxInstalled) {
                        "Downloaded Release APK fallback (${downloaded.name}). You can also tap Run in Termux to build from source on-device."
                      } else {
                        "Downloaded Release APK fallback (${downloaded.name})."
                      },
                    candidateApk = verified,
                    signatureMatchesInstalled = sigMatch,
                    hasNewRemoteUpdate = true,
                    statusMessage = "Self-update APK ready from Release fallback (${downloaded.name}).",
                    isErrorStatus = false,
                  )
                }
                return@onSuccess
              }
            }

            _selfUpdateState.update {
              it.copy(
                isRunningSelfUpdate = false,
                hasNewRemoteUpdate = true,
                pipelineStep =
                  if (env.termuxInstalled) {
                    "Source synced & build_offline.sh hardened for ARM64. Tap Run in Termux to compile on-device via Gradle, or trigger Cloud GitHub Actions."
                  } else {
                    "Source synced & build_offline.sh generated. Android's app sandbox doesn't bundle JDK/Gradle — install Termux for on-device Gradle or tap Cloud Fallback."
                  },
                statusMessage = "Source synced (${snap.remoteSha.take(7)}). Choose Termux or Cloud Actions to finish APK.",
                isErrorStatus = false,
              )
            }
          }
        }
      }.onFailure { err ->
        _selfUpdateState.update {
          it.copy(
            isRunningSelfUpdate = false,
            pipelineStep = null,
            statusMessage = err.message ?: "Self-update check/sync failed.",
            isErrorStatus = true,
          )
        }
      }
    }
  }

  fun openSelfRepoInBuilderCloud() {
    val state = _selfUpdateState.value
    val slug = state.config.repoSlug
    if (slug.isBlank()) {
      _selfUpdateState.update {
        it.copy(isConfigExpanded = true, statusMessage = "Link your GitHub repo first.", isErrorStatus = true)
      }
      return
    }
    val stagedPath = state.stagedWorkspacePath
    val existing =
      stagedPath?.let { p -> _builderState.value.workspaces.firstOrNull { it.rootPath == p } }
        ?: _builderState.value.workspaces.firstOrNull { ws ->
          val meta = gitHubRepository.readMeta(File(ws.rootPath))
          meta != null && "${meta.owner}/${meta.repo}".equals(slug, ignoreCase = true)
        }
    if (existing != null) {
      selectWorkspace(existing)
      setBuildMode(BuildMode.CLOUD_GITHUB_ACTIONS)
      _activeTab.value = AppTab.BUILD
    } else {
      _builderState.update {
        it.copy(
          gitHubRepoInput = "https://github.com/$slug/tree/${state.config.branch}",
          buildMode = BuildMode.CLOUD_GITHUB_ACTIONS,
        )
      }
      _activeTab.value = AppTab.BUILD
      importFromGitHub()
    }
  }

  fun prepareSelfTermuxRunIntent(): Intent? {
    val path = _selfUpdateState.value.stagedWorkspacePath ?: return null
    val dir = File(path)
    val scriptName = _selfUpdateState.value.stagedScriptName
    val oneLiner =
      selfUpdateRepository.buildOneLineTermuxShellCommand(
        workspaceName = dir.name,
        scriptFileName = scriptName,
      )
    copyTextToClipboard("TermForge Self-Update Termux command", oneLiner)
    return selfUpdateRepository.buildTermuxRunCommandIntent(dir, scriptName)
  }

  fun buildShareApkIntent(apkPath: String): Intent? {
    val file = File(apkPath)
    if (!file.exists()) return null
    return runCatching { selfUpdateRepository.buildShareApkIntent(file) }.getOrNull()
  }

  fun exportApkFile(sourcePath: String, destinationUri: Uri) {
    val file = File(sourcePath)
    if (!file.exists()) return
    viewModelScope.launch {
      runCatching { signingService.exportFileToUri(file, destinationUri) }
        .onSuccess { bytes ->
          _selfUpdateState.update {
            it.copy(
              statusMessage = "Exported ${file.name} (${formatByteCount(bytes)})",
              isErrorStatus = false,
            )
          }
          _signState.update {
            it.copy(
              statusMessage = "Exported ${file.name} (${formatByteCount(bytes)})",
              isErrorStatus = false,
            )
          }
        }
        .onFailure { err ->
          _selfUpdateState.update {
            it.copy(statusMessage = err.message ?: "Export failed.", isErrorStatus = true)
          }
        }
    }
  }

  // --- Watched Apps & Multi-App Update Checker Actions ---

  fun refreshNotificationPermissionStatus() {
    _appsState.update {
      it.copy(canPostNotifications = updateNotifier.canPostNotifications())
    }
  }

  fun toggleAddWatchedAppForm() {
    _appsState.update { it.copy(isAddFormExpanded = !it.isAddFormExpanded) }
  }

  fun updateAddWatchedRepoInput(value: String) {
    _appsState.update { it.copy(addRepoInput = value) }
  }

  fun toggleBatteryInfoExpanded() {
    _appsState.update { it.copy(isBatteryInfoExpanded = !it.isBatteryInfoExpanded) }
  }

  fun toggleAutoCheckOnAppOpen() {
    val next = !_appsState.value.autoCheckOnAppOpen
    watchedAppsRepository.setAutoCheckOnAppOpen(next)
    _appsState.update {
      it.copy(
        autoCheckOnAppOpen = next,
        statusMessage =
          if (next) "Battery-saver check ON: GitHub updates check when you open the app."
          else "Auto-check on open paused. Tap Check All anytime.",
        isErrorStatus = false,
      )
    }
  }

  fun addWatchedApp(rawInputOverride: String? = null) {
    val input = (rawInputOverride ?: _appsState.value.addRepoInput).trim()
    val token = _builderState.value.gitHubTokenInput.trim()
    if (input.isEmpty()) {
      _appsState.update {
        it.copy(
          statusMessage = "Enter a GitHub repository (owner/repo or URL) to watch.",
          isErrorStatus = true,
        )
      }
      return
    }
    viewModelScope.launch {
      _appsState.update { it.copy(isAddingApp = true, statusMessage = null, isErrorStatus = false) }
      runCatching {
        val snapshot = gitHubRepository.fetchRepoRemoteSnapshot(input, tokenOverride = token)
        val matchedWorkspace =
          _builderState.value.workspaces.firstOrNull { ws ->
            val meta = gitHubRepository.readMeta(File(ws.rootPath))
            meta != null &&
              WatchedAppsRepository.makeId(meta.owner, meta.repo) ==
              WatchedAppsRepository.makeId(snapshot.owner, snapshot.repo)
          }
        val entry =
          watchedAppsRepository.upsertInitialSnapshot(
            snapshot = snapshot,
            workspacePath = matchedWorkspace?.rootPath,
          )
        val all = watchedAppsRepository.loadAll()
        entry to all
      }.onSuccess { (entry, all) ->
        _appsState.update {
          it.copy(
            watchedApps = all,
            isAddingApp = false,
            isAddFormExpanded = false,
            addRepoInput = if (rawInputOverride == null) "" else it.addRepoInput,
            canPostNotifications = updateNotifier.canPostNotifications(),
            statusMessage =
              "Watching ${entry.fullName} (${entry.branch} @ ${entry.baselineSha.take(7)})",
            isErrorStatus = false,
          )
        }
        if (rawInputOverride != null) {
          _builderState.update {
            it.copy(
              statusMessage = "Added ${entry.fullName} to Apps watchlist",
              isErrorStatus = false,
            )
          }
        }
      }.onFailure { err ->
        _appsState.update {
          it.copy(
            isAddingApp = false,
            statusMessage = err.message ?: "Could not add GitHub app.",
            isErrorStatus = true,
          )
        }
      }
    }
  }

  fun watchSearchRepo(repo: GitHubSearchRepo) {
    addWatchedApp(rawInputOverride = repo.fullName)
  }

  fun removeWatchedApp(id: String) {
    viewModelScope.launch {
      val updated = watchedAppsRepository.remove(id)
      _appsState.update {
        it.copy(
          watchedApps = updated,
          statusMessage = "Removed app from watchlist",
          isErrorStatus = false,
        )
      }
    }
  }

  fun markWatchedAppSeen(id: String) {
    viewModelScope.launch {
      val updated = watchedAppsRepository.markAsSeen(id)
      _appsState.update {
        it.copy(
          watchedApps = updated,
          statusMessage = "Marked update as seen",
          isErrorStatus = false,
        )
      }
    }
  }

  fun checkSingleWatchedApp(id: String, notifyOnUpdate: Boolean = true) {
    val target = _appsState.value.watchedApps.firstOrNull { it.id == id } ?: return
    val token = _builderState.value.gitHubTokenInput.trim()
    viewModelScope.launch {
      _appsState.update { it.copy(checkingAppId = id, statusMessage = null) }
      runCatching {
        val snap =
          gitHubRepository.fetchRepoRemoteSnapshot(
            rawInputOrFullName = target.fullName,
            branchOverride = target.branch,
            tokenOverride = token,
          )
        watchedAppsRepository.recordRemoteCheckSuccess(id, snap)
      }.onSuccess { updatedEntry ->
        val list = watchedAppsRepository.loadAll()
        val finalEntry = updatedEntry
        if (notifyOnUpdate && finalEntry != null && finalEntry.hasUpdate) {
          updateNotifier.notifyMultiAppUpdates(listOf(finalEntry))
          watchedAppsRepository.markNotified(setOf(finalEntry.id))
        }
        val reloaded = watchedAppsRepository.loadAll()
        _appsState.update {
          it.copy(
            watchedApps = reloaded,
            checkingAppId = null,
            canPostNotifications = updateNotifier.canPostNotifications(),
            statusMessage =
              if (finalEntry?.hasUpdate == true) {
                "Update found for ${target.fullName}!"
              } else {
                "${target.fullName} is up to date"
              },
            isErrorStatus = false,
          )
        }
      }.onFailure { err ->
        watchedAppsRepository.recordRemoteCheckFailure(id, err.message ?: "Check failed")
        val list = watchedAppsRepository.loadAll()
        _appsState.update {
          it.copy(
            watchedApps = list,
            checkingAppId = null,
            statusMessage = err.message ?: "Failed to check ${target.fullName}",
            isErrorStatus = true,
          )
        }
      }
    }
  }

  fun onAppForegrounded(forceNow: Boolean = false) {
    refreshNotificationPermissionStatus()
    val state = _appsState.value
    if (!state.autoCheckOnAppOpen || state.isCheckingAll) return
    val lastCheck = watchedAppsRepository.getLastCheckAllAtMs()
    val elapsedSinceLast = System.currentTimeMillis() - lastCheck
    if (!forceNow && elapsedSinceLast < AUTO_CHECK_COOLDOWN_MS) return
    viewModelScope.launch {
      val synced =
        watchedAppsRepository.syncWithWorkspaces(
          _builderState.value.workspaces,
          gitHubRepository,
        )
      _appsState.update { it.copy(watchedApps = synced) }
      if (synced.isNotEmpty()) {
        checkAllWatchedApps(notifyOnNewUpdates = true, isAutoCheck = true)
      }
    }
  }

  fun checkAllWatchedApps(notifyOnNewUpdates: Boolean = true, isAutoCheck: Boolean = false) {
    if (_appsState.value.isCheckingAll) return
    val token = _builderState.value.gitHubTokenInput.trim()
    viewModelScope.launch {
      val list =
        watchedAppsRepository.syncWithWorkspaces(
          _builderState.value.workspaces,
          gitHubRepository,
        )
      if (list.isEmpty()) {
        _appsState.update {
          it.copy(
            watchedApps = list,
            statusMessage = if (isAutoCheck) null else "Add a GitHub app first to check for updates.",
            isErrorStatus = false,
          )
        }
        return@launch
      }

      _appsState.update {
        it.copy(
          watchedApps = list,
          isCheckingAll = true,
          statusMessage = null,
          isErrorStatus = false,
        )
      }

      val nowMs = System.currentTimeMillis()
      coroutineScope {
        list
          .map { entry ->
            async {
              runCatching {
                val snap =
                  gitHubRepository.fetchRepoRemoteSnapshot(
                    rawInputOrFullName = entry.fullName,
                    branchOverride = entry.branch,
                    tokenOverride = token,
                  )
                watchedAppsRepository.recordRemoteCheckSuccess(entry.id, snap, nowMs)
              }.onFailure { err ->
                watchedAppsRepository.recordRemoteCheckFailure(
                  entry.id,
                  err.message ?: "Network check failed",
                  nowMs,
                )
              }
            }
          }
          .awaitAll()
      }

      watchedAppsRepository.setLastCheckAllAtMs(nowMs)
      val afterList = watchedAppsRepository.loadAll()
      val updatedEntries = afterList.filter { it.hasUpdate }
      val entriesToNotify =
        if (isAutoCheck) {
          updatedEntries.filter { it.needsNotification }
        } else {
          updatedEntries
        }

      if (notifyOnNewUpdates && entriesToNotify.isNotEmpty()) {
        updateNotifier.notifyMultiAppUpdates(entriesToNotify)
        watchedAppsRepository.markNotified(entriesToNotify.map { it.id }.toSet())
      }

      val finalList = watchedAppsRepository.loadAll()
      val failedCount = finalList.count { it.lastError != null }
      val updatesCount = finalList.count { it.hasUpdate }
      val summaryMsg =
        when {
          updatesCount > 0 ->
            "$updatesCount app${if (updatesCount == 1) "" else "s"} updated on GitHub"
          failedCount > 0 && failedCount == finalList.size ->
            "Could not reach GitHub for update check"
          else -> "All ${finalList.size} watched apps are up to date"
        }

      _appsState.update {
        it.copy(
          watchedApps = finalList,
          isCheckingAll = false,
          lastCheckAllAtMs = nowMs,
          canPostNotifications = updateNotifier.canPostNotifications(),
          statusMessage = summaryMsg,
          isErrorStatus = failedCount > 0 && failedCount == finalList.size,
        )
      }
    }
  }

  fun importOrOpenWatchedApp(entry: WatchedAppEntry) {
    val existing =
      entry.workspacePath?.let { path ->
        _builderState.value.workspaces.firstOrNull { it.rootPath == path }
      }
    if (existing != null) {
      selectWorkspace(existing)
      _activeTab.value = AppTab.BUILD
      if (entry.hasUpdate) {
        pullLatestFromGitHub()
      }
    } else {
      _builderState.update {
        it.copy(
          gitHubRepoInput = "https://github.com/${entry.owner}/${entry.repo}/tree/${entry.branch}",
          isGitHubFormExpanded = false,
        )
      }
      _activeTab.value = AppTab.BUILD
      importFromGitHub()
    }
  }

  fun downloadWatchedAppReleaseApk(entry: WatchedAppEntry) {
    val url = entry.remoteApkUrl ?: return
    val name = entry.remoteApkName ?: "${entry.repo}-release.apk"
    val token = _builderState.value.gitHubTokenInput.trim()

    importJob?.cancel()
    importJob =
      viewModelScope.launch {
        _builderState.update {
          it.copy(isImporting = true, importProgress = null, statusMessage = null)
        }
        _activeTab.value = AppTab.BUILD
        runCatching {
          gitHubRepository.downloadReleaseApk(url, name, token) { progress ->
            _builderState.update { it.copy(importProgress = progress) }
          }
        }.onSuccess { apkFile ->
          val updated = watchedAppsRepository.markAsSeen(entry.id)
          _appsState.update { it.copy(watchedApps = updated) }
          _builderState.update {
            it.copy(
              isImporting = false,
              importProgress = null,
              statusMessage = "Downloaded ${apkFile.name} to Sign tab",
              isErrorStatus = false,
            )
          }
          refreshSigningAssets(selectApkFile = apkFile)
          _activeTab.value = AppTab.SIGN
        }.onFailure { err ->
          if (err is kotlinx.coroutines.CancellationException) return@onFailure
          _builderState.update {
            it.copy(
              isImporting = false,
              importProgress = null,
              statusMessage = err.message ?: "Failed to download APK.",
              isErrorStatus = true,
            )
          }
        }
      }
  }

  // --- Sign Tab Actions ---

  fun refreshSigningAssets(selectApkFile: File? = null) {
    viewModelScope.launch {
      val apks = signingService.listAvailableApks(projectRepository.workspacesRoot)
      val keystores = signingService.listKeystores()
      val targetApk =
        selectApkFile?.takeIf { it.exists() }
          ?: _signState.value.selectedApk?.let { File(it.apkPath) }?.takeIf { it.exists() }
          ?: apks.firstOrNull()
      val verified = targetApk?.let { signingService.verifyApk(it) }
      val matchesInstalled = verified?.let { signingService.isSignatureCompatibleWithInstalled(it) }
      val currentKs =
        _signState.value.selectedKeystore?.takeIf { File(it.absolutePath).exists() }
          ?: keystores.firstOrNull()

      _signState.update {
        it.copy(
          availableApks = apks,
          selectedApk = verified,
          selectedApkMatchesInstalled = matchesInstalled,
          keystores = keystores,
          selectedKeystore = currentKs,
        )
      }
    }
  }

  fun importApkUri(uri: Uri) {
    viewModelScope.launch {
      _signState.update { it.copy(isVerifyingApk = true, statusMessage = null) }
      runCatching {
        val file = signingService.importApkFromUri(uri)
        val summary = signingService.verifyApk(file)
        file to summary
      }.onSuccess { (file, summary) ->
        val apks = signingService.listAvailableApks(projectRepository.workspacesRoot)
        val matchesInstalled = signingService.isSignatureCompatibleWithInstalled(summary)
        _signState.update {
          it.copy(
            isVerifyingApk = false,
            availableApks = apks,
            selectedApk = summary,
            selectedApkMatchesInstalled = matchesInstalled,
            signingResult = null,
            statusMessage = "Loaded ${file.name}",
            isErrorStatus = false,
          )
        }
      }.onFailure { err ->
        _signState.update {
          it.copy(
            isVerifyingApk = false,
            statusMessage = err.message ?: "Failed to load APK.",
            isErrorStatus = true,
          )
        }
      }
    }
  }

  fun selectApkFile(file: File) {
    viewModelScope.launch {
      _signState.update { it.copy(isVerifyingApk = true, statusMessage = null) }
      val summary = signingService.verifyApk(file)
      val matchesInstalled = signingService.isSignatureCompatibleWithInstalled(summary)
      _signState.update {
        it.copy(
          isVerifyingApk = false,
          selectedApk = summary,
          selectedApkMatchesInstalled = matchesInstalled,
          signingResult = null,
        )
      }
    }
  }

  fun importKeystoreUri(uri: Uri) {
    viewModelScope.launch {
      runCatching { signingService.importKeystoreFromUri(uri) }
        .onSuccess { ks ->
          val list = signingService.listKeystores()
          _signState.update {
            it.copy(
              keystores = list,
              selectedKeystore = ks,
              discoveredAliases = emptyList(),
              statusMessage = "Imported keystore ${ks.name}",
              isErrorStatus = false,
            )
          }
        }
        .onFailure { err ->
          _signState.update {
            it.copy(
              statusMessage = err.message ?: "Failed to import keystore.",
              isErrorStatus = true,
            )
          }
        }
    }
  }

  fun selectKeystore(ks: KeystoreFileInfo) {
    _signState.update {
      it.copy(
        selectedKeystore = ks,
        discoveredAliases = emptyList(),
        statusMessage = null,
      )
    }
  }

  fun updateKeystorePassword(password: String) {
    _signState.update { it.copy(keystorePassword = password) }
  }

  fun updateKeyAliasInput(alias: String) {
    _signState.update { it.copy(keyAliasInput = alias) }
  }

  fun unlockKeystoreAliases() {
    val ks = _signState.value.selectedKeystore ?: return
    val pass = _signState.value.keystorePassword
    viewModelScope.launch {
      runCatching { signingService.inspectKeystoreAliases(ks.absolutePath, pass) }
        .onSuccess { aliases ->
          _signState.update {
            it.copy(
              discoveredAliases = aliases,
              keyAliasInput = it.keyAliasInput.ifBlank { aliases.firstOrNull().orEmpty() },
              statusMessage =
                if (aliases.isEmpty()) "No aliases found in keystore."
                else "Unlocked ${aliases.size} alias(es): ${aliases.joinToString(", ")}",
              isErrorStatus = aliases.isEmpty(),
            )
          }
        }
        .onFailure { err ->
          _signState.update {
            it.copy(
              statusMessage = err.message ?: "Incorrect keystore password or format.",
              isErrorStatus = true,
            )
          }
        }
    }
  }

  fun toggleCreateKeystore() {
    _signState.update { it.copy(isCreateKeystoreExpanded = !it.isCreateKeystoreExpanded) }
  }

  fun updateNewKeyFields(
    fileName: String? = null,
    alias: String? = null,
    commonName: String? = null,
    org: String? = null,
    password: String? = null,
  ) {
    _signState.update { state ->
      state.copy(
        newKeyFileName = fileName ?: state.newKeyFileName,
        newKeyAlias = alias ?: state.newKeyAlias,
        newKeyCommonName = commonName ?: state.newKeyCommonName,
        newKeyOrg = org ?: state.newKeyOrg,
        newKeyPassword = password ?: state.newKeyPassword,
      )
    }
  }

  fun generateNewKeystore() {
    val state = _signState.value
    viewModelScope.launch {
      _signState.update { it.copy(isGeneratingKeystore = true, statusMessage = null) }
      runCatching {
        signingService.generatePkcs12Keystore(
          fileName = state.newKeyFileName,
          alias = state.newKeyAlias,
          commonName = state.newKeyCommonName,
          organization = state.newKeyOrg,
          password = state.newKeyPassword,
        )
      }.onSuccess { created ->
        val list = signingService.listKeystores()
        _signState.update {
          it.copy(
            isGeneratingKeystore = false,
            isCreateKeystoreExpanded = false,
            keystores = list,
            selectedKeystore = created,
            keystorePassword = state.newKeyPassword,
            keyAliasInput = state.newKeyAlias,
            discoveredAliases = listOf(state.newKeyAlias),
            statusMessage = "Generated RSA-2048 keystore ${created.name}",
            isErrorStatus = false,
          )
        }
      }.onFailure { err ->
        _signState.update {
          it.copy(
            isGeneratingKeystore = false,
            statusMessage = err.message ?: "Keystore generation failed.",
            isErrorStatus = true,
          )
        }
      }
    }
  }

  fun signSelectedApk() {
    val apkSummary = _signState.value.selectedApk ?: return
    val ks = _signState.value.selectedKeystore ?: return
    val password = _signState.value.keystorePassword
    val alias = _signState.value.keyAliasInput.trim().ifBlank { null }

    viewModelScope.launch {
      _signState.update { it.copy(isSigning = true, statusMessage = null, signingResult = null) }
      runCatching {
        signingService.signApk(
          inputApk = File(apkSummary.apkPath),
          keystoreFile = File(ks.absolutePath),
          storePassword = password,
          alias = alias,
          keyPassword = password,
        )
      }.onSuccess { res ->
        val apks = signingService.listAvailableApks(projectRepository.workspacesRoot)
        val matchesInstalled = signingService.isSignatureCompatibleWithInstalled(res.verification)
        _signState.update {
          it.copy(
            isSigning = false,
            availableApks = apks,
            selectedApk = res.verification,
            selectedApkMatchesInstalled = matchesInstalled,
            signingResult = res,
            statusMessage = "Signed ${res.signedApkName} in ${res.durationMs}ms",
            isErrorStatus = false,
          )
        }
      }.onFailure { err ->
        _signState.update {
          it.copy(
            isSigning = false,
            statusMessage = err.message ?: "APK signing failed.",
            isErrorStatus = true,
          )
        }
      }
    }
  }

  fun exportSignedApk(destinationUri: Uri) {
    val path = _signState.value.signingResult?.signedApkPath ?: _signState.value.selectedApk?.apkPath ?: return
    viewModelScope.launch {
      runCatching {
        signingService.exportFileToUri(File(path), destinationUri)
      }.onSuccess { bytes ->
        _signState.update {
          it.copy(
            statusMessage = "Exported signed APK (${formatByteCount(bytes)})",
            isErrorStatus = false,
          )
        }
      }.onFailure { err ->
        _signState.update {
          it.copy(
            statusMessage = err.message ?: "Failed to export APK.",
            isErrorStatus = true,
          )
        }
      }
    }
  }
}

fun formatByteCount(bytes: Long): String {
  if (bytes < 1024L) return "$bytes B"
  val kb = bytes / 1024.0
  if (kb < 1024.0) return String.format("%.1f KB", kb)
  val mb = kb / 1024.0
  if (mb < 1024.0) return String.format("%.1f MB", mb)
  val gb = mb / 1024.0
  return String.format("%.2f GB", gb)
}

fun formatDurationDetailed(totalSeconds: Long): String {
  val safe = totalSeconds.coerceAtLeast(0L)
  val days = safe / 86_400L
  val hours = (safe % 86_400L) / 3_600L
  val minutes = (safe % 3_600L) / 60L
  val seconds = safe % 60L
  return when {
    days > 0L -> "${days}d ${hours}h ${minutes}m"
    hours > 0L -> "${hours}h ${minutes}m ${seconds}s"
    minutes > 0L -> "${minutes}m ${seconds}s"
    else -> "${seconds}s"
  }
}

fun formatElapsedDuration(elapsedMs: Long): String {
  if (elapsedMs < 1000L) return "${elapsedMs}ms"
  if (elapsedMs < 10_000L) return String.format("%.1fs", elapsedMs / 1000.0)
  return formatDurationDetailed(elapsedMs / 1000L)
}

fun formatRelativeTime(timestampMs: Long, nowMs: Long = System.currentTimeMillis()): String {
  if (timestampMs <= 0L) return "Never checked"
  val diffSec = ((nowMs - timestampMs).coerceAtLeast(0L)) / 1000L
  return when {
    diffSec < 45L -> "Just now"
    diffSec < 3600L -> "${diffSec / 60L}m ago"
    diffSec < 86_400L -> "${diffSec / 3600L}h ago"
    else -> "${diffSec / 86_400L}d ago"
  }
}

private const val AUTO_CHECK_COOLDOWN_MS = 60_000L
