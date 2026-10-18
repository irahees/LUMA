
package com.glasswave.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Background = Color(0xFFF3F2EF)
private val Glass = Color(0xD9FFFFFF)
private val Ink = Color(0xFF191919)
private val Muted = Color(0xFF777777)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LumaApp()
        }
    }
}

@Composable
fun LumaApp() {
    var search by remember { mutableStateOf("") }
    var playing by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf("Home") }

    val library = listOf(
        "All Songs",
        "Recently Played",
        "Favourites",
        "Playlists"
    )

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 22.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp, bottom = 30.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "LUMA",
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 6.sp,
                            color = Ink
                        )
                        Text(
                            "MUSIC, IN ITS PUREST FORM",
                            fontSize = 9.sp,
                            letterSpacing = 2.sp,
                            color = Muted
                        )
                    }
                    Text("◉", fontSize = 28.sp, color = Ink)
                }

                Text(
                    "Feel the music.",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Ink
                )
                Text(
                    "Keep it simple.",
                    fontSize = 28.sp,
                    color = Muted
                )

                Spacer(Modifier.height(24.dp))

                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search your library") },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color(0xFFDAD9D5),
                        focusedBorderColor = Ink
                    )
                )

                Spacer(Modifier.height(26.dp))

                Text(
                    "YOUR LIBRARY",
                    fontSize = 11.sp,
                    letterSpacing = 2.sp,
                    color = Muted
                )

                Spacer(Modifier.height(12.dp))

                library.filter {
                    it.contains(search, ignoreCase = true)
                }.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
                            .background(
                                Glass,
                                RoundedCornerShape(22.dp)
                            )
                            .clickable { selected = item }
                            .padding(19.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            when (item) {
                                "All Songs" -> "♫"
                                "Recently Played" -> "◷"
                                "Favourites" -> "♡"
                                else -> "▤"
                            },
                            fontSize = 24.sp,
                            color = Ink
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            item,
                            modifier = Modifier.weight(1f),
                            fontSize = 15.sp,
                            color = Ink
                        )
                        Text("›", fontSize = 24.sp, color = Muted)
                    }
                }

                Spacer(Modifier.weight(1f))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                        .background(
                            Glass,
                            RoundedCornerShape(24.dp)
                        )
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "LUMA Player",
                            fontWeight = FontWeight.SemiBold,
                            color = Ink
                        )
                        Text(
                            if (playing) "Playing" else "Ready to play",
                            fontSize = 12.sp,
                            color = Muted
                        )
                    }
                    Text(
                        if (playing) "Ⅱ" else "▶",
                        modifier = Modifier
                            .background(Ink, RoundedCornerShape(50))
                            .clickable { playing = !playing }
                            .padding(
                                horizontal = 18.dp,
                                vertical = 12.dp
                            ),
                        color = Color.White,
                        fontSize = 18.sp
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    listOf("Home", "Search", "Library").forEach { tab ->
                        Text(
                            tab,
                            modifier = Modifier
                                .clickable { selected = tab }
                                .padding(12.dp),
                            color = if (selected == tab) Ink else Muted,
                            fontWeight = if (selected == tab) {
                                FontWeight.Bold
                            } else {
                                FontWeight.Normal
                            }
                        )
                    }
                }
            }
        }
    }
}

