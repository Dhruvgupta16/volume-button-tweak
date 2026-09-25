package com.dhruv.volumetweak

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object UpdateChecker {

    data class CheckResult(
        val hasUpdate: Boolean,
        val latestVersion: String,
        val downloadUrl: String?,
        val releaseNotes: String?,
        val errorMessage: String? = null
    )

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private const val GITHUB_REPO_API = "https://api.github.com/repos/Dhruvgupta16/volume-button-tweak/releases/latest"
    private const val GITHUB_RELEASES_WEB = "https://github.com/Dhruvgupta16/volume-button-tweak/releases"

    fun checkForUpdate(currentVersionName: String, onResult: (CheckResult) -> Unit) {
        executor.execute {
            try {
                val url = URL(GITHUB_REPO_API)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 6000
                connection.readTimeout = 6000
                connection.setRequestProperty("User-Agent", "VolumeButtonTweak-App")
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")

                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = reader.readText()
                    reader.close()

                    val json = JSONObject(response)
                    val tagName = json.optString("tag_name", "").removePrefix("v").trim()
                    val body = json.optString("body", "No release notes provided.")
                    
                    var apkDownloadUrl: String? = null
                    val assets = json.optJSONArray("assets")
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val name = asset.optString("name", "")
                            if (name.endsWith(".apk", ignoreCase = true)) {
                                apkDownloadUrl = asset.optString("browser_download_url", null)
                                break
                            }
                        }
                    }
                    if (apkDownloadUrl == null) {
                        apkDownloadUrl = json.optString("html_url", GITHUB_RELEASES_WEB)
                    }

                    val hasUpdate = isNewerVersion(currentVersionName.removePrefix("v").trim(), tagName)
                    val result = CheckResult(
                        hasUpdate = hasUpdate,
                        latestVersion = if (tagName.isNotBlank()) "v$tagName" else "Latest",
                        downloadUrl = apkDownloadUrl,
                        releaseNotes = body
                    )
                    mainHandler.post { onResult(result) }
                } else if (responseCode == 404) {
                    // No releases published yet on repo
                    val result = CheckResult(
                        hasUpdate = false,
                        latestVersion = "v$currentVersionName",
                        downloadUrl = GITHUB_RELEASES_WEB,
                        releaseNotes = "No published releases found yet.",
                        errorMessage = "No releases found on GitHub repository yet."
                    )
                    mainHandler.post { onResult(result) }
                } else {
                    val result = CheckResult(
                        hasUpdate = false,
                        latestVersion = currentVersionName,
                        downloadUrl = GITHUB_RELEASES_WEB,
                        releaseNotes = null,
                        errorMessage = "GitHub API returned HTTP $responseCode"
                    )
                    mainHandler.post { onResult(result) }
                }
            } catch (e: Exception) {
                val result = CheckResult(
                    hasUpdate = false,
                    latestVersion = currentVersionName,
                    downloadUrl = GITHUB_RELEASES_WEB,
                    releaseNotes = null,
                    errorMessage = e.message ?: "Failed to connect to GitHub"
                )
                mainHandler.post { onResult(result) }
            }
        }
    }

    private fun isNewerVersion(current: String, remote: String): Boolean {
        if (remote.isBlank()) return false
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
        val remoteParts = remote.split(".").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(currentParts.size, remoteParts.size)
        for (i in 0 until maxLen) {
            val cur = currentParts.getOrElse(i) { 0 }
            val rem = remoteParts.getOrElse(i) { 0 }
            if (rem > cur) return true
            if (rem < cur) return false
        }
        return false
    }

    fun openDownloadUrl(context: Context, urlString: String?) {
        try {
            val targetUrl = if (!urlString.isNullOrBlank()) urlString else GITHUB_RELEASES_WEB
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (ignored: Exception) {}
    }
}
