package com.example.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.BuildConfig
import java.io.File

data class SelfUpdateConfig(
  val repoSlug: String,
  val branch: String,
  val preferOnDeviceGradle: Boolean,
  val installedAppId: String,
  val installedVersionName: String,
  val installedVersionCode: Int,
  val installedCertSha256: String?,
)

class SelfUpdateRepository(
  private val context: Context,
  private val signingService: ApkSigningService,
) {
  private val prefs = context.getSharedPreferences("termforge_self_update", Context.MODE_PRIVATE)

  fun loadConfig(): SelfUpdateConfig {
    return SelfUpdateConfig(
      repoSlug = prefs.getString(KEY_REPO_SLUG, "") ?: "",
      branch = prefs.getString(KEY_BRANCH, "main")?.ifBlank { "main" } ?: "main",
      preferOnDeviceGradle = prefs.getBoolean(KEY_PREFER_ON_DEVICE, true),
      installedAppId = BuildConfig.APPLICATION_ID,
      installedVersionName = BuildConfig.VERSION_NAME,
      installedVersionCode = BuildConfig.VERSION_CODE,
      installedCertSha256 = signingService.getInstalledAppCertSha256(),
    )
  }

  fun saveRepo(repoSlug: String, branch: String = "main") {
    prefs
      .edit()
      .putString(KEY_REPO_SLUG, repoSlug.trim())
      .putString(KEY_BRANCH, branch.trim().ifBlank { "main" })
      .apply()
  }

  fun setPreferOnDeviceGradle(prefer: Boolean) {
    prefs.edit().putBoolean(KEY_PREFER_ON_DEVICE, prefer).apply()
  }

  fun clearRepo() {
    prefs.edit().remove(KEY_REPO_SLUG).remove(KEY_BRANCH).apply()
  }

  fun buildTermuxRunCommandIntent(
    workspaceDir: File,
    scriptFileName: String = "build_offline.sh",
  ): Intent {
    val scriptPath = File(workspaceDir, scriptFileName).absolutePath
    return Intent("com.termux.RUN_COMMAND").apply {
      component = ComponentName("com.termux", "com.termux.app.RunCommandService")
      putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/sh")
      putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf(scriptPath))
      putExtra("com.termux.RUN_COMMAND_WORKDIR", workspaceDir.absolutePath)
      putExtra("com.termux.RUN_COMMAND_BACKGROUND", false)
      putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0")
    }
  }

  fun buildOneLineTermuxShellCommand(
    workspaceName: String,
    zipExportName: String = "$workspaceName.zip",
    scriptFileName: String = "build_offline.sh",
  ): String {
    return """
      mkdir -p ~/termforge_builds/$workspaceName && cd ~/termforge_builds/$workspaceName && unzip -o ~/storage/downloads/$zipExportName && sh ./$scriptFileName
    """.trimIndent()
  }

  fun buildShareApkIntent(apkFile: File): Intent {
    val uri =
      FileProvider.getUriForFile(
        context,
        "${BuildConfig.APPLICATION_ID}.fileprovider",
        apkFile,
      )
    return Intent(Intent.ACTION_SEND).apply {
      type = "application/vnd.android.package-archive"
      putExtra(Intent.EXTRA_STREAM, uri)
      putExtra(Intent.EXTRA_SUBJECT, "${apkFile.name} update APK")
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
  }

  fun defaultGitHubActionsWorkflowYaml(): String =
    """
    name: APK Builder Android APK

    on:
      workflow_dispatch:
      push:
        branches: [ main, master ]

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

  companion object {
    private const val KEY_REPO_SLUG = "self_repo_slug"
    private const val KEY_BRANCH = "self_repo_branch"
    private const val KEY_PREFER_ON_DEVICE = "prefer_on_device_gradle"
  }
}
