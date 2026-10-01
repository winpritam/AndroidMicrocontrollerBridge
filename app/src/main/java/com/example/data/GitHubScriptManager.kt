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
    private val repoBaseMain = "https://raw.githubusercontent.com/winpritam/pico-case/main"
    private val repoBaseMaster = "https://raw.githubusercontent.com/winpritam/pico-case/master"

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
                .url("$repoBaseMain/main.py")
                .header("User-Agent", "Picocase-Android-Companion")
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    downloadedCode = resp.body?.string()
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to fetch main.py from 'main': ${e.message}")
        }

        // If 'main' branch didn't work, try 'master' branch
        if (downloadedCode == null) {
            try {
                val req = Request.Builder()
                    .url("$repoBaseMaster/main.py")
                    .header("User-Agent", "Picocase-Android-Companion")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        downloadedCode = resp.body?.string()
                    }
                }
            } catch (e: Exception) {
                Log.w(tag, "Failed to fetch main.py from 'master': ${e.message}")
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

    // Fetches preset .bin animation file from GitHub repo winpritam/pico-case/anims/<fileName>
    suspend fun fetchPresetAnimation(fileName: String): ByteArray? = withContext(Dispatchers.IO) {
        val cleanName = if (fileName.startsWith("anims/")) fileName.substringAfter("anims/") else fileName

        // 1. Try 'main' branch
        try {
            val req = Request.Builder()
                .url("$repoBaseMain/anims/$cleanName")
                .header("User-Agent", "Picocase-Android-Companion")
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val bytes = resp.body?.bytes()
                    if (bytes != null && bytes.isNotEmpty()) {
                        Log.i(tag, "Fetched $cleanName (${bytes.size} B) from GitHub main branch")
                        return@withContext bytes
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to fetch $cleanName from GitHub main: ${e.message}")
        }

        // 2. Try 'master' branch
        try {
            val req = Request.Builder()
                .url("$repoBaseMaster/anims/$cleanName")
                .header("User-Agent", "Picocase-Android-Companion")
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val bytes = resp.body?.bytes()
                    if (bytes != null && bytes.isNotEmpty()) {
                        Log.i(tag, "Fetched $cleanName (${bytes.size} B) from GitHub master branch")
                        return@withContext bytes
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to fetch $cleanName from GitHub master: ${e.message}")
        }

        return@withContext null
    }
}
