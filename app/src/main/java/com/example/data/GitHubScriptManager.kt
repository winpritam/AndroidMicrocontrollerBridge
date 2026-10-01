package com.example.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class GitHubScriptManager(
    private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    private val tag = "GitHubScriptManager"
    private val repoUrl = "https://github.com/winpritam/pico-case"
    private val rawUrlMain = "https://raw.githubusercontent.com/winpritam/pico-case/main/main.py"
    private val rawUrlMaster = "https://raw.githubusercontent.com/winpritam/pico-case/master/main.py"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val _syncStatus = MutableStateFlow("Ready to sync from winpritam/pico-case")
    val syncStatus: StateFlow<String> = _syncStatus.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _cachedScript = MutableStateFlow<String>("")
    val cachedScript: StateFlow<String> = _cachedScript.asStateFlow()

    suspend fun init() {
        val saved = settingsRepository.getSetting("github_cached_main_py", "")
        if (saved.isNotBlank()) {
            _cachedScript.value = saved
            _syncStatus.value = "Loaded cached main.py (${saved.length} bytes)"
        }
        // Auto-fetch in background
        fetchFromGitHub(silent = true)
    }

    suspend fun fetchFromGitHub(silent: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        _isSyncing.value = true
        if (!silent) _syncStatus.value = "Connecting to GitHub (winpritam/pico-case)..."

        var downloadedCode: String? = null

        // Try 'main' branch first
        try {
            val req = Request.Builder()
                .url(rawUrlMain)
                .header("User-Agent", "Picocase-Android-Companion")
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    downloadedCode = resp.body?.string()
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to fetch from 'main': ${e.message}")
        }

        // If 'main' branch didn't work, try 'master' branch
        if (downloadedCode == null) {
            try {
                val req = Request.Builder()
                    .url(rawUrlMaster)
                    .header("User-Agent", "Picocase-Android-Companion")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        downloadedCode = resp.body?.string()
                    }
                }
            } catch (e: Exception) {
                Log.w(tag, "Failed to fetch from 'master': ${e.message}")
            }
        }

        _isSyncing.value = false

        if (!downloadedCode.isNullOrBlank()) {
            _cachedScript.value = downloadedCode!!
            settingsRepository.saveSetting("github_cached_main_py", downloadedCode!!)
            settingsRepository.saveSetting("github_last_sync_time", System.currentTimeMillis().toString())
            _syncStatus.value = "Auto-updated from winpritam/pico-case (${downloadedCode!!.length} B)"
            Log.i(tag, "Successfully updated main.py from GitHub repo winpritam/pico-case")
            return@withContext true
        } else {
            val hasCached = _cachedScript.value.isNotBlank()
            _syncStatus.value = if (hasCached) {
                "Offline: Using cached GitHub main.py"
            } else {
                "GitHub repo not reachable (offline)"
            }
            return@withContext false
        }
    }
}
