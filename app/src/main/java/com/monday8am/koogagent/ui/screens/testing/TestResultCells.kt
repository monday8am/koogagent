package com.monday8am.koogagent.ui.screens.testing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.monday8am.presentation.testing.TestResultFrame
import com.monday8am.presentation.testing.ValidationResult

@Composable
internal fun DescriptionCell(frame: TestResultFrame.Description) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "${frame.testName} • Description",
                style = MaterialTheme.typography.titleSmall,
            )
            if (frame.description.isNotEmpty()) {
                Text(
                    text = frame.description,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                text = "System Prompt: ${frame.systemPrompt}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun QueryCell(frame: TestResultFrame.Query) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "User • Query",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = frame.query,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
internal fun ThinkingCell(frame: TestResultFrame.Thinking) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = "${frame.testName} • Thinking",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
            Text(
                text = frame.accumulator,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun ToolCell(frame: TestResultFrame.Tool) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = "${frame.testName} • Tool Call",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
            )
            Text(
                text = frame.accumulator,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
internal fun ContentCell(frame: TestResultFrame.Content) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = "${frame.testName} • Response",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f),
            )
            Text(
                text = frame.accumulator,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

@Composable
internal fun ValidationCell(frame: TestResultFrame.Validation) {
    val (backgroundColor, textColor, message) =
        when (val result = frame.result) {
            is ValidationResult.Pass -> Triple(
                MaterialTheme.colorScheme.primaryContainer,
                MaterialTheme.colorScheme.primary,
                result.message
            )

            is ValidationResult.Fail -> Triple(
                MaterialTheme.colorScheme.errorContainer,
                MaterialTheme.colorScheme.error,
                result.message
            )
        }

    Card(
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = "${frame.testName} • Validation",
                style = MaterialTheme.typography.titleSmall,
                color = textColor,
            )
            Text(
                text = "$message (${frame.duration}ms)",
                color = textColor,
            )
        }
    }
}

@Preview
@Composable
private fun ComponentsPreview() {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(16.dp)
    ) {
        DescriptionCell(
            TestResultFrame.Description(
                testName = "Test 01",
                description = "This is a description",
                systemPrompt = "This is a system prompt"
            )
        )
        QueryCell(
            TestResultFrame.Query(
                testName = "Test 01",
                query = "This is a query"
            )
        )
        ThinkingCell(
            TestResultFrame.Thinking(
                testName = "Test 01",
                chunk = "...",
                accumulator = "I am thinking about the problem..."
            )
        )
        ToolCell(
            TestResultFrame.Tool(
                testName = "Test 01",
                content = "get_weather",
                accumulator = "Calling weather tool..."
            )
        )
        ContentCell(
            TestResultFrame.Content(
                testName = "Test 01",
                chunk = "Hello",
                accumulator = "Hello, world!"
            )
        )
        ValidationCell(
            TestResultFrame.Validation(
                testName = "Test 01",
                result = ValidationResult.Pass("Test Passed"),
                duration = 123L,
                fullContent = "Complete"
            )
        )
        ValidationCell(
            TestResultFrame.Validation(
                testName = "Test 01",
                result = ValidationResult.Fail("Test Failed", "Error details"),
                duration = 456L,
                fullContent = "Failed content"
            )
        )
    }
}
