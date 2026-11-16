package com.example.data

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.StatFs
import android.os.SystemClock
import android.system.Os
import androidx.core.content.ContextCompat
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext

data class ToolAvailability(
  val name: String,
  val resolvedPath: String?,
) {
  val isAvailable: Boolean
    get() = !resolvedPath.isNullOrBlank()
}

data class SystemEnvironmentInfo(
  val kernelName: String,
  val kernelRelease: String,
  val machineArch: String,
  val supportedAbis: List<String>,
  val cpuCores: Int,
  val totalRamBytes: Long,
  val availRamBytes: Long,
  val lowMemory: Boolean,
  val recommendedHeapMb: Int,
  val freeStorageBytes: Long,
  val homeDir: String,
  val workspacesDir: String,
  val nativeLibDir: String,
  val bundledNativeBins: List<String>,
  val termuxInstalled: Boolean,
  val termuxVersion: String?,
  val hasTermuxPermission: Boolean,
  val termuxApiInstalled: Boolean,
  val systemBinToolsCount: Int,
  val tools: List<ToolAvailability>,
)

data class FsEntry(
  val name: String,
  val absolutePath: String,
  val isDirectory: Boolean,
  val sizeBytes: Long,
  val canExecute: Boolean,
)

sealed interface ShellEvent {
  data class Line(val text: String) : ShellEvent

  data class Finished(val exitCode: Int, val durationMs: Long, val newCwd: String) : ShellEvent
}

class ShellEngine(private val context: Context) {
  val homeDir: File = File(context.filesDir, "home").apply { mkdirs() }
  val tmpDir: File = File(context.filesDir, "tmp").apply { mkdirs() }
  val prefixDir: File = File(context.filesDir, "usr").apply { mkdirs() }
  val workspacesDir: File = File(context.filesDir, "workspaces").apply { mkdirs() }
  val nativeLibDirPath: String = context.applicationInfo.nativeLibraryDir.orEmpty()

  private val envVars: MutableMap<String, String> =
    mutableMapOf(
      "HOME" to homeDir.absolutePath,
      "TMPDIR" to tmpDir.absolutePath,
      "PREFIX" to prefixDir.absolutePath,
      "TERM" to "xterm-256color",
      "LANG" to "en_US.UTF-8",
      "WORKSPACES" to workspacesDir.absolutePath,
      "NATIVE_LIB_DIR" to nativeLibDirPath,
    )

  @Volatile var currentWorkingDir: File = homeDir
    private set

  @Volatile private var activeProcess: Process? = null

  init {
    val binDir = File(prefixDir, "bin").apply { mkdirs() }
    val systemPath = System.getenv("PATH") ?: "/system/bin:/system/xbin:/vendor/bin"
    val termuxBin = "/data/data/com.termux/files/usr/bin"
    val pathSegments =
      listOfNotNull(
        nativeLibDirPath.takeIf { it.isNotBlank() },
        binDir.absolutePath,
        systemPath,
        termuxBin,
      )
    envVars["PATH"] = pathSegments.joinToString(":")
    val existingLd = System.getenv("LD_LIBRARY_PATH").orEmpty()
    val ldSegments =
      listOfNotNull(
        nativeLibDirPath.takeIf { it.isNotBlank() },
        File(prefixDir, "lib").apply { mkdirs() }.absolutePath,
        existingLd.takeIf { it.isNotBlank() },
      )
    envVars["LD_LIBRARY_PATH"] = ldSegments.joinToString(":")
    val memInfo = readMemoryInfo()
    val heapMb = computeRecommendedHeapMb(memInfo.totalMem)
    envVars["GRADLE_OPTS"] = "-Xmx${heapMb}m -XX:+UseParallelGC"
    envVars["JAVA_TOOL_OPTIONS"] = "-XX:+UseParallelGC"
  }

  private fun readMemoryInfo(): ActivityManager.MemoryInfo {
    val memInfo = ActivityManager.MemoryInfo()
    runCatching {
      val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
      am?.getMemoryInfo(memInfo)
    }
    return memInfo
  }

  private fun computeRecommendedHeapMb(totalRamBytes: Long): Int {
    if (totalRamBytes <= 0L) return 1024
    val totalMb = totalRamBytes / (1024L * 1024L)
    val raw = ((totalMb * 35L) / 100L).toInt()
    val aligned = (raw / 128) * 128
    return aligned.coerceIn(512, 4096)
  }

  fun setWorkingDirectory(dir: File): Boolean {
    if (dir.exists() && dir.isDirectory && dir.canRead()) {
      currentWorkingDir = dir
      return true
    }
    return false
  }

  fun stopActiveProcess() {
    activeProcess?.let { proc ->
      proc.destroy()
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && proc.isAlive) {
        proc.destroyForcibly()
      }
    }
  }

  fun hasTermuxRunPermission(): Boolean {
    return ContextCompat.checkSelfPermission(context, TERMUX_RUN_PERMISSION) ==
      PackageManager.PERMISSION_GRANTED
  }

  fun createInstallTermuxIntent(): Intent {
    return Intent(Intent.ACTION_VIEW, Uri.parse(TERMUX_RELEASES_URL)).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
  }

  suspend fun inspectEnvironment(): SystemEnvironmentInfo =
    withContext(Dispatchers.IO) {
      val uname = runCatching { Os.uname() }.getOrNull()
      val stat = runCatching { StatFs(context.filesDir.absolutePath) }.getOrNull()
      val freeBytes = stat?.availableBytes ?: 0L
      val memInfo = readMemoryInfo()
      val cpuCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
      val heapMb = computeRecommendedHeapMb(memInfo.totalMem)

      val termuxInfo = getPackageVersion(TERMUX_PACKAGE)
      val termuxPerm = termuxInfo != null && hasTermuxRunPermission()
      val termuxApiInfo = getPackageVersion("com.termux.api")

      val probedTools =
        listOf("sh", "toybox", "busybox", "uname", "id", "ls", "java", "gradle", "kotlinc", "aapt2", "d8", "proot")
          .map { tool ->
            ToolAvailability(
              name = tool,
              resolvedPath = resolveBinary(tool),
            )
          }

      val bundledBins = listBundledNativeTools()
      val systemBinCount =
        File("/system/bin").listFiles()?.count { it.isFile } ?: 0

      SystemEnvironmentInfo(
        kernelName = uname?.sysname ?: "Linux",
        kernelRelease = uname?.release ?: System.getProperty("os.version") ?: "unknown",
        machineArch = uname?.machine ?: Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
        supportedAbis = Build.SUPPORTED_ABIS.toList(),
        cpuCores = cpuCores,
        totalRamBytes = memInfo.totalMem,
        availRamBytes = memInfo.availMem,
        lowMemory = memInfo.lowMemory,
        recommendedHeapMb = heapMb,
        freeStorageBytes = freeBytes,
        homeDir = homeDir.absolutePath,
        workspacesDir = workspacesDir.absolutePath,
        nativeLibDir = nativeLibDirPath,
        bundledNativeBins = bundledBins,
        termuxInstalled = termuxInfo != null,
        termuxVersion = termuxInfo,
        hasTermuxPermission = termuxPerm,
        termuxApiInstalled = termuxApiInfo != null,
        systemBinToolsCount = systemBinCount,
        tools = probedTools,
      )
    }

  fun listBundledNativeTools(): List<String> {
    if (nativeLibDirPath.isBlank()) return emptyList()
    val dir = File(nativeLibDirPath)
    if (!dir.exists() || !dir.isDirectory) return emptyList()
    return dir.listFiles()
      ?.filter { it.isFile && (it.canExecute() || it.name.endsWith(".so")) }
      ?.map { it.name }
      ?.sorted()
      ?: emptyList()
  }

  fun currentEnvSnapshot(): Map<String, String> = envVars.toMap()

  fun createLaunchTermuxIntent(): Intent? {
    return context.packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE)
  }

  suspend fun listDirectory(dir: File = currentWorkingDir): List<FsEntry> =
    withContext(Dispatchers.IO) {
      val children = dir.listFiles() ?: return@withContext emptyList()
      children
        .sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
        .map { file ->
          FsEntry(
            name = file.name,
            absolutePath = file.absolutePath,
            isDirectory = file.isDirectory,
            sizeBytes = if (file.isFile) file.length() else 0L,
            canExecute = file.canExecute(),
          )
        }
    }

  fun executeCommand(rawCommand: String): Flow<ShellEvent> = channelFlow {
    val startElapsed = SystemClock.elapsedRealtime()
    val trimmed = rawCommand.trim()
    if (trimmed.isEmpty()) {
      send(ShellEvent.Finished(0, 0L, currentWorkingDir.absolutePath))
      return@channelFlow
    }

    // Handle stateful shell builtins directly so cwd and env persist across commands
    if (trimmed == "pwd") {
      send(ShellEvent.Line(currentWorkingDir.absolutePath))
      send(
        ShellEvent.Finished(
          0,
          SystemClock.elapsedRealtime() - startElapsed,
          currentWorkingDir.absolutePath,
        )
      )
      return@channelFlow
    }

    if (trimmed == "cd" || trimmed.startsWith("cd ")) {
      val arg = trimmed.removePrefix("cd").trim()
      val target =
        when {
          arg.isEmpty() || arg == "~" -> homeDir
          arg == "-" -> homeDir
          arg.startsWith("~/") -> File(homeDir, arg.removePrefix("~/"))
          arg.startsWith("/") -> File(arg)
          else -> File(currentWorkingDir, arg)
        }.canonicalFile

      if (target.exists() && target.isDirectory && target.canRead()) {
        currentWorkingDir = target
        send(ShellEvent.Line("cwd -> ${currentWorkingDir.absolutePath}"))
        send(
          ShellEvent.Finished(
            0,
            SystemClock.elapsedRealtime() - startElapsed,
            currentWorkingDir.absolutePath,
          )
        )
      } else {
        send(ShellEvent.Line("sh: cd: $arg: No accessible directory"))
        send(
          ShellEvent.Finished(
            1,
            SystemClock.elapsedRealtime() - startElapsed,
            currentWorkingDir.absolutePath,
          )
        )
      }
      return@channelFlow
    }

    if (trimmed.startsWith("export ") && trimmed.contains("=")) {
      val assignment = trimmed.removePrefix("export ").trim()
      val eqIndex = assignment.indexOf('=')
      if (eqIndex > 0) {
        val key = assignment.substring(0, eqIndex).trim()
        val value = assignment.substring(eqIndex + 1).trim().trim('"', '\'')
        envVars[key] = value
        send(ShellEvent.Line("exported $key=$value"))
        send(
          ShellEvent.Finished(
            0,
            SystemClock.elapsedRealtime() - startElapsed,
            currentWorkingDir.absolutePath,
          )
        )
        return@channelFlow
      }
    }

    if (trimmed == "tools") {
      val bundled = listBundledNativeTools()
      if (bundled.isEmpty()) {
        send(ShellEvent.Line("NATIVE_LIB_DIR ($nativeLibDirPath): no extra bundled lib*.so CLIs"))
      } else {
        send(ShellEvent.Line("NATIVE_LIB_DIR (${bundled.size} bundled executables):"))
        bundled.chunked(4).forEach { row -> send(ShellEvent.Line("  " + row.joinToString("  "))) }
      }
      val bins =
        File("/system/bin")
          .listFiles()
          ?.filter { it.isFile }
          ?.map { it.name }
          ?.sorted()
          ?: emptyList()
      if (bins.isEmpty()) {
        send(ShellEvent.Line("No readable binaries found in /system/bin"))
      } else {
        send(ShellEvent.Line("/system/bin (${bins.size} entries):"))
        bins.chunked(6).forEach { row -> send(ShellEvent.Line("  " + row.joinToString("  "))) }
      }
      send(
        ShellEvent.Finished(
          0,
          SystemClock.elapsedRealtime() - startElapsed,
          currentWorkingDir.absolutePath,
        )
      )
      return@channelFlow
    }

    withContext(Dispatchers.IO) {
      try {
        val pb = ProcessBuilder("/system/bin/sh", "-c", trimmed)
        pb.directory(currentWorkingDir)
        pb.redirectErrorStream(true)
        pb.environment().putAll(envVars)
        pb.environment()["PWD"] = currentWorkingDir.absolutePath

        val process = pb.start()
        activeProcess = process

        BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
          var line: String? = reader.readLine()
          while (line != null) {
            send(ShellEvent.Line(line))
            line = reader.readLine()
          }
        }

        val exitCode = process.waitFor()
        activeProcess = null
        send(
          ShellEvent.Finished(
            exitCode = exitCode,
            durationMs = SystemClock.elapsedRealtime() - startElapsed,
            newCwd = currentWorkingDir.absolutePath,
          )
        )
      } catch (e: Exception) {
        activeProcess = null
        send(ShellEvent.Line("exec error: ${e.message ?: e::class.java.simpleName}"))
        send(
          ShellEvent.Finished(
            exitCode = 126,
            durationMs = SystemClock.elapsedRealtime() - startElapsed,
            newCwd = currentWorkingDir.absolutePath,
          )
        )
      }
    }
  }

  private fun resolveBinary(tool: String): String? {
    if (nativeLibDirPath.isNotBlank()) {
      val libCandidate = File(nativeLibDirPath, "lib${tool}.so")
      if (libCandidate.exists() && libCandidate.isFile && libCandidate.canExecute()) {
        return libCandidate.absolutePath
      }
    }
    val searchPaths = (envVars["PATH"] ?: "/system/bin:/system/xbin").split(":")
    for (dir in searchPaths) {
      if (dir.isBlank()) continue
      val candidate = File(dir, tool)
      if (candidate.exists() && candidate.isFile && candidate.canExecute()) {
        return candidate.absolutePath
      }
    }
    return runCatching {
      val proc = ProcessBuilder("/system/bin/sh", "-c", "command -v $tool").start()
      val out = proc.inputStream.bufferedReader().readText().trim()
      val code = proc.waitFor()
      if (code == 0 && out.isNotBlank()) out else null
    }.getOrNull()
  }

  private fun getPackageVersion(packageName: String): String? {
    return runCatching {
      val info =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          context.packageManager.getPackageInfo(
            packageName,
            PackageManager.PackageInfoFlags.of(0),
          )
        } else {
          @Suppress("DEPRECATION")
          context.packageManager.getPackageInfo(packageName, 0)
        }
      info.versionName ?: "installed"
    }.getOrNull()
  }

  companion object {
    const val TERMUX_PACKAGE = "com.termux"
    const val TERMUX_RUN_PERMISSION = "com.termux.permission.RUN_COMMAND"
    const val TERMUX_RELEASES_URL = "https://github.com/termux/termux-app/releases"
  }
}
