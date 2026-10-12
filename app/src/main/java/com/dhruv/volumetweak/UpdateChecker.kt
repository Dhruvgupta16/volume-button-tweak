package com.dhruv.volumetweak

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
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

    data class ReleaseItem(
        val tag: String,
        val name: String,
        val body: String,
        val apkUrl: String?,
        val publishedAt: String
    )

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private const val GITHUB_REPO_API = "https://api.github.com/repos/Dhruvgupta16/volume-button-tweak/releases/latest"
    private const val GITHUB_REPO_RELEASES_LIST_API = "https://api.github.com/repos/Dhruvgupta16/volume-button-tweak/releases"
    private const val GITHUB_RELEASES_WEB = "https://github.com/Dhruvgupta16/volume-button-tweak/releases"

    fun checkForUpdate(currentVersionName: String, onResult: (CheckResult) -> Unit) {
        executor.execute {
            try {
                val url = URL(GITHUB_REPO_API)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 7000
                connection.readTimeout = 7000
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

    fun fetchReleaseHistory(onResult: (List<ReleaseItem>, String?) -> Unit) {
        executor.execute {
            try {
                val url = URL(GITHUB_REPO_RELEASES_LIST_API)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                connection.setRequestProperty("User-Agent", "VolumeButtonTweak-App")
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")

                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = reader.readText()
                    reader.close()

                    val jsonArray = JSONArray(response)
                    val releases = mutableListOf<ReleaseItem>()
                    for (i in 0 until jsonArray.length()) {
                        val relObj = jsonArray.getJSONObject(i)
                        val tag = relObj.optString("tag_name", "").trim()
                        val name = relObj.optString("name", tag).ifBlank { tag }
                        val body = relObj.optString("body", "No release notes available.")
                        val publishedAt = relObj.optString("published_at", "")

                        var apkUrl: String? = null
                        val assets = relObj.optJSONArray("assets")
                        if (assets != null) {
                            for (j in 0 until assets.length()) {
                                val asset = assets.getJSONObject(j)
                                val assetName = asset.optString("name", "")
                                if (assetName.endsWith(".apk", ignoreCase = true)) {
                                    apkUrl = asset.optString("browser_download_url", null)
                                    break
                                }
                            }
                        }
                        if (apkUrl == null) {
                            apkUrl = relObj.optString("html_url", GITHUB_RELEASES_WEB)
                        }

                        if (tag.isNotBlank()) {
                            releases.add(
                                ReleaseItem(
                                    tag = tag,
                                    name = name,
                                    body = body,
                                    apkUrl = apkUrl,
                                    publishedAt = publishedAt
                                )
                            )
                        }
                    }
                    mainHandler.post { onResult(releases, null) }
                } else {
                    mainHandler.post { onResult(emptyList(), "GitHub returned HTTP $responseCode") }
                }
            } catch (e: Exception) {
                mainHandler.post { onResult(emptyList(), e.message ?: "Failed to fetch releases") }
            }
        }
    }

    data class VersionChunk(val num: Int, val suffix: String) : Comparable<VersionChunk> {
        override fun compareTo(other: VersionChunk): Int {
            if (this.num != other.num) {
                return this.num.compareTo(other.num)
            }
            if (this.suffix.isEmpty() && other.suffix.isNotEmpty()) {
                return -1
            }
            if (this.suffix.isNotEmpty() && other.suffix.isEmpty()) {
                return 1
            }
            return this.suffix.compareTo(other.suffix, ignoreCase = true)
        }
    }

    private fun parseVersionChunk(str: String): VersionChunk {
        val trimmed = str.trim()
        val match = Regex("""^(\d*)(.*)$""").find(trimmed)
        val num = match?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val suffix = match?.groupValues?.get(2)?.trim() ?: ""
        return VersionChunk(num, suffix)
    }

    internal fun isNewerVersion(current: String, remote: String): Boolean {
        if (remote.isBlank()) return false
        val cClean = current.removePrefix("v").removePrefix("V").trim()
        val rClean = remote.removePrefix("v").removePrefix("V").trim()
        if (cClean.equals(rClean, ignoreCase = true)) return false

        val cChunks = cClean.split(".").map { parseVersionChunk(it) }
        val rChunks = rClean.split(".").map { parseVersionChunk(it) }

        val maxLen = maxOf(cChunks.size, rChunks.size)
        for (i in 0 until maxLen) {
            val c = cChunks.getOrElse(i) { VersionChunk(0, "") }
            val r = rChunks.getOrElse(i) { VersionChunk(0, "") }
            val cmp = r.compareTo(c)
            if (cmp > 0) return true
            if (cmp < 0) return false
        }
        return false
    }

    /**
     * Downloads APK in-app, follows CDN 302 redirects, streams bytes with progress tracking,
     * and triggers Android's system package installer screen directly.
     */
    fun downloadAndInstallApk(
        activity: Activity,
        downloadUrl: String,
        onProgress: (Int) -> Unit,
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ) {
        // Android 8.0+ Unknown App Sources Permission Check
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!activity.packageManager.canRequestPackageInstalls()) {
                try {
                    val permissionIntent = Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${activity.packageName}")
                    )
                    activity.startActivity(permissionIntent)
                    onError("Enable 'Allow from this source' for Volume Tweak in Settings, then tap Install Update again.")
                } catch (e: Exception) {
                    onError("Permission required to install unknown apps: ${e.message}")
                }
                return
            }
        }

        executor.execute {
            try {
                var currentUrl = downloadUrl
                var connection: HttpURLConnection
                var redirects = 0

                // Follow redirects (GitHub Releases 302 redirect to AWS S3)
                while (true) {
                    val u = URL(currentUrl)
                    connection = u.openConnection() as HttpURLConnection
                    connection.connectTimeout = 12000
                    connection.readTimeout = 20000
                    connection.setRequestProperty("User-Agent", "VolumeButtonTweak-App")
                    connection.instanceFollowRedirects = true

                    val status = connection.responseCode
                    if (status == HttpURLConnection.HTTP_MOVED_TEMP ||
                        status == HttpURLConnection.HTTP_MOVED_PERM ||
                        status == 307 || status == 308
                    ) {
                        currentUrl = connection.getHeaderField("Location")
                        connection.disconnect()
                        redirects++
                        if (redirects > 6) throw Exception("Too many redirects")
                        continue
                    }
                    if (status != HttpURLConnection.HTTP_OK) {
                        throw Exception("Download server returned HTTP $status")
                    }
                    break
                }

                val totalLength = connection.contentLength
                val apkDir = File(activity.cacheDir, "updates")
                if (!apkDir.exists()) apkDir.mkdirs()
                val apkFile = File(apkDir, "update.apk")
                if (apkFile.exists()) apkFile.delete()

                val input = connection.inputStream
                val output = FileOutputStream(apkFile)

                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalBytesRead: Long = 0
                var lastReportedPercent = -1

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    if (totalLength > 0) {
                        val percent = ((totalBytesRead * 100) / totalLength).toInt()
                        if (percent != lastReportedPercent) {
                            lastReportedPercent = percent
                            mainHandler.post { onProgress(percent) }
                        }
                    }
                }

                output.flush()
                output.close()
                input.close()
                connection.disconnect()

                mainHandler.post {
                    onComplete()
                    try {
                        val contentUri = FileProvider.getUriForFile(
                            activity,
                            "${activity.packageName}.fileprovider",
                            apkFile
                        )
                        val installIntent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(contentUri, "application/vnd.android.package-archive")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        activity.startActivity(installIntent)
                    } catch (e: Exception) {
                        onError("Failed to launch package installer: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    onComplete()
                    onError(e.message ?: "Failed to download update APK")
                }
            }
        }
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
