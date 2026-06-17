package com.au.launcher.ui.screens

import android.app.Application
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.au.launcher.R
import com.au.launcher.api.GameRepository
import com.au.launcher.db.GameEntity
import com.au.launcher.ui.theme.*
import com.au.launcher.utils.Constants
import com.au.launcher.utils.PackageUtils
import com.au.launcher.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch
import java.util.UUID

class ImportViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = GameRepository(application)
    
    var name by mutableStateOf("")
    var author by mutableStateOf("")
    var engine by mutableStateOf("")
    var packageName by mutableStateOf("")
    var imgUri by mutableStateOf<Uri?>(null)
    
    var showSuccessDialog by mutableStateOf(false)
    var errorMessage by mutableStateOf("")

    fun importGame(
        errorSelectApp: String,
        errorFillName: String
    ) {
        if (packageName.isEmpty()) {
            errorMessage = errorSelectApp
            return
        }
        if (name.trim().isEmpty()) {
            errorMessage = errorFillName
            return
        }
        
        errorMessage = ""
        viewModelScope.launch {
            val entity = GameEntity(
                id = "local_${UUID.randomUUID()}",
                name = name,
                author = author.ifBlank { "Unknown" },
                engine = engine.ifBlank { "Unknown" },
                packageName = packageName,
                coverUri = imgUri?.toString() ?: Constants.DEFAULT_COVER
            )
            repository.addLocalGame(entity)
            showSuccessDialog = true
            
            // Reset
            name = ""
            author = ""
            engine = ""
            packageName = ""
            imgUri = null
        }
    }
}

@Composable
fun ImportScreen(
    onBack: () -> Unit,
    viewModel: ImportViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val imgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { viewModel.imgUri = it }
    
    var showAppSelector by remember { mutableStateOf(false) }

    val bgImageUri by settingsViewModel.bgImageUri.collectAsState()
    val bgBlur by settingsViewModel.bgBlur.collectAsState()
    val bgOpacity by settingsViewModel.bgOpacity.collectAsState()
    val maskColorInt by settingsViewModel.maskColor.collectAsState()

    val errorSelectApp = stringResource(R.string.import_error_select_app)
    val errorFillName = stringResource(R.string.import_error_fill_name)

    if (showAppSelector) {
        val apps = remember { PackageUtils.getInstalledApps(context) }
        AlertDialog(
            onDismissRequest = { showAppSelector = false },
            confirmButton = {},
            title = { Text(stringResource(R.string.import_select_app), color = White, fontFamily = FzxsFontFamily) },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(apps) { app ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { 
                                    viewModel.packageName = app.packageName
                                    if (viewModel.name.isEmpty()) viewModel.name = app.name
                                    showAppSelector = false
                                }
                                .padding(vertical = 12.dp)
                        ) {
                            Text(text = app.name, color = White, fontFamily = FzxsFontFamily, fontSize = 14.sp)
                            Text(text = app.packageName, color = DimText, fontFamily = FzxsFontFamily, fontSize = 10.sp)
                        }
                    }
                }
            },
            containerColor = Color(0xFF1A1A1A),
            shape = RectangleShape,
            modifier = Modifier.border(5.dp, White, RectangleShape)
        )
    }

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
                            onBack()
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
            title = { Text(stringResource(R.string.import_success), color = White, fontFamily = FzxsFontFamily) },
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
                modifier = Modifier.fillMaxSize().blur(bgBlur.dp),
                contentScale = ContentScale.Crop
            )
            Box(modifier = Modifier.fillMaxSize().background(Color(maskColorInt).copy(alpha = 1f - bgOpacity)))
        }

        Scaffold(
            topBar = {
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier.size(44.dp).border(5.dp, White, RectangleShape).background(Black.copy(alpha = 0.7f)).clickable { onBack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = White)
                    }
                    Box(
                        modifier = Modifier.weight(1f).border(5.dp, White, RectangleShape).background(Black.copy(alpha = 0.7f)).padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(text = stringResource(R.string.import_title), color = White, fontSize = 15.sp, fontFamily = FzxsFontFamily, fontWeight = FontWeight.Bold)
                    }
                }
            },
            containerColor = Color.Transparent
        ) { innerPadding ->
            Column(
                modifier = Modifier.padding(innerPadding).fillMaxSize().padding(12.dp).verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                UploadPicker(
                    label = stringResource(R.string.upload_cover),
                    selectedUri = viewModel.imgUri,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { imgLauncher.launch("image/*") }
                )

                // App Selector Button (Mandatory)
                val appSelectorInteractionSource = remember { MutableInteractionSource() }
                val isAppSelectorPressed by appSelectorInteractionSource.collectIsPressedAsState()
                val selectorColor = if (isAppSelectorPressed) Highlight else White

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(5.dp, selectorColor, RectangleShape)
                        .background(Black.copy(alpha = 0.7f))
                        .clickable(
                            interactionSource = appSelectorInteractionSource,
                            indication = null
                        ) { showAppSelector = true }
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if(viewModel.packageName.isEmpty()) stringResource(R.string.import_select_app) else viewModel.packageName,
                        color = selectorColor,
                        fontSize = 14.sp,
                        fontFamily = FzxsFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                }

                PixelField(label = stringResource(R.string.upload_name), value = viewModel.name, onValueChange = { viewModel.name = it })
                PixelField(label = stringResource(R.string.import_author), value = viewModel.author, onValueChange = { viewModel.author = it })
                PixelField(label = stringResource(R.string.import_engine), value = viewModel.engine, onValueChange = { viewModel.engine = it })

                if (viewModel.errorMessage.isNotEmpty()) {
                    Text(text = viewModel.errorMessage, color = Color.Red, fontSize = 14.sp, fontFamily = FzxsFontFamily)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(5.dp, White, RectangleShape)
                        .background(Black.copy(alpha = 0.7f))
                        .clickable { viewModel.importGame(errorSelectApp, errorFillName) }
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = stringResource(R.string.import_confirm), color = White, fontSize = 16.sp, fontFamily = FzxsFontFamily, fontWeight = FontWeight.Bold)
                }
                
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}
