package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.data.ShellEngine
import com.example.ui.AppTab
import com.example.ui.AppsScreen
import com.example.ui.BuildMode
import com.example.ui.BuilderScreen
import com.example.ui.MainViewModel
import com.example.ui.SelfUpdateUiState
import com.example.ui.ShellScreen
import com.example.ui.ShellUiState
import com.example.ui.SignScreen
import com.example.ui.theme.TermForgeTheme
import java.io.File

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      TermForgeTheme {
        TermForgeApp()
      }
    }
  }
}

@Composable
fun TermForgeApp(viewModel: MainViewModel = viewModel()) {
  val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()
  val appsState by viewModel.appsState.collectAsStateWithLifecycle()
  val selfUpdateState by viewModel.selfUpdateState.collectAsStateWithLifecycle()
  val builderState by viewModel.builderState.collectAsStateWithLifecycle()
  val shellState by viewModel.shellState.collectAsStateWithLifecycle()
  val signState by viewModel.signState.collectAsStateWithLifecycle()
  val lifecycleOwner = LocalLifecycleOwner.current
  val context = LocalContext.current
  var pendingExportApkPath by remember { mutableStateOf<String?>(null) }
  var pendingTermuxAfterPermission by remember { mutableStateOf<(() -> Unit)?>(null) }

  DisposableEffect(lifecycleOwner, viewModel) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_START) {
        viewModel.onAppForegrounded()
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
    }
  }

  val zipPickerLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        viewModel.importZip(uri)
      }
    }

  val folderPickerLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
      if (uri != null) {
        viewModel.importFolderTree(uri)
      }
    }

  val exportZipLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
      if (uri != null) {
        viewModel.exportActiveProjectZip(uri)
      }
    }

  val apkPickerLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        viewModel.importApkUri(uri)
      }
    }

  val keystorePickerLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        viewModel.importKeystoreUri(uri)
      }
    }

  val exportApkLauncher =
    rememberLauncherForActivityResult(
      ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")
    ) { uri ->
      if (uri != null) {
        viewModel.exportSignedApk(uri)
      }
    }

  val notificationPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
      viewModel.refreshNotificationPermissionStatus()
      viewModel.checkGitHubUpdates(notifyOnUpdate = true)
    }

  val watchlistNotificationPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
      viewModel.refreshNotificationPermissionStatus()
    }

  val termuxPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      viewModel.onTermuxPermissionResult(granted)
      val pending = pendingTermuxAfterPermission
      pendingTermuxAfterPermission = null
      if (granted) {
        pending?.invoke()
      }
    }

  val exportSelfApkLauncher =
    rememberLauncherForActivityResult(
      ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")
    ) { uri ->
      val path = pendingExportApkPath
      pendingExportApkPath = null
      if (uri != null && path != null) {
        viewModel.exportApkFile(path, uri)
      }
    }

  val triggerUpdateCheckWithPermission = {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    } else {
      viewModel.checkGitHubUpdates(notifyOnUpdate = true)
    }
  }

  val requestNotificationsOnly = {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      watchlistNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    } else {
      viewModel.refreshNotificationPermissionStatus()
    }
  }

  val requestTermuxPermission: () -> Unit = {
    val env = builderState.envInfo
    if (env?.termuxInstalled == true) {
      termuxPermissionLauncher.launch(ShellEngine.TERMUX_RUN_PERMISSION)
    } else {
      runCatching { context.startActivity(viewModel.createInstallTermuxIntent()) }
    }
  }

  val runSelfInTermuxWithPermCheck = {
    val dispatch = {
      dispatchTermuxRunIntent(
        context = context,
        runIntent = viewModel.prepareSelfTermuxRunIntent(),
        fallbackLaunch = viewModel.createTermuxLaunchIntent(),
        onResult = viewModel::onTermuxDispatchResult,
      )
    }
    if (builderState.envInfo?.hasTermuxPermission == true) {
      dispatch()
    } else {
      pendingTermuxAfterPermission = dispatch
      termuxPermissionLauncher.launch(ShellEngine.TERMUX_RUN_PERMISSION)
    }
  }

  val runWorkspaceInTermuxWithPermCheck = {
    val dispatch = {
      viewModel.launchActiveWorkspaceInTermux { intent ->
        dispatchTermuxRunIntent(
          context = context,
          runIntent = intent,
          fallbackLaunch = viewModel.createTermuxLaunchIntent(),
          onResult = viewModel::onTermuxDispatchResult,
        )
      }
    }
    if (builderState.envInfo?.hasTermuxPermission == true) {
      dispatch()
    } else {
      pendingTermuxAfterPermission = dispatch
      termuxPermissionLauncher.launch(ShellEngine.TERMUX_RUN_PERMISSION)
    }
  }

  BackHandler(
    enabled =
      activeTab != AppTab.BUILD ||
        appsState.isAddFormExpanded ||
        selfUpdateState.isConfigExpanded ||
        selfUpdateState.isCiYamlExpanded ||
        shellState.isDiagnosticsExpanded ||
        builderState.expandedFilePath != null ||
        builderState.isGitHubFormExpanded
  ) {
    when {
      activeTab == AppTab.APPS && selfUpdateState.isCiYamlExpanded -> viewModel.toggleSelfCiYaml()
      activeTab == AppTab.APPS && selfUpdateState.isConfigExpanded -> viewModel.toggleSelfUpdateConfig()
      activeTab == AppTab.APPS && appsState.isAddFormExpanded -> viewModel.toggleAddWatchedAppForm()
      activeTab == AppTab.SHELL && shellState.isDiagnosticsExpanded -> viewModel.toggleShellDiagnostics()
      activeTab != AppTab.BUILD -> viewModel.selectTab(AppTab.BUILD)
      builderState.expandedFilePath != null ->
        viewModel.openOrCloseFile(builderState.expandedFilePath!!)
      builderState.isGitHubFormExpanded -> viewModel.toggleGitHubForm()
    }
  }

  val topInsets =
    WindowInsets.statusBars
      .union(WindowInsets.displayCutout)
      .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useRail = maxWidth >= 600.dp

    if (useRail) {
      Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
      ) { innerPadding ->
        Row(
          modifier =
            Modifier.fillMaxSize()
              .padding(innerPadding)
              .windowInsetsPadding(topInsets)
              .windowInsetsPadding(WindowInsets.ime.only(WindowInsetsSides.Bottom)),
        ) {
          NavigationRail(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxHeight(),
          ) {
            NavigationRailItem(
              selected = activeTab == AppTab.APPS,
              onClick = { viewModel.selectTab(AppTab.APPS) },
              icon = {
                AppsNavIcon(
                  selected = activeTab == AppTab.APPS,
                  updatesCount = appsState.updatesAvailableCount,
                )
              },
              label = { Text(stringResource(R.string.nav_apps)) },
              colors =
                NavigationRailItemDefaults.colors(
                  selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                  selectedTextColor = MaterialTheme.colorScheme.primary,
                  indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                ),
              modifier = Modifier.testTag("nav_apps"),
            )
            NavigationRailItem(
              selected = activeTab == AppTab.BUILD,
              onClick = { viewModel.selectTab(AppTab.BUILD) },
              icon = {
                Icon(
                  imageVector =
                    if (activeTab == AppTab.BUILD) Icons.Filled.Build else Icons.Outlined.Build,
                  contentDescription = stringResource(R.string.cd_nav_build),
                )
              },
              label = { Text(stringResource(R.string.nav_build)) },
              colors =
                NavigationRailItemDefaults.colors(
                  selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                  selectedTextColor = MaterialTheme.colorScheme.primary,
                  indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                ),
              modifier = Modifier.testTag("nav_build"),
            )
            NavigationRailItem(
              selected = activeTab == AppTab.SHELL,
              onClick = { viewModel.selectTab(AppTab.SHELL) },
              icon = {
                Icon(
                  imageVector =
                    if (activeTab == AppTab.SHELL) Icons.Filled.Terminal
                    else Icons.Outlined.Terminal,
                  contentDescription = stringResource(R.string.cd_nav_shell),
                )
              },
              label = { Text(stringResource(R.string.nav_shell)) },
              colors =
                NavigationRailItemDefaults.colors(
                  selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                  selectedTextColor = MaterialTheme.colorScheme.primary,
                  indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                ),
              modifier = Modifier.testTag("nav_shell"),
            )
            NavigationRailItem(
              selected = activeTab == AppTab.SIGN,
              onClick = { viewModel.selectTab(AppTab.SIGN) },
              icon = {
                Icon(
                  imageVector =
                    if (activeTab == AppTab.SIGN) Icons.Filled.VerifiedUser
                    else Icons.Outlined.VerifiedUser,
                  contentDescription = stringResource(R.string.cd_nav_sign),
                )
              },
              label = { Text(stringResource(R.string.nav_sign)) },
              colors =
                NavigationRailItemDefaults.colors(
                  selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                  selectedTextColor = MaterialTheme.colorScheme.primary,
                  indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                ),
              modifier = Modifier.testTag("nav_sign"),
            )
          }

          Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            TabContent(
              activeTab = activeTab,
              appsState = appsState,
              selfUpdateState = selfUpdateState,
              builderState = builderState,
              shellState = shellState,
              signState = signState,
              viewModel = viewModel,
              onCheckGitHubUpdates = triggerUpdateCheckWithPermission,
              onRequestNotifications = requestNotificationsOnly,
              onRequestTermuxPermission = requestTermuxPermission,
              onRunSelfInTermux = runSelfInTermuxWithPermCheck,
              onRunWorkspaceInTermux = runWorkspaceInTermuxWithPermCheck,
              onLaunchZipPicker = {
                zipPickerLauncher.launch(
                  arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")
                )
              },
              onLaunchFolderPicker = { folderPickerLauncher.launch(null) },
              onLaunchExportZip = { suggested -> exportZipLauncher.launch(suggested) },
              onLaunchApkPicker = {
                apkPickerLauncher.launch(
                  arrayOf("application/vnd.android.package-archive", "application/octet-stream")
                )
              },
              onLaunchKeystorePicker = {
                keystorePickerLauncher.launch(
                  arrayOf("application/x-pkcs12", "application/octet-stream", "*/*")
                )
              },
              onLaunchExportApk = { suggested -> exportApkLauncher.launch(suggested) },
              onLaunchExportSelfApk = { path ->
                pendingExportApkPath = path
                exportSelfApkLauncher.launch(File(path).name)
              },
            )
          }
        }
      }
    } else {
      Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
          NavigationBar(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 0.dp,
          ) {
            NavigationBarItem(
              selected = activeTab == AppTab.APPS,
              onClick = { viewModel.selectTab(AppTab.APPS) },
              icon = {
                AppsNavIcon(
                  selected = activeTab == AppTab.APPS,
                  updatesCount = appsState.updatesAvailableCount,
                )
              },
              label = {
                Text(
                  text = stringResource(R.string.nav_apps),
                  style = MaterialTheme.typography.labelMedium,
                )
              },
              colors =
                NavigationBarItemDefaults.colors(
                  selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                  selectedTextColor = MaterialTheme.colorScheme.primary,
                  indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                ),
              modifier = Modifier.testTag("nav_apps"),
            )
            NavigationBarItem(
              selected = activeTab == AppTab.BUILD,
              onClick = { viewModel.selectTab(AppTab.BUILD) },
              icon = {
                Icon(
                  imageVector =
                    if (activeTab == AppTab.BUILD) Icons.Filled.Build else Icons.Outlined.Build,
                  contentDescription = stringResource(R.string.cd_nav_build),
                )
              },
              label = {
                Text(
                  text = stringResource(R.string.nav_build),
                  style = MaterialTheme.typography.labelMedium,
                )
              },
              colors =
                NavigationBarItemDefaults.colors(
                  selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                  selectedTextColor = MaterialTheme.colorScheme.primary,
                  indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                ),
              modifier = Modifier.testTag("nav_build"),
            )
            NavigationBarItem(
              selected = activeTab == AppTab.SHELL,
              onClick = { viewModel.selectTab(AppTab.SHELL) },
              icon = {
                Icon(
                  imageVector =
                    if (activeTab == AppTab.SHELL) Icons.Filled.Terminal
                    else Icons.Outlined.Terminal,
                  contentDescription = stringResource(R.string.cd_nav_shell),
                )
              },
              label = {
                Text(
                  text = stringResource(R.string.nav_shell),
                  style = MaterialTheme.typography.labelMedium,
                  maxLines = 1,
                )
              },
              colors =
                NavigationBarItemDefaults.colors(
                  selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                  selectedTextColor = MaterialTheme.colorScheme.primary,
                  indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                ),
              modifier = Modifier.testTag("nav_shell"),
            )
            NavigationBarItem(
              selected = activeTab == AppTab.SIGN,
              onClick = { viewModel.selectTab(AppTab.SIGN) },
              icon = {
                Icon(
                  imageVector =
                    if (activeTab == AppTab.SIGN) Icons.Filled.VerifiedUser
                    else Icons.Outlined.VerifiedUser,
                  contentDescription = stringResource(R.string.cd_nav_sign),
                )
              },
              label = {
                Text(
                  text = stringResource(R.string.nav_sign),
                  style = MaterialTheme.typography.labelMedium,
                  maxLines = 1,
                )
              },
              colors =
                NavigationBarItemDefaults.colors(
                  selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                  selectedTextColor = MaterialTheme.colorScheme.primary,
                  indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                ),
              modifier = Modifier.testTag("nav_sign"),
            )
          }
        },
      ) { innerPadding ->
        Box(
          modifier =
            Modifier.fillMaxSize()
              .padding(innerPadding)
              .windowInsetsPadding(topInsets)
              .windowInsetsPadding(WindowInsets.ime.only(WindowInsetsSides.Bottom))
        ) {
          TabContent(
            activeTab = activeTab,
            appsState = appsState,
            selfUpdateState = selfUpdateState,
            builderState = builderState,
            shellState = shellState,
            signState = signState,
            viewModel = viewModel,
            onCheckGitHubUpdates = triggerUpdateCheckWithPermission,
            onRequestNotifications = requestNotificationsOnly,
            onRequestTermuxPermission = requestTermuxPermission,
            onRunSelfInTermux = runSelfInTermuxWithPermCheck,
            onRunWorkspaceInTermux = runWorkspaceInTermuxWithPermCheck,
            onLaunchZipPicker = {
              zipPickerLauncher.launch(
                arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")
              )
            },
            onLaunchFolderPicker = { folderPickerLauncher.launch(null) },
            onLaunchExportZip = { suggested -> exportZipLauncher.launch(suggested) },
            onLaunchApkPicker = {
              apkPickerLauncher.launch(
                arrayOf("application/vnd.android.package-archive", "application/octet-stream")
              )
            },
            onLaunchKeystorePicker = {
              keystorePickerLauncher.launch(
                arrayOf("application/x-pkcs12", "application/octet-stream", "*/*")
              )
            },
            onLaunchExportApk = { suggested -> exportApkLauncher.launch(suggested) },
            onLaunchExportSelfApk = { path ->
              pendingExportApkPath = path
              exportSelfApkLauncher.launch(File(path).name)
            },
          )
        }
      }
    }
  }
}

@Composable
private fun AppsNavIcon(selected: Boolean, updatesCount: Int) {
  val icon =
    if (selected) Icons.Filled.NotificationsActive else Icons.Outlined.Notifications
  if (updatesCount > 0) {
    BadgedBox(
      badge = {
        Badge(
          containerColor = MaterialTheme.colorScheme.primary,
          contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
          Text(updatesCount.toString())
        }
      }
    ) {
      Icon(
        imageVector = icon,
        contentDescription = stringResource(R.string.cd_nav_apps),
      )
    }
  } else {
    Icon(
      imageVector = icon,
      contentDescription = stringResource(R.string.cd_nav_apps),
    )
  }
}

@Composable
private fun TabContent(
  activeTab: AppTab,
  appsState: com.example.ui.AppsUiState,
  selfUpdateState: SelfUpdateUiState,
  builderState: com.example.ui.BuilderUiState,
  shellState: ShellUiState,
  signState: com.example.ui.SignUiState,
  viewModel: MainViewModel,
  onCheckGitHubUpdates: () -> Unit,
  onRequestNotifications: () -> Unit,
  onRequestTermuxPermission: () -> Unit,
  onRunSelfInTermux: () -> Unit,
  onRunWorkspaceInTermux: () -> Unit,
  onLaunchZipPicker: () -> Unit,
  onLaunchFolderPicker: () -> Unit,
  onLaunchExportZip: (String) -> Unit,
  onLaunchApkPicker: () -> Unit,
  onLaunchKeystorePicker: () -> Unit,
  onLaunchExportApk: (String) -> Unit,
  onLaunchExportSelfApk: (String) -> Unit,
) {
  AnimatedContent(
    targetState = activeTab,
    transitionSpec = { fadeIn() togetherWith fadeOut() },
    label = "tab_switcher",
  ) { tab ->
    val context = LocalContext.current
    when (tab) {
      AppTab.APPS -> {
        AppsScreen(
          state = appsState,
          selfUpdateState = selfUpdateState,
          gitHubTokenInput = builderState.gitHubTokenInput,
          onGitHubTokenChange = viewModel::updateGitHubTokenInput,
          onToggleSelfInlineToken = viewModel::toggleSelfInlineToken,
          onToggleSelfConfig = viewModel::toggleSelfUpdateConfig,
          onSelfRepoInputChange = viewModel::updateSelfRepoInput,
          onSaveSelfRepo = viewModel::saveSelfUpdateRepo,
          onToggleSelfCiYaml = viewModel::toggleSelfCiYaml,
          onCopySelfCiYaml = { _ -> viewModel.copyStarterCiWorkflow() },
          starterCiYaml = viewModel.getStarterCiWorkflowYaml(),
          onRunSelfUpdate = viewModel::runSelfUpdateResilient,
          onOpenBuiltInShell = { viewModel.openBuiltInShell("probe") },
          onToggleSelfExternalTermux = viewModel::toggleSelfExternalTermux,
          onRunSelfInTermux = onRunSelfInTermux,
          onInstallTermux = {
            runCatching { context.startActivity(viewModel.createInstallTermuxIntent()) }
          },
          onRequestTermuxPermission = onRequestTermuxPermission,
          onCopySelfTermuxSetup = { viewModel.copyTermuxSetupCommand(forSelfUpdate = true) },
          onOpenSelfCloudBuild = viewModel::openSelfRepoInBuilderCloud,
          onExportSelfApk = onLaunchExportSelfApk,
          onShareSelfApk = { path ->
            shareApkIntent(context, viewModel.buildShareApkIntent(path))
          },
          onInspectSelfApkInSigner = viewModel::sendBuiltApkToSigner,
          onToggleAddForm = viewModel::toggleAddWatchedAppForm,
          onAddRepoInputChange = viewModel::updateAddWatchedRepoInput,
          onAddWatchedApp = { viewModel.addWatchedApp() },
          onToggleAutoCheckOnOpen = viewModel::toggleAutoCheckOnAppOpen,
          onRequestNotifications = onRequestNotifications,
          onToggleBatteryInfo = viewModel::toggleBatteryInfoExpanded,
          onCheckAll = {
            if (!appsState.canPostNotifications) onRequestNotifications()
            viewModel.checkAllWatchedApps(notifyOnNewUpdates = true, isAutoCheck = false)
          },
          onCheckSingle = { id -> viewModel.checkSingleWatchedApp(id, notifyOnUpdate = true) },
          onMarkSeen = viewModel::markWatchedAppSeen,
          onImportOrOpen = viewModel::importOrOpenWatchedApp,
          onDownloadApk = viewModel::downloadWatchedAppReleaseApk,
          onRemoveApp = viewModel::removeWatchedApp,
        )
      }
      AppTab.BUILD -> {
        BuilderScreen(
          state = builderState,
          onToggleGitHubForm = viewModel::toggleGitHubForm,
          onGitHubRepoChange = viewModel::updateGitHubRepoInput,
          onToggleTokenField = viewModel::toggleTokenField,
          onGitHubTokenChange = viewModel::updateGitHubTokenInput,
          onImportFromGitHub = viewModel::importFromGitHub,
          onSearchGitHub = viewModel::searchGitHubRepositories,
          onToggleAndroidFilter = viewModel::toggleGitHubSearchAndroidFilter,
          onOpenBrowserSearch = {
            launchUrlInBrowser(context, viewModel.buildGitHubBrowserSearchUrl())
          },
          onImportSearchResult = viewModel::selectSearchRepoToImport,
          onWatchSearchResult = viewModel::watchSearchRepo,
          onOpenRepoUrl = { url -> launchUrlInBrowser(context, url) },
          onCancelImport = viewModel::cancelGitHubImport,
          onCheckGitHubUpdates = onCheckGitHubUpdates,
          onPullLatestGitHub = viewModel::pullLatestFromGitHub,
          onDownloadReleaseApk = viewModel::downloadLatestReleaseApkToSigner,
          onPickZip = onLaunchZipPicker,
          onPickFolder = onLaunchFolderPicker,
          onCreateStarter = viewModel::createStarterProject,
          onToggleMoreSources = viewModel::toggleMoreSources,
          onToggleProjectSwitcher = viewModel::toggleProjectSwitcher,
          onSelectWorkspace = viewModel::selectWorkspace,
          onDeleteWorkspace = viewModel::deleteWorkspace,
          onToggleToolchain = viewModel::toggleToolchainExpanded,
          onRefreshToolchain = viewModel::refreshEnvironment,
          onToggleFiles = viewModel::toggleBuilderFiles,
          onOpenOrCloseFile = viewModel::openOrCloseFile,
          onEditingFileTextChange = viewModel::updateEditingFileText,
          onSaveFile = viewModel::saveExpandedFile,
          onSetBuildMode = viewModel::setBuildMode,
          onToggleGradleOnlineMode = viewModel::toggleGradleOnlineMode,
          onRefreshActions = viewModel::refreshGitHubActions,
          onTriggerWorkflow = viewModel::triggerGitHubWorkflow,
          onInstallWorkflow = viewModel::installTermforgeWorkflowOnGitHub,
          onFetchRunArtifacts = viewModel::fetchRunArtifacts,
          onDownloadArtifact = viewModel::downloadWorkflowArtifactApk,
          onRunOfflineBuild = viewModel::runPrimaryBuildAction,
          onToggleBuildPanel = viewModel::toggleBuildPanelExpanded,
          onSendApkToSigner = viewModel::sendBuiltApkToSigner,
          onExportZip = onLaunchExportZip,
          onCopyScript = viewModel::copyGeneratedScriptToClipboard,
          onOpenBuiltInShell = { viewModel.openBuiltInShell() },
          onToggleExternalTermux = viewModel::toggleBuilderExternalTermux,
          onRunInTermux = onRunWorkspaceInTermux,
          onInstallTermux = {
            runCatching { context.startActivity(viewModel.createInstallTermuxIntent()) }
          },
          onRequestTermuxPermission = onRequestTermuxPermission,
          onCopyTermuxSetup = { viewModel.copyTermuxSetupCommand(forSelfUpdate = false) },
          onCreateTermuxIntent = viewModel::createTermuxLaunchIntent,
        )
      }
      AppTab.SHELL -> {
        ShellScreen(
          state = shellState,
          onCommandInputChange = viewModel::updateShellCommandInput,
          onRunCommand = viewModel::runShellCommand,
          onStopCommand = viewModel::stopShellCommand,
          onToggleDiagnostics = viewModel::toggleShellDiagnostics,
          onRefreshProbe = { viewModel.runShellCommand("probe") },
          onRecallPreviousCommand = viewModel::recallPreviousShellCommand,
          onOpenCloudBuild = {
            if (builderState.activeGitHubMeta != null) {
              viewModel.setBuildMode(BuildMode.CLOUD_GITHUB_ACTIONS)
            }
            viewModel.selectTab(AppTab.BUILD)
          },
        )
      }
      AppTab.SIGN -> {
        SignScreen(
          state = signState,
          onPickApk = onLaunchApkPicker,
          onSelectApk = viewModel::selectApkFile,
          onPickKeystore = onLaunchKeystorePicker,
          onSelectKeystore = viewModel::selectKeystore,
          onKeystorePasswordChange = viewModel::updateKeystorePassword,
          onKeyAliasChange = viewModel::updateKeyAliasInput,
          onUnlockKeystore = viewModel::unlockKeystoreAliases,
          onToggleCreateKeystore = viewModel::toggleCreateKeystore,
          onNewKeyFieldsChange = viewModel::updateNewKeyFields,
          onGenerateKeystore = viewModel::generateNewKeystore,
          onSignApk = viewModel::signSelectedApk,
          onExportSignedApk = onLaunchExportApk,
        )
      }
    }
  }
}

private fun dispatchTermuxRunIntent(
  context: Context,
  runIntent: Intent?,
  fallbackLaunch: Intent?,
  onResult: (dispatched: Boolean, errorDetail: String?) -> Unit,
) {
  var errorDetail: String? = null
  val dispatched =
    if (runIntent != null) {
      val direct =
        runCatching {
          context.startService(runIntent)
          true
        }.getOrElse { err ->
          errorDetail = err.message ?: err::class.java.simpleName
          false
        }
      if (direct) {
        true
      } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        runCatching {
          context.startForegroundService(runIntent)
          true
        }.getOrElse { err ->
          errorDetail = err.message ?: err::class.java.simpleName
          false
        }
      } else {
        false
      }
    } else {
      errorDetail = "No build target configured"
      false
    }
  if (!dispatched) {
    fallbackLaunch?.let { launch ->
      runCatching { context.startActivity(launch) }
    }
  }
  onResult(dispatched, errorDetail)
}

private fun shareApkIntent(context: Context, shareIntent: Intent?) {
  if (shareIntent == null) return
  runCatching {
    context.startActivity(Intent.createChooser(shareIntent, "Share Update APK"))
  }
}

private fun launchUrlInBrowser(context: Context, url: String) {
  runCatching {
    val intent =
      Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    context.startActivity(intent)
  }
}
