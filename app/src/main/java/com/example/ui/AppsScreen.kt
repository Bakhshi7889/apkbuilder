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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.WatchedAppEntry
import com.example.ui.theme.JetBrainsMono

import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.SystemUpdate
import com.example.data.BuildOutcomeStatus

private val AppsMorphSpring =
  spring<IntSize>(
    dampingRatio = 0.8f,
    stiffness = Spring.StiffnessMediumLow,
  )

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppsScreen(
  state: AppsUiState,
  selfUpdateState: SelfUpdateUiState,
  onToggleSelfConfig: () -> Unit,
  onSelfRepoInputChange: (String) -> Unit,
  onSaveSelfRepo: () -> Unit,
  onToggleSelfCiYaml: () -> Unit,
  onCopySelfCiYaml: (String) -> Unit,
  starterCiYaml: String,
  onRunSelfUpdate: () -> Unit,
  onRunSelfInTermux: () -> Unit,
  onOpenSelfCloudBuild: () -> Unit,
  onExportSelfApk: (String) -> Unit,
  onShareSelfApk: (String) -> Unit,
  onInspectSelfApkInSigner: (String) -> Unit,
  onToggleAddForm: () -> Unit,
  onAddRepoInputChange: (String) -> Unit,
  onAddWatchedApp: () -> Unit,
  onToggleAutoCheckOnOpen: () -> Unit,
  onRequestNotifications: () -> Unit,
  onToggleBatteryInfo: () -> Unit,
  onCheckAll: () -> Unit,
  onCheckSingle: (String) -> Unit,
  onMarkSeen: (String) -> Unit,
  onImportOrOpen: (WatchedAppEntry) -> Unit,
  onDownloadApk: (WatchedAppEntry) -> Unit,
  onRemoveApp: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Column(modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp).weight(1f)) {
      Spacer(modifier = Modifier.height(8.dp))

      LazyColumn(
        modifier = Modifier.fillMaxWidth().weight(1f).testTag("apps_lazy_column"),
        contentPadding = PaddingValues(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        item(key = "self_update_card") {
          SelfUpdateCard(
            state = selfUpdateState,
            starterCiYaml = starterCiYaml,
            onToggleConfig = onToggleSelfConfig,
            onRepoInputChange = onSelfRepoInputChange,
            onSaveRepo = onSaveSelfRepo,
            onToggleCiYaml = onToggleSelfCiYaml,
            onCopyCiYaml = { onCopySelfCiYaml(starterCiYaml) },
            onRunSelfUpdate = onRunSelfUpdate,
            onRunInTermux = onRunSelfInTermux,
            onOpenCloudBuild = onOpenSelfCloudBuild,
            onExportApk = onExportSelfApk,
            onShareApk = onShareSelfApk,
            onInspectInSigner = onInspectSelfApkInSigner,
          )
        }

        item(key = "apps_summary_header") {
          WatchlistSummaryCard(
            state = state,
            onToggleAutoCheckOnOpen = onToggleAutoCheckOnOpen,
            onRequestNotifications = onRequestNotifications,
            onToggleBatteryInfo = onToggleBatteryInfo,
          )
        }

        state.statusMessage?.let { msg ->
          item(key = "apps_status_banner") {
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

        if (state.watchedApps.isEmpty()) {
          item(key = "apps_empty") {
            Surface(
              shape = RoundedCornerShape(24.dp),
              color = MaterialTheme.colorScheme.surfaceContainer,
              shadowElevation = 2.dp,
              modifier = Modifier.fillMaxWidth().testTag("apps_empty_card"),
            ) {
              Column(
                modifier = Modifier.fillMaxWidth().padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Icon(
                  imageVector = Icons.Filled.NotificationsActive,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(30.dp),
                )
                Text(
                  text = stringResource(R.string.apps_empty_title),
                  style = MaterialTheme.typography.titleLarge,
                  color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                  text = stringResource(R.string.apps_empty_subtitle),
                  style = MaterialTheme.typography.bodyLarge,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        } else {
          items(state.watchedApps, key = { it.id }) { entry ->
            WatchedAppCard(
              entry = entry,
              isCheckingThis = state.checkingAppId == entry.id || state.isCheckingAll,
              onCheckSingle = { onCheckSingle(entry.id) },
              onMarkSeen = { onMarkSeen(entry.id) },
              onImportOrOpen = { onImportOrOpen(entry) },
              onDownloadApk = { onDownloadApk(entry) },
              onRemove = { onRemoveApp(entry.id) },
            )
          }
        }
      }

      // Bottom Thumb-Zone Control Card (morphs inline to add any GitHub repo)
      Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 4.dp,
        modifier =
          Modifier.fillMaxWidth()
            .padding(bottom = 12.dp)
            .animateContentSize(animationSpec = AppsMorphSpring),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onToggleAddForm)
                .padding(horizontal = 10.dp, vertical = 10.dp)
                .testTag("apps_add_toggle_button"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = stringResource(R.string.apps_add_button),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
            Icon(
              imageVector =
                if (state.isAddFormExpanded) Icons.Filled.ExpandLess
                else Icons.Filled.ExpandMore,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          AnimatedVisibility(
            visible = state.isAddFormExpanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
          ) {
            Column(
              modifier = Modifier.fillMaxWidth(),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              MonoInputField(
                value = state.addRepoInput,
                onValueChange = onAddRepoInputChange,
                hint = stringResource(R.string.apps_add_hint),
                testTag = "apps_add_input",
              )
              Button(
                onClick = onAddWatchedApp,
                enabled = !state.isAddingApp,
                shape = RoundedCornerShape(14.dp),
                modifier =
                  Modifier.fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("apps_add_submit_button"),
              ) {
                if (state.isAddingApp) {
                  CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = "Adding App…",
                    style = MaterialTheme.typography.labelLarge,
                  )
                } else {
                  Icon(
                    imageVector = Icons.Filled.NotificationsActive,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = stringResource(R.string.apps_add_submit),
                    style = MaterialTheme.typography.labelLarge,
                  )
                }
              }
            }
          }

          val primaryAction =
            if (state.watchedApps.isEmpty()) onToggleAddForm else onCheckAll
          val primaryLabel =
            when {
              state.isCheckingAll -> "Checking GitHub for All Apps…"
              state.watchedApps.isEmpty() -> stringResource(R.string.apps_add_button)
              else -> stringResource(R.string.apps_check_all)
            }
          Button(
            onClick = primaryAction,
            enabled = !state.isCheckingAll,
            shape = RoundedCornerShape(16.dp),
            modifier =
              Modifier.fillMaxWidth()
                .heightIn(min = 52.dp)
                .testTag("apps_check_all_button"),
          ) {
            if (state.isCheckingAll) {
              CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
              )
            } else {
              Icon(
                imageVector =
                  if (state.watchedApps.isEmpty()) Icons.Filled.Add
                  else Icons.Filled.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = primaryLabel,
              style = MaterialTheme.typography.labelLarge,
            )
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WatchlistSummaryCard(
  state: AppsUiState,
  onToggleAutoCheckOnOpen: () -> Unit,
  onRequestNotifications: () -> Unit,
  onToggleBatteryInfo: () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(24.dp),
    color = MaterialTheme.colorScheme.surfaceContainer,
    shadowElevation = 3.dp,
    modifier =
      Modifier.fillMaxWidth()
        .animateContentSize(animationSpec = AppsMorphSpring)
        .testTag("apps_summary_card"),
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
            text = "GitHub App Updates",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
          )
          val countText =
            when {
              state.watchedApps.isEmpty() -> "No apps tracked yet"
              state.updatesAvailableCount > 0 ->
                "${state.watchedApps.size} watched • ${state.updatesAvailableCount} update${if (state.updatesAvailableCount == 1) "" else "s"} ready"
              else ->
                "${state.watchedApps.size} watched • Last check: ${formatRelativeTime(state.lastCheckAllAtMs)}"
            }
          Text(
            text = countText,
            style = MaterialTheme.typography.bodySmall,
            color =
              if (state.updatesAvailableCount > 0) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        if (state.updatesAvailableCount > 0) {
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
          ) {
            Text(
              text = "${state.updatesAvailableCount} New",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
          }
        }
      }

      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color =
            if (state.autoCheckOnAppOpen) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
          modifier =
            Modifier.heightIn(min = 48.dp)
              .clip(RoundedCornerShape(12.dp))
              .clickable(onClick = onToggleAutoCheckOnOpen)
              .testTag("apps_auto_check_chip"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = Icons.Filled.BatteryChargingFull,
              contentDescription = null,
              tint =
                if (state.autoCheckOnAppOpen) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text =
                if (state.autoCheckOnAppOpen) "Check on App Open: ON"
                else "Check on App Open: OFF",
              style = MaterialTheme.typography.labelMedium,
              color =
                if (state.autoCheckOnAppOpen) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        Surface(
          shape = RoundedCornerShape(12.dp),
          color =
            if (state.canPostNotifications) MaterialTheme.colorScheme.surfaceContainerHigh
            else MaterialTheme.colorScheme.primaryContainer,
          modifier =
            Modifier.heightIn(min = 48.dp)
              .clip(RoundedCornerShape(12.dp))
              .clickable(onClick = onRequestNotifications)
              .testTag("apps_notifications_chip"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector =
                if (state.canPostNotifications) Icons.Filled.Notifications
                else Icons.Filled.NotificationsOff,
              contentDescription = null,
              tint =
                if (state.canPostNotifications) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text =
                if (state.canPostNotifications) "Alerts Enabled"
                else "Enable Alerts",
              style = MaterialTheme.typography.labelMedium,
              color =
                if (state.canPostNotifications) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onPrimaryContainer,
            )
          }
        }
      }

      Row(
        modifier =
          Modifier.fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onToggleBatteryInfo)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = "How zero-battery checking works",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
          imageVector =
            if (state.isBatteryInfoExpanded) Icons.Filled.ExpandLess
            else Icons.Filled.ExpandMore,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      AnimatedVisibility(
        visible = state.isBatteryInfoExpanded,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
      ) {
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = MaterialTheme.colorScheme.surfaceContainerLow,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text =
              "Background timers wake the radio and drain battery. Instead, APK Builder checks all your watched GitHub apps in parallel only when you open the app (60s cooldown so quick switches don't waste data or hit GitHub's 60 req/hr limit), then fires one grouped notification if anything changed.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(12.dp),
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WatchedAppCard(
  entry: WatchedAppEntry,
  isCheckingThis: Boolean,
  onCheckSingle: () -> Unit,
  onMarkSeen: () -> Unit,
  onImportOrOpen: () -> Unit,
  onDownloadApk: () -> Unit,
  onRemove: () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(22.dp),
    color = MaterialTheme.colorScheme.surfaceContainer,
    shadowElevation = if (entry.hasUpdate) 3.dp else 2.dp,
    modifier =
      Modifier.fillMaxWidth()
        .animateContentSize(animationSpec = AppsMorphSpring)
        .testTag("watched_app_card_${entry.id}"),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = entry.fullName,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = "${entry.branch} • Checked ${formatRelativeTime(entry.lastCheckedAtMs)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }

        Spacer(modifier = Modifier.width(8.dp))

        val (badgeBg, badgeFg, badgeLabel) =
          when {
            isCheckingThis ->
              Triple(
                MaterialTheme.colorScheme.surfaceContainerHigh,
                MaterialTheme.colorScheme.onSurfaceVariant,
                "Checking…",
              )
            entry.lastError != null ->
              Triple(
                MaterialTheme.colorScheme.errorContainer,
                MaterialTheme.colorScheme.onErrorContainer,
                "Check Failed",
              )
            entry.hasUpdate ->
              Triple(
                MaterialTheme.colorScheme.primaryContainer,
                MaterialTheme.colorScheme.onPrimaryContainer,
                "Update Ready",
              )
            else ->
              Triple(
                MaterialTheme.colorScheme.surfaceContainerHigh,
                MaterialTheme.colorScheme.onSurface,
                "Up to Date",
              )
          }

        Surface(
          shape = RoundedCornerShape(10.dp),
          color = badgeBg,
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector =
                when {
                  entry.lastError != null -> Icons.Filled.WarningAmber
                  entry.hasUpdate -> Icons.Filled.NotificationsActive
                  else -> Icons.Filled.CheckCircle
                },
              contentDescription = null,
              tint = badgeFg,
              modifier = Modifier.size(14.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = badgeLabel,
              style = MaterialTheme.typography.labelMedium,
              color = badgeFg,
            )
          }
        }
      }

      if (entry.lastError != null) {
        Text(
          text = entry.lastError,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      } else if (entry.hasUpdate) {
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = MaterialTheme.colorScheme.primaryContainer,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            if (entry.hasNewRelease) {
              Text(
                text = "New Release: ${entry.remoteReleaseTag} (${entry.remoteApkName ?: "APK attached"})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
              )
            }
            if (entry.hasNewCommit) {
              val msg = entry.remoteMessage?.takeIf { it.isNotBlank() } ?: "New commit pushed"
              Text(
                text = "${entry.baselineSha.take(7)} → ${entry.remoteSha?.take(7)}: $msg",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
      } else {
        val currentSha = (entry.remoteSha ?: entry.baselineSha).take(7)
        val summaryLine =
          buildString {
            append("Commit $currentSha")
            val tag = entry.remoteReleaseTag ?: entry.baselineReleaseTag
            if (!tag.isNullOrBlank()) append(" • Release $tag")
            val msg = (entry.remoteMessage ?: entry.baselineMessage).trim()
            if (msg.isNotBlank()) append(" — $msg")
          }
        Text(
          text = summaryLine,
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        FlowRow(
          modifier = Modifier.weight(1f),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          if (!entry.remoteApkUrl.isNullOrBlank() && !entry.remoteApkName.isNullOrBlank()) {
            val tag = entry.remoteReleaseTag?.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
            CompactActionChip(
              icon = Icons.Filled.VerifiedUser,
              label = "Get$tag APK",
              onClick = onDownloadApk,
            )
          }

          val buildLabel =
            when {
              entry.workspacePath != null && entry.hasUpdate -> "Pull & Build"
              entry.workspacePath != null -> "Open Build"
              else -> "Import & Build"
            }
          CompactActionChip(
            icon = if (entry.workspacePath != null) Icons.Filled.Build else Icons.Filled.CloudDownload,
            label = buildLabel,
            onClick = onImportOrOpen,
          )

          if (entry.hasUpdate) {
            CompactActionChip(
              icon = Icons.Filled.Check,
              label = "Mark Seen",
              onClick = onMarkSeen,
            )
          }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(
            onClick = { if (!isCheckingThis) onCheckSingle() },
            modifier = Modifier.minimumInteractiveComponentSize(),
          ) {
            Icon(
              imageVector = Icons.Filled.Refresh,
              contentDescription = "Check ${entry.fullName}",
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(20.dp),
            )
          }
          IconButton(
            onClick = onRemove,
            modifier = Modifier.minimumInteractiveComponentSize(),
          ) {
            Icon(
              imageVector = Icons.Filled.DeleteOutline,
              contentDescription = "Stop watching ${entry.fullName}",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(20.dp),
            )
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelfUpdateCard(
  state: SelfUpdateUiState,
  starterCiYaml: String,
  onToggleConfig: () -> Unit,
  onRepoInputChange: (String) -> Unit,
  onSaveRepo: () -> Unit,
  onToggleCiYaml: () -> Unit,
  onCopyCiYaml: () -> Unit,
  onRunSelfUpdate: () -> Unit,
  onRunInTermux: () -> Unit,
  onOpenCloudBuild: () -> Unit,
  onExportApk: (String) -> Unit,
  onShareApk: (String) -> Unit,
  onInspectInSigner: (String) -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(24.dp),
    color = MaterialTheme.colorScheme.surfaceContainer,
    shadowElevation = 3.dp,
    modifier =
      Modifier.fillMaxWidth()
        .animateContentSize(animationSpec = AppsMorphSpring)
        .testTag("self_update_card"),
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
            text = stringResource(R.string.self_update_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text = "v${state.config.installedVersionName} (${state.config.installedVersionCode}) • ${state.config.installedAppId}",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = MaterialTheme.colorScheme.primaryContainer,
        ) {
          Text(
            text = if (state.config.repoSlug.isNotBlank()) "Linked" else "Setup",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
          )
        }
      }

      val certText =
        state.config.installedCertSha256?.take(16)?.let { "Cert $it…" } ?: "Cert unavailable"
      val repoText =
        if (state.config.repoSlug.isNotBlank()) {
          "Source: ${state.config.repoSlug} (${state.config.branch}) • $certText"
        } else {
          "Link the GitHub repo where this APK is built so APK Builder can update itself."
        }
      Text(
        text = repoText,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )

      Row(
        modifier =
          Modifier.fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onToggleConfig)
            .padding(horizontal = 6.dp)
            .testTag("self_repo_toggle"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = stringResource(R.string.self_update_configure),
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Icon(
          imageVector =
            if (state.isConfigExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      AnimatedVisibility(
        visible = state.isConfigExpanded,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          MonoInputField(
            value = state.repoInput,
            onValueChange = onRepoInputChange,
            hint = stringResource(R.string.apps_add_hint),
            testTag = "self_repo_input",
          )
          Button(
            onClick = onSaveRepo,
            shape = RoundedCornerShape(14.dp),
            modifier =
              Modifier.fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("self_repo_save_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.Check,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Save Self-Update Repo",
              style = MaterialTheme.typography.labelLarge,
            )
          }
        }
      }

      Button(
        onClick = onRunSelfUpdate,
        enabled = !state.isRunningSelfUpdate,
        shape = RoundedCornerShape(16.dp),
        modifier =
          Modifier.fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag("self_update_run_button"),
      ) {
        if (state.isRunningSelfUpdate) {
          CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onPrimary,
          )
        } else {
          Icon(
            imageVector = Icons.Filled.SystemUpdate,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text =
            if (state.isRunningSelfUpdate) "Running Self-Update Pipeline…"
            else stringResource(R.string.self_update_run),
          style = MaterialTheme.typography.labelLarge,
        )
      }

      state.pipelineStep?.let { step ->
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = MaterialTheme.colorScheme.surfaceContainerLow,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = step,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(12.dp),
          )
        }
      }

      state.statusMessage?.let { msg ->
        Surface(
          shape = RoundedCornerShape(14.dp),
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
            modifier = Modifier.padding(12.dp),
          )
        }
      }

      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        if (state.stagedWorkspacePath != null && state.termuxInstalled) {
          CompactActionChip(
            icon = Icons.Filled.Terminal,
            label = stringResource(R.string.termux_run_build),
            onClick = onRunInTermux,
          )
        }
        if (state.config.repoSlug.isNotBlank()) {
          CompactActionChip(
            icon = Icons.Filled.CloudQueue,
            label = "Cloud CI Fallback",
            onClick = onOpenCloudBuild,
          )
        }
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.surfaceContainerHigh,
          modifier =
            Modifier.heightIn(min = 48.dp)
              .clip(RoundedCornerShape(12.dp))
              .clickable(onClick = onToggleCiYaml)
              .testTag("self_ci_yaml_toggle"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = Icons.Filled.Build,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = stringResource(R.string.self_update_ci_yaml),
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
        }
      }

      AnimatedVisibility(
        visible = state.isCiYamlExpanded,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
      ) {
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = MaterialTheme.colorScheme.surfaceContainerLow,
          modifier = Modifier.fillMaxWidth().testTag("self_ci_yaml_preview"),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              text = ".github/workflows/termforge-android.yml",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.primary,
            )
            Text(
              text = starterCiYaml.trimEnd(),
              style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CompactActionChip(
              icon = Icons.Filled.ContentCopy,
              label = "Copy Workflow YAML",
              onClick = onCopyCiYaml,
            )
          }
        }
      }

      state.candidateApk?.let { apk ->
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = MaterialTheme.colorScheme.surfaceContainerLow,
          modifier = Modifier.fillMaxWidth().testTag("self_candidate_apk_card"),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              text = "Ready: ${apk.apkName} (${formatByteCount(apk.sizeBytes)})",
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurface,
            )
            val sigMsg =
              when (state.signatureMatchesInstalled) {
                true -> "Signature matches installed app — ready to install update."
                false -> "Different signing key than installed app — re-sign in Sign tab before updating."
                null -> "Could not compare installed app certificate."
              }
            Text(
              text = sigMsg,
              style = MaterialTheme.typography.bodySmall,
              color =
                if (state.signatureMatchesInstalled == true) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              CompactActionChip(
                icon = Icons.Filled.Archive,
                label = "Export APK",
                onClick = { onExportApk(apk.apkPath) },
              )
              CompactActionChip(
                icon = Icons.Filled.Share,
                label = "Share APK",
                onClick = { onShareApk(apk.apkPath) },
              )
              CompactActionChip(
                icon = Icons.Filled.VerifiedUser,
                label = "Inspect / Sign",
                onClick = { onInspectInSigner(apk.apkPath) },
              )
            }
          }
        }
      }
    }
  }
}
