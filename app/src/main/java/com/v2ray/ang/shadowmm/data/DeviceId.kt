package com.v2ray.ang.shadowmm.data

import android.content.Context
import java.util.UUID

object DeviceId {

    private const val KEY = "device_id"

    fun get(context: Context): String {
        val sp = context.getSharedPreferences("silentvpn", Context.MODE_PRIVATE)
        var id = sp.getString(KEY, null)
        if (id == null) {
            id = UUID.randomUUID().toString()
            sp.edit().putString(KEY, id).apply()
        }
        return id
    }
}
