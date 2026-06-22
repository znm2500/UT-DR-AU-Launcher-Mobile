package com.au.launcher.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.au.launcher.R
import com.au.launcher.ui.theme.*
import com.au.launcher.utils.Constants
import com.au.launcher.utils.DownloadState

enum class GameStatus {
    INSTALLED, DOWNLOADABLE, DOWNLOADING
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GameCard(
    id: String,
    name: String,
    author: String,
    engine: String,
    status: GameStatus,
    coverUrl: String? = null,          // 保留外部可覆盖的 URL
    downloadState: DownloadState? = null,
    isLocal: Boolean = false,           // 新增 isLocal
    onActionClick: () -> Unit,
    onRemoveClick: (() -> Unit)? = null, // 新增 onRemoveClick
    modifier: Modifier = Modifier       // 新增 modifier 参数
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val contentColor = if (isPressed) Highlight else White

    // 定义备用 URL 列表（按优先级）
    val fallbackUrls = remember(id, coverUrl, Constants.isChinaRegion) {
        val list = mutableListOf<String>()
        if (!coverUrl.isNullOrEmpty()) {
            list.add(coverUrl)
        }
        
        if (Constants.isChinaRegion) {
            // 中国区：优先使用 API 接口（带 Token，最稳定），其次是直链，最后是全球 CDN
            val token = com.au.launcher.BuildConfig.GITCODE_TOKEN
            list.add("${Constants.IMAGE_BASE_URL_CN_API}${id}.webp?ref=data&access_token=$token")
            list.add("${Constants.IMAGE_BASE_URL_CN}${id}.webp")
            list.add("${Constants.IMAGE_BASE_URL_GLOBAL}${id}.webp")
        } else {
            // 全球区：优先使用 jsdelivr，其次是 GitCode
            list.add("${Constants.IMAGE_BASE_URL_GLOBAL}${id}.webp")
            list.add("${Constants.IMAGE_BASE_URL_CN}${id}.webp")
        }
        list
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 18.dp)
            .border(5.dp, White)
            .background(CardBg)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
                onLongClick = if (isLocal) { {
                    com.au.launcher.utils.SoundHelper.playConfirm()
                    onRemoveClick?.invoke()
                } } else null
            )
    ) {
        // Cover 区域：带 fallback 的图片
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
        ) {
            FallbackImage(
                urls = fallbackUrls,
                defaultResId = R.drawable.ic_default_cover, // 你需要准备一个默认封面图
                modifier = Modifier.fillMaxSize(),
                contentDescription = name,
                contentScale = ContentScale.Crop
            )
        }

        HorizontalDivider(thickness = 5.dp, color = White)

        // Info Container
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    color = White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FzxsFontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "by $author [$engine]",
                    color = DimText,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontFamily = FzxsFontFamily
                )
            }

            val buttonText = when {
                status == GameStatus.INSTALLED -> stringResource(R.string.play)
                status == GameStatus.DOWNLOADING -> {
                    if (downloadState?.isPaused == true) stringResource(R.string.resume)
                    else "${downloadState?.progress ?: 0}%"
                }
                else -> stringResource(R.string.get)
            }

            // 按钮区域
            Box(
                modifier = Modifier
                    .width(80.dp) // Fixed width for the progress button
                    .height(36.dp) // Fixed height
                    .border(5.dp, contentColor)
                    .background(Black)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null // 保持无涟漪效果（原样）
                    ) { 
                        com.au.launcher.utils.SoundHelper.playClick()
                        onActionClick() 
                    },
                contentAlignment = Alignment.Center
            ) {
                // Progress background
                if (status == GameStatus.DOWNLOADING && downloadState != null) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .padding(2.dp) // Leave a small gap for the border
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction = (downloadState.progress / 100f).coerceIn(0f, 1f))
                                .background(Highlight.copy(alpha = 0.5f))
                        )
                    }
                }

                Text(
                    text = buttonText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FzxsFontFamily,
                    color = contentColor,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

/**
 * 支持多 URL 自动回退的图片组件
 * @param urls 图片地址列表（按优先级排序）
 * @param defaultResId 所有地址都失败时显示的本地资源 ID
 */
@Composable
private fun FallbackImage(
    urls: List<String>,
    defaultResId: Int,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val context = LocalContext.current
    // 使用 rememberSaveable 保持索引，防止列表滚动时重置
    var currentIndex by remember(urls) { mutableIntStateOf(0) }
    val currentUrl = urls.getOrNull(currentIndex)

    if (currentUrl == null) {
        androidx.compose.foundation.Image(
            painter = painterResource(defaultResId),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale
        )
    } else {
        val cacheKey = remember(currentUrl) { currentUrl.substringBefore("?") }
        val model = remember(currentUrl) {
            ImageRequest.Builder(context)
                .data(currentUrl)
                .diskCacheKey(cacheKey)
                .memoryCacheKey(cacheKey)
                .placeholderMemoryCacheKey(cacheKey) // 关键：如果内存有，直接用作占位
                .allowHardware(false) // 某些设备上硬件位图会导致闪烁
                .listener(
                    onError = { _, _ ->
                        if (currentIndex < urls.size - 1) {
                            currentIndex++
                        }
                    }
                )
                .build()
        }

        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
            // 只有在内存/磁盘都没有时才降级到默认图
            placeholder = painterResource(R.drawable.ic_default_cover),
            error = painterResource(defaultResId),
            filterQuality = androidx.compose.ui.graphics.FilterQuality.Low // 提升滚动性能
        )
    }
}