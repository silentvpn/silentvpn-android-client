package com.v2ray.ang.handler

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.ProfileItem
import com.v2ray.ang.extension.toSpeedString
import com.v2ray.ang.handler.V2RayServiceManager
import com.v2ray.ang.shadowmm.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.min

object NotificationManager {
    private const val NOTIFICATION_ID = 1
    private const val NOTIFICATION_PENDING_INTENT_CONTENT = 0
    private const val NOTIFICATION_ICON_THRESHOLD = 3000

    private var lastQueryTime = 0L
    private var mBuilder: NotificationCompat.Builder? = null
    private var speedNotificationJob: Job? = null
    private var mNotificationManager: NotificationManager? = null

    fun startSpeedNotification(currentConfig: ProfileItem?) {
        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_SPEED_ENABLED) != true) return
        if (speedNotificationJob != null || V2RayServiceManager.isRunning() == false) return

        lastQueryTime = System.currentTimeMillis()
        var lastZeroSpeed = false
        val outboundTags = currentConfig?.getAllOutboundTags()
        outboundTags?.remove(AppConfig.TAG_DIRECT)

        speedNotificationJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                val queryTime = System.currentTimeMillis()
                val sinceLastQueryInSeconds = (queryTime - lastQueryTime) / 1000.0
                var proxyTotal = 0L
                val text = StringBuilder()
                outboundTags?.forEach {
                    val up = V2RayServiceManager.queryStats(it, AppConfig.UPLINK)
                    val down = V2RayServiceManager.queryStats(it, AppConfig.DOWNLINK)
                    if (up + down > 0) {
                        appendSpeedString(text, it, up / sinceLastQueryInSeconds, down / sinceLastQueryInSeconds)
                        proxyTotal += up + down
                    }
                }
                val directUplink = V2RayServiceManager.queryStats(AppConfig.TAG_DIRECT, AppConfig.UPLINK)
                val directDownlink = V2RayServiceManager.queryStats(AppConfig.TAG_DIRECT, AppConfig.DOWNLINK)
                val zeroSpeed = proxyTotal == 0L && directUplink == 0L && directDownlink == 0L
                if (!zeroSpeed || !lastZeroSpeed) {
                    if (proxyTotal == 0L) {
                        appendSpeedString(text, outboundTags?.firstOrNull(), 0.0, 0.0)
                    }
                    appendSpeedString(
                        text, AppConfig.TAG_DIRECT, directUplink / sinceLastQueryInSeconds,
                        directDownlink / sinceLastQueryInSeconds
                    )
                    updateNotification(text.toString(), proxyTotal, directDownlink + directUplink)
                }
                lastZeroSpeed = zeroSpeed
                lastQueryTime = queryTime
                delay(3000)
            }
        }
    }

    fun showNotification(currentConfig: ProfileItem?) {
        val service = getService() ?: return
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        // Click on Notification -> Open MainActivity (ShadowMM)
        val startMainIntent = Intent(service, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(service, NOTIFICATION_PENDING_INTENT_CONTENT, startMainIntent, flags)

        val channelId =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                createNotificationChannel()
            } else {
                ""
            }

        // 🔥 CLEAN NOTIFICATION: Removed Action Buttons (Stop/Restart)
        mBuilder = NotificationCompat.Builder(service, channelId)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(currentConfig?.remarks)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentPendingIntent)
        // .addAction(...) <-- Removed STOP button
        // .addAction(...) <-- Removed RESTART button

        service.startForeground(NOTIFICATION_ID, mBuilder?.build())
    }

    fun cancelNotification() {
        val service = getService() ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            service.stopForeground(Service.STOP_FOREGROUND_REMOVE)
        } else {
            service.stopForeground(true)
        }

        getNotificationManager()?.cancel(NOTIFICATION_ID)

        mBuilder = null
        speedNotificationJob?.cancel()
        speedNotificationJob = null
        mNotificationManager = null
    }

    fun stopSpeedNotification(currentConfig: ProfileItem?) {
        speedNotificationJob?.let {
            it.cancel()
            speedNotificationJob = null
            updateNotification(currentConfig?.remarks, 0, 0)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(): String {
        val channelId = AppConfig.RAY_NG_CHANNEL_ID
        val channelName = AppConfig.RAY_NG_CHANNEL_NAME
        val chan = NotificationChannel(
            channelId,
            channelName, NotificationManager.IMPORTANCE_HIGH
        )
        chan.lightColor = Color.DKGRAY
        chan.importance = NotificationManager.IMPORTANCE_NONE
        chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        getNotificationManager()?.createNotificationChannel(chan)
        return channelId
    }

    private fun updateNotification(contentText: String?, proxyTraffic: Long, directTraffic: Long) {
        if (mBuilder != null) {
            if (proxyTraffic < NOTIFICATION_ICON_THRESHOLD && directTraffic < NOTIFICATION_ICON_THRESHOLD) {
                mBuilder?.setSmallIcon(R.drawable.ic_stat_name)
            } else if (proxyTraffic > directTraffic) {
                mBuilder?.setSmallIcon(R.drawable.ic_stat_proxy)
            } else {
                mBuilder?.setSmallIcon(R.drawable.ic_stat_direct)
            }
            mBuilder?.setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            mBuilder?.setContentText(contentText)
            getNotificationManager()?.notify(NOTIFICATION_ID, mBuilder?.build())
        }
    }

    private fun getNotificationManager(): NotificationManager? {
        if (mNotificationManager == null) {
            val service = getService() ?: return null
            mNotificationManager = service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        }
        return mNotificationManager
    }

    private fun appendSpeedString(text: StringBuilder, name: String?, up: Double, down: Double) {
        var n = name ?: "no tag"
        n = n.substring(0, min(n.length, 6))
        text.append(n)
        for (i in n.length..6 step 2) {
            text.append("\t")
        }
        text.append("•  ${up.toLong().toSpeedString()}↑  ${down.toLong().toSpeedString()}↓\n")
    }
    // 🔥 NEW: 80% / 90% Warning Notification
    fun showUsageWarning(percent: Int) {
        val service = getService() ?: return
        val builder = NotificationCompat.Builder(service, AppConfig.RAY_NG_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle("Data Warning")
            .setContentText("You have used $percent% of your daily limit.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        getNotificationManager()?.notify(2, builder.build()) // ID 2 for warnings
    }

    // 🔥 NEW: Limit Reached with "Get More Data" Action
    fun showLimitReachedAlert() {
        val service = getService() ?: return


        val intent = Intent(service, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("SHOW_LIMIT_DIALOG", true) // 🔥 Trigger Dialog
        }
        val pendingIntent = PendingIntent.getActivity(
            service, 0, intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(service, AppConfig.RAY_NG_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle("Daily Limit Reached!")
            .setContentText("You have reached your daily limit.")
            .setPriority(NotificationCompat.PRIORITY_HIGH) // Popup
            .setDefaults(Notification.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_stat_name, "Get More Data", pendingIntent) // Button
            .setAutoCancel(true)

        getNotificationManager()?.notify(3, builder.build()) // ID 3 for Limit Alert
    }

    private fun getService(): Service? {
        return V2RayServiceManager.serviceControl?.get()?.getService()
    }
}