package com.glasswave.music

import android.net.Uri
import android.util.Base64
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class SpotifySession(
    val accessToken: String,
    val refreshToken: String?,
    val clientId: String,
    val expiresAtMillis: Long
)

data class SpotifyPlaylist(
    val id: String,
    val name: String,
    val trackCount: Int,
    val spotifyUrl: String
)

data class SpotifyTrack(
    val id: String,
    val name: String,
    val artists: String,
    val album: String,
    val spotifyUrl: String
)

object SpotifyApi {
    const val REDIRECT_URI = "http://127.0.0.1:43825/callback"
    private const val AUTH_URL = "https://accounts.spotify.com/authorize"
    private const val TOKEN_URL = "https://accounts.spotify.com/api/token"
    private const val API_BASE = "https://api.spotify.com/v1"
    private const val CALLBACK_TIMEOUT_MS = 180_000

    suspend fun authorize(clientIdInput: String, openBrowser: (String) -> Unit): SpotifySession =
        withContext(Dispatchers.IO) {
            val clientId = clientIdInput.trim()
            require(clientId.isNotEmpty()) { "Enter your Spotify Client ID first." }
            require(!clientId.contains(" ")) { "The Spotify Client ID does not look valid." }

            val verifier = randomUrlSafeString(64)
            val challenge = Base64.encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)),
                Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
            )
            val state = randomUrlSafeString(32)
            val authUri = Uri.parse(AUTH_URL).buildUpon()
                .appendQueryParameter("client_id", clientId)
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("redirect_uri", REDIRECT_URI)
                .appendQueryParameter("scope", "playlist-read-private playlist-read-collaborative user-library-read")
                .appendQueryParameter("state", state)
                .appendQueryParameter("code_challenge_method", "S256")
                .appendQueryParameter("code_challenge", challenge)
                .build()
                .toString()

            // Spotify redirects the browser to this local loopback listener after consent.
            val server = ServerSocket()
            server.reuseAddress = true
            server.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 43825), 1)
            server.soTimeout = CALLBACK_TIMEOUT_MS
            try {
                withContext(Dispatchers.Main) { openBrowser(authUri) }
                val callback = server.accept().use { socket ->
                    socket.soTimeout = CALLBACK_TIMEOUT_MS
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                    val requestLine = reader.readLine() ?: throw IOException("Spotify sign-in returned an empty response.")
                    while (true) {
                        val header = reader.readLine() ?: break
                        if (header.isEmpty()) break
                    }
                    val requestTarget = requestLine.split(" ").getOrNull(1)
                        ?: throw IOException("Spotify sign-in returned an invalid callback.")
                    val callbackUri = Uri.parse("http://127.0.0.1:43825$requestTarget")
                    val body = "<!doctype html><html><meta name='viewport' content='width=device-width, initial-scale=1'><body style='font-family:sans-serif;padding:24px'><h2>LUMA Spotify sign-in</h2><p>You can return to LUMA now.</p></body></html>"
                    val bytes = body.toByteArray(Charsets.UTF_8)
                    val header = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nConnection: close\r\nContent-Length: ${bytes.size}\r\n\r\n"
                    socket.getOutputStream().apply {
                        write(header.toByteArray(Charsets.UTF_8))
                        write(bytes)
                        flush()
                    }
                    val error = callbackUri.getQueryParameter("error")
                    if (error != null) throw IOException("Spotify sign-in was cancelled: $error")
                    val returnedState = callbackUri.getQueryParameter("state")
                    if (returnedState != state) throw IOException("Spotify sign-in state did not match. Please try again.")
                    callbackUri.getQueryParameter("code") ?: throw IOException("Spotify did not return an authorization code.")
                }
                val tokenJson = postForm(TOKEN_URL, mapOf(
                    "client_id" to clientId,
                    "grant_type" to "authorization_code",
                    "code" to callback,
                    "redirect_uri" to REDIRECT_URI,
                    "code_verifier" to verifier
                ))
                SpotifySession(
                    accessToken = tokenJson.getString("access_token"),
                    refreshToken = tokenJson.optString("refresh_token").takeIf { it.isNotBlank() },
                    clientId = clientId,
                    expiresAtMillis = System.currentTimeMillis() + tokenJson.optLong("expires_in", 3600L) * 1000L
                )
            } finally {
                runCatching { server.close() }
            }
        }

    suspend fun refreshIfNeeded(session: SpotifySession): SpotifySession = withContext(Dispatchers.IO) {
        if (session.expiresAtMillis > System.currentTimeMillis() + 60_000L) return@withContext session
        val refreshToken = session.refreshToken ?: throw IOException("Spotify session expired. Connect Spotify again.")
        val json = postForm(TOKEN_URL, mapOf(
            "client_id" to session.clientId,
            "grant_type" to "refresh_token",
            "refresh_token" to refreshToken
        ))
        SpotifySession(
            accessToken = json.getString("access_token"),
            refreshToken = json.optString("refresh_token").takeIf { it.isNotBlank() } ?: refreshToken,
            clientId = session.clientId,
            expiresAtMillis = System.currentTimeMillis() + json.optLong("expires_in", 3600L) * 1000L
        )
    }

    suspend fun fetchPlaylists(session: SpotifySession): List<SpotifyPlaylist> = withContext(Dispatchers.IO) {
        val json = getJson("$API_BASE/me/playlists?limit=50", session.accessToken)
        val items = json.optJSONArray("items") ?: return@withContext emptyList()
        buildList {
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
                val url = item.optJSONObject("external_urls")?.optString("spotify").orEmpty()
                val count = item.optJSONObject("items")?.optInt("total")
                    ?: item.optJSONObject("tracks")?.optInt("total") ?: 0
                add(SpotifyPlaylist(id, item.optString("name", "Spotify playlist"), count, url.ifBlank { "https://open.spotify.com/playlist/$id" }))
            }
        }
    }

    suspend fun fetchLikedSongs(session: SpotifySession): List<SpotifyTrack> = withContext(Dispatchers.IO) {
        val json = getJson("$API_BASE/me/tracks?limit=50", session.accessToken)
        val items = json.optJSONArray("items") ?: return@withContext emptyList()
        buildList {
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val track = item.optJSONObject("track") ?: item.optJSONObject("item") ?: continue
                parseTrack(track)?.let { add(it) }
            }
        }
    }

    suspend fun fetchPlaylistTracks(session: SpotifySession, playlistId: String): List<SpotifyTrack> = withContext(Dispatchers.IO) {
        val safeId = playlistId.filter { it.isLetterOrDigit() }
        require(safeId.isNotEmpty()) { "Invalid Spotify playlist ID." }
        val json = getJson("$API_BASE/playlists/$safeId/items?limit=50", session.accessToken)
        val items = json.optJSONArray("items") ?: return@withContext emptyList()
        buildList {
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val track = item.optJSONObject("item") ?: item.optJSONObject("track") ?: continue
                parseTrack(track)?.let { add(it) }
            }
        }
    }

    private fun parseTrack(track: JSONObject): SpotifyTrack? {
        if (track.optString("type", "track") != "track") return null
        val id = track.optString("id").takeIf { it.isNotBlank() } ?: return null
        val artistsArray = track.optJSONArray("artists")
        val artists = buildList {
            if (artistsArray != null) {
                for (i in 0 until artistsArray.length()) {
                    val artist = artistsArray.optJSONObject(i)?.optString("name").orEmpty()
                    if (artist.isNotBlank()) add(artist)
                }
            }
        }.joinToString(", ").ifBlank { "Unknown artist" }
        val album = track.optJSONObject("album")?.optString("name").orEmpty()
        val url = track.optJSONObject("external_urls")?.optString("spotify").orEmpty()
            .ifBlank { "https://open.spotify.com/track/$id" }
        return SpotifyTrack(id, track.optString("name", "Unknown track"), artists, album, url)
    }

    private fun randomUrlSafeString(bytesCount: Int): String {
        val bytes = ByteArray(bytesCount)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun postForm(url: String, values: Map<String, String>): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 12_000
        connection.readTimeout = 12_000
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        val body = values.entries.joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
        }
        return try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IOException("Spotify token request failed ($code): ${JSONObject(response).optString("error_description", JSONObject(response).optString("error", "unknown error"))}")
            JSONObject(response)
        } finally {
            connection.disconnect()
        }
    }

    private fun getJson(url: String, accessToken: String): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 12_000
        connection.readTimeout = 12_000
        connection.setRequestProperty("Authorization", "Bearer $accessToken")
        connection.setRequestProperty("Accept", "application/json")
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val detail = runCatching { JSONObject(response).optString("error") }.getOrNull().orEmpty()
                throw IOException("Spotify API request failed ($code). ${detail.ifBlank { "Check your Spotify app setup and account access." }}")
            }
            JSONObject(response)
        } finally {
            connection.disconnect()
        }
    }
}
