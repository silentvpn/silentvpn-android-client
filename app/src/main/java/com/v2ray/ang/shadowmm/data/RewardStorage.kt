package com.v2ray.ang.shadowmm.data

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Stores per-day ad watch count and extra data MB.
 *
 * - Each ad: +150 MB
 * - Max 4 ads per day (handled in UI)
 * - Resets automatically when day changes.
 */
object RewardStorage {

    private const val PREFS_NAME = "shadowlink_rewards"
    private const val KEY_DAY = "day_key"
    private const val KEY_AD_COUNT = "ad_count"
    private const val KEY_REWARD_MB = "reward_mb"

    data class RewardState(
        val extraDataTodayMB: Int = 0,      // <<--- THIS NAME
        val adWatchCountToday: Int = 0
    )

    private fun todayKey(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date())
    }

    fun loadState(context: Context): RewardState {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storedDay = prefs.getString(KEY_DAY, null)
        val today = todayKey()

        return if (storedDay == today) {
            val mb = prefs.getInt(KEY_REWARD_MB, 0)
            val count = prefs.getInt(KEY_AD_COUNT, 0)
            RewardState(
                extraDataTodayMB = mb,
                adWatchCountToday = count
            )
        } else {
            // new day -> reset everything
            prefs.edit()
                .putString(KEY_DAY, today)
                .putInt(KEY_AD_COUNT, 0)
                .putInt(KEY_REWARD_MB, 0)
                .apply()

            RewardState(extraDataTodayMB = 0, adWatchCountToday = 0)
        }
    }

    fun saveState(context: Context, state: RewardState) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_DAY, todayKey())
            .putInt(KEY_AD_COUNT, state.adWatchCountToday)
            .putInt(KEY_REWARD_MB, state.extraDataTodayMB)
            .apply()
    }
}
