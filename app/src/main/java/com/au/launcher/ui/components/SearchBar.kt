package com.au.launcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.au.launcher.R
import com.au.launcher.ui.theme.*

@Composable
fun TopSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onUploadClick: () -> Unit,
    onImportClick: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val menuInteractionSource = remember { MutableInteractionSource() }
    val isMenuPressed by menuInteractionSource.collectIsPressedAsState()
    val triggerColor = if (isMenuPressed || expanded) Highlight else White
    val focusManager = LocalFocusManager.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Search Box
        Box(
            modifier = Modifier
                .weight(1f)
                .border(5.dp, White, RectangleShape)
                .background(Black)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (query.isEmpty()) {
                Text(
                    text = stringResource(R.string.search_hint),
                    color = DimText,
                    fontSize = 15.sp,
                    fontFamily = FzxsFontFamily
                )
            }
            
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(
                    color = White,
                    fontSize = 15.sp,
                    fontFamily = FzxsFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                cursorBrush = SolidColor(Highlight),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    focusManager.clearFocus()
                })
            )
        }

        // Menu Trigger
        Box(
            modifier = Modifier
                .size(44.dp)
                .border(5.dp, triggerColor, RectangleShape)
                .background(Black)
                .clickable(
                    interactionSource = menuInteractionSource,
                    indication = null
                ) { expanded = true },
            contentAlignment = Alignment.Center
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .background(triggerColor)
                    )
                }
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .background(PanelBg)
                    .border(5.dp, White, RectangleShape)
                    .width(160.dp)
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_settings), color = White, fontFamily = FzxsFontFamily) },
                    onClick = {
                        expanded = false
                        onSettingsClick()
                    }
                )
                HorizontalDivider(color = Color(0xFF333333))
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_upload), color = White, fontFamily = FzxsFontFamily) },
                    onClick = {
                        expanded = false
                        onUploadClick()
                    }
                )
                HorizontalDivider(color = Color(0xFF333333))
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_import), color = White, fontFamily = FzxsFontFamily) },
                    onClick = {
                        expanded = false
                        onImportClick()
                    }
                )
            }
        }
    }
}
