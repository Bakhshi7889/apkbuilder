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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.JetBrainsMono

private val ShellMorphSpring =
  spring<IntSize>(
    dampingRatio = 0.8f,
    stiffness = Spring.StiffnessMediumLow,
  )

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShellScreen(
  state: ShellUiState,
  onCommandInputChange: (String) -> Unit,
  onRunCommand: (String?) -> Unit,
  onStopCommand: () -> Unit,
  onToggleDiagnostics: () -> Unit,
  onRefreshProbe: () -> Unit,
  onRecallPreviousCommand: () -> Unit,
  onOpenCloudBuild: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val consoleListState = rememberLazyListState()

  LaunchedEffect(state.outputLines.size) {
    if (state.outputLines.isNotEmpty()) {
      consoleListState.animateScrollToItem(state.outputLines.lastIndex)
    }
  }

  Column(
    modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Column(modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp).weight(1f)) {
      Spacer(modifier = Modifier.height(8.dp))

      // Runtime Status & Diagnostics Header (morphs open on tap)
      Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 3.dp,
        modifier =
          Modifier.fillMaxWidth()
            .animateContentSize(animationSpec = ShellMorphSpring)
            .testTag("shell_header_card"),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onToggleDiagnostics)
                .padding(horizontal = 4.dp)
                .testTag("shell_diagnostics_toggle"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Filled.Terminal,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
              )
              Spacer(modifier = Modifier.width(10.dp))
              Column {
                Text(
                  text = stringResource(R.string.shell_title),
                  style = MaterialTheme.typography.titleMedium,
                  color = MaterialTheme.colorScheme.onSurface,
                )
                val env = state.envInfo
                val subtitle =
                  if (env != null) {
                    "${env.kernelName} ${env.machineArch} • ${env.cpuCores} cores • -Xmx${env.recommendedHeapMb}m"
                  } else {
                    "/system/bin/sh + Built-In APK/GitHub CLI"
                  }
                Text(
                  text = subtitle,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.primary,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
              if (state.lastExitCode != null) {
                Surface(
                  shape = RoundedCornerShape(8.dp),
                  color =
                    if (state.lastExitCode == 0) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.errorContainer,
                ) {
                  Text(
                    text = "exit ${state.lastExitCode}",
                    style = MaterialTheme.typography.labelMedium,
                    color =
                      if (state.lastExitCode == 0) MaterialTheme.colorScheme.onPrimaryContainer
                      else MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                  )
                }
                Spacer(modifier = Modifier.width(4.dp))
              }
              Icon(
                imageVector =
                  if (state.isDiagnosticsExpanded) Icons.Filled.ExpandLess
                  else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }

          AnimatedVisibility(
            visible = state.isDiagnosticsExpanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
          ) {
            Surface(
              shape = RoundedCornerShape(16.dp),
              color = MaterialTheme.colorScheme.surfaceContainerLow,
              modifier = Modifier.fillMaxWidth().testTag("shell_diagnostics_panel"),
            ) {
              Column(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                val env = state.envInfo
                if (env != null) {
                  Text(
                    text = "cwd: ${state.currentDir}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                    color = MaterialTheme.colorScheme.onSurface,
                  )
                  Text(
                    text =
                      "NATIVE_LIB_DIR (${env.bundledNativeBins.size} bundled): ${env.nativeLibDir.ifBlank { "unavailable" }}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  Text(
                    text =
                      "Android W^X (targetSdk 36): POSIX /system/bin/sh (${env.systemBinToolsCount} bins), bundled lib*.so CLIs, & in-app GitHub/APK CLI run natively inside this APK. Full Kotlin/Compose Gradle builds compile without any separate Termux app via 1-tap Cloud Actions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                FlowRow(
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  CompactActionChip(
                    icon = Icons.Filled.Refresh,
                    label = "Re-probe Runtime",
                    onClick = onRefreshProbe,
                  )
                  CompactActionChip(
                    icon = Icons.Filled.CloudQueue,
                    label = "Open Cloud CI",
                    onClick = onOpenCloudBuild,
                  )
                }
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Quick CLI & Shell Command Bar
      LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().testTag("shell_quick_commands"),
      ) {
        items(
          listOf(
            Triple("help", Icons.Filled.Terminal, "shell_chip_help"),
            Triple("probe", Icons.Filled.Build, "shell_chip_probe"),
            Triple("tools", Icons.Filled.Terminal, "shell_chip_tools"),
            Triple("apk-list", Icons.Filled.VerifiedUser, "shell_chip_apk_list"),
            Triple("uname -a", Icons.Filled.Terminal, "shell_chip_uname"),
            Triple("clear", Icons.Filled.ClearAll, "shell_chip_clear"),
          )
        ) { (cmd, icon, tag) ->
          CompactActionChip(
            icon = icon,
            label = cmd,
            onClick = { onRunCommand(cmd) },
            testTag = tag,
          )
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Monospace Streaming Console Surface
      Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth().weight(1f).testTag("shell_output_box"),
      ) {
        if (state.outputLines.isEmpty()) {
          Box(
            modifier = Modifier.fillMaxSize().padding(18.dp),
            contentAlignment = Alignment.CenterStart,
          ) {
            Text(
              text = "Type 'help' or tap a command above to run inside APK Builder's built-in shell.",
              style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        } else {
          LazyColumn(
            state = consoleListState,
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxSize(),
          ) {
            items(state.outputLines) { line ->
              val isPrompt = line.startsWith("$ ")
              val isError =
                line.startsWith("error:", ignoreCase = true) ||
                  line.startsWith("exec error:", ignoreCase = true)
              Text(
                text = line,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                fontWeight = if (isPrompt) FontWeight.SemiBold else FontWeight.Normal,
                color =
                  when {
                    isPrompt -> MaterialTheme.colorScheme.primary
                    isError -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface
                  },
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Bottom Thumb-Zone Command Bar (single primary Run/Stop button)
      Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Box(modifier = Modifier.weight(1f)) {
              MonoInputField(
                value = state.commandInput,
                onValueChange = onCommandInputChange,
                hint = stringResource(R.string.shell_input_hint),
                testTag = "shell_command_input",
              )
            }
            if (state.commandHistory.isNotEmpty()) {
              IconButton(
                onClick = onRecallPreviousCommand,
                modifier =
                  Modifier.minimumInteractiveComponentSize().testTag("shell_history_button"),
              ) {
                Icon(
                  imageVector = Icons.Filled.History,
                  contentDescription = "Recall previous command",
                  tint = MaterialTheme.colorScheme.primary,
                )
              }
            }
          }

          val isRunning = state.isRunning
          Button(
            onClick = { if (isRunning) onStopCommand() else onRunCommand(null) },
            colors =
              if (isRunning) {
                ButtonDefaults.buttonColors(
                  containerColor = MaterialTheme.colorScheme.errorContainer,
                  contentColor = MaterialTheme.colorScheme.onErrorContainer,
                )
              } else {
                ButtonDefaults.buttonColors()
              },
            shape = RoundedCornerShape(16.dp),
            modifier =
              Modifier.fillMaxWidth()
                .heightIn(min = 50.dp)
                .testTag("shell_run_button"),
          ) {
            if (isRunning) {
              CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onErrorContainer,
              )
              Spacer(modifier = Modifier.width(8.dp))
              Icon(
                imageVector = Icons.Filled.Stop,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = stringResource(R.string.shell_stop_button),
                style = MaterialTheme.typography.labelLarge,
              )
            } else {
              Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = stringResource(R.string.shell_run_button),
                style = MaterialTheme.typography.labelLarge,
              )
            }
          }
        }
      }
    }
  }
}
