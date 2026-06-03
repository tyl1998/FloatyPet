package com.floatypet.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun HomeRoute(
    onToggleOverlay: () -> Unit,
    onAdoptPet: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(
        uiState = uiState,
        onToggleOverlay = onToggleOverlay,
        onAdoptPet = onAdoptPet,
        modifier = modifier,
    )
}

@Composable
internal fun HomeScreen(
    uiState: HomeUiState,
    onToggleOverlay: () -> Unit,
    onAdoptPet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (uiState) {
            HomeUiState.Loading -> CircularProgressIndicator()
            HomeUiState.Empty -> EmptyContent(onAdoptPet)
            is HomeUiState.Ready -> ReadyContent(uiState, onToggleOverlay)
        }
    }
}

@Composable
private fun EmptyContent(onAdoptPet: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(32.dp),
    ) {
        Text("🐱", style = MaterialTheme.typography.headlineLarge)
        Text(
            "领养你的第一只桌宠",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            "导入一张照片，或用你的 AI 服务生成一只独一无二的桌面伙伴",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        Button(
            onClick = onAdoptPet,
            shape = RoundedCornerShape(30.dp),
            modifier = Modifier.padding(top = 24.dp),
        ) { Text("开始领养") }
    }
}

@Composable
private fun ReadyContent(state: HomeUiState.Ready, onToggleOverlay: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("我的桌宠", style = MaterialTheme.typography.titleLarge)
        Card(
            modifier = Modifier.fillMaxWidth().height(220.dp),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("🐱", style = MaterialTheme.typography.headlineLarge)
                Text(
                    state.petName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    state.statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Button(
            onClick = onToggleOverlay,
            shape = RoundedCornerShape(30.dp),
            colors = if (state.overlayRunning) {
                ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            } else {
                ButtonDefaults.buttonColors()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.overlayRunning) "宠物已在桌面 · 点击收起" else "把宠物放到桌面")
        }
    }
}
