package com.v2ray.ang.shadowmm.data

import android.content.Context

object CoinStorage {

    private const val PREF_NAME = "shadowlink_rewards"
    private const val KEY_COINS = "coins"

    fun loadCoins(context: Context): Int {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_COINS, 0)
    }

    fun saveCoins(context: Context, coins: Int) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_COINS, coins).apply()
    }
}
