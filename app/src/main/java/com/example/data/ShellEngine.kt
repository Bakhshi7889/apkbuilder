package com.example.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.StatFs
import android.os.SystemClock
import android.system.Os
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext

import android.app.ActivityManager

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
  val termuxInstalled: Boolean,
  val termuxVersion: String?,
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

  private val envVars: MutableMap<String, String> =
    mutableMapOf(
      "HOME" to homeDir.absolutePath,
      "TMPDIR" to tmpDir.absolutePath,
      "PREFIX" to prefixDir.absolutePath,
      "TERM" to "xterm-256color",
      "LANG" to "en_US.UTF-8",
      "WORKSPACES" to workspacesDir.absolutePath,
    )

  @Volatile var currentWorkingDir: File = homeDir
    private set

  @Volatile private var activeProcess: Process? = null

  init {
    val binDir = File(prefixDir, "bin").apply { mkdirs() }
    val systemPath = System.getenv("PATH") ?: "/system/bin:/system/xbin:/vendor/bin"
    val termuxBin = "/data/data/com.termux/files/usr/bin"
    envVars["PATH"] = "${binDir.absolutePath}:$systemPath:$termuxBin"
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

  suspend fun inspectEnvironment(): SystemEnvironmentInfo =
    withContext(Dispatchers.IO) {
      val uname = runCatching { Os.uname() }.getOrNull()
      val stat = runCatching { StatFs(context.filesDir.absolutePath) }.getOrNull()
      val freeBytes = stat?.availableBytes ?: 0L
      val memInfo = readMemoryInfo()
      val cpuCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
      val heapMb = computeRecommendedHeapMb(memInfo.totalMem)

      val termuxInfo = getPackageVersion("com.termux")
      val termuxApiInfo = getPackageVersion("com.termux.api")

      val probedTools =
        listOf("sh", "toybox", "busybox", "uname", "id", "ls", "java", "gradle", "kotlinc", "aapt2", "d8", "proot")
          .map { tool ->
            ToolAvailability(
              name = tool,
              resolvedPath = resolveBinary(tool),
            )
          }

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
        termuxInstalled = termuxInfo != null,
        termuxVersion = termuxInfo,
        termuxApiInstalled = termuxApiInfo != null,
        systemBinToolsCount = systemBinCount,
        tools = probedTools,
      )
    }

  fun createLaunchTermuxIntent(): Intent? {
    return context.packageManager.getLaunchIntentForPackage("com.termux")
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
}
