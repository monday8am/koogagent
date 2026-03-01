package com.monday8am.edgelab.copilot.ui.screens.liveride

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.monday8am.edgelab.copilot.Dependencies
import com.monday8am.edgelab.presentation.liveride.LiveRideAction
import com.monday8am.edgelab.presentation.liveride.LiveRideUiState

@Composable
fun LiveRideScreen(
    routeId: String,
    playbackSpeed: Float,
    onNavigateBack: () -> Unit,
    viewModel: AndroidLiveRideViewModel = viewModel {
        AndroidLiveRideViewModel(
            routeId = routeId,
            routeRepository = Dependencies.routeRepository,
        )
    },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LiveRideScreenContent(
        uiState = uiState,
        onAction = viewModel::onUiAction,
        onNavigateBack = onNavigateBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LiveRideScreenContent(
    uiState: LiveRideUiState,
    onAction: (LiveRideAction) -> Unit,
    onNavigateBack: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Map — full screen base layer
        MapLibreMapView(
            routePolyline = uiState.routePolyline,
            completedPolyline = uiState.completedPolyline,
            riderPosition = uiState.currentPosition,
            pois = uiState.pois,
            modifier = Modifier.fillMaxSize(),
        )

        // Top app bar overlay (semi-transparent)
        CenterAlignedTopAppBar(
            title = {
                Text(uiState.routeName.ifEmpty { "Loading…" })
            },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                    )
                }
            },
            colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                ),
            modifier = Modifier.align(Alignment.TopStart),
        )

        if (uiState.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else {
            // Playback controls — top right
            PlaybackControls(
                uiState = uiState,
                onAction = onAction,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 72.dp, end = 12.dp),
            )

            // Bottom panel: HUD + Chat
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            ) {
                HudStrip(metrics = uiState.hudMetrics)
                ChatPanel(
                    chatMessages = uiState.chatMessages,
                    isChatExpanded = uiState.isChatExpanded,
                    isProcessing = uiState.isProcessing,
                    onAction = onAction,
                )
            }
        }
    }
}

@Composable
private fun PlaybackControls(
    uiState: LiveRideUiState,
    onAction: (LiveRideAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.width(96.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.92f)
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Play/pause toggle
            IconButton(onClick = { onAction(LiveRideAction.TogglePlayback) }) {
                Icon(
                    imageVector =
                        if (uiState.playbackState.isPlaying) Icons.Default.Pause
                        else Icons.Default.PlayArrow,
                    contentDescription = if (uiState.playbackState.isPlaying) "Pause" else "Play",
                )
            }
            // Speed multiplier — tap to cycle
            TextButton(
                onClick = { onAction(LiveRideAction.CycleSpeed) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "${uiState.playbackState.speedMultiplier.toInt()}x",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            // Current km
            Text(
                text = "%.1f km".format(uiState.playbackState.currentKm),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
    }
}
