package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.ApkSigningService
import com.example.data.BuildOutcomeStatus
import com.example.data.BuildStepEvent
import com.example.data.GitHubRepository
import com.example.data.ShellEvent
import com.example.data.ImportPhase
import com.example.data.ImportProgress
import com.example.data.ProjectRepository
import com.example.data.SelfUpdateRepository
import com.example.data.ShellEngine
import com.example.ui.theme.TermForgeTheme
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h780dp-xhdpi")
class TermForgeRobolectricTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun apkSigningService_generatesKeystoreSignsAndVerifiesApk() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val signingService = ApkSigningService(context)

    val ks =
      signingService.generatePkcs12Keystore(
        fileName = "test_release",
        alias = "upload",
        commonName = "Test Dev",
        organization = "TermForge",
        password = "secret123",
      )
    assertTrue(File(ks.absolutePath).exists())

    val unsignedApk = File(signingService.apksDir, "sample-unsigned.apk")
    ZipOutputStream(FileOutputStream(unsignedApk)).use { zos ->
      zos.putNextEntry(ZipEntry("AndroidManifest.xml"))
      zos.write(buildMinimalBinaryManifest(minSdk = 24))
      zos.closeEntry()
      zos.putNextEntry(ZipEntry("classes.dex"))
      zos.write("dex\n035\u0000".toByteArray(StandardCharsets.US_ASCII))
      zos.closeEntry()
    }

    val preVerify = signingService.verifyApk(unsignedApk)
    assertFalse(preVerify.isVerified)

    val result =
      signingService.signApk(
        inputApk = unsignedApk,
        keystoreFile = File(ks.absolutePath),
        storePassword = "secret123",
        alias = "upload",
        keyPassword = "secret123",
      )

    assertTrue(File(result.signedApkPath).exists())
    assertTrue(result.verification.isVerified)
    assertTrue(result.verification.verifiedV2 || result.verification.verifiedV3)
    assertNotNull(result.verification.signerSha256)
  }

  @Test
  fun gitHubRepository_parsesUrlsAndBranches() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val engine = ShellEngine(context)
    val repo = ProjectRepository(context, engine)
    val gh = GitHubRepository(context, repo)

    val s1 = gh.parseRepoInput("square/okhttp")
    assertEquals("square", s1.owner)
    assertEquals("okhttp", s1.repo)
    assertEquals(null, s1.branch)

    val s2 = gh.parseRepoInput("https://github.com/android/nowinandroid/tree/main")
    assertEquals("android", s2.owner)
    assertEquals("nowinandroid", s2.repo)
    assertEquals("main", s2.branch)

    val dlProgressKnown =
      ImportProgress(
        phase = ImportPhase.DOWNLOADING_ARCHIVE,
        label = "Downloading",
        bytesDone = 512L,
        bytesTotal = 1024L,
        bytesPerSec = 256L,
        etaSeconds = 2L,
      )
    assertEquals(0.5f, dlProgressKnown.fraction!!, 0.01f)

    val dlProgressChunked =
      ImportProgress(
        phase = ImportPhase.DOWNLOADING_ARCHIVE,
        label = "Downloading",
        bytesDone = 512L,
        bytesTotal = -1L,
      )
    assertEquals(null, dlProgressChunked.fraction)
  }

  @Test
  fun gitHubRepository_reportsRealExtractionProgress() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val engine = ShellEngine(context)
    val repo = ProjectRepository(context, engine)
    val gh = GitHubRepository(context, repo)

    // 40 entries crosses the >=32 threshold so multi-core parallel ZipFile extraction executes
    val zip = File(context.cacheDir, "progress_test.zip")
    ZipOutputStream(FileOutputStream(zip)).use { zos ->
      repeat(40) { idx ->
        zos.putNextEntry(ZipEntry("repo-main/file_$idx.txt"))
        zos.write("hello $idx\n".repeat(16).toByteArray())
        zos.closeEntry()
      }
    }

    val outDir = File(context.cacheDir, "progress_out").apply {
      deleteRecursively()
      mkdirs()
    }
    val events = mutableListOf<ImportProgress>()
    val count = gh.extractZipFileWithProgress(zip, outDir) { events += it }

    assertEquals(40, count)
    assertTrue(events.isNotEmpty())
    val last = events.last()
    assertEquals(ImportPhase.EXTRACTING_FILES, last.phase)
    assertEquals(40, last.filesDone)
    assertEquals(40, last.filesTotal)
    assertEquals(1.0f, last.fraction!!, 0.001f)
  }

  @Test
  fun gitHubRepository_buildsBrowserSearchUrls() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val engine = ShellEngine(context)
    val repo = ProjectRepository(context, engine)
    val gh = GitHubRepository(context, repo)

    assertEquals("https://github.com/topics/android", gh.buildBrowserSearchUrl("", true))
    assertEquals(
      "https://github.com/search?type=repositories",
      gh.buildBrowserSearchUrl("  ", false),
    )
    val filteredUrl = gh.buildBrowserSearchUrl("compose player", true)
    assertTrue(filteredUrl.contains("https://github.com/search?q="))
    assertTrue(filteredUrl.contains("compose+player+android"))
    val unfilteredUrl = gh.buildBrowserSearchUrl("termux", false)
    assertEquals(
      "https://github.com/search?q=termux&type=repositories",
      unfilteredUrl,
    )
  }

  @Test
  fun projectRepository_createsStarterAndGeneratesTurboScripts() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val engine = ShellEngine(context)
    val repo = ProjectRepository(context, engine)

    val env = engine.inspectEnvironment()
    assertTrue(env.cpuCores >= 1)
    assertTrue(env.recommendedHeapMb in 512..4096)

    val summary = repo.createStarterGradleProject()
    assertNotNull(summary.appGradle)
    assertEquals("com.offline.hello", summary.appGradle?.applicationId)

    val buildEvents = repo.runOfflineBuild(summary).toList()
    val completed = buildEvents.filterIsInstance<BuildStepEvent.Completed>().single()
    assertTrue(
      completed.status == BuildOutcomeStatus.TOOLCHAIN_MISSING ||
        completed.status == BuildOutcomeStatus.APK_BUILT ||
        completed.status == BuildOutcomeStatus.GRADLE_FAILED
    )
    assertNotNull(completed.scriptPath)
    val offlineScript = File(completed.scriptPath!!)
    assertTrue(offlineScript.exists())
    val offlineText = offlineScript.readText()
    assertTrue(offlineText.contains("--max-workers=${env.cpuCores}"))
    assertTrue(offlineText.contains("--parallel"))
    assertTrue(offlineText.contains("--build-cache"))
    assertTrue(offlineText.contains("-Xmx${env.recommendedHeapMb}m"))
    assertTrue(offlineText.contains("-XX:MaxMetaspaceSize=512m"))
    assertTrue(offlineText.contains("android.aapt2FromMavenOverride"))
    assertTrue(offlineText.contains("local.properties"))
    assertTrue(offlineText.contains("debug.keystore.base64"))
    assertTrue(offlineText.contains("termux-open"))
    assertTrue(offlineText.contains("Retrying with network dependencies".lowercase()) || offlineText.contains("retrying with network dependencies enabled"))

    val onlineEvents = repo.runOfflineBuild(summary, onlineGradle = true).toList()
    val onlineCompleted = onlineEvents.filterIsInstance<BuildStepEvent.Completed>().single()
    val onlineScript = File(onlineCompleted.scriptPath!!)
    assertTrue(onlineScript.name == "build_online.sh")
    assertFalse(onlineScript.readText().contains("--offline"))
    assertTrue(onlineScript.readText().contains("--max-workers=${env.cpuCores}"))
  }

  @Test
  fun selfUpdateRepository_buildsTermuxIntentWorkflowYamlAndFileProviderShareIntent() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val signingService = ApkSigningService(context)
    val selfRepo = SelfUpdateRepository(context, signingService)

    val initial = selfRepo.loadConfig()
    assertEquals(BuildConfig.APPLICATION_ID, initial.installedAppId)
    assertEquals(BuildConfig.VERSION_NAME, initial.installedVersionName)
    assertEquals(BuildConfig.VERSION_CODE, initial.installedVersionCode)

    selfRepo.saveRepo("alice/termforge", "release")
    val saved = selfRepo.loadConfig()
    assertEquals("alice/termforge", saved.repoSlug)
    assertEquals("release", saved.branch)

    val engine = ShellEngine(context)
    assertFalse(engine.hasTermuxRunPermission())
    assertEquals(
      ShellEngine.TERMUX_RELEASES_URL,
      engine.createInstallTermuxIntent().dataString,
    )

    val termuxIntent =
      selfRepo.buildTermuxRunCommandIntent(
        workspaceName = "apkbuilder",
        repoSlug = "Bakhshi7889/apkbuilder",
        branch = "main",
      )
    assertEquals("com.termux.RUN_COMMAND", termuxIntent.action)
    assertEquals("com.termux.app.RunCommandService", termuxIntent.component?.className)
    assertEquals(
      SelfUpdateRepository.TERMUX_HOME,
      termuxIntent.getStringExtra("com.termux.RUN_COMMAND_WORKDIR"),
    )
    val args = termuxIntent.getStringArrayExtra("com.termux.RUN_COMMAND_ARGUMENTS")!!
    assertEquals("-lc", args[0])
    assertTrue(args[1].contains("Bakhshi7889/apkbuilder"))
    assertTrue(args[1].contains("debug.keystore.base64"))
    assertTrue(args[1].contains("android.aapt2FromMavenOverride"))
    assertTrue(args[1].contains("commandlinetools-linux"))
    assertTrue(args[1].contains("termux-open"))

    val setupOneLiner =
      selfRepo.buildTermuxEnableExternalAppsOneLiner(
        workspaceName = "apkbuilder",
        repoSlug = "Bakhshi7889/apkbuilder",
      )
    assertTrue(setupOneLiner.contains("allow-external-apps=true"))

    val yaml = selfRepo.defaultGitHubActionsWorkflowYaml()
    assertTrue(yaml.contains("assembleDebug"))
    assertTrue(yaml.contains("9.3.1"))
    assertTrue(yaml.contains("debug.keystore.base64"))
    assertTrue(yaml.contains("actions/upload-artifact@v4"))

    val dummyApk = File(signingService.apksDir, "self-update-test.apk").apply {
      parentFile?.mkdirs()
      writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04))
    }
    val shareIntent = selfRepo.buildShareApkIntent(dummyApk)
    assertEquals(android.content.Intent.ACTION_SEND, shareIntent.action)
    assertEquals("application/vnd.android.package-archive", shareIntent.type)
    assertTrue((shareIntent.flags and android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
  }

  @Test
  fun durationAndSpeedTracker_formatsDaysHoursMinutesSeconds() {
    assertEquals("45s", com.example.ui.formatDurationDetailed(45L))
    assertEquals("1m 30s", com.example.ui.formatDurationDetailed(90L))
    assertEquals("1h 1m 5s", com.example.ui.formatDurationDetailed(3665L))
    assertEquals("2d 1h 2m", com.example.ui.formatDurationDetailed(2 * 86_400L + 3_600L + 120L))

    val tracker = com.example.data.ProgressSpeedTracker(phaseStartMs = 1000L)
    val s1 = tracker.sample(totalBytesDone = 500L, bytesTotal = 1500L, nowMs = 2000L)
    assertEquals(500L, s1.currentBytesPerSec)
    assertEquals(500L, s1.avgBytesPerSec)
    assertEquals(500L, s1.peakBytesPerSec)
    assertEquals(2L, s1.etaSeconds)
  }

  @Test
  fun watchedAppsRepository_tracksMultipleAppsAndDetectsUpdates() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val watchRepo = com.example.data.WatchedAppsRepository(context)

    assertTrue(watchRepo.isAutoCheckOnAppOpenEnabled())
    watchRepo.setAutoCheckOnAppOpen(false)
    assertFalse(watchRepo.isAutoCheckOnAppOpenEnabled())
    watchRepo.setAutoCheckOnAppOpen(true)

    val app1 =
      com.example.data.GitHubRemoteSnapshot(
        owner = "ownerA",
        repo = "app-one",
        branch = "main",
        remoteSha = "1111111111111111111111111111111111111111",
        remoteMessage = "Initial release",
        remoteAuthor = "Alice",
        remoteDate = "2026-11-15T10:00:00Z",
        latestReleaseTag = "v1.0.0",
        latestApkName = "app-one-v1.0.0.apk",
        latestApkUrl = "https://github.com/ownerA/app-one/releases/download/v1.0.0/app.apk",
      )
    val app2 =
      com.example.data.GitHubRemoteSnapshot(
        owner = "ownerB",
        repo = "app-two",
        branch = "main",
        remoteSha = "aaaaaaa000000000000000000000000000000000",
        remoteMessage = "Clean build",
        remoteAuthor = "Bob",
        remoteDate = "2026-11-15T11:00:00Z",
        latestReleaseTag = null,
        latestApkName = null,
        latestApkUrl = null,
      )

    val saved1 = watchRepo.upsertInitialSnapshot(app1, checkedAtMs = 1000L)
    val saved2 = watchRepo.upsertInitialSnapshot(app2, checkedAtMs = 1000L)
    assertFalse(saved1.hasUpdate)
    assertFalse(saved2.hasUpdate)
    assertEquals(2, watchRepo.loadAll().size)

    // Simulate app1 receiving a new commit + new release APK on GitHub
    val updatedSnap1 =
      app1.copy(
        remoteSha = "2222222222222222222222222222222222222222",
        remoteMessage = "Fix crash on startup",
        latestReleaseTag = "v1.1.0",
        latestApkName = "app-one-v1.1.0.apk",
        latestApkUrl = "https://github.com/ownerA/app-one/releases/download/v1.1.0/app.apk",
      )
    val afterCheck = watchRepo.recordRemoteCheckSuccess(saved1.id, updatedSnap1, checkedAtMs = 2000L)!!
    assertTrue(afterCheck.hasUpdate)
    assertTrue(afterCheck.hasNewCommit)
    assertTrue(afterCheck.hasNewRelease)
    assertTrue(afterCheck.needsNotification)

    // After notification is posted once, needsNotification prevents duplicate alerts for same SHA+tag
    val afterNotify = watchRepo.markNotified(setOf(saved1.id)).first { it.id == saved1.id }
    assertTrue(afterNotify.hasUpdate)
    assertFalse(afterNotify.needsNotification)

    // Marking update seen advances baseline and clears update badge
    val afterSeen = watchRepo.markAsSeen(saved1.id).first { it.id == saved1.id }
    assertFalse(afterSeen.hasUpdate)
    assertEquals("v1.1.0", afterSeen.baselineReleaseTag)

    // Removing one app preserves remaining watched apps
    val remaining = watchRepo.remove(saved1.id)
    assertEquals(1, remaining.size)
    assertEquals(saved2.id, remaining.single().id)
  }

  @Test
  fun shellEngine_setsNativeLibDirEnvResolvesLibSoAndRunsBuiltins(): Unit = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val fakeNativeDir = File(context.cacheDir, "fake_native_libs").apply {
      deleteRecursively()
      mkdirs()
    }
    context.applicationInfo.nativeLibraryDir = fakeNativeDir.absolutePath
    val libTool = File(fakeNativeDir, "libdummytool.so").apply {
      writeText("#!/bin/sh\necho ok\n")
      setExecutable(true)
    }

    val engine = ShellEngine(context)
    val snap = engine.currentEnvSnapshot()
    assertEquals(fakeNativeDir.absolutePath, snap["NATIVE_LIB_DIR"])
    assertTrue(snap["PATH"].orEmpty().contains(fakeNativeDir.absolutePath))
    assertTrue(snap["LD_LIBRARY_PATH"].orEmpty().contains(fakeNativeDir.absolutePath))
    assertTrue(engine.listBundledNativeTools().contains("libdummytool.so"))

    val toolsEvents = engine.executeCommand("tools").toList()
    val toolsLines = toolsEvents.filterIsInstance<ShellEvent.Line>().map { it.text }
    assertTrue(toolsLines.any { it.contains("libdummytool.so") })

    val exportEvents = engine.executeCommand("export APKBUILDER_MODE=in_app").toList()
    assertEquals(0, exportEvents.filterIsInstance<ShellEvent.Finished>().single().exitCode)
    assertEquals("in_app", engine.currentEnvSnapshot()["APKBUILDER_MODE"])

    val pwdEvents = engine.executeCommand("pwd").toList()
    assertTrue(pwdEvents.filterIsInstance<ShellEvent.Line>().any { it.text == engine.homeDir.absolutePath })
    assertTrue(libTool.delete())
  }

  @Test
  fun appUi_rendersAt360dpWith13xFontScaleAndSwitchesBetweenTabs() {
    RuntimeEnvironment.setFontScale(1.3f)
    composeTestRule.setContent {
      TermForgeTheme {
        TermForgeApp()
      }
    }

    composeTestRule.onNodeWithTag("nav_apps").assertIsDisplayed()
    composeTestRule.onNodeWithTag("nav_build").assertIsDisplayed()
    composeTestRule.onNodeWithTag("nav_shell").assertIsDisplayed()
    composeTestRule.onNodeWithTag("nav_sign").assertIsDisplayed()
    composeTestRule.onNodeWithTag("builder_import_github_button").assertIsDisplayed()

    composeTestRule.onNodeWithTag("builder_import_github_button").performClick()
    composeTestRule.onNodeWithTag("github_search_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("github_android_filter_chip").assertIsDisplayed()
    composeTestRule.onNodeWithTag("github_open_browser_button").assertIsDisplayed()

    composeTestRule.onNodeWithTag("nav_apps").performClick()
    composeTestRule.onNodeWithTag("self_update_card").assertIsDisplayed()
    composeTestRule.onNodeWithTag("self_update_run_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("self_open_shell_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("self_inline_token_toggle").assertIsDisplayed()
    composeTestRule.onNodeWithTag("self_external_termux_toggle").assertIsDisplayed()

    // External Termux APK links stay hidden behind the optional disclosure by default
    composeTestRule.onNodeWithTag("self_install_termux_button").assertDoesNotExist()

    composeTestRule.onNodeWithTag("self_inline_token_toggle").performClick()
    composeTestRule.onNodeWithTag("apps_lazy_column").performScrollToNode(hasTestTag("self_inline_token_input"))
    composeTestRule.onNodeWithTag("self_inline_token_input").assertIsDisplayed()
    composeTestRule.onNodeWithTag("self_inline_token_toggle").performClick()

    composeTestRule.onNodeWithTag("apps_lazy_column").performScrollToNode(hasTestTag("self_external_termux_toggle"))
    composeTestRule.onNodeWithTag("self_external_termux_toggle").performClick()
    composeTestRule.onNodeWithTag("apps_lazy_column").performScrollToNode(hasTestTag("self_install_termux_button"))
    composeTestRule.onNodeWithTag("self_install_termux_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("self_copy_termux_setup_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("self_external_termux_toggle").performClick()

    composeTestRule.onNodeWithTag("apps_lazy_column").performScrollToNode(hasTestTag("self_repo_toggle"))
    composeTestRule.onNodeWithTag("self_repo_toggle").performClick()
    composeTestRule.onNodeWithTag("self_repo_input").assertIsDisplayed()
    composeTestRule.onNodeWithTag("self_repo_save_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("self_ci_yaml_toggle").performClick()
    composeTestRule.onNodeWithTag("apps_lazy_column").performScrollToNode(hasTestTag("self_ci_yaml_preview"))
    composeTestRule.onNodeWithTag("self_ci_yaml_preview").assertIsDisplayed()
    composeTestRule.onNodeWithTag("self_ci_yaml_toggle").performClick()
    composeTestRule.onNodeWithTag("self_repo_toggle").performClick()

    composeTestRule.onNodeWithTag("apps_lazy_column").performScrollToNode(hasTestTag("apps_summary_card"))
    composeTestRule.onNodeWithTag("apps_summary_card").assertIsDisplayed()
    composeTestRule.onNodeWithTag("apps_auto_check_chip").assertIsDisplayed()
    composeTestRule.onNodeWithTag("apps_notifications_chip").assertIsDisplayed()
    composeTestRule.onNodeWithTag("apps_check_all_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("apps_add_toggle_button").performClick()
    composeTestRule.onNodeWithTag("apps_add_input").assertIsDisplayed()
    composeTestRule.onNodeWithTag("apps_add_submit_button").assertIsDisplayed()

    composeTestRule.onNodeWithTag("nav_shell").performClick()
    composeTestRule.onNodeWithTag("shell_diagnostics_toggle").assertIsDisplayed()
    composeTestRule.onNodeWithTag("shell_output_box").assertIsDisplayed()
    composeTestRule.onNodeWithTag("shell_command_input").assertIsDisplayed()
    composeTestRule.onNodeWithTag("shell_run_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("shell_chip_help").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5000L) {
      composeTestRule.onAllNodes(hasText("Built-In APK Builder CLI commands:", substring = true))
        .fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("shell_diagnostics_toggle").performClick()
    composeTestRule.onNodeWithTag("shell_diagnostics_panel").assertIsDisplayed()
    composeTestRule.onNodeWithTag("shell_diagnostics_toggle").performClick()
    composeTestRule.onNodeWithTag("shell_command_input").performTextInput("probe")
    composeTestRule.onNodeWithTag("shell_run_button").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5000L) {
      composeTestRule.onAllNodes(hasText("NATIVE_LIB_DIR:", substring = true))
        .fetchSemanticsNodes().isNotEmpty()
    }

    composeTestRule.onNodeWithTag("nav_sign").performClick()
    composeTestRule.onNodeWithTag("sign_apk_action_button").assertIsDisplayed()
  }

  private fun buildMinimalBinaryManifest(minSdk: Int): ByteArray {
    val strings =
      listOf(
        "minSdkVersion",
        "http://schemas.android.com/apk/res/android",
        "android",
        "manifest",
        "uses-sdk",
        "package",
        "com.test.app",
      )

    // String pool (UTF-8)
    val strData = ByteArrayOutputStream()
    val offsets = IntArray(strings.size)
    strings.forEachIndexed { i, s ->
      offsets[i] = strData.size()
      val bytes = s.toByteArray(StandardCharsets.UTF_8)
      strData.write(s.length)
      strData.write(bytes.size)
      strData.write(bytes)
      strData.write(0)
    }
    while (strData.size() % 4 != 0) strData.write(0)
    val strDataBytes = strData.toByteArray()
    val poolStringsStart = 28 + offsets.size * 4
    val poolSize = poolStringsStart + strDataBytes.size
    val poolBuf =
      ByteBuffer.allocate(poolSize).order(ByteOrder.LITTLE_ENDIAN).apply {
        putShort(0x0001) // RES_STRING_POOL_TYPE
        putShort(28)
        putInt(poolSize)
        putInt(strings.size)
        putInt(0) // styleCount
        putInt(0x0100) // UTF8_FLAG
        putInt(poolStringsStart)
        putInt(0) // stylesStart
        offsets.forEach { putInt(it) }
        put(strDataBytes)
      }.array()

    // Resource map (index 0 = android:minSdkVersion = 0x0101020c)
    val resMapBuf =
      ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN).apply {
        putShort(0x0180) // RES_XML_RESOURCE_MAP_TYPE
        putShort(8)
        putInt(12)
        putInt(0x0101020c)
      }.array()

    fun nodeChunk(type: Short, extraInts: IntArray): ByteArray {
      val size = 16 + extraInts.size * 4
      return ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN).apply {
        putShort(type)
        putShort(16)
        putInt(size)
        putInt(1) // lineNumber
        putInt(-1) // comment
        extraInts.forEach { putInt(it) }
      }.array()
    }

    val startNs = nodeChunk(0x0100, intArrayOf(2, 1))
    // manifest(name=3, attrCount=1, attr package=5 -> "com.test.app"=6)
    val startManifest =
      nodeChunk(
        0x0102,
        intArrayOf(
          -1, // ns
          3, // name = "manifest"
          (20 shl 16) or 20, // attrStart=20, attrSize=20
          1, // attrCount=1 (high 16 idIndex=0)
          0, // classIndex=0, styleIndex=0
          -1, // attr ns
          5, // attr name = "package"
          6, // rawValue = "com.test.app"
          (8 and 0xFFFF) or (3 shl 24), // size=8, res0=0, dataType=TYPE_STRING(3)
          6, // data = string index 6
        ),
      )
    // uses-sdk(name=4, attrCount=1, attr android:minSdkVersion=0 -> TYPE_INT_DEC(0x10)=minSdk)
    val startUsesSdk =
      nodeChunk(
        0x0102,
        intArrayOf(
          -1, // ns
          4, // name = "uses-sdk"
          (20 shl 16) or 20,
          1,
          0,
          1, // attr ns = android URI
          0, // attr name = "minSdkVersion"
          -1, // rawValue
          8 or (0x10 shl 24), // size=8, res0=0, dataType=TYPE_INT_DEC(0x10)
          minSdk,
        ),
      )
    val endUsesSdk = nodeChunk(0x0103, intArrayOf(-1, 4))
    val endManifest = nodeChunk(0x0103, intArrayOf(-1, 3))
    val endNs = nodeChunk(0x0101, intArrayOf(2, 1))

    val bodySize =
      poolBuf.size +
        resMapBuf.size +
        startNs.size +
        startManifest.size +
        startUsesSdk.size +
        endUsesSdk.size +
        endManifest.size +
        endNs.size
    val totalSize = 8 + bodySize
    return ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN).apply {
      putShort(0x0003) // RES_XML_TYPE
      putShort(8)
      putInt(totalSize)
      put(poolBuf)
      put(resMapBuf)
      put(startNs)
      put(startManifest)
      put(startUsesSdk)
      put(endUsesSdk)
      put(endManifest)
      put(endNs)
    }.array()
  }
}
