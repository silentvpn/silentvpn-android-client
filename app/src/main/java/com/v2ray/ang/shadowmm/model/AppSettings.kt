package com.v2ray.ang.shadowmm.model

data class NotificationsSettings(
    val speed: Boolean = true,
    val dataUsage: Boolean = true
)

data class AppSettings(
    val theme: Theme = Theme.LIGHT,
    val language: Language = Language.EN,
    val notifications: NotificationsSettings = NotificationsSettings(),
    val splitTunneling: Boolean = false
)
