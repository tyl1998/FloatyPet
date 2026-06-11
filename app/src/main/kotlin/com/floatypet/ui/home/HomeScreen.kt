package com.floatypet.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.floatypet.ui.theme.Ink
import com.floatypet.ui.theme.Ink2
import com.floatypet.ui.theme.Mint
import com.floatypet.ui.theme.Primary

@Composable
fun HomeRoute(
    onAdoptPet: () -> Unit,
    onToggleOverlay: () -> Unit,
    onGoAiConfig: () -> Unit,
    onManagePet: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(
        uiState = uiState,
        onAdoptPet = onAdoptPet,
        onToggleOverlay = onToggleOverlay,
        onGoAiConfig = onGoAiConfig,
        onManagePet = onManagePet,
        onSwitchPet = viewModel::switchPet,
        onDeletePet = viewModel::deletePet,
        modifier = modifier,
    )
}

@Composable
internal fun HomeScreen(
    uiState: HomeUiState,
    onAdoptPet: () -> Unit,
    onToggleOverlay: () -> Unit,
    onGoAiConfig: () -> Unit,
    onManagePet: () -> Unit,
    onSwitchPet: (String) -> Unit,
    onDeletePet: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        when (uiState) {
            HomeUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
            HomeUiState.Empty -> EmptyContent(onAdoptPet, onGoAiConfig)
            is HomeUiState.Ready -> ReadyContent(
                state = uiState,
                onToggleOverlay = onToggleOverlay,
                onAdoptPet = onAdoptPet,
                onManagePet = onManagePet,
                onSwitchPet = onSwitchPet,
                onDeletePet = onDeletePet,
                onGoAiConfig = onGoAiConfig,
            )
        }
    }
}

@Composable
private fun EmptyContent(onAdoptPet: () -> Unit, onGoAiConfig: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color(0xFFFFE0CC), Color(0xFFFFF1EA)))),
            contentAlignment = Alignment.Center,
        ) {
            Text("🐾", fontSize = 52.sp)
        }
        Spacer(Modifier.height(24.dp))
        Text("还没有桌宠", style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(8.dp))
        Text(
            "导入一张照片，让 AI 帮你生成\n独一无二的桌面小伙伴",
            style = MaterialTheme.typography.bodyMedium, color = Ink2,
            textAlign = TextAlign.Center, lineHeight = 22.sp,
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onAdoptPet,
            shape = RoundedCornerShape(30.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text("选一张照片，领养它", fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onGoAiConfig) { Text("配置 AI 服务", color = Ink2) }
    }
}

@Composable
private fun ReadyContent(
    state: HomeUiState.Ready,
    onToggleOverlay: () -> Unit,
    onAdoptPet: () -> Unit,
    onManagePet: () -> Unit,
    onSwitchPet: (String) -> Unit,
    onDeletePet: (String) -> Unit,
    onGoAiConfig: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        // 顶栏
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("浮宠", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold, color = Ink, modifier = Modifier.weight(1f))
            IconButton(onClick = onGoAiConfig) {
                Icon(Icons.Default.Settings, contentDescription = "AI 配置", tint = Ink2)
            }
        }

        // 宠物主卡片
        PetHeroCard(state)

        Spacer(Modifier.height(12.dp))

        // 多宠切换行（有多只宠物时才显示）
        if (state.allPets.size >= 2) {
            PetSwitcherRow(
                pets = state.allPets,
                currentPetId = state.currentPetId,
                onSwitch = onSwitchPet,
                onDelete = onDeletePet,
            )
            Spacer(Modifier.height(8.dp))
        }

        // 桌面按钮
        Button(
            onClick = onToggleOverlay,
            shape = RoundedCornerShape(30.dp),
            colors = if (state.overlayRunning) ButtonDefaults.buttonColors(containerColor = Mint)
                     else ButtonDefaults.buttonColors(containerColor = Primary),
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) {
            Text(
                if (state.overlayRunning) "宠物在桌面上 · 点击收回" else "放到桌面 🐾",
                fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
            )
        }

        Spacer(Modifier.height(8.dp))

        TextButton(onClick = onManagePet, modifier = Modifier.fillMaxWidth()) {
            Text("管理宠物 · 续生成动作", color = Primary)
        }
        TextButton(onClick = onAdoptPet, modifier = Modifier.fillMaxWidth()) {
            Text("添加新宠物", color = Ink2)
        }
    }
}

@Composable
private fun PetSwitcherRow(
    pets: List<PetSummary>,
    currentPetId: String,
    onSwitch: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var deleteTargetId by remember { mutableStateOf<String?>(null) }

    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(pets, key = { it.id }) { pet ->
            val isCurrent = pet.id == currentPetId
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(62.dp),
            ) {
                Box {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(if (isCurrent) Color(0xFFFFE0CC) else Color(0xFFF0F0F0))
                            .then(if (!isCurrent) Modifier.clickable { onSwitch(pet.id) } else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (pet.thumbnail != null) {
                            Image(
                                bitmap = pet.thumbnail.asImageBitmap(),
                                contentDescription = pet.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Text("🐱", fontSize = 22.sp)
                        }
                    }
                    // 删除按钮（非当前宠物才显示）
                    if (!isCurrent) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .align(Alignment.TopEnd)
                                .clip(CircleShape)
                                .background(Color(0xFFCCCCCC))
                                .clickable { deleteTargetId = pet.id },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.Close, null, tint = Color.White,
                                modifier = Modifier.size(12.dp))
                        }
                    }
                    // 当前宠物指示点
                    if (isCurrent) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .align(Alignment.BottomEnd)
                                .clip(CircleShape)
                                .background(Primary),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(pet.name, style = MaterialTheme.typography.labelSmall,
                    color = if (isCurrent) Primary else Ink2,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }

    // 删除确认弹窗
    deleteTargetId?.let { targetId ->
        val target = pets.find { it.id == targetId }
        AlertDialog(
            onDismissRequest = { deleteTargetId = null },
            title = { Text("删除宠物") },
            text = { Text("确认删除「${target?.name ?: ""}」？所有帧素材将被永久删除。") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(targetId)
                    deleteTargetId = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTargetId = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun PetHeroCard(state: HomeUiState.Ready) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(300.dp),
        shape = RoundedCornerShape(28.dp),
        color = Color(0xFFFFF1EA),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .align(Alignment.TopEnd)
                    .background(Brush.radialGradient(listOf(Color(0x22FF8A65), Color.Transparent)))
            )
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val thumb = state.thumbnail
                Box(
                    modifier = Modifier.size(140.dp).clip(CircleShape).background(Color(0xFFFFE0CC)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (thumb != null) {
                        Image(bitmap = thumb.asImageBitmap(), contentDescription = state.petName,
                            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Text("🐱", fontSize = 60.sp)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(state.petName, style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold, color = Ink)
                Spacer(Modifier.height(6.dp))
                Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFFFFE0CC)) {
                    Text(state.statusText, style = MaterialTheme.typography.labelMedium,
                        color = Primary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                }
            }
        }
    }
}
