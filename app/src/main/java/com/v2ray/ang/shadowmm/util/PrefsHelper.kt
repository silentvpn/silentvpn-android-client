// ✅ CREATE NEW FILE: utils/PrefsHelper.kt
package com.v2ray.ang.shadowmm.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

object PrefsHelper {
    private const val TAG = "PrefsHelper"

    /**
     * Safe way to save Int values
     */
    fun saveInt(context: Context, prefsName: String, key: String, value: Int): Boolean {
        return try {
            context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                .edit()
                .putInt(key, value)
                .apply()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save int [$key]: ${e.message}")
            false
        }
    }

    /**
     * Safe way to save multiple values at once
     */
    fun saveMultiple(
        context: Context,
        prefsName: String,
        values: Map<String, Any>
    ): Boolean {
        return try {
            val editor = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit()

            values.forEach { (key, value) ->
                when (value) {
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is String -> editor.putString(key, value)
                    is Float -> editor.putFloat(key, value)
                }
            }

            editor.apply()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save multiple values: ${e.message}")
            false
        }
    }

    /**
     * Safe way to get Int with default value
     */
    fun getInt(context: Context, prefsName: String, key: String, default: Int): Int {
        return try {
            context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                .getInt(key, default)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get int [$key]: ${e.message}")
            default
        }
    }

    /**
     * Safe way to get Boolean
     */
    fun getBoolean(context: Context, prefsName: String, key: String, default: Boolean): Boolean {
        return try {
            context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                .getBoolean(key, default)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get boolean [$key]: ${e.message}")
            default
        }
    }
}

// ========================================
// HOW TO USE (Example):
// ========================================

// ❌ OLD WAY (Unsafe):
// prefs.edit().putInt("key", value).apply()

// ✅ NEW WAY (Safe):
// PrefsHelper.saveInt(context, "vpn_state", "saved_used_mb", usedMB)

// OR save multiple at once:
// PrefsHelper.saveMultiple(context, "vpn_state", mapOf(
//     "saved_used_mb" to usedMB,
//     "saved_total_limit" to totalLimit,
//     "current_is_official" to true
// ))