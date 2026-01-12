package com.v2ray.ang.shadowmm.data

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Cache for daily claim status to prevent button flicker
 * Syncs with server but uses local cache for instant UI
 */
object ClaimedStatusStorage {

    private const val PREFS = "claimed_status_prefs"
    private const val KEY_CLAIMED = "is_claimed"
    private const val KEY_DATE = "claimed_date"

    private fun today(): String {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return format.format(Date())
    }

    /**
     * Check if user claimed today (from cache)
     * Use this for initial UI state to prevent flicker
     */
    fun isClaimedToday(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val claimedDate = prefs.getString(KEY_DATE, "")
        val isClaimed = prefs.getBoolean(KEY_CLAIMED, false)

        // If different day, reset
        if (claimedDate != today()) {
            clearClaimed(context)
            return false
        }

        return isClaimed
    }

    /**
     * Mark as claimed for today
     * Call this after successful claim OR after server sync confirms claimed
     */
    fun setClaimedToday(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_CLAIMED, true)
            .putString(KEY_DATE, today())
            .apply()
    }

    /**
     * Clear claimed status
     * Call this when new day starts OR server says not claimed
     */
    fun clearClaimed(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_CLAIMED, false)
            .remove(KEY_DATE)
            .apply()
    }
}