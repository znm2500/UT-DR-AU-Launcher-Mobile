package com.au.launcher.ui.screens

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.au.launcher.BuildConfig
import com.au.launcher.R
import com.au.launcher.api.*
import com.au.launcher.ui.theme.*
import com.au.launcher.viewmodel.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class UploadViewModel : ViewModel() {
    var name by mutableStateOf("")
    var downloadUrl by mutableStateOf("")
    var description by mutableStateOf("")
    var imgUri by mutableStateOf<Uri?>(null)

    var isUploading by mutableStateOf(false)
    var uploadStatus by mutableStateOf("")
    var isError by mutableStateOf(false)
    var showSuccessDialog by mutableStateOf(false)

    var cooldownSeconds by mutableIntStateOf(0)

    fun upload(
        context: Context,
        statusFillFields: String,
        statusUploading: String,
        statusError: String,
        statusCooldown: String,
        descHint: String
    ) {
        if (cooldownSeconds > 0) {
            uploadStatus = statusCooldown.format(cooldownSeconds)
            isError = true
            return
        }

        if (name.isEmpty() || downloadUrl.isEmpty()) {
            uploadStatus = statusFillFields
            isError = true
            return
        }

        viewModelScope.launch {
            isUploading = true
            isError = false
            uploadStatus = statusUploading
            try {
                // 1. Image Upload (Same as before)
                imgUri?.let { uri ->
                    val imageData = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    }
                    if (imageData != null) {
                        val base64 = Base64.encodeToString(imageData, Base64.NO_WRAP)
                        val md5 = MessageDigest.getInstance("MD5")
                            .digest(imageData)
                            .joinToString("") { "%02x".format(it) }

                        RetrofitClient.webhookApi.sendMessage(
                            key = BuildConfig.WEBHOOK_KEY,
                            message = WebhookMessage(
                                msgtype = "image",
                                image = ImageContent(base64 = base64, md5 = md5)
                            )
                        )
                    }
                }

                // 2. Information Markdown Message (Updated Template)
                val submitTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                val finalDesc = description.trim().ifEmpty { descHint }
                
                val markdownContent = """
                    ### 收到新的游戏申请（手机版）
                    > **游戏名称**：<font color="info">$name</font>
                    > **下载链接**：[点击查看]($downloadUrl)
                    > **补充说明**：$finalDesc
                    > **提交时间**：$submitTime
                """.trimIndent()

                RetrofitClient.webhookApi.sendMessage(
                    key = BuildConfig.WEBHOOK_KEY,
                    message = WebhookMessage(
                        msgtype = "markdown",
                        markdown = MarkdownContent(markdownContent)
                    )
                )
                
                uploadStatus = ""
                showSuccessDialog = true
                
                // Reset form
                name = ""
                downloadUrl = ""
                description = ""
                imgUri = null

                startCooldown()
            } catch (e: Exception) {
                uploadStatus = statusError.replace("%s", e.message ?: "Unknown Error")
                isError = true
            } finally {
                isUploading = false
            }
        }
    }

    private fun startCooldown() {
        viewModelScope.launch {
            cooldownSeconds = 60
            while (cooldownSeconds > 0) {
                delay(1000)
                cooldownSeconds--
            }
        }
    }
}

@Composable
fun UploadScreen(
    onBack: () -> Unit,
    viewModel: UploadViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val imgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { viewModel.imgUri = it }

    val bgImageUri by settingsViewModel.bgImageUri.collectAsState()
    val bgBlur by settingsViewModel.bgBlur.collectAsState()
    val bgOpacity by settingsViewModel.bgOpacity.collectAsState()
    val maskColorInt by settingsViewModel.maskColor.collectAsState()

    val statusFillFields = stringResource(R.string.upload_status_fill_fields)
    val statusUploading = stringResource(R.string.upload_status_uploading)
    val statusError = stringResource(R.string.upload_status_error)
    val statusCooldown = stringResource(R.string.upload_status_cooldown)
    val descHint = stringResource(R.string.upload_desc_hint)

    if (viewModel.showSuccessDialog) {
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val buttonColor = if (isPressed) Highlight else White

        AlertDialog(
            onDismissRequest = { viewModel.showSuccessDialog = false },
            confirmButton = {
                Box(
                    modifier = Modifier
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null
                        ) {
                            viewModel.showSuccessDialog = false
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.ok),
                        color = buttonColor,
                        fontFamily = FzxsFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            title = {
                Text(
                    text = stringResource(R.string.upload_success_title),
                    color = White,
                    fontFamily = FzxsFontFamily,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.upload_success_message),
                    color = White,
                    fontFamily = FzxsFontFamily
                )
            },
            containerColor = Color(0xFF1A1A1A),
            shape = RectangleShape,
            modifier = Modifier.border(5.dp, White, RectangleShape)
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(Black)) {
        if (bgImageUri != null) {
            AsyncImage(
                model = bgImageUri,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(bgBlur.dp),
                contentScale = ContentScale.Crop
            )
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
                            .background(Black.copy(alpha = 0.7f))
                            .clickable { onBack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = White)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .border(5.dp, White, RectangleShape)
                            .background(Black.copy(alpha = 0.7f))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.upload_title),
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
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                UploadPicker(
                    label = stringResource(R.string.upload_cover),
                    selectedUri = viewModel.imgUri,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { imgLauncher.launch("image/*") }
                )

                PixelField(
                    label = stringResource(R.string.upload_name),
                    value = viewModel.name,
                    onValueChange = { viewModel.name = it }
                )
                PixelField(
                    label = stringResource(R.string.upload_download_url),
                    value = viewModel.downloadUrl,
                    onValueChange = { viewModel.downloadUrl = it }
                )
                PixelField(
                    label = stringResource(R.string.upload_desc),
                    value = viewModel.description,
                    onValueChange = { viewModel.description = it },
                    singleLine = false,
                    modifier = Modifier.heightIn(min = 100.dp)
                )

                if (viewModel.uploadStatus.isNotEmpty()) {
                    Text(
                        text = viewModel.uploadStatus,
                        color = if (viewModel.isError) Color.Red else Highlight,
                        fontSize = 14.sp,
                        fontFamily = FzxsFontFamily
                    )
                }

                val isButtonEnabled = !viewModel.isUploading && viewModel.cooldownSeconds == 0
                val buttonText = if (viewModel.isUploading) {
                    statusUploading
                } else if (viewModel.cooldownSeconds > 0) {
                    statusCooldown.format(viewModel.cooldownSeconds)
                } else {
                    stringResource(R.string.upload_confirm)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(5.dp, if (!isButtonEnabled) DimText else White, RectangleShape)
                        .background(if (!isButtonEnabled) Color(0xFF1A1A1A) else Black.copy(alpha = 0.7f))
                        .clickable(enabled = isButtonEnabled) { 
                            viewModel.upload(context, statusFillFields, statusUploading, statusError, statusCooldown, descHint)
                        }
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = buttonText,
                        color = if (!isButtonEnabled) DimText else White,
                        fontSize = 16.sp,
                        fontFamily = FzxsFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

@Composable
fun UploadPicker(label: String, selectedUri: Uri?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(200.dp)
            .border(5.dp, if (selectedUri != null) Highlight else White, RectangleShape)
            .background(Black.copy(alpha = 0.7f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (selectedUri != null) {
            AsyncImage(
                model = selectedUri,
                contentDescription = "Preview",
                modifier = Modifier.fillMaxSize().padding(5.dp),
                contentScale = ContentScale.Fit
            )
        } else {
            Text(
                text = label,
                color = White,
                fontSize = 14.sp,
                fontFamily = FzxsFontFamily,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun PixelField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    singleLine: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = label, color = White, fontSize = 12.sp, fontFamily = FzxsFontFamily, fontWeight = FontWeight.Bold)
        Box(
            modifier = modifier
                .fillMaxWidth()
                .border(5.dp, White, RectangleShape)
                .background(Black.copy(alpha = 0.7f))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(color = White, fontSize = 14.sp, fontFamily = FzxsFontFamily),
                cursorBrush = SolidColor(White),
                singleLine = singleLine
            )
        }
    }
}
