package com.garminstreaming.app.strava

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.edit
import com.garminstreaming.app.data.ActivitySession
import com.garminstreaming.app.export.GpxExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Strava API client for OAuth and activity uploads
 *
 * To use this integration, you need to:
 * 1. Create a Strava API application at https://www.strava.com/settings/api
 * 2. Set your Client ID and Client Secret
 * 3. Configure the redirect URI as: garminstreaming://strava/callback
 */
object StravaClient {

    // Replace these with your Strava API credentials
    private const val CLIENT_ID = "YOUR_STRAVA_CLIENT_ID"
    private const val CLIENT_SECRET = "YOUR_STRAVA_CLIENT_SECRET"
    private const val REDIRECT_URI = "garminstreaming://strava/callback"

    private const val AUTH_URL = "https://www.strava.com/oauth/authorize"
    private const val TOKEN_URL = "https://www.strava.com/oauth/token"
    private const val UPLOAD_URL = "https://www.strava.com/api/v3/uploads"
    private const val ATHLETE_URL = "https://www.strava.com/api/v3/athlete"

    private const val PREFS_NAME = "strava_prefs"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private const val KEY_EXPIRES_AT = "expires_at"
    private const val KEY_ATHLETE_NAME = "athlete_name"

    /**
     * Check if user is connected to Strava
     */
    fun isConnected(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_ACCESS_TOKEN, null) != null
    }

    /**
     * Get athlete name if connected
     */
    fun getAthleteName(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_ATHLETE_NAME, null)
    }

    /**
     * Start OAuth authorization flow
     */
    fun startAuthorization(context: Context) {
        val authUrl = Uri.parse(AUTH_URL)
            .buildUpon()
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", "activity:write,activity:read")
            .build()

        val intent = Intent(Intent.ACTION_VIEW, authUrl)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /**
     * Handle OAuth callback and exchange code for tokens
     */
    suspend fun handleAuthCallback(context: Context, code: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(TOKEN_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

                val params = "client_id=$CLIENT_ID" +
                        "&client_secret=$CLIENT_SECRET" +
                        "&code=$code" +
                        "&grant_type=authorization_code"

                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(params)
                    writer.flush()
                }

                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().readText()
                    val json = JSONObject(response)

                    val accessToken = json.getString("access_token")
                    val refreshToken = json.getString("refresh_token")
                    val expiresAt = json.getLong("expires_at")

                    // Get athlete info
                    val athlete = json.getJSONObject("athlete")
                    val firstName = athlete.optString("firstname", "")
                    val lastName = athlete.optString("lastname", "")
                    val athleteName = "$firstName $lastName".trim()

                    // Save tokens
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    prefs.edit {
                        putString(KEY_ACCESS_TOKEN, accessToken)
                        putString(KEY_REFRESH_TOKEN, refreshToken)
                        putLong(KEY_EXPIRES_AT, expiresAt)
                        putString(KEY_ATHLETE_NAME, athleteName)
                    }

                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    /**
     * Refresh access token if expired
     */
    private suspend fun refreshTokenIfNeeded(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0)
        val currentTime = System.currentTimeMillis() / 1000

        if (currentTime < expiresAt - 60) {
            // Token still valid
            return prefs.getString(KEY_ACCESS_TOKEN, null)
        }

        // Need to refresh
        val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null) ?: return null

        return withContext(Dispatchers.IO) {
            try {
                val url = URL(TOKEN_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

                val params = "client_id=$CLIENT_ID" +
                        "&client_secret=$CLIENT_SECRET" +
                        "&refresh_token=$refreshToken" +
                        "&grant_type=refresh_token"

                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(params)
                    writer.flush()
                }

                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().readText()
                    val json = JSONObject(response)

                    val newAccessToken = json.getString("access_token")
                    val newRefreshToken = json.getString("refresh_token")
                    val newExpiresAt = json.getLong("expires_at")

                    prefs.edit {
                        putString(KEY_ACCESS_TOKEN, newAccessToken)
                        putString(KEY_REFRESH_TOKEN, newRefreshToken)
                        putLong(KEY_EXPIRES_AT, newExpiresAt)
                    }

                    newAccessToken
                } else {
                    null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    /**
     * Upload activity to Strava
     */
    suspend fun uploadActivity(
        context: Context,
        session: ActivitySession,
        name: String? = null,
        description: String? = null
    ): UploadResult {
        val accessToken = refreshTokenIfNeeded(context)
            ?: return UploadResult.Error("Not authenticated with Strava")

        return withContext(Dispatchers.IO) {
            try {
                // Generate GPX content
                val gpxContent = GpxExporter.export(session)

                // Create multipart request
                val boundary = "----FormBoundary${System.currentTimeMillis()}"
                val url = URL(UPLOAD_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Authorization", "Bearer $accessToken")
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

                val activityName = name ?: "${session.activityType.replaceFirstChar { it.uppercase() }} Activity"
                val activityType = mapActivityType(session.activityType)

                val outputStream = connection.outputStream
                val writer = OutputStreamWriter(outputStream)

                // Activity type
                writer.write("--$boundary\r\n")
                writer.write("Content-Disposition: form-data; name=\"activity_type\"\r\n\r\n")
                writer.write("$activityType\r\n")

                // Data type
                writer.write("--$boundary\r\n")
                writer.write("Content-Disposition: form-data; name=\"data_type\"\r\n\r\n")
                writer.write("gpx\r\n")

                // Name
                writer.write("--$boundary\r\n")
                writer.write("Content-Disposition: form-data; name=\"name\"\r\n\r\n")
                writer.write("$activityName\r\n")

                // Description
                if (description != null) {
                    writer.write("--$boundary\r\n")
                    writer.write("Content-Disposition: form-data; name=\"description\"\r\n\r\n")
                    writer.write("$description\r\n")
                }

                // File
                writer.write("--$boundary\r\n")
                writer.write("Content-Disposition: form-data; name=\"file\"; filename=\"activity.gpx\"\r\n")
                writer.write("Content-Type: application/gpx+xml\r\n\r\n")
                writer.write(gpxContent)
                writer.write("\r\n")

                // End
                writer.write("--$boundary--\r\n")
                writer.flush()
                writer.close()

                when (connection.responseCode) {
                    201 -> {
                        val response = connection.inputStream.bufferedReader().readText()
                        val json = JSONObject(response)
                        val uploadId = json.getLong("id")
                        UploadResult.Success(uploadId)
                    }
                    401 -> UploadResult.Error("Authentication failed")
                    else -> {
                        val error = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                        UploadResult.Error("Upload failed: $error")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                UploadResult.Error("Upload failed: ${e.message}")
            }
        }
    }

    /**
     * Disconnect from Strava (clear tokens)
     */
    fun disconnect(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            clear()
        }
    }

    private fun mapActivityType(type: String): String {
        return when (type.lowercase()) {
            "running" -> "run"
            "cycling" -> "ride"
            "walking" -> "walk"
            "hiking" -> "hike"
            "swimming" -> "swim"
            else -> "workout"
        }
    }
}

/**
 * Result of upload operation
 */
sealed class UploadResult {
    data class Success(val uploadId: Long) : UploadResult()
    data class Error(val message: String) : UploadResult()
}
