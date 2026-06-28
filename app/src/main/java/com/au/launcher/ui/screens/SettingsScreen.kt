package com.au.launcher.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.au.launcher.R
import com.au.launcher.ui.theme.*
import com.au.launcher.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(),
    onBack: () -> Unit
) {
    val language by viewModel.language.collectAsState()
    val bgImageUri by viewModel.bgImageUri.collectAsState()
    val bgBlur by viewModel.bgBlur.collectAsState()
    val bgOpacity by viewModel.bgOpacity.collectAsState()
    val maskColorInt by viewModel.maskColor.collectAsState()
    val deleteAfterInstall by viewModel.deleteAfterInstall.collectAsState()
    val backgroundDownload by viewModel.backgroundDownload.collectAsState()
    
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                viewModel.setBgImage(uri.toString())
            } catch (e: Exception) {
                viewModel.setBgImage(uri.toString())
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Black)) {
        // Background Image Layer
        if (bgImageUri != null) {
            AsyncImage(
                model = bgImageUri,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(bgBlur.dp),
                contentScale = ContentScale.Crop
            )
            
            // Mask/Overlay Layer
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(maskColorInt).copy(alpha = 1f - bgOpacity))
            )
        }

        Scaffold(
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .border(5.dp, White, RectangleShape)
                            .background(Black)
                            .clickable { 
                                com.au.launcher.utils.SoundHelper.playCancel()
                                onBack() 
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            modifier = Modifier.size(24.dp),
                            tint = White
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .border(5.dp, White, RectangleShape)
                            .background(Black)
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_title),
                            color = White,
                            fontSize = 15.sp,
                            fontFamily = FzxsFontFamily,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            containerColor = Color.Transparent
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(12.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                // Language Selection
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingsLabel(stringResource(R.string.settings_language))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LanguageButton(
                            text = stringResource(R.string.lang_en),
                            isActive = language == "en",
                            onClick = { 
                                com.au.launcher.utils.SoundHelper.playSwitch()
                                viewModel.setLanguage("en") 
                            }
                        )
                        LanguageButton(
                            text = stringResource(R.string.lang_zh),
                            isActive = language == "zh",
                            onClick = { 
                                com.au.launcher.utils.SoundHelper.playSwitch()
                                viewModel.setLanguage("zh") 
                            }
                        )
                    }
                }

                // Background Selection
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingsLabel(stringResource(R.string.settings_background))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(5.dp, White, RectangleShape)
                            .background(Black.copy(alpha = 0.7f))
                            .clickable { 
                                com.au.launcher.utils.SoundHelper.playClick()
                                launcher.launch(arrayOf("image/*")) 
                            }
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (bgImageUri != null) stringResource(R.string.image_selected) else stringResource(R.string.select_image),
                            color = if (bgImageUri != null) Highlight else White,
                            fontSize = 14.sp,
                            fontFamily = FzxsFontFamily
                        )
                    }

                    if (bgImageUri != null) {
                        // Blur Control
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${stringResource(R.string.settings_blur).uppercase()}: ${bgBlur.toInt()}", 
                                color = White, fontSize = 12.sp, fontFamily = FzxsFontFamily)
                            PixelSlider(
                                value = bgBlur,
                                onValueChange = { viewModel.setBgBlur(it) },
                                valueRange = 0f..25f
                            )
                        }

                        // Opacity Control
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${stringResource(R.string.settings_opacity).uppercase()}: ${(bgOpacity * 100).toInt()}%", 
                                color = White, fontSize = 12.sp, fontFamily = FzxsFontFamily)
                            PixelSlider(
                                value = bgOpacity,
                                onValueChange = { viewModel.setBgOpacity(it) },
                                valueRange = 0f..1f
                            )
                        }

                        // Mask Color Selection
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.settings_mask_color).uppercase(), 
                                color = White, fontSize = 12.sp, fontFamily = FzxsFontFamily)
                            val colors = listOf(Color.Black, Color.DarkGray, Color(0xFF1A1A1A), Color(0xFF001219), Color(0xFF1B0000))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                colors.forEach { color ->
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(color)
                                            .border(
                                                width = 5.dp,
                                                color = if (maskColorInt == color.toArgb()) Highlight else White,
                                                shape = RectangleShape
                                            )
                                            .clickable { 
                                                com.au.launcher.utils.SoundHelper.playSwitch()
                                                viewModel.setMaskColor(color.toArgb()) 
                                            }
                                    )
                                }
                            }
                        }

                        Text(
                            text = stringResource(R.string.settings_clear_bg),
                            color = DimText,
                            fontSize = 12.sp,
                            fontFamily = FzxsFontFamily,
                            modifier = Modifier
                                .clickable { 
                                    com.au.launcher.utils.SoundHelper.playClick()
                                    viewModel.setBgImage(null) 
                                }
                                .padding(vertical = 8.dp)
                        )
                    }
                }

                // Download Settings
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingsLabel(stringResource(R.string.settings_download))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(5.dp, White, RectangleShape)
                            .background(Black.copy(alpha = 0.7f))
                            .clickable { 
                                com.au.launcher.utils.SoundHelper.playSwitch()
                                viewModel.setDeleteAfterInstall(!deleteAfterInstall) 
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.delete_after_install),
                            color = White,
                            fontSize = 14.sp,
                            fontFamily = FzxsFontFamily
                        )
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .border(3.dp, if (deleteAfterInstall) Highlight else White, RectangleShape)
                                .background(if (deleteAfterInstall) Highlight else Color.Transparent)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(5.dp, White, RectangleShape)
                            .background(Black.copy(alpha = 0.7f))
                            .clickable { 
                                com.au.launcher.utils.SoundHelper.playSwitch()
                                viewModel.setBackgroundDownload(!backgroundDownload) 
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.background_download),
                            color = White,
                            fontSize = 14.sp,
                            fontFamily = FzxsFontFamily
                        )
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .border(3.dp, if (backgroundDownload) Highlight else White, RectangleShape)
                                .background(if (backgroundDownload) Highlight else Color.Transparent)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PixelSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        thumb = {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .border(3.dp, White, RectangleShape)
                    .background(White)
            )
        },
        track = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .border(2.dp, White, RectangleShape)
                    .background(Color(0xFF333333))
            ) {
                // Active track (left side)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = (value - valueRange.start) / (valueRange.endInclusive - valueRange.start))
                        .fillMaxHeight()
                        .background(White)
                )
            }
        }
    )
}

@Composable
fun SettingsLabel(text: String) {
    Text(
        text = text,
        color = White,
        fontSize = 14.sp,
        fontFamily = FzxsFontFamily,
        fontWeight = FontWeight.Bold
    )
}

@Composable
fun LanguageButton(text: String, isActive: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .border(5.dp, if (isActive) Highlight else White, RectangleShape)
            .background(Black.copy(alpha = 0.7f))
            .clickable { 
                com.au.launcher.utils.SoundHelper.playClick()
                onClick() 
            }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            color = if (isActive) Highlight else White,
            fontSize = 13.sp,
            fontFamily = FzxsFontFamily,
            fontWeight = FontWeight.Bold
        )
    }
}
