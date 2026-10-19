package com.glasswave.music

import android.Manifest
import android.content.ContentUris
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.util.Locale

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

class MainActivity : ComponentActivity() {
    private var mediaPlayer: MediaPlayer? = null

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

    private fun playTrack(track: LumaTrack) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@MainActivity, track.uri)
                setOnPreparedListener { it.start() }
                prepareAsync()
            }
        } catch (_: Exception) {
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    private fun pauseTrack() {
        if (mediaPlayer?.isPlaying == true) mediaPlayer?.pause()
    }

    private fun resumeTrack() {
        try { mediaPlayer?.start() } catch (_: Exception) { }
    }

    override fun onDestroy() {
        mediaPlayer?.release()
        mediaPlayer = null
        super.onDestroy()
    }
}

@Composable
private fun LumaApp(
    loadTracks: () -> List<LumaTrack>,
    playTrack: (LumaTrack) -> Unit,
    pauseTrack: () -> Unit,
    resumeTrack: () -> Unit
) {
    var tracks by remember { mutableStateOf(emptyList<LumaTrack>()) }
    var hasPermission by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf("Home") }
    var currentTrack by remember { mutableStateOf<LumaTrack?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var favourites by remember { mutableStateOf(setOf<Long>()) }

    val context = androidx.compose.ui.platform.LocalContext.current
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

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFFCFCFE), Color(0xFFEDEFFA), Color(0xFFF8F9FC))))) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 19.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("L U M A", fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, color = LumaInk)
                    Text("YOUR SOUND, YOUR SPACE", fontSize = 9.sp, letterSpacing = 2.sp, color = LumaMuted)
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
                Text(when (tab) { "Search" -> "Find your sound"; "Favourites" -> "Your favourites"; "Now Playing" -> "Now playing"; else -> "Your library" }, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = LumaInk)
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

            if (tab == "Now Playing" && currentTrack != null) {
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
    Row(Modifier.fillMaxWidth().shadow(13.dp, RoundedCornerShape(28.dp), ambientColor = Color(0x16000000)).background(Brush.verticalGradient(listOf(Color(0xFAFFFFFF), Color(0xDFFFFFFF))), RoundedCornerShape(28.dp)).padding(horizontal = 7.dp, vertical = 9.dp), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
        listOf(Triple("Home", "⌂", "Home"), Triple("Search", "⌕", "Search"), Triple("Library", "♫", "Library"), Triple("Favourites", "♡", "Likes")).forEach { (name, icon, label) ->
            Column(Modifier.weight(1f).clickable { onSelect(name) }.padding(vertical = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(icon, fontSize = 21.sp, color = if (tab == name) LumaAccent else LumaMuted)
                Text(label, fontSize = 9.sp, color = if (tab == name) LumaAccent else LumaMuted, fontWeight = if (tab == name) FontWeight.Bold else FontWeight.Normal)
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
