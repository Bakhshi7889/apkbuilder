package com.example.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

class UpdateNotifier(private val context: Context) {
  private val manager =
    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

  fun canPostNotifications(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    } else {
      true
    }
  }

  fun notifyGitHubUpdate(
    repoName: String,
    branch: String,
    shortSha: String,
    commitMessage: String,
    releaseTag: String?,
  ) {
    if (!canPostNotifications()) return
    ensureChannel()

    val intent =
      Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      }
    val pendingIntent =
      PendingIntent.getActivity(
        context,
        repoName.hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val title = "$repoName updated ($branch @ $shortSha)"
    val body =
      buildString {
        if (commitMessage.isNotBlank()) append(commitMessage)
        if (!releaseTag.isNullOrBlank()) {
          if (isNotEmpty()) append(" • ")
          append("Release $releaseTag")
        }
      }.ifBlank { "New commit pushed to GitHub. Tap to pull and rebuild." }

    val notification =
      NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)
        .build()

    manager.notify(repoName.hashCode(), notification)
  }

  fun notifyMultiAppUpdates(updatedApps: List<WatchedAppEntry>) {
    if (updatedApps.isEmpty() || !canPostNotifications()) return
    if (updatedApps.size == 1) {
      val single = updatedApps.first()
      val full = "${single.owner}/${single.repo}"
      notifyGitHubUpdate(
        repoName = full,
        branch = single.branch,
        shortSha = (single.remoteSha ?: single.baselineSha).take(7),
        commitMessage = single.remoteMessage ?: single.baselineMessage,
        releaseTag = single.remoteReleaseTag,
      )
      return
    }

    ensureChannel()
    val intent =
      Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      }
    val pendingIntent =
      PendingIntent.getActivity(
        context,
        MULTI_UPDATE_NOTIFICATION_ID,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val title = "${updatedApps.size} GitHub apps have updates"
    val summaryLines =
      updatedApps.map { entry ->
        val rel = entry.remoteReleaseTag?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
        val sha = entry.remoteSha?.take(7)?.let { " @ $it" }.orEmpty()
        "• ${entry.owner}/${entry.repo}$rel$sha"
      }
    val preview =
      updatedApps.joinToString(", ") { "${it.owner}/${it.repo}" }

    val inboxStyle = NotificationCompat.InboxStyle().setBigContentTitle(title)
    summaryLines.take(5).forEach { inboxStyle.addLine(it) }
    if (summaryLines.size > 5) {
      inboxStyle.setSummaryText("+${summaryLines.size - 5} more updated")
    }

    val notification =
      NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(title)
        .setContentText(preview)
        .setStyle(inboxStyle)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)
        .build()

    manager.notify(MULTI_UPDATE_NOTIFICATION_ID, notification)
  }

  private fun ensureChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel =
        NotificationChannel(
          CHANNEL_ID,
          "GitHub Project Updates",
          NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
          description = "Notifies when watched GitHub Android repositories have new commits or APK releases."
        }
      manager.createNotificationChannel(channel)
    }
  }

  companion object {
    private const val CHANNEL_ID = "termforge_github_updates"
    private const val MULTI_UPDATE_NOTIFICATION_ID = 41002
  }
}
