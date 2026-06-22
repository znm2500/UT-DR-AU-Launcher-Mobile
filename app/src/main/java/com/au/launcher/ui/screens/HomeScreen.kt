package com.au.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.au.launcher.R
import com.au.launcher.api.GameModel
import com.au.launcher.api.LocalizedString
import com.au.launcher.ui.components.GameCard
import com.au.launcher.ui.components.GameStatus
import com.au.launcher.ui.components.TopSearchBar
import com.au.launcher.ui.theme.*
import com.au.launcher.utils.Constants
import com.au.launcher.utils.DownloadState
import com.au.launcher.viewmodel.GameViewModel
import com.au.launcher.viewmodel.SettingsViewModel

@Composable
fun HomeScreen(
    viewModel: GameViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel(),
    onNavigateToSettings: () -> Unit,
    onNavigateToUpload: () -> Unit,
    onNavigateToImport: () -> Unit
) {
    val games by viewModel.pagedGames.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isMoreLoading by viewModel.isMoreLoading.collectAsState()
    val currentCategory by viewModel.currentCategory.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val downloadStates by viewModel.downloadStates.collectAsState()
    val installedUpdate by viewModel.installedPackagesUpdate.collectAsState()
    val announcementToShow by viewModel.announcementToShow.collectAsState()
    val updateToShow by viewModel.updateToShow.collectAsState()
    var gameIdToRemove by remember { mutableStateOf<String?>(null) }
    val hasMore = viewModel.hasMore()
    val isRegionDetected by settingsViewModel.isRegionDetected.collectAsState()
    
    LaunchedEffect(isRegionDetected) {
        if (isRegionDetected) {
            viewModel.refreshGames()
        }
    }
    
    val language by settingsViewModel.language.collectAsState()
    val bgImageUri by settingsViewModel.bgImageUri.collectAsState()
    val bgBlur by settingsViewModel.bgBlur.collectAsState()
    val bgOpacity by settingsViewModel.bgOpacity.collectAsState()
    val maskColorInt by settingsViewModel.maskColor.collectAsState()
    val context = LocalContext.current

    if (announcementToShow != null) {
        PixelDialog(
            title = stringResource(R.string.announcement_title),
            content = announcementToShow!!.get(language),
            confirmText = stringResource(R.string.ok),
            onConfirm = { viewModel.dismissAnnouncement() }
        )
    }

    if (updateToShow != null) {
        PixelDialog(
            title = stringResource(R.string.update_title) + " " + updateToShow!!.newestVersion,
            content = updateToShow!!.updateLog.get(language),
            confirmText = stringResource(R.string.ok),
            secondaryText = stringResource(R.string.download),
            dismissText = "不再提示",
            onConfirm = { viewModel.dismissUpdate() },
            onSecondary = {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(Constants.UPDATE_PAGE_URL))
                context.startActivity(intent)
            },
            onDismiss = {
                viewModel.neverShowUpdateAgain(updateToShow!!.newestVersion)
            }
        )
    }

    if (gameIdToRemove != null) {
        PixelDialog(
            title = stringResource(R.string.remove_confirm_title),
            content = stringResource(R.string.remove_confirm_msg),
            confirmText = stringResource(R.string.ok),
            secondaryText = stringResource(R.string.cancel),
            onConfirm = {
                com.au.launcher.utils.SoundHelper.playConfirm()
                viewModel.removeLocalGame(gameIdToRemove!!)
                gameIdToRemove = null
            },
            onSecondary = {
                com.au.launcher.utils.SoundHelper.playCancel()
                gameIdToRemove = null
            }
        )
    }

    HomeScreenContent(
        games = games,
        isLoading = isLoading,
        isMoreLoading = isMoreLoading,
        hasMore = hasMore,
        currentCategory = currentCategory,
        searchQuery = searchQuery,
        language = language,
        bgImageUri = bgImageUri,
        bgBlur = bgBlur,
        bgOpacity = bgOpacity,
        maskColor = Color(maskColorInt),
        downloadStates = downloadStates,
        onSearchQueryChange = { viewModel.setSearchQuery(it) },
        onCategorySelect = { viewModel.setCategory(it) },
        onLoadMore = { viewModel.loadNextPage() },
        isInstalled = { id -> 
            @Suppress("UNUSED_VARIABLE")
            val trigger = installedUpdate
            viewModel.isInstalled(id) 
        },
        onGameAction = { game -> viewModel.handleGameAction(game.id, game) },
        onRemoveLocal = { id -> gameIdToRemove = id },
        onSettingsClick = onNavigateToSettings,
        onUploadClick = onNavigateToUpload,
        onImportClick = onNavigateToImport
    )
}

@Composable
fun PixelDialog(
    title: String,
    content: String,
    confirmText: String,
    secondaryText: String? = null,
    dismissText: String? = null,
    onConfirm: () -> Unit,
    onSecondary: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null
) {
    Dialog(onDismissRequest = {}) {
        PixelDialogContent(
            title = title,
            content = content,
            confirmText = confirmText,
            secondaryText = secondaryText,
            dismissText = dismissText,
            onConfirm = onConfirm,
            onSecondary = onSecondary,
            onDismiss = onDismiss
        )
    }
}

@Composable
fun PixelDialogContent(
    title: String,
    content: String,
    confirmText: String,
    secondaryText: String? = null,
    dismissText: String? = null,
    onConfirm: () -> Unit,
    onSecondary: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(5.dp, White, RectangleShape)
            .background(Color(0xFF1A1A1A))
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = title.uppercase(),
                color = Highlight,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FzxsFontFamily
            )
            
            Text(
                text = content,
                color = White,
                fontSize = 14.sp,
                fontFamily = FzxsFontFamily,
                lineHeight = 20.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
            ) {
                if (dismissText != null && onDismiss != null) {
                    PixelDialogButton(text = dismissText, onClick = onDismiss)
                }
                if (secondaryText != null && onSecondary != null) {
                    PixelDialogButton(text = secondaryText, onClick = onSecondary)
                }
                PixelDialogButton(text = confirmText, onClick = onConfirm)
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun PixelAnnouncementDialogPreview() {
    AULauncherTheme {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Gray),
            contentAlignment = Alignment.Center
        ) {
            PixelDialogContent(
                title = "ANNOUNCEMENT",
                content = "Attention players!\n\nThe server will undergo maintenance tonight from 2:00 AM to 4:00 AM UTC. Please save your progress.\n\nThank you for your cooperation!",
                confirmText = "OK",
                secondaryText = null,
                onConfirm = {},
                onSecondary = null
            )
        }
    }
}

@Composable
fun PixelDialogButton(text: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val color = if (isPressed) Highlight else White

    Box(
        modifier = Modifier
            .border(5.dp, color, RectangleShape)
            .background(Black)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    com.au.launcher.utils.SoundHelper.playClick()
                    onClick()
                }
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.uppercase(),
            color = color,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FzxsFontFamily
        )
    }
}

@Composable
fun HomeScreenContent(
    games: List<GameModel>,
    isLoading: Boolean,
    isMoreLoading: Boolean,
    hasMore: Boolean,
    currentCategory: String,
    searchQuery: String,
    language: String,
    bgImageUri: String?,
    bgBlur: Float,
    bgOpacity: Float,
    maskColor: Color,
    downloadStates: Map<String, DownloadState>,
    onSearchQueryChange: (String) -> Unit,
    onCategorySelect: (String) -> Unit,
    onLoadMore: () -> Unit,
    isInstalled: (String) -> Boolean,
    onGameAction: (GameModel) -> Unit,
    onRemoveLocal: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onUploadClick: () -> Unit,
    onImportClick: () -> Unit
) {
    val tabs = listOf(
        "ALL" to stringResource(R.string.tab_all),
        "INSTALLED" to stringResource(R.string.tab_installed),
        "HOT" to stringResource(R.string.tab_hot),
        "NEW" to stringResource(R.string.tab_new)
    )
    val listState = rememberLazyListState()

    val shouldLoadMore = remember(games.size, hasMore, isMoreLoading, isLoading) {
        derivedStateOf {
            val lastVisibleItemIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleItemIndex >= games.size - 2 && hasMore && !isMoreLoading && !isLoading
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) {
            onLoadMore()
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
                    .background(maskColor.copy(alpha = 1f - bgOpacity))
            )
        }

        Scaffold(
            topBar = {
                TopSearchBar(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    onSettingsClick = onSettingsClick,
                    onUploadClick = onUploadClick,
                    onImportClick = onImportClick
                )
            },
            containerColor = Color.Transparent
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
            ) {
                // Category Tabs
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    items(tabs) { (id, label) ->
                        val isActive = currentCategory == id
                        val interactionSource = remember { MutableInteractionSource() }
                        val isPressed by interactionSource.collectIsPressedAsState()
                        val tabContentColor = if (isActive || isPressed) Highlight else White
                        
                        Box(
                            modifier = Modifier
                                .border(5.dp, tabContentColor, RectangleShape)
                                .background(Black.copy(alpha = 0.8f))
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null
                                ) { 
                                    com.au.launcher.utils.SoundHelper.playSwitch()
                                    onCategorySelect(id) 
                                }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = label,
                                color = tabContentColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FzxsFontFamily
                            )
                        }
                    }
                }

                // Game List
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    if (isLoading && games.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.loading),
                                    color = Highlight,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FzxsFontFamily
                                )
                            }
                        }
                    }

                    if (!isLoading && games.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.no_games_found),
                                    color = DimText,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FzxsFontFamily
                                )
                            }
                        }
                    }

                    items(games, key = { it.id }) { game ->
                        val installed = isInstalled(game.id)
                        val downloadState = downloadStates[game.id]
                        val isDownloading = downloadState?.isDownloading == true
                        val isPaused = downloadState?.isPaused == true
                        
                        val cardStatus = when {
                            installed -> GameStatus.INSTALLED
                            isDownloading || isPaused -> GameStatus.DOWNLOADING
                            else -> GameStatus.DOWNLOADABLE
                        }
                        
                        GameCard(
                            id = game.id,
                            name = game.name.get(language),
                            author = game.author.get(language),
                            engine = game.engine,
                            status = cardStatus,
                            coverUrl = game.localCoverUri,
                            downloadState = downloadState,
                            isLocal = game.isLocal,
                            onActionClick = {
                                onGameAction(game)
                            },
                            onRemoveClick = {
                                onRemoveLocal(game.id)
                            }
                        )
                    }

                    if (isMoreLoading) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.loading),
                                    color = Highlight,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FzxsFontFamily
                                )
                            }
                        }
                    } else if (hasMore) {
                        item {
                            Text(
                                text = stringResource(R.string.scroll_to_load_more),
                                color = DimText,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                fontFamily = FzxsFontFamily
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
fun HomeScreenPreview() {
    val mockGames = (1..16).map { i ->
        GameModel(
            id = "game_$i",
            name = LocalizedString("Game $i", "游戏 $i"),
            author = LocalizedString("Author $i", "作者 $i"),
            engine = "Engine",
            hotScore = 100,
            version = "1.0.0",
            publishTime = ""
        )
    }

    AULauncherTheme {
        HomeScreenContent(
            games = mockGames.take(8),
            isLoading = false,
            isMoreLoading = false,
            hasMore = true,
            currentCategory = "ALL",
            searchQuery = "",
            language = "zh",
            bgImageUri = null,
            bgBlur = 0f,
            bgOpacity = 0.5f,
            maskColor = Color.Black,
            downloadStates = emptyMap(),
            onSearchQueryChange = {},
            onCategorySelect = {},
            onLoadMore = {},
            isInstalled = { id -> id == "game_1" || id == "game_3" },
            onGameAction = {},
            onRemoveLocal = {},
            onSettingsClick = {},
            onUploadClick = {},
            onImportClick = {}
        )
    }
}
