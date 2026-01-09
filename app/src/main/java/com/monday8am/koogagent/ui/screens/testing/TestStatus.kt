package com.monday8am.koogagent.ui.screens.testing

import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.monday8am.koogagent.ui.theme.KoogAgentTheme
import com.monday8am.presentation.testing.TestStatus
import com.monday8am.presentation.testing.ToolCallingTest.Companion.REGRESSION_TEST_SUITE
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

@Composable
internal fun TestStatusList(
    testStatuses: ImmutableList<TestStatus>,
    modifier: Modifier = Modifier,
) {
    LazyRow(modifier = modifier.fillMaxWidth(), horizontalArrangement = spacedBy(8.dp)) {
        items(testStatuses) { status -> TestStatusCard(status = status) }
    }
}

@Composable
internal fun TestStatusCard(status: TestStatus) {
    Card(modifier = Modifier.width(160.dp).height(100.dp).padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = spacedBy(8.dp)) {
            Text(text = status.name, style = MaterialTheme.typography.bodySmall)

            when (status.state) {
                TestStatus.State.IDLE ->
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Idle",
                        tint = MaterialTheme.colorScheme.outline,
                    )

                TestStatus.State.RUNNING ->
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)

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
        }
    }
}

@Preview
@Composable
private fun TestStatusListPreview() {
    KoogAgentTheme {
        TestStatusList(
            testStatuses =
                REGRESSION_TEST_SUITE.map {
                        TestStatus(name = it.name, state = TestStatus.State.PASS)
                    }
                    .toImmutableList()
        )
    }
}

@Preview
@Composable
private fun TestStatusCardPreview() {
    KoogAgentTheme {
        TestStatusCard(status = TestStatus(name = "Test Name", state = TestStatus.State.PASS))
    }
}
