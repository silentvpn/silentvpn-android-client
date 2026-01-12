package com.v2ray.ang.shadowmm.data
import com.v2ray.ang.shadowmm.model.Server
object ServerCache {

    private var cache: List<Server> = emptyList()

    fun save(servers: List<Server>) {
        cache = servers
    }

    fun get(): List<Server> = cache
}
