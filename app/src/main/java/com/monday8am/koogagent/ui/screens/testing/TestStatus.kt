package com.monday8am.koogagent.ui.screens.testing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.monday8am.koogagent.data.testing.TestDomain
import com.monday8am.koogagent.ui.theme.KoogAgentTheme
import com.monday8am.presentation.testing.TestStatus
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@Composable
internal fun TestStatusList(
    testStatuses: ImmutableList<TestStatus>,
    filterDomain: TestDomain?,
    availableDomains: ImmutableList<TestDomain>,
    onSetDomainFilter: (TestDomain?) -> Unit,
    onNavigateToTestDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    val runningIndex = remember(testStatuses) {
        testStatuses.indexOfFirst { it.state == TestStatus.State.RUNNING }
    }

    LaunchedEffect(runningIndex) {
        if (runningIndex >= 0) {
            var runningItemInfo = listState.layoutInfo.visibleItemsInfo.find { it.index == runningIndex }

            if (runningItemInfo == null) {
                listState.scrollToItem(runningIndex)
                runningItemInfo = listState.layoutInfo.visibleItemsInfo.find { it.index == runningIndex }
            }

            if (runningItemInfo != null) {
                val viewportWidth = listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset
                val centerOffset = (viewportWidth / 2) - (runningItemInfo.size / 2)
                val scrollOffset = centerOffset.coerceAtLeast(0)
                listState.animateScrollToItem(runningIndex, scrollOffset)
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = spacedBy(8.dp)) {
        if (availableDomains.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LazyRow(
                    horizontalArrangement = spacedBy(8.dp),
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    item {
                        FilterChip(
                            selected = filterDomain == null,
                            onClick = { onSetDomainFilter(null) },
                            label = { Text("All") },
                        )
                    }
                    items(availableDomains) { domain ->
                        FilterChip(
                            selected = filterDomain == domain,
                            onClick = { onSetDomainFilter(domain) },
                            label = {
                                Text(domain.name.lowercase().replaceFirstChar { it.uppercase() })
                            },
                        )
                    }
                }

                IconButton(onClick = onNavigateToTestDetails) {
                    Icon(
                        imageVector = Icons.Default.List,
                        contentDescription = "View All Tests",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        LazyRow(state = listState, modifier = Modifier.fillMaxWidth(), horizontalArrangement = spacedBy(8.dp)) {
            items(testStatuses) { status -> TestStatusCard(status = status) }
        }
    }
}

@Composable
internal fun TestStatusCard(status: TestStatus) {
    // Different colors for different domains
    val containerColor =
        when (status.domain) {
            TestDomain.GENERIC -> MaterialTheme.colorScheme.surfaceVariant
            TestDomain.YAZIO -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        }

    Card(
        modifier = Modifier.width(160.dp).height(120.dp).padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = spacedBy(8.dp)) {
            // Header row with domain icon and test name
            Row(
                modifier = Modifier.fillMaxWidth().weight(1.0f),
                horizontalArrangement = spacedBy(4.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    imageVector =
                        when (status.domain) {
                            TestDomain.GENERIC -> Icons.Default.Memory
                            TestDomain.YAZIO -> Icons.Filled.Psychology
                        },
                    contentDescription = status.domain.name,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = status.name,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
            }

            // State indicator and speed
            Row(
                horizontalArrangement = spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.height(24.dp),
            ) {
                // State indicator
                when (status.state) {
                    TestStatus.State.IDLE ->
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Idle",
                            tint = MaterialTheme.colorScheme.outline,
                        )

                    TestStatus.State.RUNNING ->
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)

                    TestStatus.State.PASS ->
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Pass",
                            tint = MaterialTheme.colorScheme.primary,
                        )

                    TestStatus.State.FAIL ->
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Fail",
                            tint = MaterialTheme.colorScheme.error,
                        )
                }

                // Token speed display
                val currentSpeed = status.currentTokensPerSecond
                val avgSpeed = status.averageTokensPerSecond
                when {
                    status.state == TestStatus.State.RUNNING && currentSpeed != null -> {
                        Text(
                            text = "${currentSpeed.toInt()} tok/s",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    (status.state == TestStatus.State.PASS || status.state == TestStatus.State.FAIL) &&
                        avgSpeed != null -> {
                        Text(
                            text = "⌀ ${avgSpeed.toInt()} tok/s",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun TestStatusListPreview() {
    KoogAgentTheme {
        TestStatusList(
            testStatuses =
                listOf(
                    TestStatus(
                        name = "Test 1",
                        domain = TestDomain.GENERIC,
                        state = TestStatus.State.PASS,
                    ),
                    TestStatus(
                        name = "Test 2",
                        domain = TestDomain.GENERIC,
                        state = TestStatus.State.RUNNING,
                    ),
                    TestStatus(
                        name = "Test 3",
                        domain = TestDomain.GENERIC,
                        state = TestStatus.State.IDLE,
                    ),
                )
                    .toImmutableList(),
            filterDomain = null,
            availableDomains = persistentListOf(TestDomain.GENERIC, TestDomain.YAZIO),
            onSetDomainFilter = {},
            onNavigateToTestDetails = {},
        )
    }
}

@Preview
@Composable
private fun TestStatusCardPreview() {
    KoogAgentTheme {
        TestStatusCard(
            status =
                TestStatus(
                    name = "Test Name",
                    domain = TestDomain.GENERIC,
                    state = TestStatus.State.PASS,
                )
        )
    }
}
