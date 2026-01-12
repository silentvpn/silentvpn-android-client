package com.v2ray.ang.shadowmm.vpn

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Very small manager whose only job is:
 *  - copy core binaries from assets -> app's filesDir
 *  - make them executable (best effort)
 *  - return File handles for ShadowLinkVpnService
 *
 * Expected asset names (put them in app/src/main/assets/):
 *  - sslocal-arm64
 *  - tun2socks-arm64
 */
object ShadowsocksCoreManager {

    private const val TAG = "ShadowCore"

    private const val SSLOCAL_ARM64 = "sslocal-arm64"
    private const val TUN2SOCKS_ARM64 = "tun2socks-arm64"

    private const val V2RAY_PLUGIN = "v2ray-plugin"

    /**
     * Ensure v2ray-plugin exists and is executable
     */
    fun ensureV2RayPlugin(context: Context): File? {
        return ensureAssetCore(context, V2RAY_PLUGIN)
    }

    /**
     * Ensure sslocal binary exists in filesDir and is executable.
     * Returns null if something fails.
     */
    fun ensureSsLocal(context: Context): File? {
        return ensureAssetCore(context, SSLOCAL_ARM64)
    }

    /**
     * Ensure tun2socks binary exists in filesDir and is executable.
     * Returns null if something fails.
     */
    fun ensureTun2Socks(context: Context): File? {
        return ensureAssetCore(context, TUN2SOCKS_ARM64)
    }

    /**
     * Generic helper that copies an asset named [assetName] to filesDir
     * and marks it executable (best effort).
     */
    private fun ensureAssetCore(context: Context, assetName: String): File? {
        val destFile = File(context.filesDir, assetName)

        if (!destFile.exists()) {
            Log.d(TAG, "Core $assetName not found, extracting from assets…")
            try {
                context.assets.open(assetName).use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to extract core $assetName from assets", e)
                return null
            }
        }

        // Try to make it executable. On many ROMs this still won't allow exec,
        // but it doesn't hurt to try.
        if (!destFile.setExecutable(true)) {
            Log.w(TAG, "setExecutable(false) for $assetName (path=${destFile.absolutePath})")
        }

        Log.d(TAG, "Core $assetName ready at ${destFile.absolutePath}")
        return destFile
    }
}
