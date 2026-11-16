package com.example.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.StringReader
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

data class ProjectFileNode(
  val relativePath: String,
  val name: String,
  val absolutePath: String,
  val sizeBytes: Long,
  val isTextEditable: Boolean,
)

data class ParsedManifest(
  val path: String,
  val packageName: String?,
  val minSdk: String?,
  val targetSdk: String?,
  val permissions: List<String>,
  val activities: List<String>,
)

data class ParsedGradleConfig(
  val path: String,
  val isKotlinDsl: Boolean,
  val namespace: String?,
  val applicationId: String?,
  val compileSdk: String?,
  val minSdk: String?,
  val targetSdk: String?,
  val dependencyCount: Int,
)

data class AndroidProjectSummary(
  val workspaceName: String,
  val rootPath: String,
  val fileCount: Int,
  val totalBytes: Long,
  val hasSettingsGradle: Boolean,
  val hasGradleWrapperProps: Boolean,
  val gradleWrapperVersion: String?,
  val hasGradlewScript: Boolean,
  val hasGradleWrapperJar: Boolean,
  val hasOfflineCacheDir: Boolean,
  val manifest: ParsedManifest?,
  val appGradle: ParsedGradleConfig?,
  val sourceFileCount: Int,
  val generatedScriptPath: String?,
  val builtApkPaths: List<String>,
  val files: List<ProjectFileNode>,
)

sealed interface BuildStepEvent {
  data class Log(val line: String) : BuildStepEvent

  data class Completed(
    val status: BuildOutcomeStatus,
    val summary: String,
    val durationMs: Long,
    val scriptPath: String?,
    val apkPaths: List<String>,
  ) : BuildStepEvent
}

enum class BuildOutcomeStatus {
  APK_BUILT,
  GRADLE_FAILED,
  TOOLCHAIN_MISSING,
}

class ProjectRepository(
  private val context: Context,
  private val shellEngine: ShellEngine,
) {
  val workspacesRoot: File
    get() = shellEngine.workspacesDir

  suspend fun listWorkspaces(): List<AndroidProjectSummary> =
    withContext(Dispatchers.IO) {
      val dirs =
        workspacesRoot
          .listFiles()
          ?.filter { it.isDirectory }
          ?.sortedByDescending { it.lastModified() }
          ?: emptyList()
      dirs.map { inspectProject(it) }
    }

  suspend fun importZip(
    uri: Uri,
    onProgress: (ImportProgress) -> Unit = {},
  ): AndroidProjectSummary =
    withContext(Dispatchers.IO) {
      val opStart = SystemClock.elapsedRealtime()
      val meta = queryUriMeta(uri)
      val rawName = meta.first ?: "imported_project_${System.currentTimeMillis()}"
      val zipFileSize = meta.second
      val baseName = sanitizeDirName(rawName.removeSuffix(".zip").removeSuffix(".ZIP"))
      val targetDir = uniqueWorkspaceDir(baseName)
      targetDir.mkdirs()

      val canonicalRoot = targetDir.canonicalFile
      var extractedCount = 0
      var extractedBytes = 0L
      val tracker = ProgressSpeedTracker(opStart)
      var lastEmitAt = 0L

      context.contentResolver.openInputStream(uri)?.use { rawInput ->
        var compressedBytesRead = 0L
        val countingInput =
          object : java.io.FilterInputStream(rawInput.buffered()) {
            override fun read(): Int {
              val b = super.read()
              if (b != -1) compressedBytesRead++
              return b
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
              val count = super.read(b, off, len)
              if (count > 0) compressedBytesRead += count
              return count
            }
          }
        val copyBuf = ByteArray(128 * 1024)
        ZipInputStream(countingInput).use { zis ->
          var entry: ZipEntry? = zis.nextEntry
          while (entry != null) {
            val outFile = File(canonicalRoot, entry.name)
            val canonicalOut = outFile.canonicalFile
            if (!canonicalOut.path.startsWith(canonicalRoot.path + File.separator) &&
              canonicalOut != canonicalRoot
            ) {
              throw SecurityException("Blocked unsafe zip path: ${entry.name}")
            }

            if (entry.isDirectory) {
              canonicalOut.mkdirs()
            } else {
              canonicalOut.parentFile?.mkdirs()
              FileOutputStream(canonicalOut).use { fos ->
                while (true) {
                  val r = zis.read(copyBuf)
                  if (r == -1) break
                  fos.write(copyBuf, 0, r)
                  extractedBytes += r
                }
              }
              extractedCount++
            }
            zis.closeEntry()

            val now = SystemClock.elapsedRealtime()
            if (now - lastEmitAt >= 90L) {
              lastEmitAt = now
              val trackedBytes = if (zipFileSize > 0L) compressedBytesRead else extractedBytes
              val sample = tracker.sample(trackedBytes, zipFileSize, now)
              onProgress(
                ImportProgress(
                  phase = ImportPhase.IMPORTING_LOCAL,
                  label = "Extracting ${entry.name.substringAfterLast('/')}",
                  bytesDone = trackedBytes,
                  bytesTotal = zipFileSize,
                  filesDone = extractedCount,
                  filesTotal = -1,
                  bytesPerSec = sample.currentBytesPerSec,
                  avgBytesPerSec = sample.avgBytesPerSec,
                  peakBytesPerSec = sample.peakBytesPerSec,
                  elapsedMs = now - opStart,
                  etaSeconds = sample.etaSeconds,
                )
              )
            }
            entry = zis.nextEntry
          }
        }
      } ?: throw IllegalStateException("Unable to open archive stream from selected URI.")

      if (extractedCount == 0) {
        targetDir.deleteRecursively()
        throw IllegalStateException("Archive contained no extractable files.")
      }

      // Unwrap single top-level wrapper folder when the zip wraps the project in one directory
      unwrapSingleRootDirectoryIfNeeded(targetDir)
      inspectProject(targetDir)
    }

  suspend fun importFolderTree(
    treeUri: Uri,
    onProgress: (ImportProgress) -> Unit = {},
  ): AndroidProjectSummary =
    withContext(Dispatchers.IO) {
      val opStart = SystemClock.elapsedRealtime()
      val docId = DocumentsContract.getTreeDocumentId(treeUri)
      val folderName = docId.substringAfterLast('/').substringAfterLast(':').ifBlank { "folder_project" }
      val targetDir = uniqueWorkspaceDir(sanitizeDirName(folderName))
      targetDir.mkdirs()

      val tracker = ProgressSpeedTracker(opStart)
      var copiedBytes = 0L
      var copiedFiles = 0
      var lastEmitAt = 0L

      val copied =
        copyDocumentTree(treeUri, docId, targetDir, depth = 0) { fileName, fileBytes ->
          copiedFiles++
          copiedBytes += fileBytes
          val now = SystemClock.elapsedRealtime()
          if (now - lastEmitAt >= 90L) {
            lastEmitAt = now
            val sample = tracker.sample(copiedBytes, -1L, now)
            onProgress(
              ImportProgress(
                phase = ImportPhase.IMPORTING_LOCAL,
                label = "Copying $fileName",
                bytesDone = copiedBytes,
                bytesTotal = -1L,
                filesDone = copiedFiles,
                filesTotal = -1,
                bytesPerSec = sample.currentBytesPerSec,
                avgBytesPerSec = sample.avgBytesPerSec,
                peakBytesPerSec = sample.peakBytesPerSec,
                elapsedMs = now - opStart,
                etaSeconds = null,
              )
            )
          }
        }
      if (copied == 0) {
        targetDir.deleteRecursively()
        throw IllegalStateException("Selected folder contained no readable files.")
      }
      inspectProject(targetDir)
    }

  suspend fun createStarterGradleProject(): AndroidProjectSummary =
    withContext(Dispatchers.IO) {
      val dir = uniqueWorkspaceDir("hello_offline")
      dir.mkdirs()

      writeProjectFile(
        dir,
        "settings.gradle.kts",
        """
        pluginManagement {
            repositories {
                google()
                mavenCentral()
                gradlePluginPortal()
            }
        }
        dependencyResolutionManagement {
            repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
            repositories {
                google()
                mavenCentral()
            }
        }
        rootProject.name = "HelloOffline"
        include(":app")
        """
          .trimIndent() + "\n",
      )

      writeProjectFile(
        dir,
        "build.gradle.kts",
        """
        plugins {
            id("com.android.application") version "8.7.3" apply false
            id("org.jetbrains.kotlin.android") version "2.0.21" apply false
        }
        """
          .trimIndent() + "\n",
      )

      writeProjectFile(
        dir,
        "gradle.properties",
        """
        org.gradle.jvmargs=-Xmx1024m
        android.useAndroidX=true
        kotlin.code.style=official
        """
          .trimIndent() + "\n",
      )

      writeProjectFile(
        dir,
        "gradle/wrapper/gradle-wrapper.properties",
        """
        distributionBase=GRADLE_USER_HOME
        distributionPath=wrapper/dists
        distributionUrl=https\://services.gradle.org/distributions/gradle-8.9-bin.zip
        zipStoreBase=GRADLE_USER_HOME
        zipStorePath=wrapper/dists
        """
          .trimIndent() + "\n",
      )

      writeProjectFile(
        dir,
        "app/build.gradle.kts",
        """
        plugins {
            id("com.android.application")
            id("org.jetbrains.kotlin.android")
        }

        android {
            namespace = "com.offline.hello"
            compileSdk = 35

            defaultConfig {
                applicationId = "com.offline.hello"
                minSdk = 24
                targetSdk = 35
                versionCode = 1
                versionName = "1.0"
            }

            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
            kotlinOptions {
                jvmTarget = "17"
            }
        }

        dependencies {
            implementation("androidx.core:core-ktx:1.15.0")
        }
        """
          .trimIndent() + "\n",
      )

      writeProjectFile(
        dir,
        "app/src/main/AndroidManifest.xml",
        """
        <?xml version="1.0" encoding="utf-8"?>
        <manifest xmlns:android="http://schemas.android.com/apk/res/android">
            <application
                android:allowBackup="true"
                android:label="@string/app_name">
                <activity
                    android:name=".MainActivity"
                    android:exported="true">
                    <intent-filter>
                        <action android:name="android.intent.action.MAIN" />
                        <category android:name="android.intent.category.LAUNCHER" />
                    </intent-filter>
                </activity>
            </application>
        </manifest>
        """
          .trimIndent() + "\n",
      )

      writeProjectFile(
        dir,
        "app/src/main/res/values/strings.xml",
        """
        <resources>
            <string name="app_name">HelloOffline</string>
        </resources>
        """
          .trimIndent() + "\n",
      )

      writeProjectFile(
        dir,
        "app/src/main/java/com/offline/hello/MainActivity.kt",
        """
        package com.offline.hello

        import android.app.Activity
        import android.os.Bundle
        import android.widget.TextView

        class MainActivity : Activity() {
            override fun onCreate(savedInstanceState: Bundle?) {
                super.onCreate(savedInstanceState)
                val label = TextView(this).apply {
                    text = "Built offline with Gradle"
                    setPadding(48, 96, 48, 48)
                }
                setContentView(label)
            }
        }
        """
          .trimIndent() + "\n",
      )

      inspectProject(dir)
    }

  suspend fun inspectProject(rootDir: File): AndroidProjectSummary =
    withContext(Dispatchers.IO) {
      val allFiles =
        rootDir
          .walkTopDown()
          .filter { it.isFile }
          .toList()
          .sortedBy { it.relativeTo(rootDir).path }

      val totalBytes = allFiles.sumOf { it.length() }
      val settingsFile =
        File(rootDir, "settings.gradle.kts").takeIf { it.exists() }
          ?: File(rootDir, "settings.gradle").takeIf { it.exists() }

      val wrapperProps = File(rootDir, "gradle/wrapper/gradle-wrapper.properties")
      val gradleVersion =
        if (wrapperProps.exists()) {
          Regex("""gradle-([0-9]+\.[0-9]+(?:\.[0-9]+)?)-""")
            .find(wrapperProps.readText())
            ?.groupValues
            ?.getOrNull(1)
        } else {
          null
        }

      val gradlew = File(rootDir, "gradlew").exists()
      val wrapperJar = File(rootDir, "gradle/wrapper/gradle-wrapper.jar").exists()
      val hasOfflineCache =
        File(rootDir, ".gradle").exists() ||
          File(rootDir, "offline-repo").exists() ||
          File(rootDir, "libs").exists()

      val manifestFile =
        allFiles.firstOrNull {
          it.name == "AndroidManifest.xml" && it.relativeTo(rootDir).path.contains("src/main")
        } ?: allFiles.firstOrNull { it.name == "AndroidManifest.xml" }

      val appGradleFile =
        allFiles.firstOrNull {
          val rel = it.relativeTo(rootDir).path
          rel == "app/build.gradle.kts" || rel == "app/build.gradle"
        } ?: allFiles.firstOrNull {
          it.name == "build.gradle.kts" || it.name == "build.gradle"
        }

      val sourceFiles =
        allFiles.count {
          it.extension.equals("kt", ignoreCase = true) ||
            it.extension.equals("java", ignoreCase = true)
        }

      val builtApks =
        allFiles
          .filter { it.extension.equals("apk", ignoreCase = true) }
          .map { it.relativeTo(rootDir).path }

      val scriptFile = File(rootDir, "build_offline.sh").takeIf { it.exists() }

      val nodes =
        allFiles.map { file ->
          val rel = file.relativeTo(rootDir).path
          ProjectFileNode(
            relativePath = rel,
            name = file.name,
            absolutePath = file.absolutePath,
            sizeBytes = file.length(),
            isTextEditable = isEditableTextFile(file),
          )
        }

      AndroidProjectSummary(
        workspaceName = rootDir.name,
        rootPath = rootDir.absolutePath,
        fileCount = allFiles.size,
        totalBytes = totalBytes,
        hasSettingsGradle = settingsFile != null,
        hasGradleWrapperProps = wrapperProps.exists(),
        gradleWrapperVersion = gradleVersion,
        hasGradlewScript = gradlew,
        hasGradleWrapperJar = wrapperJar,
        hasOfflineCacheDir = hasOfflineCache,
        manifest = manifestFile?.let { parseManifest(rootDir, it) },
        appGradle = appGradleFile?.let { parseGradleFile(rootDir, it) },
        sourceFileCount = sourceFiles,
        generatedScriptPath = scriptFile?.absolutePath,
        builtApkPaths = builtApks,
        files = nodes,
      )
    }

  suspend fun readFileText(filePath: String): String =
    withContext(Dispatchers.IO) {
      val file = File(filePath)
      if (!file.exists() || !file.isFile) {
        throw IllegalArgumentException("File does not exist: $filePath")
      }
      if (file.length() > 512 * 1024L) {
        return@withContext file.bufferedReader().use { reader ->
          val buf = CharArray(32768)
          val read = reader.read(buf)
          if (read > 0) String(buf, 0, read) + "\n\n[Truncated preview at 32 KB]" else ""
        }
      }
      file.readText()
    }

  suspend fun writeFileText(filePath: String, content: String) =
    withContext(Dispatchers.IO) {
      val file = File(filePath)
      file.writeText(content)
    }

  suspend fun deleteWorkspace(rootPath: String) =
    withContext(Dispatchers.IO) {
      val dir = File(rootPath)
      if (dir.canonicalPath.startsWith(workspacesRoot.canonicalPath + File.separator)) {
        dir.deleteRecursively()
      }
    }

  suspend fun exportWorkspaceZip(rootPath: String, destinationUri: Uri): Long =
    withContext(Dispatchers.IO) {
      val rootDir = File(rootPath)
      var bytesWritten = 0L
      context.contentResolver.openOutputStream(destinationUri)?.use { out ->
        ZipOutputStream(java.io.BufferedOutputStream(out, 128 * 1024)).use { zos ->
          val copyBuf = ByteArray(128 * 1024)
          rootDir.walkTopDown().filter { it.isFile }.forEach { file ->
            val relPath = file.relativeTo(rootDir).path
            zos.putNextEntry(ZipEntry(relPath))
            FileInputStream(file).use { fis ->
              while (true) {
                val r = fis.read(copyBuf)
                if (r == -1) break
                zos.write(copyBuf, 0, r)
                bytesWritten += r
              }
            }
            zos.closeEntry()
          }
        }
      } ?: throw IllegalStateException("Could not open destination output stream.")
      bytesWritten
    }

  suspend fun stageWorkspaceZipToPublicDownloads(
    rootPath: String,
    workspaceName: String,
  ): String? =
    withContext(Dispatchers.IO) {
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext null
      val safeFileName = "${sanitizeDirName(workspaceName)}.zip"
      runCatching {
        val resolver = context.contentResolver
        val selection =
          "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?"
        val relPath = "${Environment.DIRECTORY_DOWNLOADS}/APKBuilder/"
        resolver.delete(
          MediaStore.Downloads.EXTERNAL_CONTENT_URI,
          selection,
          arrayOf(safeFileName, relPath),
        )
        val values =
          ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, safeFileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
          }
        val uri =
          resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: return@withContext null
        exportWorkspaceZip(rootPath, uri)
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        safeFileName
      }.getOrNull()
    }

  fun runOfflineBuild(
    summary: AndroidProjectSummary,
    onlineGradle: Boolean = false,
  ): Flow<BuildStepEvent> = channelFlow {
    val startElapsed = SystemClock.elapsedRealtime()
    val rootDir = File(summary.rootPath)
    val modeLabel = if (onlineGradle) "Online" else "Offline"

    send(BuildStepEvent.Log("=== TermForge $modeLabel Gradle Pre-flight ==="))
    send(BuildStepEvent.Log("Workspace: ${summary.rootPath}"))
    send(
      BuildStepEvent.Log(
        "Files: ${summary.fileCount} (${summary.sourceFileCount} Kotlin/Java sources)"
      )
    )
    send(
      BuildStepEvent.Log(
        "settings.gradle(.kts): ${if (summary.hasSettingsGradle) "FOUND" else "MISSING"}"
      )
    )
    send(
      BuildStepEvent.Log(
        "app/build.gradle(.kts): ${summary.appGradle?.path ?: "MISSING"}"
      )
    )
    send(
      BuildStepEvent.Log(
        "AndroidManifest.xml: ${summary.manifest?.path ?: "MISSING"}"
      )
    )

    val env = shellEngine.inspectEnvironment()
    val javaTool = env.tools.firstOrNull { it.name == "java" }
    val gradleTool = env.tools.firstOrNull { it.name == "gradle" }
    val aapt2Tool = env.tools.firstOrNull { it.name == "aapt2" }
    val d8Tool = env.tools.firstOrNull { it.name == "d8" }

    val workers = env.cpuCores.coerceAtLeast(1)
    val heapMb = env.recommendedHeapMb
    val useDaemon = !env.lowMemory && (env.totalRamBytes == 0L || env.totalRamBytes >= 3_500_000_000L)
    val daemonFlag = if (useDaemon) "--daemon" else "--no-daemon"
    val jvmArgs = "-Xmx${heapMb}m -XX:MaxMetaspaceSize=512m -XX:+UseParallelGC"
    val aapt2OverrideFlag =
      if (aapt2Tool?.isAvailable == true && env.machineArch.contains("aarch64", ignoreCase = true) ||
        aapt2Tool?.isAvailable == true && env.machineArch.contains("arm64", ignoreCase = true)
      ) {
        " -Pandroid.aapt2FromMavenOverride=${aapt2Tool.resolvedPath}"
      } else {
        ""
      }
    val turboFlags =
      "--max-workers=$workers --parallel --build-cache --configure-on-demand $daemonFlag " +
        "-Dorg.gradle.jvmargs=\"$jvmArgs\"$aapt2OverrideFlag"

    send(BuildStepEvent.Log(""))
    send(BuildStepEvent.Log("=== Toolchain & Performance Probe ==="))
    send(BuildStepEvent.Log("Kernel/Arch: ${env.kernelName} ${env.kernelRelease} (${env.machineArch})"))
    val totalMb = env.totalRamBytes / (1024L * 1024L)
    val availMb = env.availRamBytes / (1024L * 1024L)
    send(
      BuildStepEvent.Log(
        "Device Hardware: $workers CPU cores • ${availMb}/${totalMb} MB RAM • Gradle tuning: --max-workers=$workers -Xmx${heapMb}m $daemonFlag"
      )
    )
    send(BuildStepEvent.Log("java: ${javaTool?.resolvedPath ?: "NOT FOUND"}"))
    send(BuildStepEvent.Log("gradle: ${gradleTool?.resolvedPath ?: "NOT FOUND"}"))
    send(BuildStepEvent.Log("aapt2: ${aapt2Tool?.resolvedPath ?: "NOT FOUND"}"))
    send(BuildStepEvent.Log("d8: ${d8Tool?.resolvedPath ?: "NOT FOUND"}"))
    send(
      BuildStepEvent.Log(
        "Termux app (com.termux): ${if (env.termuxInstalled) "INSTALLED (${env.termuxVersion})" else "NOT INSTALLED"}"
      )
    )

    val scriptFile = generateBuildScript(rootDir, summary, onlineGradle, env)
    send(BuildStepEvent.Log(""))
    send(BuildStepEvent.Log("Generated self-healing build script: ${scriptFile.name}"))

    val canRunGradleLocally =
      javaTool?.isAvailable == true &&
        (gradleTool?.isAvailable == true || summary.hasGradlewScript)

    if (!canRunGradleLocally) {
      send(BuildStepEvent.Log(""))
      send(
        BuildStepEvent.Log(
          "STOPPED: Android's app sandbox does not ship OpenJDK (java), Gradle, aapt2, or d8 on PATH."
        )
      )
      send(
        BuildStepEvent.Log(
          if (env.termuxInstalled) {
            "Options: Tap 'Run in Termux' to compile natively on-device, switch to Cloud (GitHub Actions), or export .zip + ${scriptFile.name}."
          } else {
            "Options: Switch to Cloud (GitHub Actions) to build online & download the APK, or install Termux and export .zip + ${scriptFile.name}."
          }
        )
      )
      send(
        BuildStepEvent.Completed(
          status = BuildOutcomeStatus.TOOLCHAIN_MISSING,
          summary = "Sandbox has no java/gradle binaries. Use Termux or Cloud Build fallback.",
          durationMs = SystemClock.elapsedRealtime() - startElapsed,
          scriptPath = scriptFile.absolutePath,
          apkPaths = summary.builtApkPaths,
        )
      )
      return@channelFlow
    }

    val offlineFlag = if (onlineGradle) "" else " --offline"
    val runner =
      if (gradleTool?.isAvailable == true && !summary.hasGradlewScript) "gradle"
      else if (summary.hasGradlewScript) "sh ./gradlew"
      else "gradle"
    val gradleCmd = "$runner assembleDebug$offlineFlag $turboFlags"

    send(BuildStepEvent.Log(""))
    send(BuildStepEvent.Log("Executing: $gradleCmd"))
    val previousDir = shellEngine.currentWorkingDir
    shellEngine.setWorkingDirectory(rootDir)

    var exitCode = -1
    shellEngine.executeCommand(gradleCmd).collect { event ->
      when (event) {
        is ShellEvent.Line -> send(BuildStepEvent.Log(event.text))
        is ShellEvent.Finished -> exitCode = event.exitCode
      }
    }

    // Smart auto-recovery: if offline run fails, retry once allowing dependency download or system gradle fallback
    if (exitCode != 0 && !onlineGradle) {
      send(BuildStepEvent.Log(""))
      send(BuildStepEvent.Log("Offline pass exited $exitCode — retrying resilient online dependency resolution…"))
      val retryRunner = if (gradleTool?.isAvailable == true) "gradle" else runner
      val retryCmd = "$retryRunner assembleDebug $turboFlags"
      shellEngine.executeCommand(retryCmd).collect { event ->
        when (event) {
          is ShellEvent.Line -> send(BuildStepEvent.Log(event.text))
          is ShellEvent.Finished -> exitCode = event.exitCode
        }
      }
    }
    shellEngine.setWorkingDirectory(previousDir)

    val updatedSummary = inspectProject(rootDir)
    val duration = SystemClock.elapsedRealtime() - startElapsed
    if (exitCode == 0 && updatedSummary.builtApkPaths.isNotEmpty()) {
      send(
        BuildStepEvent.Completed(
          status = BuildOutcomeStatus.APK_BUILT,
          summary = "Gradle finished (exit 0). Built ${updatedSummary.builtApkPaths.size} APK(s).",
          durationMs = duration,
          scriptPath = scriptFile.absolutePath,
          apkPaths = updatedSummary.builtApkPaths,
        )
      )
    } else {
      send(
        BuildStepEvent.Completed(
          status = BuildOutcomeStatus.GRADLE_FAILED,
          summary = "Gradle exited with code $exitCode.",
          durationMs = duration,
          scriptPath = scriptFile.absolutePath,
          apkPaths = updatedSummary.builtApkPaths,
        )
      )
    }
  }

  private fun generateBuildScript(
    rootDir: File,
    summary: AndroidProjectSummary,
    onlineGradle: Boolean,
    env: SystemEnvironmentInfo,
  ): File {
    val workers = env.cpuCores.coerceAtLeast(1)
    val heapMb = env.recommendedHeapMb
    val useDaemon = !env.lowMemory && (env.totalRamBytes == 0L || env.totalRamBytes >= 3_500_000_000L)
    val daemonFlag = if (useDaemon) "--daemon" else "--no-daemon"
    val scriptName = if (onlineGradle) "build_online.sh" else "build_offline.sh"
    val buildStepBlock =
      if (!onlineGradle) {
        """
        if ! ${'$'}RUNNER assembleDebug --offline ${'$'}COMMON_FLAGS; then
          echo "[TermForge] Offline cache miss — retrying with network dependencies enabled..."
          ${'$'}RUNNER assembleDebug ${'$'}COMMON_FLAGS
        fi
        """.trimIndent()
      } else {
        "${'$'}RUNNER assembleDebug ${'$'}COMMON_FLAGS"
      }
    val script =
      """
      #!/usr/bin/env sh
      set -eu

      # Generated by TermForge (${if (onlineGradle) "Online" else "Resilient Offline"} • $workers-core ARM64 hardened) for: ${summary.workspaceName}
      # One-time toolchain setup in Termux:
      #   pkg update && pkg install -y openjdk-21 gradle aapt2 apksigner unzip curl git (or openjdk-17)
      export JAVA_HOME="${'$'}{JAVA_HOME:-/data/data/com.termux/files/usr/opt/openjdk}"
      export ANDROID_HOME="${'$'}{ANDROID_HOME:-${'$'}HOME/android-sdk}"
      export ANDROID_SDK_ROOT="${'$'}{ANDROID_SDK_ROOT:-${'$'}ANDROID_HOME}"
      export GRADLE_USER_HOME="${'$'}{GRADLE_USER_HOME:-${'$'}HOME/.gradle}"
      export GRADLE_OPTS="${'$'}{GRADLE_OPTS:--Xmx${heapMb}m -XX:MaxMetaspaceSize=512m -XX:+UseParallelGC}"

      # Auto-repair local.properties if missing and SDK exists
      if [ ! -f local.properties ] && [ -d "${'$'}ANDROID_HOME" ]; then
        printf 'sdk.dir=%s\n' "${'$'}ANDROID_HOME" > local.properties
      fi

      # Restore debug.keystore from debug.keystore.base64 (or generate fallback) for upgrade-compatible signing
      if [ ! -f debug.keystore ]; then
        if [ -f debug.keystore.base64 ] && command -v base64 >/dev/null 2>&1; then
          base64 -d debug.keystore.base64 > debug.keystore
        elif command -v keytool >/dev/null 2>&1; then
          keytool -genkeypair -noprompt -keystore debug.keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US" || true
        fi
      fi

      # Auto-detect ARM64 aapt2 to prevent x86_64 Maven binary crash on phones
      AAPT2_FLAG=""
      AAPT2_BIN="${'$'}(command -v aapt2 2>/dev/null || true)"
      if [ -z "${'$'}AAPT2_BIN" ] && [ -x /data/data/com.termux/files/usr/bin/aapt2 ]; then
        AAPT2_BIN="/data/data/com.termux/files/usr/bin/aapt2"
      fi
      if [ -n "${'$'}AAPT2_BIN" ]; then
        AAPT2_FLAG="-Pandroid.aapt2FromMavenOverride=${'$'}AAPT2_BIN"
        echo "[TermForge] Using native ARM64 aapt2: ${'$'}AAPT2_BIN"
      fi

      # Prefer system gradle in Termux if present (avoids wrapper download issues), otherwise use ./gradlew
      RUNNER=""
      if command -v gradle >/dev/null 2>&1; then
        RUNNER="gradle"
      elif [ -f ./gradlew ]; then
        chmod +x ./gradlew || true
        RUNNER="./gradlew"
      else
        echo "[TermForge] ERROR: Neither gradle nor ./gradlew found."
        exit 127
      fi

      COMMON_FLAGS="--max-workers=$workers --parallel --build-cache --configure-on-demand $daemonFlag ${'$'}AAPT2_FLAG"
      echo "[TermForge] Project root: $(pwd)"
      echo "[TermForge] Runner: ${'$'}RUNNER | $workers workers, -Xmx${heapMb}m"

      $buildStepBlock

      echo "[TermForge] Built APK outputs:"
      APK_LIST="${'$'}(find app/build/outputs/apk -type f -name "*.apk" 2>/dev/null || true)"
      printf '%s\n' "${'$'}APK_LIST"
      FIRST_APK="${'$'}(printf '%s\n' "${'$'}APK_LIST" | head -n 1)"
      if [ -n "${'$'}FIRST_APK" ] && command -v termux-open >/dev/null 2>&1; then
        echo "[TermForge] Opening built APK in Android installer..."
        termux-open "${'$'}FIRST_APK" || true
      fi
      """
        .trimIndent() + "\n"

    val outFile = File(rootDir, scriptName)
    outFile.writeText(script)
    outFile.setExecutable(true)
    return outFile
  }

  private fun parseManifest(rootDir: File, manifestFile: File): ParsedManifest {
    val text = runCatching { manifestFile.readText() }.getOrDefault("")
    var pkg: String? = null
    var minSdk: String? = null
    var targetSdk: String? = null
    val permissions = mutableListOf<String>()
    val activities = mutableListOf<String>()

    runCatching {
      val factory = XmlPullParserFactory.newInstance()
      val parser = factory.newPullParser()
      parser.setInput(StringReader(text))
      var eventType = parser.eventType
      while (eventType != XmlPullParser.END_DOCUMENT) {
        if (eventType == XmlPullParser.START_TAG) {
          when (parser.name) {
            "manifest" -> {
              pkg = parser.getAttributeValue(null, "package") ?: pkg
            }
            "uses-sdk" -> {
              minSdk =
                parser.getAttributeValue("http://schemas.android.com/apk/res/android", "minSdkVersion")
                  ?: minSdk
              targetSdk =
                parser.getAttributeValue(
                  "http://schemas.android.com/apk/res/android",
                  "targetSdkVersion",
                ) ?: targetSdk
            }
            "uses-permission" -> {
              val name =
                parser.getAttributeValue("http://schemas.android.com/apk/res/android", "name")
              if (!name.isNullOrBlank()) {
                permissions += name.substringAfterLast('.')
              }
            }
            "activity" -> {
              val name =
                parser.getAttributeValue("http://schemas.android.com/apk/res/android", "name")
              if (!name.isNullOrBlank()) {
                activities += name
              }
            }
          }
        }
        eventType = parser.next()
      }
    }

    return ParsedManifest(
      path = manifestFile.relativeTo(rootDir).path,
      packageName = pkg,
      minSdk = minSdk,
      targetSdk = targetSdk,
      permissions = permissions.distinct(),
      activities = activities.distinct(),
    )
  }

  private fun parseGradleFile(rootDir: File, gradleFile: File): ParsedGradleConfig {
    val text = runCatching { gradleFile.readText() }.getOrDefault("")
    val namespace = Regex("""namespace\s*=?\s*["']([^"']+)["']""").find(text)?.groupValues?.getOrNull(1)
    val appId = Regex("""applicationId\s*=?\s*["']([^"']+)["']""").find(text)?.groupValues?.getOrNull(1)
    val compileSdk = Regex("""compileSdk\s*=?\s*(\d+)""").find(text)?.groupValues?.getOrNull(1)
    val minSdk = Regex("""minSdk\s*=?\s*(\d+)""").find(text)?.groupValues?.getOrNull(1)
    val targetSdk = Regex("""targetSdk\s*=?\s*(\d+)""").find(text)?.groupValues?.getOrNull(1)
    val depCount =
      Regex("""^\s*(implementation|api|compileOnly|runtimeOnly|testImplementation|androidTestImplementation|ksp|kapt)\b""", RegexOption.MULTILINE)
        .findAll(text)
        .count()

    return ParsedGradleConfig(
      path = gradleFile.relativeTo(rootDir).path,
      isKotlinDsl = gradleFile.name.endsWith(".kts"),
      namespace = namespace,
      applicationId = appId,
      compileSdk = compileSdk,
      minSdk = minSdk,
      targetSdk = targetSdk,
      dependencyCount = depCount,
    )
  }

  private fun copyDocumentTree(
    treeUri: Uri,
    docId: String,
    targetDir: File,
    depth: Int,
    onFileCopied: (fileName: String, bytesCopied: Long) -> Unit = { _, _ -> },
  ): Int {
    if (depth > 20) return 0
    var count = 0
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
    val projection =
      arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
      )
    context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
      val idIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
      val nameIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
      val mimeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
      while (cursor.moveToNext()) {
        val childId = cursor.getString(idIdx)
        val rawName = cursor.getString(nameIdx) ?: continue
        val safeName = rawName.replace("/", "_").replace("\\", "_")
        val mime = cursor.getString(mimeIdx)
        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
          if (safeName == ".git") continue
          val childDir = File(targetDir, safeName).apply { mkdirs() }
          count += copyDocumentTree(treeUri, childId, childDir, depth + 1, onFileCopied)
        } else {
          val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
          val outFile = File(targetDir, safeName)
          context.contentResolver.openInputStream(docUri)?.use { input ->
            val copied =
              FileOutputStream(outFile).use { output -> input.copyTo(output) }
            count++
            onFileCopied(safeName, copied)
          }
        }
      }
    }
    return count
  }

  private fun unwrapSingleRootDirectoryIfNeeded(targetDir: File) {
    val entries = targetDir.listFiles()?.filter { it.name != "__MACOSX" } ?: return
    if (entries.size == 1 && entries.first().isDirectory) {
      val innerDir = entries.first()
      val hasGradleInside =
        File(innerDir, "settings.gradle.kts").exists() ||
          File(innerDir, "settings.gradle").exists() ||
          File(innerDir, "build.gradle.kts").exists() ||
          File(innerDir, "build.gradle").exists() ||
          File(innerDir, "app").isDirectory
      if (hasGradleInside) {
        innerDir.listFiles()?.forEach { child ->
          val dest = File(targetDir, child.name)
          child.renameTo(dest)
        }
        innerDir.deleteRecursively()
      }
    }
  }

  private fun queryUriMeta(uri: Uri): Pair<String?, Long> {
    return runCatching {
      context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
        null,
        null,
        null,
      )?.use { c ->
        if (c.moveToFirst()) {
          val name = c.getString(0)
          val size = if (!c.isNull(1)) c.getLong(1) else -1L
          name to size
        } else {
          null to -1L
        }
      } ?: (null to -1L)
    }.getOrDefault(null to -1L)
  }

  private fun sanitizeDirName(raw: String): String {
    val cleaned = raw.trim().replace(Regex("[^a-zA-Z0-9._-]"), "_")
    return cleaned.ifBlank { "android_project" }.take(40)
  }

  fun allocateWorkspaceDir(baseName: String): File = uniqueWorkspaceDir(sanitizeDirName(baseName))

  private fun uniqueWorkspaceDir(baseName: String): File {
    var candidate = File(workspacesRoot, baseName)
    var index = 1
    while (candidate.exists()) {
      candidate = File(workspacesRoot, "${baseName}_$index")
      index++
    }
    return candidate
  }

  private fun writeProjectFile(root: File, relativePath: String, content: String) {
    val file = File(root, relativePath)
    file.parentFile?.mkdirs()
    file.writeText(content)
  }

  private fun isEditableTextFile(file: File): Boolean {
    val ext = file.extension.lowercase()
    return ext in
      setOf(
        "kt",
        "java",
        "kts",
        "gradle",
        "xml",
        "properties",
        "pro",
        "sh",
        "txt",
        "md",
        "json",
        "yaml",
        "yml",
        "toml",
      ) || file.name in setOf("gradlew", "Dockerfile", "Makefile", ".gitignore")
  }
}
