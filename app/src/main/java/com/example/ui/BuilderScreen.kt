package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.AndroidProjectSummary
import com.example.data.BuildOutcomeStatus
import com.example.data.BuildStepEvent
import com.example.data.GitHubUpdateResult
import com.example.data.GitHubWorkspaceMeta
import com.example.data.ImportPhase
import com.example.data.ImportProgress
import com.example.data.ProjectFileNode
import com.example.data.SystemEnvironmentInfo
import com.example.ui.theme.JetBrainsMono

import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import com.example.data.GitHubArtifactInfo
import com.example.data.GitHubSearchRepo
import com.example.data.GitHubWorkflowInfo
import com.example.data.GitHubWorkflowRun

private val BuilderMorphSpring =
  spring<androidx.compose.ui.unit.IntSize>(
    dampingRatio = 0.8f,
    stiffness = Spring.StiffnessMediumLow,
  )

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BuilderScreen(
  state: BuilderUiState,
  onToggleGitHubForm: () -> Unit,
  onGitHubRepoChange: (String) -> Unit,
  onToggleTokenField: () -> Unit,
  onGitHubTokenChange: (String) -> Unit,
  onImportFromGitHub: () -> Unit,
  onSearchGitHub: () -> Unit,
  onToggleAndroidFilter: () -> Unit,
  onOpenBrowserSearch: () -> Unit,
  onImportSearchResult: (GitHubSearchRepo) -> Unit,
  onWatchSearchResult: (GitHubSearchRepo) -> Unit,
  onOpenRepoUrl: (String) -> Unit,
  onCancelImport: () -> Unit,
  onCheckGitHubUpdates: () -> Unit,
  onPullLatestGitHub: () -> Unit,
  onDownloadReleaseApk: () -> Unit,
  onPickZip: () -> Unit,
  onPickFolder: () -> Unit,
  onCreateStarter: () -> Unit,
  onToggleMoreSources: () -> Unit,
  onToggleProjectSwitcher: () -> Unit,
  onSelectWorkspace: (AndroidProjectSummary) -> Unit,
  onDeleteWorkspace: (String) -> Unit,
  onToggleToolchain: () -> Unit,
  onRefreshToolchain: () -> Unit,
  onToggleFiles: () -> Unit,
  onOpenOrCloseFile: (String) -> Unit,
  onEditingFileTextChange: (String) -> Unit,
  onSaveFile: () -> Unit,
  onSetBuildMode: (BuildMode) -> Unit,
  onToggleGradleOnlineMode: () -> Unit,
  onRefreshActions: () -> Unit,
  onTriggerWorkflow: (Long) -> Unit,
  onInstallWorkflow: () -> Unit,
  onFetchRunArtifacts: (Long) -> Unit,
  onDownloadArtifact: (GitHubArtifactInfo) -> Unit,
  onRunOfflineBuild: () -> Unit,
  onToggleBuildPanel: () -> Unit,
  onSendApkToSigner: (String) -> Unit,
  onExportZip: (String) -> Unit,
  onCopyScript: () -> Unit,
  onRunInTermux: () -> Unit,
  onCreateTermuxIntent: () -> android.content.Intent?,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val termuxInstalled = state.envInfo?.termuxInstalled == true

  Column(
    modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Column(modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp).weight(1f)) {
      Spacer(modifier = Modifier.height(8.dp))

      val active = state.activeProject
      if (active == null) {
        if (state.isImporting) {
          Spacer(modifier = Modifier.height(4.dp))
          ImportProgressCard(
            progress =
              state.importProgress
                ?: ImportProgress(
                  phase = ImportPhase.RESOLVING_METADATA,
                  label = "Starting import…",
                ),
            onCancel = onCancelImport,
          )
          Spacer(modifier = Modifier.height(8.dp))
        }

        Box(
          modifier = Modifier.fillMaxWidth().weight(1f),
          contentAlignment = Alignment.Center,
        ) {
          Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            shadowElevation = 3.dp,
            modifier = Modifier.fillMaxWidth().testTag("builder_empty_card"),
          ) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(24.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.Build,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
              )
              Text(
                text = stringResource(R.string.builder_empty_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Text(
                text = stringResource(R.string.builder_empty_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              state.statusMessage?.let { msg ->
                Text(
                  text = msg,
                  style = MaterialTheme.typography.bodySmall,
                  color =
                    if (state.isErrorStatus) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                )
              }
            }
          }
        }

        // Bottom Thumb-Zone Primary Source Card (morphs inline for GitHub & Local options)
        Surface(
          shape = RoundedCornerShape(24.dp),
          color = MaterialTheme.colorScheme.surfaceContainer,
          shadowElevation = 4.dp,
          modifier =
            Modifier.fillMaxWidth()
              .padding(bottom = 12.dp)
              .animateContentSize(animationSpec = BuilderMorphSpring),
        ) {
          Column(
            modifier =
              Modifier.fillMaxWidth()
                .heightIn(max = 500.dp)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Button(
              onClick = onToggleGitHubForm,
              enabled = !state.isImporting,
              shape = RoundedCornerShape(16.dp),
              modifier =
                Modifier.fillMaxWidth()
                  .heightIn(min = 52.dp)
                  .testTag("builder_import_github_button"),
            ) {
              if (state.isImporting) {
                CircularProgressIndicator(
                  modifier = Modifier.size(18.dp),
                  strokeWidth = 2.dp,
                  color = MaterialTheme.colorScheme.onPrimary,
                )
              } else {
                Icon(
                  imageVector = Icons.Filled.CloudDownload,
                  contentDescription = stringResource(R.string.cd_import_github),
                  modifier = Modifier.size(20.dp),
                )
              }
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = stringResource(R.string.builder_import_github),
                style = MaterialTheme.typography.labelLarge,
              )
            }

            AnimatedVisibility(
              visible = state.isGitHubFormExpanded,
              enter = fadeIn() + expandVertically(),
              exit = fadeOut() + shrinkVertically(),
            ) {
              GitHubImportForm(
                repoInput = state.gitHubRepoInput,
                onRepoInputChange = onGitHubRepoChange,
                isTokenExpanded = state.isTokenFieldExpanded,
                onToggleToken = onToggleTokenField,
                tokenInput = state.gitHubTokenInput,
                onTokenChange = onGitHubTokenChange,
                isImporting = state.isImporting,
                isSearching = state.isSearchingGitHub,
                androidFilter = state.gitHubSearchAndroidFilter,
                searchError = state.gitHubSearchError,
                searchResults = state.gitHubSearchResults,
                onSearch = onSearchGitHub,
                onToggleAndroidFilter = onToggleAndroidFilter,
                onOpenBrowserSearch = onOpenBrowserSearch,
                onImportSearchResult = onImportSearchResult,
                onWatchSearchResult = onWatchSearchResult,
                onOpenRepoUrl = onOpenRepoUrl,
                onImport = onImportFromGitHub,
              )
            }

            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .heightIn(min = 48.dp)
                  .clip(RoundedCornerShape(14.dp))
                  .clickable(onClick = onToggleMoreSources)
                  .padding(horizontal = 12.dp, vertical = 10.dp)
                  .testTag("builder_more_sources_toggle"),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(
                text = stringResource(R.string.builder_more_sources),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Icon(
                imageVector =
                  if (state.isMoreSourcesExpanded) Icons.Filled.ExpandLess
                  else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }

            AnimatedVisibility(
              visible = state.isMoreSourcesExpanded,
              enter = fadeIn() + expandVertically(),
              exit = fadeOut() + shrinkVertically(),
            ) {
              Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryActionRow(
                  icon = Icons.Filled.UploadFile,
                  label = stringResource(R.string.builder_import_zip),
                  testTag = "builder_import_zip_button",
                  onClick = onPickZip,
                )
                SecondaryActionRow(
                  icon = Icons.Filled.FolderOpen,
                  label = stringResource(R.string.builder_pick_folder),
                  testTag = "builder_pick_folder_button",
                  onClick = onPickFolder,
                )
                SecondaryActionRow(
                  icon = Icons.Filled.CreateNewFolder,
                  label = stringResource(R.string.builder_create_starter),
                  testTag = "builder_create_starter_button",
                  onClick = onCreateStarter,
                )
              }
            }
          }
        }
      } else {
        // Active Project Workspace
        LazyColumn(
          modifier = Modifier.fillMaxWidth().weight(1f),
          contentPadding = PaddingValues(bottom = 12.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          // Morphing Project Header & Switcher (with GitHub + Local import)
          item(key = "project_header") {
            Surface(
              shape = RoundedCornerShape(24.dp),
              color = MaterialTheme.colorScheme.surfaceContainer,
              shadowElevation = 3.dp,
              modifier =
                Modifier.fillMaxWidth()
                  .animateContentSize(animationSpec = BuilderMorphSpring)
                  .testTag("builder_project_header"),
            ) {
              Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(
                  modifier =
                    Modifier.fillMaxWidth()
                      .heightIn(min = 48.dp)
                      .clip(RoundedCornerShape(14.dp))
                      .clickable(onClick = onToggleProjectSwitcher)
                      .padding(horizontal = 4.dp, vertical = 4.dp)
                      .testTag("builder_switcher_toggle"),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = active.workspaceName,
                      style = MaterialTheme.typography.titleLarge,
                      color = MaterialTheme.colorScheme.onSurface,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                      text = "${active.fileCount} files • ${formatByteCount(active.totalBytes)}",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.primary,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                  }
                  Icon(
                    imageVector =
                      if (state.isProjectSwitcherExpanded) Icons.Filled.ExpandLess
                      else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }

                AnimatedVisibility(
                  visible = state.isProjectSwitcherExpanded,
                  enter = fadeIn() + expandVertically(),
                  exit = fadeOut() + shrinkVertically(),
                ) {
                  Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    state.workspaces.forEach { ws ->
                      Surface(
                        shape = RoundedCornerShape(14.dp),
                        color =
                          if (ws.rootPath == active.rootPath)
                            MaterialTheme.colorScheme.primaryContainer
                          else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                      ) {
                        Row(
                          modifier =
                            Modifier.fillMaxWidth()
                              .heightIn(min = 48.dp)
                              .clickable { onSelectWorkspace(ws) }
                              .padding(start = 12.dp, end = 4.dp),
                          verticalAlignment = Alignment.CenterVertically,
                        ) {
                          Column(modifier = Modifier.weight(1f)) {
                            Text(
                              text = ws.workspaceName,
                              style = MaterialTheme.typography.labelLarge,
                              color =
                                if (ws.rootPath == active.rootPath)
                                  MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface,
                              maxLines = 1,
                              overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                              text = "${ws.fileCount} files",
                              style = MaterialTheme.typography.bodySmall,
                              color =
                                if (ws.rootPath == active.rootPath)
                                  MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                          }
                          IconButton(
                            onClick = { onDeleteWorkspace(ws.rootPath) },
                            modifier = Modifier.minimumInteractiveComponentSize(),
                          ) {
                            Icon(
                              imageVector = Icons.Filled.DeleteOutline,
                              contentDescription = "Delete ${ws.workspaceName}",
                              tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                          }
                        }
                      }
                    }

                    FlowRow(
                      horizontalArrangement = Arrangement.spacedBy(8.dp),
                      verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                      CompactActionChip(
                        icon = Icons.Filled.CloudDownload,
                        label = "GitHub Repo",
                        onClick = onToggleGitHubForm,
                      )
                      CompactActionChip(
                        icon = Icons.Filled.UploadFile,
                        label = "Import .zip",
                        onClick = onPickZip,
                      )
                      CompactActionChip(
                        icon = Icons.Filled.FolderOpen,
                        label = "Pick Folder",
                        onClick = onPickFolder,
                      )
                      CompactActionChip(
                        icon = Icons.Filled.CreateNewFolder,
                        label = "Starter Project",
                        onClick = onCreateStarter,
                      )
                    }

                    AnimatedVisibility(
                      visible = state.isGitHubFormExpanded,
                      enter = fadeIn() + expandVertically(),
                      exit = fadeOut() + shrinkVertically(),
                    ) {
                      GitHubImportForm(
                        repoInput = state.gitHubRepoInput,
                        onRepoInputChange = onGitHubRepoChange,
                        isTokenExpanded = state.isTokenFieldExpanded,
                        onToggleToken = onToggleTokenField,
                        tokenInput = state.gitHubTokenInput,
                        onTokenChange = onGitHubTokenChange,
                        isImporting = state.isImporting,
                        isSearching = state.isSearchingGitHub,
                        androidFilter = state.gitHubSearchAndroidFilter,
                        searchError = state.gitHubSearchError,
                        searchResults = state.gitHubSearchResults,
                        onSearch = onSearchGitHub,
                        onToggleAndroidFilter = onToggleAndroidFilter,
                        onOpenBrowserSearch = onOpenBrowserSearch,
                        onImportSearchResult = onImportSearchResult,
                        onWatchSearchResult = onWatchSearchResult,
                        onOpenRepoUrl = onOpenRepoUrl,
                        onImport = onImportFromGitHub,
                      )
                    }
                  }
                }
              }
            }
          }

          // Live Transfer / Download / Extraction Progress Card (always visible during import/download)
          if (state.isImporting) {
            item(key = "active_import_progress") {
              ImportProgressCard(
                progress =
                  state.importProgress
                    ?: ImportProgress(
                      phase = ImportPhase.RESOLVING_METADATA,
                      label = "Preparing transfer…",
                    ),
                onCancel = onCancelImport,
              )
            }
          }

          // Status Feedback Banner
          state.statusMessage?.let { msg ->
            item(key = "status_banner") {
              Surface(
                shape = RoundedCornerShape(16.dp),
                color =
                  if (state.isErrorStatus) MaterialTheme.colorScheme.errorContainer
                  else MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text(
                  text = msg,
                  style = MaterialTheme.typography.bodySmall,
                  color =
                    if (state.isErrorStatus) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onPrimaryContainer,
                  modifier = Modifier.padding(14.dp),
                )
              }
            }
          }

          // GitHub Sync & Update Notifications Card (when imported from GitHub)
          state.activeGitHubMeta?.let { meta ->
            item(key = "github_sync_card") {
              GitHubSyncCard(
                meta = meta,
                updateResult = state.latestUpdateResult,
                isChecking = state.isCheckingUpdates,
                isPulling = state.isImporting,
                onCheckUpdates = onCheckGitHubUpdates,
                onPullLatest = onPullLatestGitHub,
                onDownloadReleaseApk = onDownloadReleaseApk,
              )
            }
          }

          // Parsed Android / Gradle Summary & Build Target Selector
          item(key = "project_inspection") {
            ProjectSummaryCard(
              project = active,
              hasGitHubMeta = state.activeGitHubMeta != null,
              buildMode = state.buildMode,
              gradleOnlineMode = state.gradleOnlineMode,
              onSetBuildMode = onSetBuildMode,
              onToggleGradleOnlineMode = onToggleGradleOnlineMode,
              onSendApkToSigner = onSendApkToSigner,
              onExportZip = { onExportZip("${active.workspaceName}.zip") },
            )
          }

          // GitHub Actions Cloud Build Card
          if (state.activeGitHubMeta != null && state.buildMode == BuildMode.CLOUD_GITHUB_ACTIONS) {
            item(key = "github_actions_cloud_build") {
              CloudActionsCard(
                meta = state.activeGitHubMeta,
                hasToken = state.gitHubTokenInput.isNotBlank(),
                workflows = state.workflows,
                runs = state.workflowRuns,
                artifactsByRun = state.actionsArtifactsByRun,
                isLoading = state.isLoadingActions,
                isPolling = state.isPollingActionsRun,
                error = state.actionsError,
                onRefresh = onRefreshActions,
                onTriggerWorkflow = onTriggerWorkflow,
                onInstallWorkflow = onInstallWorkflow,
                onFetchArtifacts = onFetchRunArtifacts,
                onDownloadArtifact = onDownloadArtifact,
                onOpenTokenSettings = onToggleProjectSwitcher,
              )
            }
          }

          // Morphing Device Toolchain Disclosure Card
          state.envInfo?.let { env ->
            item(key = "device_toolchain") {
              ToolchainCard(
                env = env,
                isExpanded = state.isToolchainExpanded,
                onToggle = onToggleToolchain,
                onRefresh = onRefreshToolchain,
                onOpenTermux = {
                  onCreateTermuxIntent()?.let { intent -> context.startActivity(intent) }
                },
              )
            }
          }

          // Morphing Project Files Card + Inline Code Editor
          item(key = "project_files") {
            Surface(
              shape = RoundedCornerShape(24.dp),
              color = MaterialTheme.colorScheme.surfaceContainer,
              shadowElevation = 2.dp,
              modifier =
                Modifier.fillMaxWidth()
                  .animateContentSize(animationSpec = BuilderMorphSpring)
                  .testTag("builder_files_card"),
            ) {
              Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(
                  modifier =
                    Modifier.fillMaxWidth()
                      .heightIn(min = 48.dp)
                      .clip(RoundedCornerShape(14.dp))
                      .clickable(onClick = onToggleFiles)
                      .padding(horizontal = 4.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                      imageVector = Icons.Filled.Code,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                      text = "${stringResource(R.string.builder_files_label)} (${active.files.size})",
                      style = MaterialTheme.typography.titleMedium,
                      color = MaterialTheme.colorScheme.onSurface,
                    )
                  }
                  Icon(
                    imageVector =
                      if (state.isFilesExpanded) Icons.Filled.ExpandLess
                      else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }

                AnimatedVisibility(
                  visible = state.isFilesExpanded,
                  enter = fadeIn() + expandVertically(),
                  exit = fadeOut() + shrinkVertically(),
                ) {
                  Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    active.files.forEach { node ->
                      FileNodeMorphRow(
                        node = node,
                        isExpanded = state.expandedFilePath == node.absolutePath,
                        editingText =
                          if (state.expandedFilePath == node.absolutePath) state.editingFileText
                          else "",
                        isSaving = state.isFileSaving,
                        onToggle = { onOpenOrCloseFile(node.absolutePath) },
                        onTextChange = onEditingFileTextChange,
                        onSave = onSaveFile,
                      )
                    }
                  }
                }
              }
            }
          }

          // Morphing Build Output & Script Panel
          if (state.buildLogs.isNotEmpty() || state.buildOutcome != null || state.isBuilding) {
            item(key = "build_output") {
              BuildOutputCard(
                isBuilding = state.isBuilding,
                isExpanded = state.isBuildPanelExpanded,
                logs = state.buildLogs,
                outcome = state.buildOutcome,
                scriptPreview = state.scriptPreview,
                termuxInstalled = termuxInstalled,
                hasGitHubMeta = state.activeGitHubMeta != null,
                onToggleExpanded = onToggleBuildPanel,
                onSwitchToCloudBuild = { onSetBuildMode(BuildMode.CLOUD_GITHUB_ACTIONS) },
                onCopyScript = onCopyScript,
                onSendApkToSigner = onSendApkToSigner,
                onExportZip = { onExportZip("${active.workspaceName}.zip") },
                onRunInTermux = onRunInTermux,
                onOpenTermux = {
                  onCreateTermuxIntent()?.let { intent -> context.startActivity(intent) }
                },
              )
            }
          }
        }

        // Bottom Thumb-Zone Single Primary Action
        val isCloud =
          state.buildMode == BuildMode.CLOUD_GITHUB_ACTIONS && state.activeGitHubMeta != null
        val isBusy = if (isCloud) state.isLoadingActions || state.isPollingActionsRun else state.isBuilding
        val ctaLabel =
          when {
            isCloud && state.isPollingActionsRun -> "Building on GitHub Actions…"
            isCloud && state.isLoadingActions -> "Syncing GitHub Actions…"
            isCloud -> stringResource(R.string.builder_run_cloud)
            state.isBuilding -> "Probing & Building…"
            state.gradleOnlineMode -> stringResource(R.string.builder_run_online)
            else -> stringResource(R.string.builder_run_offline)
          }
        Surface(
          shape = RoundedCornerShape(24.dp),
          color = MaterialTheme.colorScheme.surfaceContainer,
          shadowElevation = 4.dp,
          modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        ) {
          Box(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
            Button(
              onClick = onRunOfflineBuild,
              enabled = !isBusy,
              shape = RoundedCornerShape(16.dp),
              modifier =
                Modifier.fillMaxWidth()
                  .heightIn(min = 52.dp)
                  .testTag("builder_run_offline_button"),
            ) {
              if (isBusy) {
                CircularProgressIndicator(
                  modifier = Modifier.size(18.dp),
                  strokeWidth = 2.dp,
                  color = MaterialTheme.colorScheme.onPrimary,
                )
              } else {
                Icon(
                  imageVector = if (isCloud) Icons.Filled.CloudQueue else Icons.Filled.Build,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp),
                )
              }
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = ctaLabel,
                style = MaterialTheme.typography.labelLarge,
              )
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GitHubImportForm(
  repoInput: String,
  onRepoInputChange: (String) -> Unit,
  isTokenExpanded: Boolean,
  onToggleToken: () -> Unit,
  tokenInput: String,
  onTokenChange: (String) -> Unit,
  isImporting: Boolean,
  isSearching: Boolean,
  androidFilter: Boolean,
  searchError: String?,
  searchResults: List<GitHubSearchRepo>,
  onSearch: () -> Unit,
  onToggleAndroidFilter: () -> Unit,
  onOpenBrowserSearch: () -> Unit,
  onImportSearchResult: (GitHubSearchRepo) -> Unit,
  onWatchSearchResult: (GitHubSearchRepo) -> Unit,
  onOpenRepoUrl: (String) -> Unit,
  onImport: () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    modifier =
      Modifier.fillMaxWidth()
        .animateContentSize(animationSpec = BuilderMorphSpring)
        .testTag("github_import_form"),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      MonoInputField(
        value = repoInput,
        onValueChange = onRepoInputChange,
        hint = stringResource(R.string.github_repo_hint),
        testTag = "github_repo_input",
      )

      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.primaryContainer,
          modifier =
            Modifier.heightIn(min = 48.dp)
              .clip(RoundedCornerShape(12.dp))
              .clickable(enabled = !isSearching, onClick = onSearch)
              .testTag("github_search_button"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            if (isSearching) {
              CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
              )
            } else {
              Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(16.dp),
              )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = if (isSearching) "Searching…" else stringResource(R.string.github_search_action),
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
          }
        }

        Surface(
          shape = RoundedCornerShape(12.dp),
          color =
            if (androidFilter) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
          modifier =
            Modifier.heightIn(min = 48.dp)
              .clip(RoundedCornerShape(12.dp))
              .clickable(onClick = onToggleAndroidFilter)
              .testTag("github_android_filter_chip"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text =
                if (androidFilter) "${stringResource(R.string.github_filter_android)}: ON"
                else "${stringResource(R.string.github_filter_android)}: OFF",
              style = MaterialTheme.typography.labelMedium,
              color =
                if (androidFilter) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        Surface(
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.surfaceContainerHigh,
          modifier =
            Modifier.heightIn(min = 48.dp)
              .clip(RoundedCornerShape(12.dp))
              .clickable(onClick = onOpenBrowserSearch)
              .testTag("github_open_browser_button"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.OpenInNew,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = stringResource(R.string.github_open_browser),
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
        }
      }

      if (!searchError.isNullOrBlank()) {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.errorContainer,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = searchError,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(10.dp),
          )
        }
      }

      AnimatedVisibility(
        visible = searchResults.isNotEmpty(),
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
      ) {
        Column(
          modifier =
            Modifier.fillMaxWidth()
              .heightIn(max = 260.dp)
              .verticalScroll(rememberScrollState())
              .testTag("github_search_results"),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          searchResults.forEach { repo ->
            Surface(
              shape = RoundedCornerShape(14.dp),
              color = MaterialTheme.colorScheme.surfaceContainer,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(
                modifier = Modifier.fillMaxWidth().padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Text(
                    text = repo.fullName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                  )
                  IconButton(
                    onClick = { if (repo.htmlUrl.isNotBlank()) onOpenRepoUrl(repo.htmlUrl) },
                    modifier = Modifier.minimumInteractiveComponentSize(),
                  ) {
                    Icon(
                      imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                      contentDescription = "Open ${repo.fullName} in browser",
                      tint = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(18.dp),
                    )
                  }
                }

                val metaParts = buildList {
                  add("★ ${repo.stars}")
                  if (!repo.language.isNullOrBlank()) add(repo.language)
                  if (repo.sizeKb > 0L) add("~${formatByteCount(repo.sizeKb * 1024L)}")
                }
                Text(
                  text = metaParts.joinToString(" • "),
                  style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                  color = MaterialTheme.colorScheme.primary,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )

                if (repo.description.isNotBlank()) {
                  Text(
                    text = repo.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                  )
                }

                FlowRow(
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  CompactActionChip(
                    icon = Icons.Filled.CloudDownload,
                    label = "Import (${repo.defaultBranch})",
                    onClick = { if (!isImporting) onImportSearchResult(repo) },
                  )
                  CompactActionChip(
                    icon = Icons.Filled.NotificationsActive,
                    label = "Watch",
                    onClick = { onWatchSearchResult(repo) },
                  )
                }
              }
            }
          }
        }
      }

      Row(
        modifier =
          Modifier.fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onToggleToken)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
          Icon(
            imageVector = Icons.Filled.Key,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = stringResource(R.string.github_token_toggle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Icon(
          imageVector =
            if (isTokenExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      AnimatedVisibility(
        visible = isTokenExpanded,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
      ) {
        MonoInputField(
          value = tokenInput,
          onValueChange = onTokenChange,
          hint = stringResource(R.string.github_token_hint),
          isSecret = true,
          testTag = "github_token_input",
        )
      }

      Button(
        onClick = onImport,
        enabled = !isImporting,
        shape = RoundedCornerShape(14.dp),
        modifier =
          Modifier.fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag("github_download_button"),
      ) {
        Text(
          text =
            if (isImporting) "Importing…"
            else stringResource(R.string.github_clone_action),
          style = MaterialTheme.typography.labelLarge,
        )
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GitHubSyncCard(
  meta: GitHubWorkspaceMeta,
  updateResult: GitHubUpdateResult?,
  isChecking: Boolean,
  isPulling: Boolean,
  onCheckUpdates: () -> Unit,
  onPullLatest: () -> Unit,
  onDownloadReleaseApk: () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(24.dp),
    color = MaterialTheme.colorScheme.surfaceContainer,
    shadowElevation = 2.dp,
    modifier =
      Modifier.fillMaxWidth()
        .animateContentSize(animationSpec = BuilderMorphSpring)
        .testTag("github_sync_card"),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "${meta.owner}/${meta.repo} • ${meta.branch}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = "Commit ${meta.importedSha.take(7)} — ${meta.importedMessage.ifBlank { "checked out" }}",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
        Icon(
          imageVector = Icons.Filled.NotificationsActive,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(20.dp),
        )
      }

      if (updateResult?.hasNewCommit == true) {
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = MaterialTheme.colorScheme.primaryContainer,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Text(
              text = "Update available: ${updateResult.remoteSha.take(7)}",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (updateResult.remoteMessage.isNotBlank()) {
              Text(
                text = updateResult.remoteMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
              )
            }
          }
        }
      }

      val releaseTag = updateResult?.latestReleaseTag ?: meta.latestReleaseTag
      val releaseApk = updateResult?.latestApkName ?: meta.latestApkName
      val releaseUrl = updateResult?.latestApkUrl ?: meta.latestApkUrl

      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        CompactActionChip(
          icon = Icons.Filled.Refresh,
          label =
            if (isChecking) "Checking…"
            else stringResource(R.string.github_check_updates),
          onClick = { if (!isChecking) onCheckUpdates() },
        )
        if (updateResult?.hasNewCommit == true) {
          CompactActionChip(
            icon = Icons.Filled.CloudDownload,
            label =
              if (isPulling) "Pulling…"
              else stringResource(R.string.github_pull_latest),
            onClick = { if (!isPulling) onPullLatest() },
          )
        }
        if (!releaseUrl.isNullOrBlank() && !releaseApk.isNullOrBlank()) {
          CompactActionChip(
            icon = Icons.Filled.VerifiedUser,
            label =
              if (!releaseTag.isNullOrBlank()) "Get $releaseTag APK"
              else stringResource(R.string.github_download_release_apk),
            onClick = onDownloadReleaseApk,
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImportProgressCard(
  progress: ImportProgress,
  onCancel: () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(20.dp),
    color = MaterialTheme.colorScheme.surfaceContainer,
    shadowElevation = 3.dp,
    modifier = Modifier.fillMaxWidth().testTag("github_import_progress_card"),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = progress.label,
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        val badgeText =
          progress.fraction?.let { "${(it * 100).toInt()}%" }
            ?: when (progress.phase) {
              ImportPhase.DOWNLOADING_ARCHIVE,
              ImportPhase.DOWNLOADING_APK -> "Chunked"
              else -> "Active"
            }
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = MaterialTheme.colorScheme.primaryContainer,
        ) {
          Text(
            text = badgeText,
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
          )
        }
      }

      val fraction = progress.fraction
      if (fraction != null) {
        LinearProgressIndicator(
          progress = { fraction },
          color = MaterialTheme.colorScheme.primary,
          trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
          modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
        )
      } else {
        LinearProgressIndicator(
          color = MaterialTheme.colorScheme.primary,
          trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
          modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
        )
      }

      val transferredValue =
        when (progress.phase) {
          ImportPhase.RESOLVING_METADATA -> "Resolving repository metadata…"
          ImportPhase.EXTRACTING_FILES -> {
            val base = "${progress.filesDone} / ${progress.filesTotal.coerceAtLeast(1)} files"
            if (progress.bytesTotal > 0L) {
              "$base (${formatByteCount(progress.bytesDone)} / ${formatByteCount(progress.bytesTotal)})"
            } else {
              "$base (${formatByteCount(progress.bytesDone)})"
            }
          }
          ImportPhase.IMPORTING_LOCAL -> {
            if (progress.bytesTotal > 0L) {
              "${formatByteCount(progress.bytesDone)} / ${formatByteCount(progress.bytesTotal)} (${progress.filesDone} files)"
            } else {
              "${formatByteCount(progress.bytesDone)} (${progress.filesDone} files)"
            }
          }
          ImportPhase.DOWNLOADING_ARCHIVE,
          ImportPhase.DOWNLOADING_APK -> {
            when {
              progress.bytesTotal > 0L ->
                "${formatByteCount(progress.bytesDone)} / ${formatByteCount(progress.bytesTotal)}"
              progress.streamHintBytes > 0L ->
                "${formatByteCount(progress.bytesDone)} downloaded (repo ~${formatByteCount(progress.streamHintBytes)})"
              else -> "${formatByteCount(progress.bytesDone)} downloaded"
            }
          }
        }

      val speedValue =
        if (progress.bytesPerSec > 0L) {
          buildString {
            append("${formatByteCount(progress.bytesPerSec)}/s")
            if (progress.avgBytesPerSec > 0L) {
              append(" • avg ${formatByteCount(progress.avgBytesPerSec)}/s")
            }
            if (progress.peakBytesPerSec > progress.bytesPerSec) {
              append(" • peak ${formatByteCount(progress.peakBytesPerSec)}/s")
            }
          }
        } else {
          "Measuring…"
        }

      val elapsedValue = formatElapsedDuration(progress.elapsedMs)
      val remainingValue =
        when {
          progress.etaSeconds != null -> formatDurationDetailed(progress.etaSeconds)
          progress.phase == ImportPhase.RESOLVING_METADATA -> "Pending transfer…"
          progress.bytesTotal <= 0L && progress.bytesDone > 0L ->
            "Unknown (server omitted Content-Length)"
          else -> "Calculating…"
        }

      FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        TelemetryStat(label = "Transferred", value = transferredValue)
        TelemetryStat(label = "Speed", value = speedValue)
        TelemetryStat(label = "Time taken", value = elapsedValue)
        TelemetryStat(label = "Est. remaining", value = remainingValue)
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
      ) {
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = MaterialTheme.colorScheme.surfaceContainerHigh,
          modifier =
            Modifier.heightIn(min = 48.dp)
              .clip(RoundedCornerShape(10.dp))
              .clickable(onClick = onCancel)
              .testTag("github_import_cancel_button"),
        ) {
          Box(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = "Cancel",
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.error,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun TelemetryStat(label: String, value: String) {
  Surface(
    shape = RoundedCornerShape(12.dp),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
  ) {
    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
      Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        text = value,
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProjectSummaryCard(
  project: AndroidProjectSummary,
  hasGitHubMeta: Boolean,
  buildMode: BuildMode,
  gradleOnlineMode: Boolean,
  onSetBuildMode: (BuildMode) -> Unit,
  onToggleGradleOnlineMode: () -> Unit,
  onSendApkToSigner: (String) -> Unit,
  onExportZip: () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(24.dp),
    color = MaterialTheme.colorScheme.surfaceContainer,
    shadowElevation = 2.dp,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      val appId =
        project.appGradle?.applicationId
          ?: project.appGradle?.namespace
          ?: project.manifest?.packageName
          ?: "Unknown applicationId"
      Text(
        text = appId,
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
        color = MaterialTheme.colorScheme.primary,
      )

      val sdkLine =
        buildString {
          append("compileSdk ")
          append(project.appGradle?.compileSdk ?: "?")
          append(" • minSdk ")
          append(project.appGradle?.minSdk ?: project.manifest?.minSdk ?: "?")
          append(" • targetSdk ")
          append(project.appGradle?.targetSdk ?: project.manifest?.targetSdk ?: "?")
        }
      Text(
        text = sdkLine,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface,
      )

      val gradleLine =
        "Gradle: ${project.gradleWrapperVersion ?: "not specified"} • " +
          "Sources: ${project.sourceFileCount} • " +
          "Deps: ${project.appGradle?.dependencyCount ?: 0}"
      Text(
        text = gradleLine,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      // Build Mode Selector (Device vs Cloud GitHub Actions, Online vs Offline Gradle)
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        ModeSelectableChip(
          selected = buildMode == BuildMode.DEVICE_GRADLE,
          label = stringResource(R.string.builder_mode_device),
          onClick = { onSetBuildMode(BuildMode.DEVICE_GRADLE) },
        )
        if (hasGitHubMeta) {
          ModeSelectableChip(
            selected = buildMode == BuildMode.CLOUD_GITHUB_ACTIONS,
            label = stringResource(R.string.builder_mode_cloud),
            onClick = { onSetBuildMode(BuildMode.CLOUD_GITHUB_ACTIONS) },
          )
        }
        if (buildMode == BuildMode.DEVICE_GRADLE) {
          ModeSelectableChip(
            selected = gradleOnlineMode,
            label =
              if (gradleOnlineMode) stringResource(R.string.builder_gradle_online_toggle)
              else stringResource(R.string.builder_gradle_offline_toggle),
            onClick = onToggleGradleOnlineMode,
          )
        }
      }

      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        project.builtApkPaths.forEach { apkPath ->
          CompactActionChip(
            icon = Icons.Filled.VerifiedUser,
            label = "Sign ${apkPath.substringAfterLast('/')}",
            onClick = { onSendApkToSigner(apkPath) },
          )
        }
        CompactActionChip(
          icon = Icons.Filled.Archive,
          label = stringResource(R.string.builder_export_zip),
          onClick = onExportZip,
        )
      }
    }
  }
}

@Composable
private fun ModeSelectableChip(
  selected: Boolean,
  label: String,
  onClick: () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(12.dp),
    color =
      if (selected) MaterialTheme.colorScheme.primaryContainer
      else MaterialTheme.colorScheme.surfaceContainerHigh,
    modifier =
      Modifier.heightIn(min = 48.dp)
        .clip(RoundedCornerShape(12.dp))
        .clickable(onClick = onClick),
  ) {
    Box(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color =
          if (selected) MaterialTheme.colorScheme.onPrimaryContainer
          else MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CloudActionsCard(
  meta: GitHubWorkspaceMeta,
  hasToken: Boolean,
  workflows: List<GitHubWorkflowInfo>,
  runs: List<GitHubWorkflowRun>,
  artifactsByRun: Map<Long, List<GitHubArtifactInfo>>,
  isLoading: Boolean,
  isPolling: Boolean,
  error: String?,
  onRefresh: () -> Unit,
  onTriggerWorkflow: (Long) -> Unit,
  onInstallWorkflow: () -> Unit,
  onFetchArtifacts: (Long) -> Unit,
  onDownloadArtifact: (GitHubArtifactInfo) -> Unit,
  onOpenTokenSettings: () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(24.dp),
    color = MaterialTheme.colorScheme.surfaceContainer,
    shadowElevation = 2.dp,
    modifier =
      Modifier.fillMaxWidth()
        .animateContentSize(animationSpec = BuilderMorphSpring)
        .testTag("cloud_actions_card"),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Cloud Build — GitHub Actions",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text =
              if (isPolling) "Polling ${meta.owner}/${meta.repo} every 5s…"
              else "${meta.owner}/${meta.repo} (${meta.branch})",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
          )
        }
        IconButton(
          onClick = onRefresh,
          modifier = Modifier.minimumInteractiveComponentSize(),
        ) {
          Icon(
            imageVector = Icons.Filled.Refresh,
            contentDescription = "Refresh GitHub Actions runs",
            tint = MaterialTheme.colorScheme.primary,
          )
        }
      }

      if (!hasToken) {
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = MaterialTheme.colorScheme.surfaceContainerLow,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              text = "Public run logs are readable without login. Add a GitHub token (workflow scope) in Project Switcher -> GitHub Repo to trigger builds or download APK artifacts.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CompactActionChip(
              icon = Icons.Filled.Key,
              label = "Add GitHub Token",
              onClick = onOpenTokenSettings,
            )
          }
        }
      }

      error?.let { msg ->
        Text(
          text = msg,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
        )
      }

      if (workflows.isEmpty() && !isLoading) {
        Text(
          text = "No workflows found in .github/workflows/ yet.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (hasToken) {
          CompactActionChip(
            icon = Icons.Filled.CloudQueue,
            label = "Add Android CI Workflow",
            onClick = onInstallWorkflow,
          )
        }
      } else if (workflows.isNotEmpty()) {
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          workflows.forEach { wf ->
            CompactActionChip(
              icon = Icons.Filled.PlayArrow,
              label = "Run ${wf.name}",
              onClick = { onTriggerWorkflow(wf.id) },
            )
          }
        }
      }

      runs.take(5).forEach { run ->
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = MaterialTheme.colorScheme.surfaceContainerLow,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            val statusText =
              if (run.conclusion != null) "${run.status} • ${run.conclusion}" else run.status
            Text(
              text = "#${run.runNumber} ${run.name} ($statusText)",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
              text = "${run.branch} • ${run.headSha.take(7)}",
              style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val runArtifacts = artifactsByRun[run.id]
            if (runArtifacts == null && run.status == "completed") {
              CompactActionChip(
                icon = Icons.Filled.Archive,
                label = "Load Artifacts",
                onClick = { onFetchArtifacts(run.id) },
              )
            } else if (runArtifacts != null) {
              if (runArtifacts.isEmpty()) {
                Text(
                  text = "No build artifacts attached to this run.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              } else {
                FlowRow(
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  runArtifacts.filter { !it.expired }.forEach { art ->
                    CompactActionChip(
                      icon = Icons.Filled.CloudDownload,
                      label = "${art.name} (${formatByteCount(art.sizeBytes)})",
                      onClick = { onDownloadArtifact(art) },
                    )
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ToolchainCard(
  env: SystemEnvironmentInfo,
  isExpanded: Boolean,
  onToggle: () -> Unit,
  onRefresh: () -> Unit,
  onOpenTermux: () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(24.dp),
    color = MaterialTheme.colorScheme.surfaceContainer,
    shadowElevation = 2.dp,
    modifier =
      Modifier.fillMaxWidth().animateContentSize(animationSpec = BuilderMorphSpring),
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
          Icon(
            imageVector = Icons.Filled.Terminal,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
          )
          Spacer(modifier = Modifier.width(10.dp))
          Column {
            Text(
              text = stringResource(R.string.builder_toolchain_toggle),
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
              text =
                "${env.kernelName} ${env.machineArch} • ${env.cpuCores} cores • " +
                  if (env.termuxInstalled) "Termux detected" else "Termux not installed",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        Icon(
          imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      AnimatedVisibility(
        visible = isExpanded,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth().testTag("toolchain_hardware_summary"),
          ) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              val ramText =
                if (env.totalRamBytes > 0L) {
                  "${formatByteCount(env.availRamBytes)} / ${formatByteCount(env.totalRamBytes)} RAM free"
                } else {
                  "RAM unavailable"
                }
              Text(
                text = "Hardware: ${env.cpuCores} CPU cores • $ramText",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Text(
                text =
                  "Gradle Tuning: --max-workers=${env.cpuCores} -Xmx${env.recommendedHeapMb}m --parallel --build-cache",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                color = MaterialTheme.colorScheme.primary,
              )
            }
          }

          LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(env.tools) { tool ->
              Surface(
                shape = RoundedCornerShape(10.dp),
                color =
                  if (tool.isAvailable) MaterialTheme.colorScheme.primaryContainer
                  else MaterialTheme.colorScheme.surfaceContainerHigh,
              ) {
                Text(
                  text = "${tool.name}: ${if (tool.isAvailable) "ok" else "missing"}",
                  style = MaterialTheme.typography.bodySmall,
                  fontWeight = if (tool.isAvailable) FontWeight.SemiBold else FontWeight.Normal,
                  color =
                    if (tool.isAvailable) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
              }
            }
          }

          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            CompactActionChip(
              icon = Icons.Filled.Refresh,
              label = "Re-probe",
              onClick = onRefresh,
            )
            if (env.termuxInstalled) {
              CompactActionChip(
                icon = Icons.Filled.Terminal,
                label = stringResource(R.string.shell_open_termux),
                onClick = onOpenTermux,
              )
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FileNodeMorphRow(
  node: ProjectFileNode,
  isExpanded: Boolean,
  editingText: String,
  isSaving: Boolean,
  onToggle: () -> Unit,
  onTextChange: (String) -> Unit,
  onSave: () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(16.dp),
    color =
      if (isExpanded) MaterialTheme.colorScheme.surfaceContainerHigh
      else MaterialTheme.colorScheme.surfaceContainerLow,
    modifier =
      Modifier.fillMaxWidth().animateContentSize(animationSpec = BuilderMorphSpring),
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = node.isTextEditable, onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          imageVector = Icons.Filled.Description,
          contentDescription = null,
          tint =
            if (node.isTextEditable) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
          text = node.relativePath,
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = formatByteCount(node.sizeBytes),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      AnimatedVisibility(
        visible = isExpanded,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
          ) {
            val editorScroll = rememberScrollState()
            BasicTextField(
              value = editingText,
              onValueChange = onTextChange,
              textStyle =
                MaterialTheme.typography.bodySmall.copy(
                  fontFamily = JetBrainsMono,
                  color = MaterialTheme.colorScheme.onSurface,
                ),
              cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
              modifier =
                Modifier.fillMaxWidth()
                  .heightIn(min = 120.dp, max = 300.dp)
                  .verticalScroll(editorScroll)
                  .padding(12.dp),
            )
          }

          FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Button(
              onClick = onSave,
              enabled = !isSaving,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.heightIn(min = 48.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.Save,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = stringResource(R.string.builder_save_file),
                style = MaterialTheme.typography.labelLarge,
              )
            }
            Button(
              onClick = onToggle,
              colors =
                ButtonDefaults.buttonColors(
                  containerColor = MaterialTheme.colorScheme.surfaceContainer,
                  contentColor = MaterialTheme.colorScheme.onSurface,
                ),
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.heightIn(min = 48.dp),
            ) {
              Text(
                text = stringResource(R.string.builder_discard_file),
                style = MaterialTheme.typography.labelLarge,
              )
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BuildOutputCard(
  isBuilding: Boolean,
  isExpanded: Boolean,
  logs: List<String>,
  outcome: BuildStepEvent.Completed?,
  scriptPreview: String?,
  termuxInstalled: Boolean,
  hasGitHubMeta: Boolean,
  onToggleExpanded: () -> Unit,
  onSwitchToCloudBuild: () -> Unit,
  onCopyScript: () -> Unit,
  onSendApkToSigner: (String) -> Unit,
  onExportZip: () -> Unit,
  onRunInTermux: () -> Unit,
  onOpenTermux: () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(24.dp),
    color = MaterialTheme.colorScheme.surfaceContainer,
    shadowElevation = 3.dp,
    modifier =
      Modifier.fillMaxWidth()
        .animateContentSize(animationSpec = BuilderMorphSpring)
        .testTag("builder_output_card"),
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onToggleExpanded)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
          val icon =
            when (outcome?.status) {
              BuildOutcomeStatus.APK_BUILT -> Icons.Filled.CheckCircle
              BuildOutcomeStatus.GRADLE_FAILED,
              BuildOutcomeStatus.TOOLCHAIN_MISSING -> Icons.Filled.WarningAmber
              null -> Icons.Filled.Build
            }
          val tint =
            when (outcome?.status) {
              BuildOutcomeStatus.APK_BUILT -> MaterialTheme.colorScheme.primary
              BuildOutcomeStatus.GRADLE_FAILED -> MaterialTheme.colorScheme.error
              BuildOutcomeStatus.TOOLCHAIN_MISSING -> MaterialTheme.colorScheme.primary
              null -> MaterialTheme.colorScheme.onSurfaceVariant
            }
          Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
          )
          Spacer(modifier = Modifier.width(10.dp))
          Column {
            Text(
              text = stringResource(R.string.builder_build_output_label),
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurface,
            )
            val subtitle =
              when {
                isBuilding -> "Probing device toolchain…"
                outcome != null -> "${outcome.summary} (${outcome.durationMs}ms)"
                else -> "${logs.size} lines"
              }
            Text(
              text = subtitle,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        Icon(
          imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      AnimatedVisibility(
        visible = isExpanded,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          if (outcome?.status == BuildOutcomeStatus.TOOLCHAIN_MISSING) {
            Surface(
              shape = RoundedCornerShape(14.dp),
              color = MaterialTheme.colorScheme.primaryContainer,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Text(
                  text = "Android's app sandbox has no java/gradle binaries installed. Switch to Cloud (GitHub Actions) to compile an APK online, or export the script + .zip into Termux.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                if (hasGitHubMeta) {
                  CompactActionChip(
                    icon = Icons.Filled.CloudQueue,
                    label = "Use Cloud (GitHub Actions)",
                    onClick = onSwitchToCloudBuild,
                  )
                }
              }
            }
          }

          Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = logs.joinToString("\n"),
              style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
              color = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.padding(12.dp),
            )
          }

          outcome?.apkPaths?.forEach { apk ->
            CompactActionChip(
              icon = Icons.Filled.VerifiedUser,
              label = "Sign ${apk.substringAfterLast('/')}",
              onClick = { onSendApkToSigner(apk) },
            )
          }

          if (scriptPreview != null) {
            val generatedName = outcome?.scriptPath?.substringAfterLast('/') ?: "build_offline.sh"
            Text(
              text = "$generatedName (saved to workspace root)",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.primary,
            )
            Surface(
              shape = RoundedCornerShape(16.dp),
              color = MaterialTheme.colorScheme.surfaceContainerLow,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(
                text = scriptPreview.trimEnd(),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp),
              )
            }

            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              CompactActionChip(
                icon = Icons.Filled.ContentCopy,
                label = stringResource(R.string.builder_copy_script),
                onClick = onCopyScript,
              )
              CompactActionChip(
                icon = Icons.Filled.Archive,
                label = stringResource(R.string.builder_export_zip),
                onClick = onExportZip,
              )
              if (termuxInstalled) {
                CompactActionChip(
                  icon = Icons.Filled.PlayArrow,
                  label = stringResource(R.string.termux_run_build),
                  onClick = onRunInTermux,
                )
                CompactActionChip(
                  icon = Icons.Filled.Terminal,
                  label = stringResource(R.string.shell_open_termux),
                  onClick = onOpenTermux,
                )
              }
            }
            if (termuxInstalled) {
              Text(
                text = "Termux RUN_COMMAND auto-copies the command too; enable allow-external-apps=true in ~/.termux/termux.properties for direct 1-tap runs.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
internal fun MonoInputField(
  value: String,
  onValueChange: (String) -> Unit,
  hint: String,
  isSecret: Boolean = false,
  testTag: String,
) {
  Surface(
    shape = RoundedCornerShape(12.dp),
    color = MaterialTheme.colorScheme.surfaceContainer,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Box(
      modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 12.dp),
      contentAlignment = Alignment.CenterStart,
    ) {
      if (value.isEmpty()) {
        Text(
          text = hint,
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        visualTransformation =
          if (isSecret) PasswordVisualTransformation() else VisualTransformation.None,
        textStyle =
          MaterialTheme.typography.bodySmall.copy(
            fontFamily = JetBrainsMono,
            color = MaterialTheme.colorScheme.onSurface,
          ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth().testTag(testTag),
      )
    }
  }
}

@Composable
private fun SecondaryActionRow(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  testTag: String,
  onClick: () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(14.dp),
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    modifier =
      Modifier.fillMaxWidth()
        .heightIn(min = 48.dp)
        .clip(RoundedCornerShape(14.dp))
        .clickable(onClick = onClick)
        .testTag(testTag),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(20.dp),
      )
      Spacer(modifier = Modifier.width(10.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}

@Composable
internal fun CompactActionChip(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  onClick: () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(12.dp),
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    modifier =
      Modifier.heightIn(min = 48.dp)
        .clip(RoundedCornerShape(12.dp))
        .clickable(onClick = onClick),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(16.dp),
      )
      Spacer(modifier = Modifier.width(6.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}
