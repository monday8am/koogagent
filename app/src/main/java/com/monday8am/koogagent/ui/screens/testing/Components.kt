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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.monday8am.presentation.testing.TestResultFrame
import com.monday8am.presentation.testing.ValidationResult


@Composable
internal fun ThinkingCell(frame: TestResultFrame.Thinking) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2D2D44)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = frame.testName,
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
            )
            Text(
                text = frame.accumulator,
                color = Color(0xFFB0B0B0),
            )
        }
    }
}

@Composable
internal fun ToolCell(frame: TestResultFrame.Tool) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E3A5F)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = frame.testName,
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
            )
            Text(
                text = frame.accumulator,
                color = Color(0xFF64B5F6),
            )
        }
    }
}

@Composable
internal fun ContentCell(frame: TestResultFrame.Content) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = frame.testName,
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
            )
            Text(
                text = frame.accumulator,
                color = Color(0xFF00FF00),
            )
        }
    }
}

@Composable
internal fun ValidationCell(frame: TestResultFrame.Validation) {
    val (backgroundColor, textColor, message) =
        when (val result = frame.result) {
            is ValidationResult.Pass -> Triple(Color(0xFF1B5E20), Color(0xFF81C784), result.message)
            is ValidationResult.Fail -> Triple(Color(0xFF7F0000), Color(0xFFE57373), result.message)
        }

    Card(
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = frame.testName,
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
        ThinkingCell(
            TestResultFrame.Thinking(
                testName = "Thinking Test",
                chunk = "...",
                accumulator = "I am thinking about the problem..."
            )
        )
        ToolCell(
            TestResultFrame.Tool(
                testName = "Tool Test",
                content = "get_weather",
                accumulator = "Calling weather tool..."
            )
        )
        ContentCell(
            TestResultFrame.Content(
                testName = "Content Test",
                chunk = "Hello",
                accumulator = "Hello, world!"
            )
        )
        ValidationCell(
            TestResultFrame.Validation(
                testName = "Validation Pass",
                result = ValidationResult.Pass("Test Passed"),
                duration = 123L,
                fullContent = "Complete"
            )
        )
        ValidationCell(
            TestResultFrame.Validation(
                testName = "Validation Fail",
                result = ValidationResult.Fail("Test Failed", "Error details"),
                duration = 456L,
                fullContent = "Failed content"
            )
        )
    }
}
