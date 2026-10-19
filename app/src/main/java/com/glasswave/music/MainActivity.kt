package com.glasswave.music

import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.util.Locale
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlinx.coroutines.launch

private val LumaBg = Color(0xFFF4F5F8)
private val LumaInk = Color(0xFF20212A)
private val LumaMuted = Color(0xFF7E8290)
private val LumaAccent = Color(0xFF777DE0)
private val LumaLilac = Color(0xFFE7E8FF)
private val LumaGlass = Color(0xDFFFFFFF)

data class LumaTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val uri: Uri
)

private suspend fun searchFreeMusic(searchText: String): List<LumaTrack> = withContext(Dispatchers.IO) {
    val safeSearch = searchText.trim().take(80)
    val licenseFilter = "(licenseurl:http*creativecommons.org/licenses/by/* OR licenseurl:http*creativecommons.org/licenses/by-sa/*)"
    val query = if (safeSearch.isBlank()) "mediatype:audio AND $licenseFilter"
                else "mediatype:audio AND $licenseFilter AND $safeSearch"
    val encodedQuery = URLEncoder.encode(query, "UTF-8")
    val searchUrl = "https://archive.org/advancedsearch.php?q=$encodedQuery&fl%5B%5D=identifier&fl%5B%5D=title&fl%5B%5D=creator&fl%5B%5D=licenseurl&rows=12&page=1&output=json&sort%5B%5D=downloads%20desc"
    val searchJson = URL(searchUrl).openConnection().run {
        connectTimeout = 10000
        readTimeout = 12000
        getInputStream().bufferedReader().use { it.readText() }
    }
    val docs = JSONObject(searchJson).optJSONObject("response")?.optJSONArray("docs") ?: return@withContext emptyList()
    val tracks = mutableListOf<LumaTrack>()
    for (index in 0 until docs.length()) {
        val doc = docs.optJSONObject(index) ?: continue
        val identifier = doc.optString("identifier").takeIf { it.isNotBlank() } ?: continue
        try {
            val metadataText = URL("https://archive.org/metadata/${Uri.encode(identifier)}").openConnection().run {
                connectTimeout = 8000
                readTimeout = 10000
                getInputStream().bufferedReader().use { it.readText() }
            }
            val item = JSONObject(metadataText)
            val metadata = item.optJSONObject("metadata") ?: continue
            val license = metadata.optString("licenseurl", doc.optString("licenseurl"))
            val approvedLicense = license.contains("creativecommons.org/licenses/by/") ||
                license.contains("creativecommons.org/licenses/by-sa/")
            if (!approvedLicense) continue
            val files = item.optJSONArray("files") ?: continue
            var audioName: String? = null
            for (fileIndex in 0 until files.length()) {
                val file = files.optJSONObject(fileIndex) ?: continue
                val name = file.optString("name")
                val isPrivate = file.optString("private") == "true"
                val playable = name.endsWith(".mp3", true) || name.endsWith(".ogg", true) ||
                    name.endsWith(".m4a", true) || name.endsWith(".flac", true)
                if (!isPrivate && playable && !name.contains("/")) {
                    audioName = name
                    break
                }
            }
            val fileName = audioName ?: continue
            val title = doc.optString("title", metadata.optString("title", "Free audio")).ifBlank { "Free audio" }
            val creator = doc.optString("creator", metadata.optString("creator", "Internet Archive")).ifBlank { "Internet Archive" }
            val streamUrl = "https://archive.org/download/${Uri.encode(identifier)}/${Uri.encode(fileName)}"
            tracks += LumaTrack(
                id = identifier.hashCode().toLong(),
                title = title,
                artist = creator,
                album = "Creative Commons · Internet Archive",
                duration = 0L,
                uri = Uri.parse(streamUrl)
            )
        } catch (_: Exception) {
            // Skip unavailable or malformed items and keep searching the remaining results.
        }
    }
    tracks
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(244, 245, 248)
        window.navigationBarColor = android.graphics.Color.rgb(244, 245, 248)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility =
                window.decorView.systemUiVisibility or
                    android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                    android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = LumaAccent, background = LumaBg, surface = Color.White)) {
                LumaApp(
                    loadTracks = ::loadTracks,
                    playTrack = ::playTrack,
                    pauseTrack = ::pauseTrack,
                    resumeTrack = ::resumeTrack
                )
            }
        }
    }

    private fun loadTracks(): List<LumaTrack> {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION
        )
        val result = mutableListOf<LumaTrack>()
        contentResolver.query(
            collection, projection, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                result += LumaTrack(
                    id = id,
                    title = cursor.getString(titleIndex) ?: "Unknown track",
                    artist = cursor.getString(artistIndex)?.takeUnless { it == "<unknown>" } ?: "Unknown artist",
                    album = cursor.getString(albumIndex) ?: "",
                    duration = cursor.getLong(durationIndex),
                    uri = ContentUris.withAppendedId(collection, id)
                )
            }
        }
        return result
    }

    private fun sendPlaybackAction(action: String, track: LumaTrack? = null) {
        val intent = Intent(this, LocalPlaybackService::class.java).setAction(action)
        if (track != null) {
            intent.putExtra(LocalPlaybackService.EXTRA_URI, track.uri.toString())
            intent.putExtra(LocalPlaybackService.EXTRA_TITLE, track.title)
            intent.putExtra(LocalPlaybackService.EXTRA_ARTIST, track.artist)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && action == LocalPlaybackService.ACTION_PLAY) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun playTrack(track: LumaTrack) = sendPlaybackAction(LocalPlaybackService.ACTION_PLAY, track)
    private fun pauseTrack() = sendPlaybackAction(LocalPlaybackService.ACTION_PAUSE)
    private fun resumeTrack() = sendPlaybackAction(LocalPlaybackService.ACTION_RESUME)
}

@Composable
private fun LumaApp(
    loadTracks: () -> List<LumaTrack>,
    playTrack: (LumaTrack) -> Unit,
    pauseTrack: () -> Unit,
    resumeTrack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val preferences = remember { context.getSharedPreferences("luma_preferences", android.content.Context.MODE_PRIVATE) }
    var tracks by remember { mutableStateOf(emptyList<LumaTrack>()) }
    var hasPermission by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf("Spotify") }
    var currentTrack by remember { mutableStateOf<LumaTrack?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var favourites by remember {
        mutableStateOf(preferences.getStringSet("favourites", emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet())
    }
    val spotifyClientId = BuildConfig.SPOTIFY_CLIENT_ID.trim()
    var spotifySession by remember { mutableStateOf<SpotifySession?>(null) }
    var spotifyLoading by remember { mutableStateOf(false) }
    var spotifyMessage by remember { mutableStateOf<String?>(null) }
    var spotifyPlaylists by remember { mutableStateOf(emptyList<SpotifyPlaylist>()) }
    var spotifyLikedSongs by remember { mutableStateOf(emptyList<SpotifyTrack>()) }
    var spotifySelectedPlaylist by remember { mutableStateOf<SpotifyPlaylist?>(null) }
    var spotifyTracks by remember { mutableStateOf(emptyList<SpotifyTrack>()) }
    var spotifyLibraryTab by remember { mutableStateOf("Playlists") }
    val coroutineScope = rememberCoroutineScope()
    var freeMusicSearch by remember { mutableStateOf("") }
    var freeMusicTracks by remember { mutableStateOf(emptyList<LumaTrack>()) }
    var freeMusicLoading by remember { mutableStateOf(false) }
    var freeMusicRequest by remember { mutableStateOf(0) }
    var freeMusicMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(favourites) {
        preferences.edit().putStringSet("favourites", favourites.map { it.toString() }.toSet()).apply()
    }
    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (granted) tracks = loadTracks()
    }
    LaunchedEffect(Unit) {
        hasPermission = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) tracks = loadTracks() else permissionLauncher.launch(permission)
    }

    val shownTracks = remember(tracks, search, tab, favourites) {
        val source = if (tab == "Favourites") tracks.filter { it.id in favourites } else tracks
        source.filter { it.title.contains(search, true) || it.artist.contains(search, true) || it.album.contains(search, true) }
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFFAFBFF), Color(0xFFE9ECF8), Color(0xFFF7F8FC))))) {
        Box(Modifier.align(Alignment.TopEnd).offset(x = 64.dp, y = 92.dp).size(245.dp).blur(72.dp).background(Color(0x627D8CF5), CircleShape))
        Box(Modifier.align(Alignment.BottomStart).offset(x = (-75).dp, y = (-55).dp).size(250.dp).blur(76.dp).background(Color(0x50B7A8F7), CircleShape))
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 19.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("L U M A", fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, color = LumaInk)
                    Text("YOUR MUSIC, IN ITS ELEMENT", fontSize = 9.sp, letterSpacing = 1.7.sp, color = LumaMuted)
                }
                GlassCircle("✧") { tab = "Favourites" }
            }

            if (tab == "Home") {
                Text("A little more", fontSize = 29.sp, fontWeight = FontWeight.Light, color = LumaInk)
                Text("you, in every note.", fontSize = 29.sp, fontWeight = FontWeight.SemiBold, color = LumaInk)
                Spacer(Modifier.height(7.dp))
                Text("A calm place for all your music.", fontSize = 13.sp, color = LumaMuted)
                Spacer(Modifier.height(19.dp))
                FeaturedGlassCard(tracks.size) { tab = "Library" }
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Your music", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = LumaInk, modifier = Modifier.weight(1f))
                    Text("SEE ALL  ›", fontSize = 10.sp, letterSpacing = 1.sp, color = LumaAccent, modifier = Modifier.clickable { tab = "Library" }.padding(6.dp))
                }
                Spacer(Modifier.height(8.dp))
            } else {
                Text(when (tab) { "Search" -> "Find your sound"; "Favourites" -> "Your favourites"; "Now Playing" -> "Now playing"; "Spotify" -> "Connect your music"; "Free Music" -> "Free music"; else -> "Your library" }, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = LumaInk)
                Spacer(Modifier.height(13.dp))
            }

            if (tab == "Search" || tab == "Library" || tab == "Favourites") {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Songs, artists, albums", color = LumaMuted) },
                    leadingIcon = { Text("⌕", fontSize = 23.sp, color = LumaMuted) },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = LumaGlass,
                        focusedContainerColor = LumaGlass,
                        unfocusedBorderColor = Color.White,
                        focusedBorderColor = LumaAccent.copy(alpha = 0.55f)
                    )
                )
                Spacer(Modifier.height(10.dp))
            }

            if (tab == "Free Music") {
                FreeMusicPanel(
                    searchText = freeMusicSearch,
                    onSearchTextChange = { freeMusicSearch = it },
                    tracks = freeMusicTracks,
                    loading = freeMusicLoading,
                    message = freeMusicMessage,
                    onSearch = {
                        freeMusicRequest += 1
                        freeMusicMessage = null
                    },
                    favouriteIds = favourites,
                    onPlay = { track -> currentTrack = track; playTrack(track); isPlaying = true },
                    onFavourite = { track -> favourites = if (track.id in favourites) favourites - track.id else favourites + track.id }
                )
                LaunchedEffect(freeMusicRequest, tab) {
                    if (tab == "Free Music") {
                        freeMusicLoading = true
                        try {
                            freeMusicTracks = searchFreeMusic(freeMusicSearch)
                            freeMusicMessage = if (freeMusicTracks.isEmpty()) "No playable Creative Commons tracks found. Try another search." else null
                        } catch (_: Exception) {
                            freeMusicMessage = "Could not load free music. Check your internet connection and try again."
                        } finally {
                            freeMusicLoading = false
                        }
                    }
                }
            } else if (tab == "Spotify") {
                SpotifyAccountPanel(
                    oauthConfigured = spotifyClientId.isNotBlank(),
                    connected = spotifySession != null,
                    loading = spotifyLoading,
                    message = spotifyMessage,
                    playlists = spotifyPlaylists,
                    likedSongs = spotifyLikedSongs,
                    selectedPlaylist = spotifySelectedPlaylist,
                    selectedTracks = spotifyTracks,
                    libraryTab = spotifyLibraryTab,
                    onLibraryTabChange = { spotifyLibraryTab = it; spotifySelectedPlaylist = null; spotifyTracks = emptyList() },
                    onConnect = {
                        if (spotifyLoading) return@SpotifyAccountPanel
                        if (spotifyClientId.isBlank()) {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://accounts.spotify.com/login")))
                                spotifyMessage = "Spotify login opened. Library sync becomes available when the LUMA app owner configures the official OAuth Client ID once."
                            } catch (_: Exception) {
                                spotifyMessage = "Could not open Spotify login. Check that a browser is installed."
                            }
                            return@SpotifyAccountPanel
                        }
                        spotifyLoading = true
                        spotifyMessage = "Opening secure Spotify sign-in…"
                        coroutineScope.launch {
                            try {
                                val session = SpotifyApi.authorize(spotifyClientId) { authUrl ->
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)))
                                }
                                spotifySession = session
                                spotifyPlaylists = SpotifyApi.fetchPlaylists(session)
                                spotifySession = SpotifyApi.refreshIfNeeded(session)
                                spotifyLikedSongs = SpotifyApi.fetchLikedSongs(spotifySession!!)
                                spotifyLibraryTab = "Playlists"
                                spotifySelectedPlaylist = null
                                spotifyTracks = emptyList()
                                spotifyMessage = "Connected. Choose a playlist or open Liked Songs."
                            } catch (error: Exception) {
                                spotifyMessage = error.message ?: "Spotify sign-in failed. Check the Client ID and redirect URI."
                            } finally {
                                spotifyLoading = false
                            }
                        }
                    },
                    onSelectPlaylist = { playlist ->
                        val session = spotifySession
                        if (session != null) {
                            spotifyLoading = true
                            spotifyMessage = "Loading playlist…"
                            coroutineScope.launch {
                                try {
                                    val fresh = SpotifyApi.refreshIfNeeded(session)
                                    spotifySession = fresh
                                    spotifyTracks = SpotifyApi.fetchPlaylistTracks(fresh, playlist.id)
                                    spotifySelectedPlaylist = playlist
                                    spotifyLibraryTab = "Tracks"
                                    spotifyMessage = if (spotifyTracks.isEmpty()) "No tracks were returned for this playlist." else null
                                } catch (error: Exception) {
                                    spotifyMessage = error.message ?: "Couldn't load this Spotify playlist."
                                } finally {
                                    spotifyLoading = false
                                }
                            }
                        }
                    },
                    onShowLikedSongs = {
                        val session = spotifySession
                        if (session != null) {
                            spotifyLoading = true
                            spotifyMessage = "Loading Liked Songs…"
                            coroutineScope.launch {
                                try {
                                    val fresh = SpotifyApi.refreshIfNeeded(session)
                                    spotifySession = fresh
                                    spotifyLikedSongs = SpotifyApi.fetchLikedSongs(fresh)
                                    spotifySelectedPlaylist = null
                                    spotifyLibraryTab = "Liked Songs"
                                    spotifyMessage = if (spotifyLikedSongs.isEmpty()) "No saved tracks were returned." else null
                                } catch (error: Exception) {
                                    spotifyMessage = error.message ?: "Couldn't load Liked Songs."
                                } finally {
                                    spotifyLoading = false
                                }
                            }
                        }
                    },
                    onOpenSpotify = { url ->
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        } catch (_: Exception) {
                            spotifyMessage = "No app could open that Spotify link."
                        }
                    }
                )
            } else if (tab == "Now Playing" && currentTrack != null) {
                NowPlayingGlass(currentTrack!!, isPlaying) {
                    if (isPlaying) pauseTrack() else resumeTrack()
                    isPlaying = !isPlaying
                }
            } else if (shownTracks.isEmpty()) {
                GlassCard(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(if (!hasPermission) "Music access is needed" else "Your music will appear here", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = LumaInk)
                    Spacer(Modifier.height(6.dp))
                    Text(if (!hasPermission) "Allow audio permission to scan songs on this device." else "Add audio files to your phone, then reopen LUMA.", fontSize = 12.sp, color = LumaMuted)
                    if (!hasPermission) {
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { permissionLauncher.launch(permission) }) { Text("Allow access") }
                    }
                }
            } else {
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(shownTracks.take(if (tab == "Home") 5 else shownTracks.size), key = { it.id }) { track ->
                        TrackGlassRow(track, track.id in favourites,
                            onFavourite = { favourites = if (track.id in favourites) favourites - track.id else favourites + track.id },
                            onClick = { currentTrack = track; playTrack(track); isPlaying = true }
                        )
                    }
                }
            }

            Spacer(Modifier.height(5.dp))
            if (currentTrack != null && tab != "Now Playing") {
                MiniGlassPlayer(currentTrack!!, isPlaying, { tab = "Now Playing" }) {
                    if (isPlaying) pauseTrack() else resumeTrack()
                    isPlaying = !isPlaying
                }
                Spacer(Modifier.height(9.dp))
            }
            BottomGlassBar(tab) { tab = it }
            Spacer(Modifier.height(7.dp))
        }
    }
}

@Composable
private fun FeaturedGlassCard(trackCount: Int, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(152.dp)
            .shadow(16.dp, RoundedCornerShape(30.dp), ambientColor = Color(0x22777DE0))
            .background(Brush.linearGradient(listOf(Color(0xF9FFFFFF), Color(0xDCE7E8FF), Color(0xEFFFFFFF))), RoundedCornerShape(30.dp))
            .clickable(onClick = onClick).padding(20.dp)
    ) {
        Column(Modifier.align(Alignment.CenterStart)) {
            Text("YOUR PERSONAL SOUNDTRACK", fontSize = 9.sp, letterSpacing = 1.5.sp, color = LumaMuted)
            Spacer(Modifier.height(9.dp))
            Text("Every song has\nits own light.", fontSize = 22.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold, color = LumaInk)
            Spacer(Modifier.height(7.dp))
            Text("$trackCount tracks  ·  Open library  ↗", fontSize = 11.sp, color = LumaAccent)
        }
        Box(Modifier.align(Alignment.CenterEnd).size(88.dp).background(Brush.linearGradient(listOf(Color.White, Color(0xFFD7D9FF), Color(0xFFAEB4F5))), CircleShape), contentAlignment = Alignment.Center) {
            Text("♫", fontSize = 46.sp, color = LumaAccent)
        }
    }
}

@Composable
private fun TrackGlassRow(track: LumaTrack, favourite: Boolean, onFavourite: () -> Unit, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(Color(0xDFFFFFFF), RoundedCornerShape(22.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(46.dp).background(Brush.linearGradient(listOf(LumaLilac, Color.White)), RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) { Text("♫", color = LumaAccent, fontSize = 23.sp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, color = LumaInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, color = LumaMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(formatTime(track.duration), fontSize = 10.sp, color = LumaMuted)
        Spacer(Modifier.width(7.dp))
        Text(if (favourite) "♥" else "♡", fontSize = 20.sp, color = if (favourite) LumaAccent else LumaMuted, modifier = Modifier.clickable(onClick = onFavourite).padding(4.dp))
    }
}

@Composable
private fun MiniGlassPlayer(track: LumaTrack, playing: Boolean, onOpen: () -> Unit, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().shadow(13.dp, RoundedCornerShape(25.dp), ambientColor = Color(0x18000000))
            .background(Brush.horizontalGradient(listOf(Color(0xF9FFFFFF), Color(0xE8EDEEFF))), RoundedCornerShape(25.dp))
            .clickable(onClick = onOpen).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(42.dp).background(LumaLilac, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Text("♫", color = LumaAccent, fontSize = 22.sp) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = LumaInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, fontSize = 10.sp, color = LumaMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(if (playing) "Ⅱ" else "▶", fontSize = 20.sp, color = LumaAccent, modifier = Modifier.clickable(onClick = onToggle).padding(horizontal = 10.dp, vertical = 6.dp))
    }
}

@Composable
private fun NowPlayingGlass(track: LumaTrack, playing: Boolean, onPlayPause: () -> Unit) {
    Column(Modifier.fillMaxWidth().fillMaxHeight(0.72f).padding(vertical = 10.dp).background(Brush.verticalGradient(listOf(Color(0xF9FFFFFF), Color(0xDDE9EAFF))), RoundedCornerShape(32.dp)).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).padding(8.dp).shadow(18.dp, RoundedCornerShape(30.dp)).background(Brush.linearGradient(listOf(Color(0xFFFDFDFF), Color(0xFFD8DAFF), Color.White)), RoundedCornerShape(30.dp)), contentAlignment = Alignment.Center) { Text("♫", fontSize = 100.sp, color = LumaAccent) }
        Spacer(Modifier.height(24.dp))
        Text(track.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = LumaInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(track.artist, fontSize = 14.sp, color = LumaMuted)
        Spacer(Modifier.height(24.dp))
        LinearProgressIndicator(progress = { 0f }, modifier = Modifier.fillMaxWidth(), color = LumaAccent, trackColor = Color(0xFFDADCF0))
        Spacer(Modifier.height(18.dp))
        Box(Modifier.size(72.dp).background(Brush.linearGradient(listOf(Color(0xFF888DEB), Color(0xFF5D63CB))), CircleShape).clickable(onClick = onPlayPause), contentAlignment = Alignment.Center) { Text(if (playing) "Ⅱ" else "▶", fontSize = 28.sp, color = Color.White) }
    }
}

@Composable
private fun BottomGlassBar(tab: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().shadow(13.dp, RoundedCornerShape(28.dp), ambientColor = Color(0x16000000)).background(Brush.verticalGradient(listOf(Color(0xFAFFFFFF), Color(0xDFFFFFFF))), RoundedCornerShape(28.dp)).padding(horizontal = 4.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
        listOf(Triple("Home", "⌂", "Home"), Triple("Search", "⌕", "Search"), Triple("Library", "♫", "Library"), Triple("Favourites", "♡", "Likes"), Triple("Spotify", "◉", "Spotify"), Triple("Free Music", "♪", "Free")).forEach { (name, icon, label) ->
            Column(Modifier.weight(1f).clickable { onSelect(name) }.padding(vertical = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(icon, fontSize = 21.sp, color = if (tab == name) LumaAccent else LumaMuted)
                Text(label, fontSize = 9.sp, color = if (tab == name) LumaAccent else LumaMuted, fontWeight = if (tab == name) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun FreeMusicPanel(
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    tracks: List<LumaTrack>,
    loading: Boolean,
    message: String?,
    onSearch: () -> Unit,
    favouriteIds: Set<Long>,
    onPlay: (LumaTrack) -> Unit,
    onFavourite: (LumaTrack) -> Unit
) {
    Column(Modifier.fillMaxWidth().fillMaxHeight(0.70f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Free-to-play music with Creative Commons BY / BY-SA metadata from Internet Archive. Tracks are streamed from their public item pages.",
            fontSize = 11.sp, color = LumaMuted, lineHeight = 16.sp
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = searchText,
                onValueChange = onSearchTextChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search free music") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = onSearch, shape = RoundedCornerShape(16.dp)) { Text("Search") }
        }
        if (loading) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("Finding licensed tracks…", fontSize = 12.sp, color = LumaMuted)
            }
        }
        if (message != null) Text(message, fontSize = 12.sp, color = LumaMuted)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
            items(tracks, key = { it.id }) { track ->
                TrackGlassRow(track, track.id in favouriteIds, onFavourite = { onFavourite(track) }, onClick = { onPlay(track) })
            }
        }
    }
}

@Composable
private fun SpotifyAccountPanel(
    oauthConfigured: Boolean,
    connected: Boolean,
    loading: Boolean,
    message: String?,
    playlists: List<SpotifyPlaylist>,
    likedSongs: List<SpotifyTrack>,
    selectedPlaylist: SpotifyPlaylist?,
    selectedTracks: List<SpotifyTrack>,
    libraryTab: String,
    onLibraryTabChange: (String) -> Unit,
    onConnect: () -> Unit,
    onSelectPlaylist: (SpotifyPlaylist) -> Unit,
    onShowLikedSongs: () -> Unit,
    onOpenSpotify: (String) -> Unit
) {
    Column(
        Modifier.fillMaxWidth().fillMaxHeight(0.74f).background(
            Brush.verticalGradient(listOf(Color(0xF9FFFFFF), Color(0xDDE7E8FF))),
            RoundedCornerShape(28.dp)
        ).padding(15.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Welcome to LUMA", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = LumaInk)
                Text(if (connected) "Connected · playlist metadata" else "Sign in to sync your Spotify library", fontSize = 11.sp, color = LumaMuted)
            }
            if (connected) {
                Text("CONNECTED", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = LumaAccent)
            }
        }
        if (!connected) {
            Box(
                Modifier.fillMaxWidth().height(122.dp)
                    .background(
                        Brush.linearGradient(listOf(Color(0xFF202125), Color(0xFF34383D), Color(0xFF101114))),
                        RoundedCornerShape(24.dp)
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.70f), RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier.align(Alignment.CenterStart).offset(x = 26.dp).size(116.dp)
                        .blur(25.dp).background(Color(0x667BDFB4), CircleShape)
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(47.dp).background(
                            Brush.linearGradient(listOf(Color(0xFFB7FFD9), Color(0xFF55D69A))),
                            CircleShape
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("♫", fontSize = 29.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10271C))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("YOUR SPOTIFY, REIMAGINED", fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp, color = Color.White)
                }
                Text("✧", modifier = Modifier.align(Alignment.TopEnd).padding(16.dp), color = Color.White.copy(alpha = 0.72f), fontSize = 24.sp)
            }
            Text(
                "Continue on Spotify's official sign-in page. Your password stays with Spotify.",
                fontSize = 12.sp, color = LumaMuted, lineHeight = 17.sp
            )
            Button(
                onClick = onConnect,
                enabled = !loading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(19.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF202125), contentColor = Color.White)
            ) {
                Text(if (loading) "Opening Spotify…" else "Continue with Spotify", fontWeight = FontWeight.SemiBold)
            }
            if (!oauthConfigured) {
                Text(
                    "This opens Spotify login, but syncing playlists requires the LUMA app owner to configure official OAuth once. You won't need to enter a Client ID.",
                    fontSize = 10.sp, color = LumaMuted, lineHeight = 15.sp
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(7.dp).background(Color(0xFF65B994), CircleShape))
                Text("No password is collected by LUMA", fontSize = 10.sp, color = LumaMuted)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Playlists", "Liked Songs").forEach { option ->
                    val active = (option == "Playlists" && libraryTab == "Playlists") ||
                        (option == "Liked Songs" && libraryTab == "Liked Songs")
                    Surface(
                        onClick = {
                            if (option == "Liked Songs") onShowLikedSongs()
                            else onLibraryTabChange("Playlists")
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = if (active) LumaAccent.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(option, modifier = Modifier.padding(vertical = 10.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            color = if (active) LumaAccent else LumaMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (selectedPlaylist != null && libraryTab == "Tracks") {
                Text(
                    "‹  ${selectedPlaylist.name}",
                    modifier = Modifier.clickable { onLibraryTabChange("Playlists") }.padding(vertical = 3.dp),
                    color = LumaAccent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                )
            }
            if (loading) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("Loading Spotify library…", color = LumaMuted, fontSize = 11.sp)
                }
            }
            when (libraryTab) {
                "Playlists" -> {
                    if (playlists.isEmpty() && !loading) Text("No playlists returned. Check Spotify API access.", color = LumaMuted, fontSize = 12.sp)
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp), contentPadding = PaddingValues(bottom = 6.dp)) {
                        items(playlists, key = { it.id }) { playlist ->
                            Row(
                                Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.83f), RoundedCornerShape(17.dp))
                                    .clickable { onSelectPlaylist(playlist) }.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.size(43.dp).background(LumaLilac, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                                    Text("♫", color = LumaAccent, fontSize = 22.sp)
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(playlist.name, color = LumaInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${playlist.trackCount} tracks", color = LumaMuted, fontSize = 10.sp)
                                }
                                Text("OPEN", fontSize = 10.sp, color = LumaAccent, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable { onOpenSpotify(playlist.spotifyUrl) }.padding(6.dp))
                            }
                        }
                    }
                }
                "Liked Songs" -> SpotifyTrackList(likedSongs, loading, onOpenSpotify)
                "Tracks" -> SpotifyTrackList(selectedTracks, loading, onOpenSpotify)
            }
        }
        if (message != null) Text(message, fontSize = 10.sp, color = if (message.contains("failed", true) || message.contains("couldn't", true) || message.contains("error", true)) Color(0xFFB54747) else LumaMuted, lineHeight = 14.sp)
        Text("LUMA does not play or download Spotify audio and cannot remove ads from Spotify Free.", fontSize = 10.sp, color = LumaMuted, lineHeight = 14.sp)
    }
}

@Composable
private fun SpotifyTrackList(
    tracks: List<SpotifyTrack>,
    loading: Boolean,
    onOpenSpotify: (String) -> Unit
) {
    if (tracks.isEmpty() && !loading) {
        Text("No tracks returned.", color = LumaMuted, fontSize = 12.sp)
    }
    LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(0.37f), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 6.dp)) {
        items(tracks, key = { it.id }) { track ->
            Row(
                Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.83f), RoundedCornerShape(15.dp))
                    .clickable { onOpenSpotify(track.spotifyUrl) }.padding(11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(38.dp).background(LumaLilac, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    Text("♫", color = LumaAccent, fontSize = 20.sp)
                }
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(track.name, color = LumaInk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.artists, color = LumaMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("↗", color = LumaAccent, fontSize = 17.sp)
            }
        }
    }
}

@Composable
private fun GlassCircle(symbol: String, onClick: () -> Unit) {
    Box(Modifier.size(46.dp).shadow(8.dp, CircleShape).background(Color(0xDFFFFFFF), CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) { Text(symbol, fontSize = 24.sp, color = LumaAccent) }
}

@Composable
private fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.shadow(10.dp, RoundedCornerShape(25.dp)).background(Brush.linearGradient(listOf(Color(0xF9FFFFFF), Color(0xDFFFFFFF))), RoundedCornerShape(25.dp)).padding(20.dp), content = content)
}

private fun formatTime(ms: Long): String {
    val seconds = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60)
}