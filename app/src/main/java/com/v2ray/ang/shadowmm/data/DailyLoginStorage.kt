package com.v2ray.ang.shadowmm.data

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DailyLoginStorage {

    private const val PREFS = "daily_login_prefs"
    private const val KEY_DATE = "daily_login_date"

    private fun today(): String {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return format.format(Date())
    }

    fun isClaimedToday(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_DATE, "") == today()
    }

    fun setClaimedToday(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // ✅ Today date ကို save လုပ်သွားမယ် (remove မလုပ်တော့)
        prefs.edit().putString(KEY_DATE, today()).apply()
    }

    // ✅ NEW: reset / clear daily login status
    fun clearToday(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_DATE).apply()
    }
}

