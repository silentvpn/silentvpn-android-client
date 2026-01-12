package com.v2ray.ang.shadowmm.util

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.v2ray.ang.shadowmm.data.UsageManager

/**
 * 🔥 VPN Connection Helper with Block Check
 *
 * Use this before starting V2Ray to ensure user is not blocked
 */
object VpnConnectionHelper {

    private const val TAG = "VpnConnectionHelper"

    /**
     * Check if user is blocked and start VPN if allowed
     *
     * @param context Context
     * @param onAllowed Callback when user is allowed to connect
     * @param onBlocked Callback when user is blocked (optional)
     */
    fun checkAndConnect(
        context: Context,
        onAllowed: () -> Unit,
        onBlocked: (() -> Unit)? = null
    ) {
        Log.d(TAG, "Checking user block status...")

        // Sync with server to get latest status
        UsageManager.sync(context) { serverData ->
            Log.d(TAG, "Block status check - blocked: ${serverData.blocked}")

            if (serverData.blocked == 1) {
                // User is blocked!
                Log.w(TAG, "User is BLOCKED - preventing connection")

                // Show blocked dialog
                showBlockedDialog(context)

                // Call onBlocked callback if provided
                onBlocked?.invoke()
            } else {
                // User is not blocked, allow connection
                Log.d(TAG, "User is ACTIVE - allowing connection")
                onAllowed()
            }
        }
    }

    /**
     * Show blocked account dialog
     */
    private fun showBlockedDialog(context: Context) {
        try {
            AlertDialog.Builder(context)
                .setTitle("Account Blocked")
                .setMessage(
                    "Your account has been temporarily blocked.\n\n" +
                            "This may be due to:\n" +
                            "• Policy violation\n" +
                            "• Unusual activity\n" +
                            "• Excessive usage\n\n" +
                            "Please contact support for assistance."
                )
                .setPositiveButton("OK") { dialog, _ ->
                    dialog.dismiss()
                }
                .setNegativeButton("Contact Support") { dialog, _ ->
                    dialog.dismiss()
                    openSupportContact(context)
                }
                .setCancelable(false)
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show blocked dialog: ${e.message}")
            Toast.makeText(
                context,
                "Account Blocked - Contact Support",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Open support contact
     */
    private fun openSupportContact(context: Context) {
        Toast.makeText(
            context,
            "Contact: support@silentdns.xyz",
            Toast.LENGTH_LONG
        ).show()
    }

    /**
     * Simple check without callbacks (returns result via callback)
     */
    fun isUserBlocked(context: Context, callback: (Boolean) -> Unit) {
        UsageManager.sync(context) { serverData ->
            callback(serverData.blocked == 1)
        }
    }

    /**
     * Check block status and show toast if blocked
     */
    fun checkBlockStatusWithToast(context: Context, onResult: (Boolean) -> Unit) {
        UsageManager.sync(context) { serverData ->
            val isBlocked = serverData.blocked == 1

            if (isBlocked) {
                Toast.makeText(
                    context,
                    "⚠️ Account Blocked - Cannot Connect",
                    Toast.LENGTH_LONG
                ).show()
            }

            onResult(isBlocked)
        }
    }
}