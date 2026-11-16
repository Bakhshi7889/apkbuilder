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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.KeystoreFileInfo
import com.example.ui.theme.JetBrainsMono
import java.io.File

private val SignMorphSpring =
  spring<androidx.compose.ui.unit.IntSize>(
    dampingRatio = 0.8f,
    stiffness = Spring.StiffnessMediumLow,
  )

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SignScreen(
  state: SignUiState,
  onPickApk: () -> Unit,
  onSelectApk: (File) -> Unit,
  onPickKeystore: () -> Unit,
  onSelectKeystore: (KeystoreFileInfo) -> Unit,
  onKeystorePasswordChange: (String) -> Unit,
  onKeyAliasChange: (String) -> Unit,
  onUnlockKeystore: () -> Unit,
  onToggleCreateKeystore: () -> Unit,
  onNewKeyFieldsChange: (String?, String?, String?, String?, String?) -> Unit,
  onGenerateKeystore: () -> Unit,
  onSignApk: () -> Unit,
  onExportSignedApk: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Column(modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp).weight(1f)) {
      Spacer(modifier = Modifier.height(8.dp))

      LazyColumn(
        modifier = Modifier.fillMaxWidth().weight(1f),
        contentPadding = PaddingValues(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        // Status Banner
        state.statusMessage?.let { msg ->
          item(key = "sign_status") {
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

        // 1. Select & Verify APK Card
        item(key = "select_apk_card") {
          Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            shadowElevation = 3.dp,
            modifier =
              Modifier.fillMaxWidth()
                .animateContentSize(animationSpec = SignMorphSpring)
                .testTag("sign_apk_card"),
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
                Text(
                  text = stringResource(R.string.sign_select_apk),
                  style = MaterialTheme.typography.titleLarge,
                  color = MaterialTheme.colorScheme.onSurface,
                )
                CompactActionChip(
                  icon = Icons.Filled.UploadFile,
                  label = stringResource(R.string.sign_pick_apk),
                  onClick = onPickApk,
                )
              }

              if (state.availableApks.isNotEmpty()) {
                FlowRow(
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  state.availableApks.take(6).forEach { apk ->
                    val isSelected = state.selectedApk?.apkPath == apk.absolutePath
                    Surface(
                      shape = RoundedCornerShape(12.dp),
                      color =
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                      modifier =
                        Modifier.heightIn(min = 48.dp)
                          .clip(RoundedCornerShape(12.dp))
                          .clickable { onSelectApk(apk) },
                    ) {
                      Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                      ) {
                        Icon(
                          imageVector = Icons.Filled.Android,
                          contentDescription = null,
                          tint =
                            if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.primary,
                          modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                          text = apk.name,
                          style = MaterialTheme.typography.labelMedium,
                          color =
                            if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis,
                        )
                      }
                    }
                  }
                }
              }

              val apk = state.selectedApk
              if (apk == null) {
                Text(
                  text = "No APK selected. Pick an .apk file or download one from a GitHub Release.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              } else {
                Surface(
                  shape = RoundedCornerShape(16.dp),
                  color = MaterialTheme.colorScheme.surfaceContainerLow,
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                  ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Icon(
                        imageVector =
                          if (apk.isVerified) Icons.Filled.CheckCircle
                          else Icons.Filled.WarningAmber,
                        contentDescription = null,
                        tint =
                          if (apk.isVerified) MaterialTheme.colorScheme.primary
                          else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                      )
                      Spacer(modifier = Modifier.width(8.dp))
                      Text(
                        text =
                          "${apk.apkName} • ${formatByteCount(apk.sizeBytes)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                    }
                    Text(
                      text =
                        if (apk.isVerified) {
                          "Verified: v1=${apk.verifiedV1}  v2=${apk.verifiedV2}  v3=${apk.verifiedV3}"
                        } else {
                          "Signature: ${apk.errorMessage ?: "Unsigned"}"
                        },
                      style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                      color =
                        if (apk.isVerified) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    apk.signerSha256?.let { sha ->
                      Text(
                        text = "SHA-256: $sha",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }
                    state.selectedApkMatchesInstalled?.let { matches ->
                      Surface(
                        shape = RoundedCornerShape(10.dp),
                        color =
                          if (matches) MaterialTheme.colorScheme.primaryContainer
                          else MaterialTheme.colorScheme.surfaceContainerHigh,
                      ) {
                        Text(
                          text =
                            if (matches) "Matches this app's signing key — upgrade compatible"
                            else "Differs from this app's signing key — re-sign with matching keystore to update",
                          style = MaterialTheme.typography.bodySmall,
                          color =
                            if (matches) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                          modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                      }
                    }
                  }
                }
              }
            }
          }
        }

        // 2. Keystore Card (Select / Import / Morphing RSA-2048 Keystore Generator)
        item(key = "keystore_card") {
          Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            shadowElevation = 2.dp,
            modifier =
              Modifier.fillMaxWidth()
                .animateContentSize(animationSpec = SignMorphSpring)
                .testTag("sign_keystore_card"),
          ) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Text(
                text = stringResource(R.string.sign_keystore_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
              )

              FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                CompactActionChip(
                  icon = Icons.Filled.Add,
                  label = stringResource(R.string.sign_new_keystore),
                  onClick = onToggleCreateKeystore,
                )
                CompactActionChip(
                  icon = Icons.Filled.UploadFile,
                  label = stringResource(R.string.sign_import_keystore),
                  onClick = onPickKeystore,
                )
              }

              AnimatedVisibility(
                visible = state.isCreateKeystoreExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
              ) {
                Surface(
                  shape = RoundedCornerShape(16.dp),
                  color = MaterialTheme.colorScheme.surfaceContainerLow,
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    MonoInputField(
                      value = state.newKeyFileName,
                      onValueChange = { onNewKeyFieldsChange(it, null, null, null, null) },
                      hint = "Keystore file name (e.g. release_key)",
                      testTag = "new_ks_filename_input",
                    )
                    MonoInputField(
                      value = state.newKeyAlias,
                      onValueChange = { onNewKeyFieldsChange(null, it, null, null, null) },
                      hint = "Key alias (e.g. upload)",
                      testTag = "new_ks_alias_input",
                    )
                    MonoInputField(
                      value = state.newKeyCommonName,
                      onValueChange = { onNewKeyFieldsChange(null, null, it, null, null) },
                      hint = "Common Name (CN)",
                      testTag = "new_ks_cn_input",
                    )
                    MonoInputField(
                      value = state.newKeyOrg,
                      onValueChange = { onNewKeyFieldsChange(null, null, null, it, null) },
                      hint = "Organization (O)",
                      testTag = "new_ks_org_input",
                    )
                    MonoInputField(
                      value = state.newKeyPassword,
                      onValueChange = { onNewKeyFieldsChange(null, null, null, null, it) },
                      hint = "Password (min 6 chars)",
                      isSecret = true,
                      testTag = "new_ks_password_input",
                    )
                    Button(
                      onClick = onGenerateKeystore,
                      enabled = !state.isGeneratingKeystore,
                      shape = RoundedCornerShape(14.dp),
                      modifier =
                        Modifier.fillMaxWidth()
                          .heightIn(min = 48.dp)
                          .testTag("generate_keystore_button"),
                    ) {
                      Text(
                        text =
                          if (state.isGeneratingKeystore) "Generating RSA-2048…"
                          else "Generate Keystore (.p12)",
                        style = MaterialTheme.typography.labelLarge,
                      )
                    }
                  }
                }
              }

              if (state.keystores.isNotEmpty()) {
                FlowRow(
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  state.keystores.forEach { ks ->
                    val selected = state.selectedKeystore?.absolutePath == ks.absolutePath
                    Surface(
                      shape = RoundedCornerShape(12.dp),
                      color =
                        if (selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                      modifier =
                        Modifier.heightIn(min = 48.dp)
                          .clip(RoundedCornerShape(12.dp))
                          .clickable { onSelectKeystore(ks) },
                    ) {
                      Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                      ) {
                        Icon(
                          imageVector = Icons.Filled.Key,
                          contentDescription = null,
                          tint =
                            if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.primary,
                          modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                          text = ks.name,
                          style = MaterialTheme.typography.labelMedium,
                          color =
                            if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface,
                        )
                      }
                    }
                  }
                }
              }

              if (state.selectedKeystore != null) {
                MonoInputField(
                  value = state.keystorePassword,
                  onValueChange = onKeystorePasswordChange,
                  hint = "Keystore password",
                  isSecret = true,
                  testTag = "sign_keystore_password_input",
                )
                MonoInputField(
                  value = state.keyAliasInput,
                  onValueChange = onKeyAliasChange,
                  hint = "Key alias (leave blank for first entry)",
                  testTag = "sign_keystore_alias_input",
                )
                CompactActionChip(
                  icon = Icons.Filled.LockOpen,
                  label = "Verify Password & Read Aliases",
                  onClick = onUnlockKeystore,
                )
              }
            }
          }
        }

        // 3. Signed Output Card
        state.signingResult?.let { result ->
          item(key = "signed_output") {
            Surface(
              shape = RoundedCornerShape(24.dp),
              color = MaterialTheme.colorScheme.surfaceContainer,
              shadowElevation = 3.dp,
              modifier = Modifier.fillMaxWidth().testTag("sign_result_card"),
            ) {
              Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Text(
                  text = "Signed: ${result.signedApkName}",
                  style = MaterialTheme.typography.titleMedium,
                  color = MaterialTheme.colorScheme.primary,
                )
                Text(
                  text =
                    "v1=${result.verification.verifiedV1}  v2=${result.verification.verifiedV2}  v3=${result.verification.verifiedV3} (${result.durationMs}ms)",
                  style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                  color = MaterialTheme.colorScheme.onSurface,
                )
                result.verification.signerSha256?.let { sha ->
                  Text(
                    text = "Cert SHA-256: $sha",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                CompactActionChip(
                  icon = Icons.Filled.Archive,
                  label = stringResource(R.string.sign_export_apk),
                  onClick = { onExportSignedApk(result.signedApkName) },
                )
              }
            }
          }
        }
      }

      // Bottom Thumb-Zone Single Primary Action
      Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
      ) {
        Box(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
          val canSign =
            state.selectedApk != null &&
              state.selectedKeystore != null &&
              state.keystorePassword.isNotEmpty() &&
              !state.isSigning
          Button(
            onClick = onSignApk,
            enabled = canSign,
            shape = RoundedCornerShape(16.dp),
            modifier =
              Modifier.fillMaxWidth()
                .heightIn(min = 52.dp)
                .testTag("sign_apk_action_button"),
          ) {
            if (state.isSigning) {
              CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
              )
            } else {
              Icon(
                imageVector = Icons.Filled.VerifiedUser,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text =
                if (state.isSigning) "Signing APK…"
                else stringResource(R.string.sign_action_button),
              style = MaterialTheme.typography.labelLarge,
            )
          }
        }
      }
    }
  }
}
