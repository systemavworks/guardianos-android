package com.guardianos.core.pro.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Monitoriza cambios de permisos y apps instaladas/actualizadas.
 * Solo disponible en PRO.
 */
object PermissionChangeMonitor {
    private const val TAG = "PermissionChangeMonitor"
    private var registered = false

    fun register(context: Context, onChange: (String) -> Unit) {
        if (registered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        context.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val pkg = intent.data?.schemeSpecificPart ?: return
                CoroutineScope(Dispatchers.IO).launch {
                    onChange(pkg)
                }
            }
        }, filter)
        registered = true
        Log.d(TAG, "PermissionChangeMonitor registered")
    }
}
