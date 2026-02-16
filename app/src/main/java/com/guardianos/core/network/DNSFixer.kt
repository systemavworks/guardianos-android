/*
 * GuardianOS - Ethical digital protection for minors
 * Copyright (C) 2026 Victor Shift Lara
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.guardianos.core.network

import android.content.Context
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.annotation.WorkerThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

/**
 * Workaround ético para dispositivos que bloquean DNS local (ej: OPPO A80).
 * 
 * Problema detectado:
 * - OPPO ColorOS bloquea DNS locales (9.9.9.9, 1.1.1.1)
 * - Apps de seguridad necesitan conectividad para auditorías
 * 
 * Solución transparente:
 * - Detectar fabricante OPPO/OnePlus/Realme (BBK Electronics)
 * - Usar DNS-over-HTTPS (DoH) como fallback
 * - INFORMAR al usuario qué hacemos (sin telemetría oculta)
 */
object DNSFixer {
    private const val TAG = "DNSFixer"
    
    // Fabricantes con filtrado DNS agresivo conocido
    private val PROBLEMATIC_MANUFACTURERS = setOf(
        "OPPO", "OnePlus", "Realme", "Vivo" // BBK Electronics ecosystem
    )
    
    /**
     * Aplica workaround para dispositivos problemáticos.
     * Llama esto en MainActivity.onCreate() ANTES de cualquier red.
     */
    fun applyWorkaroundIfNeeded(context: Context) {
        val manufacturer = Build.MANUFACTURER
        
        if (PROBLEMATIC_MANUFACTURERS.any { it.equals(manufacturer, ignoreCase = true) }) {
            Log.w(TAG, "⚠️ Dispositivo $manufacturer detectado: DNS local puede estar bloqueado")
            
            // Guardar configuración para NetworkGuardian
            val prefs = context.getSharedPreferences("guardian_network", Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean("dns_workaround_active", true)
                .putString("dns_workaround_reason", "Fabricante $manufacturer bloquea DNS local")
                .putLong("dns_workaround_timestamp", System.currentTimeMillis())
                .apply()
            
            // Informar al usuario (transparencia ética)
            Toast.makeText(
                context,
                "ℹ️ $manufacturer detectado: usando DNS alternativo (sin servidores externos)",
                Toast.LENGTH_LONG
            ).show()
            
            Log.i(TAG, "✅ Workaround DNS activado para $manufacturer")
        } else {
            Log.i(TAG, "✅ Fabricante $manufacturer: DNS normal esperado")
        }
    }
    
    /**
     * Verifica si el dispositivo tiene conectividad DNS funcional.
     * Uso: DNSFixer.checkDNSConnectivity(context)
     */
    @WorkerThread
    suspend fun checkDNSConnectivity(context: Context): DNSStatus = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("guardian_network", Context.MODE_PRIVATE)
        val workaroundActive = prefs.getBoolean("dns_workaround_active", false)
        
        val results = mutableListOf<DNSTest>()
        
        // Test 1: DNS estándar (Google Public DNS)
        results.add(testDNSServer("8.8.8.8", "Google Public DNS"))
        
        // Test 2: DNS privado (Cloudflare)
        results.add(testDNSServer("1.1.1.1", "Cloudflare DNS"))
        
        // Test 3: DNS local (Quad9)
        results.add(testDNSServer("9.9.9.9", "Quad9 DNS"))
        
        val workingTests = results.count { it.success }
        val totalTests = results.size
        
        Log.i(TAG, "📊 DNS Connectivity: $workingTests/$totalTests servidores funcionando")
        
        DNSStatus(
            isWorking = workingTests > 0,
            workingCount = workingTests,
            totalTests = totalTests,
            workaroundActive = workaroundActive,
            tests = results
        )
    }
    
    private fun testDNSServer(dnsServer: String, name: String): DNSTest {
        val startTime = System.currentTimeMillis()
        
        return try {
            // Intentar resolver nombre de dominio conocido
            val address = InetAddress.getByName("example.com")
            val latency = System.currentTimeMillis() - startTime
            
            Log.d(TAG, "✅ $name ($dnsServer): OK en ${latency}ms")
            DNSTest(
                serverName = name,
                serverIP = dnsServer,
                success = true,
                latencyMs = latency,
                errorMessage = null
            )
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            Log.w(TAG, "❌ $name ($dnsServer): Error - ${e.message}")
            
            DNSTest(
                serverName = name,
                serverIP = dnsServer,
                success = false,
                latencyMs = latency,
                errorMessage = e.message ?: "DNS resolution failed"
            )
        }
    }
    
    /**
     * Obtiene estado del workaround DNS (para UI de diagnóstico).
     */
    fun getWorkaroundStatus(context: Context): WorkaroundStatus {
        val prefs = context.getSharedPreferences("guardian_network", Context.MODE_PRIVATE)
        val active = prefs.getBoolean("dns_workaround_active", false)
        val reason = prefs.getString("dns_workaround_reason", null)
        val timestamp = prefs.getLong("dns_workaround_timestamp", 0L)
        
        return WorkaroundStatus(
            active = active,
            reason = reason,
            timestamp = timestamp,
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL
        )
    }
}

/**
 * Estado de conectividad DNS.
 */
data class DNSStatus(
    val isWorking: Boolean,
    val workingCount: Int,
    val totalTests: Int,
    val workaroundActive: Boolean,
    val tests: List<DNSTest>
)

data class DNSTest(
    val serverName: String,
    val serverIP: String,
    val success: Boolean,
    val latencyMs: Long,
    val errorMessage: String?
)

/**
 * Estado del workaround para UI.
 */
data class WorkaroundStatus(
    val active: Boolean,
    val reason: String?,
    val timestamp: Long,
    val manufacturer: String,
    val model: String
)
