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
    workspaceName: String,
    repoSlug: String? = null,
    branch: String = "main",
    onlineGradle: Boolean = true,
    cpuCores: Int = Runtime.getRuntime().availableProcessors().coerceAtLeast(1),
    heapMb: Int = 1920,
    localDownloadsZipName: String? = null,
  ): Intent {
    val script =
      buildTermuxBootstrapCommand(
        workspaceName = workspaceName,
        repoSlug = repoSlug,
        branch = branch,
        onlineGradle = onlineGradle,
        cpuCores = cpuCores,
        heapMb = heapMb,
        localDownloadsZipName = localDownloadsZipName,
      )
    return Intent("com.termux.RUN_COMMAND").apply {
      component = ComponentName(ShellEngine.TERMUX_PACKAGE, "com.termux.app.RunCommandService")
      putExtra("com.termux.RUN_COMMAND_PATH", "$TERMUX_BIN/bash")
      putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", script))
      putExtra("com.termux.RUN_COMMAND_WORKDIR", TERMUX_HOME)
      putExtra("com.termux.RUN_COMMAND_BACKGROUND", false)
      putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0")
    }
  }

  fun buildTermuxEnableExternalAppsOneLiner(
    workspaceName: String = "apkbuilder",
    repoSlug: String? = null,
    branch: String = "main",
    onlineGradle: Boolean = true,
    cpuCores: Int = Runtime.getRuntime().availableProcessors().coerceAtLeast(1),
    heapMb: Int = 1920,
    localDownloadsZipName: String? = null,
  ): String {
    val buildCmd =
      buildTermuxBootstrapCommand(
        workspaceName = workspaceName,
        repoSlug = repoSlug,
        branch = branch,
        onlineGradle = onlineGradle,
        cpuCores = cpuCores,
        heapMb = heapMb,
        localDownloadsZipName = localDownloadsZipName,
      )
    val enableExternalApps =
      "mkdir -p ~/.termux && (grep -q '^allow-external-apps=true' ~/.termux/termux.properties 2>/dev/null || printf '\\nallow-external-apps=true\\n' >> ~/.termux/termux.properties) && (command -v termux-reload-settings >/dev/null 2>&1 && termux-reload-settings || true)"
    return "$enableExternalApps && $buildCmd"
  }

  fun buildTermuxBootstrapCommand(
    workspaceName: String,
    repoSlug: String? = null,
    branch: String = "main",
    onlineGradle: Boolean = true,
    cpuCores: Int = Runtime.getRuntime().availableProcessors().coerceAtLeast(1),
    heapMb: Int = 1920,
    localDownloadsZipName: String? = null,
  ): String {
    val safeName = sanitizeShellToken(workspaceName, "apkbuilder")
    val safeBranch = sanitizeShellToken(branch, "main")
    val safeSlug =
      repoSlug
        ?.trim()
        ?.takeIf { it.matches(Regex("^[A-Za-z0-9._-]+/[A-Za-z0-9._-]+$")) }
    val workers = cpuCores.coerceIn(1, 16)
    val heap = heapMb.coerceIn(512, 4096)

    val sourceStep =
      if (safeSlug != null) {
        """
        BUILD_DIR="${'$'}HOME/apkbuilder_builds/$safeName"
        mkdir -p "${'$'}HOME/apkbuilder_builds"
        if [ -d "${'$'}BUILD_DIR/.git" ]; then
          echo "[APK Builder] Updating git checkout $safeSlug ($safeBranch)..."
          git -C "${'$'}BUILD_DIR" fetch --depth 1 origin "$safeBranch" && git -C "${'$'}BUILD_DIR" reset --hard "origin/$safeBranch"
        elif command -v git >/dev/null 2>&1; then
          rm -rf "${'$'}BUILD_DIR"
          echo "[APK Builder] Cloning https://github.com/$safeSlug.git ($safeBranch)..."
          git clone --depth 1 -b "$safeBranch" "https://github.com/$safeSlug.git" "${'$'}BUILD_DIR"
        else
          mkdir -p "${'$'}BUILD_DIR"
          TMP_ZIP="${'$'}HOME/apkbuilder_builds/${safeName}_src.zip"
          curl -fsSL "https://github.com/$safeSlug/archive/refs/heads/$safeBranch.zip" -o "${'$'}TMP_ZIP"
          TMP_UNPACK="${'$'}HOME/apkbuilder_builds/${safeName}_unpack"
          rm -rf "${'$'}TMP_UNPACK" && mkdir -p "${'$'}TMP_UNPACK"
          unzip -oq "${'$'}TMP_ZIP" -d "${'$'}TMP_UNPACK"
          rm -rf "${'$'}BUILD_DIR"/*
          cp -r "${'$'}TMP_UNPACK"/*/* "${'$'}BUILD_DIR"/
          rm -rf "${'$'}TMP_UNPACK" "${'$'}TMP_ZIP"
        fi
        cd "${'$'}BUILD_DIR"
        """
          .trimIndent()
      } else {
        val zipFile = sanitizeShellToken(localDownloadsZipName ?: "$safeName.zip", "$safeName.zip")
        """
        if [ ! -d "${'$'}HOME/storage/downloads" ]; then
          echo "[APK Builder] Grant Termux storage access so it can read Downloads/APKBuilder/$zipFile"
          termux-setup-storage || true
          sleep 2
        fi
        ZIP_SRC="${'$'}HOME/storage/downloads/APKBuilder/$zipFile"
        if [ ! -f "${'$'}ZIP_SRC" ]; then
          ZIP_SRC="${'$'}HOME/storage/downloads/$zipFile"
        fi
        if [ ! -f "${'$'}ZIP_SRC" ]; then
          echo "[APK Builder] ERROR: Could not find $zipFile in Downloads/APKBuilder or Downloads."
          echo "[APK Builder] Tap Export (.zip) in APK Builder first, then re-run."
          exit 1
        fi
        BUILD_DIR="${'$'}HOME/apkbuilder_builds/$safeName"
        mkdir -p "${'$'}BUILD_DIR"
        unzip -oq "${'$'}ZIP_SRC" -d "${'$'}BUILD_DIR"
        cd "${'$'}BUILD_DIR"
        """
          .trimIndent()
      }

    val gradleInvocation =
      if (onlineGradle) {
        "${'$'}RUNNER assembleDebug ${'$'}COMMON_FLAGS"
      } else {
        "if ! ${'$'}RUNNER assembleDebug --offline ${'$'}COMMON_FLAGS; then echo '[APK Builder] Offline cache miss — retrying online...'; ${'$'}RUNNER assembleDebug ${'$'}COMMON_FLAGS; fi"
      }

    val lines =
      listOf(
        "set -eu",
        "echo '[APK Builder] Termux backend started...'",
        "mkdir -p ~/.termux",
        "(grep -q '^allow-external-apps=true' ~/.termux/termux.properties 2>/dev/null || printf '\\nallow-external-apps=true\\n' >> ~/.termux/termux.properties)",
        "MISSING=''",
        "for cmd in java gradle aapt2 unzip curl git; do command -v \"${'$'}cmd\" >/dev/null 2>&1 || MISSING=\"${'$'}MISSING ${'$'}cmd\"; done",
        "if [ -n \"${'$'}MISSING\" ]; then echo \"[APK Builder] Installing Termux toolchain packages:${'$'}MISSING\"; pkg update -y; (pkg install -y openjdk-21 gradle aapt2 apksigner unzip curl git || pkg install -y openjdk-17 gradle aapt2 apksigner unzip curl git); fi",
        "if [ -z \"\${JAVA_HOME:-}\" ] || [ ! -d \"\${JAVA_HOME}\" ]; then for j in /data/data/com.termux/files/usr/opt/openjdk /data/data/com.termux/files/usr/lib/jvm/java-21-openjdk /data/data/com.termux/files/usr/lib/jvm/java-17-openjdk; do if [ -d \"${'$'}j\" ]; then export JAVA_HOME=\"${'$'}j\"; break; fi; done; fi",
        "if [ -z \"\${JAVA_HOME:-}\" ] && command -v java >/dev/null 2>&1; then export JAVA_HOME=\"${'$'}(dirname \"${'$'}(dirname \"${'$'}(readlink -f \"${'$'}(command -v java)\")\")\")\"; fi",
        "export ANDROID_HOME=\"\${ANDROID_HOME:-${'$'}HOME/android-sdk}\"",
        "export ANDROID_SDK_ROOT=\"${'$'}ANDROID_HOME\"",
        "export GRADLE_USER_HOME=\"\${GRADLE_USER_HOME:-${'$'}HOME/.gradle}\"",
        "export GRADLE_OPTS=\"\${GRADLE_OPTS:--Xmx${heap}m -XX:MaxMetaspaceSize=512m -XX:+UseParallelGC}\"",
        "export PATH=\"${'$'}ANDROID_HOME/cmdline-tools/latest/bin:${'$'}ANDROID_HOME/platform-tools:${'$'}PATH\"",
        "if [ ! -d \"${'$'}ANDROID_HOME/platforms\" ] || [ -z \"${'$'}(ls -A \"${'$'}ANDROID_HOME/platforms\" 2>/dev/null || true)\" ]; then echo '[APK Builder] Bootstrapping Android SDK in ${'$'}ANDROID_HOME...'; mkdir -p \"${'$'}ANDROID_HOME/cmdline-tools\"; CMD_ZIP=\"${'$'}HOME/cmdline-tools.zip\"; curl -fsSL 'https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip' -o \"${'$'}CMD_ZIP\"; rm -rf \"${'$'}ANDROID_HOME/cmdline-tools/latest\" \"${'$'}ANDROID_HOME/cmdline-tools/cmdline-tools\"; unzip -oq \"${'$'}CMD_ZIP\" -d \"${'$'}ANDROID_HOME/cmdline-tools\"; mv \"${'$'}ANDROID_HOME/cmdline-tools/cmdline-tools\" \"${'$'}ANDROID_HOME/cmdline-tools/latest\"; rm -f \"${'$'}CMD_ZIP\"; yes | sdkmanager --licenses >/dev/null 2>&1 || true; sdkmanager 'platform-tools' 'platforms;android-36' 'platforms;android-35' 'build-tools;35.0.0'; fi",
        sourceStep.replace("\n", "; "),
        "printf 'sdk.dir=%s\\n' \"${'$'}ANDROID_HOME\" > local.properties",
        "if [ ! -f debug.keystore ]; then if [ -f debug.keystore.base64 ] && command -v base64 >/dev/null 2>&1; then base64 -d debug.keystore.base64 > debug.keystore; elif command -v keytool >/dev/null 2>&1; then keytool -genkeypair -noprompt -keystore debug.keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Android Debug,O=Android,C=US' || true; fi; fi",
        "AAPT2_FLAG=''; AAPT2_BIN=\"${'$'}(command -v aapt2 2>/dev/null || true)\"; if [ -n \"${'$'}AAPT2_BIN\" ]; then AAPT2_FLAG=\"-Pandroid.aapt2FromMavenOverride=${'$'}AAPT2_BIN\"; fi",
        "if command -v gradle >/dev/null 2>&1; then RUNNER=gradle; elif [ -f ./gradlew ]; then chmod +x ./gradlew || true; RUNNER=./gradlew; else echo '[APK Builder] No gradle binary found'; exit 127; fi",
        "COMMON_FLAGS=\"--max-workers=$workers --parallel --build-cache --configure-on-demand --daemon ${'$'}AAPT2_FLAG\"",
        gradleInvocation,
        "APK_LIST=\"${'$'}(find app/build/outputs/apk -type f -name '*.apk' 2>/dev/null || true)\"",
        "echo '[APK Builder] Built APK(s):'; printf '%s\\n' \"${'$'}APK_LIST\"",
        "FIRST_APK=\"${'$'}(printf '%s\\n' \"${'$'}APK_LIST\" | head -n 1)\"",
        "if [ -n \"${'$'}FIRST_APK\" ]; then if [ -d \"${'$'}HOME/storage/downloads\" ]; then mkdir -p \"${'$'}HOME/storage/downloads/APKBuilder\"; cp -f \"${'$'}FIRST_APK\" \"${'$'}HOME/storage/downloads/APKBuilder/\"; echo \"[APK Builder] Copied APK to Downloads/APKBuilder/\"; fi; if command -v termux-open >/dev/null 2>&1; then termux-open \"${'$'}FIRST_APK\" || true; fi; fi",
        "echo '[APK Builder] Finished. Press Enter to keep or close session...'; read -r _ || true",
      )
    return lines.joinToString(" && ")
  }

  private fun sanitizeShellToken(raw: String, fallback: String): String {
    val clean = raw.trim().replace(Regex("[^A-Za-z0-9._/-]"), "")
    return clean.ifBlank { fallback }.take(80)
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
    const val TERMUX_HOME = "/data/data/com.termux/files/home"
    const val TERMUX_BIN = "/data/data/com.termux/files/usr/bin"
    private const val KEY_REPO_SLUG = "self_repo_slug"
    private const val KEY_BRANCH = "self_repo_branch"
    private const val KEY_PREFER_ON_DEVICE = "prefer_on_device_gradle"
  }
}
