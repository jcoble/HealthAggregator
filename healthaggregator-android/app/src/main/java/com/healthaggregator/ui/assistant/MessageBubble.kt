package com.healthaggregator.ui.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.healthaggregator.ai.CitationRenderer
import com.healthaggregator.ai.CitationSegment
import com.healthaggregator.data.entities.ChatMessage
import com.healthaggregator.ui.theme.HealthAggregatorTheme

@Composable
fun MessageBubble(
	message: ChatMessage,
	onCitationClick: (sourceSystem: String, fhirRef: String) -> Unit,
) {
	val isUser = message.role == "user"
	val isTool = message.role == "tool"
	val isAssistant = message.role == "assistant"

	if (isTool) {
		ToolInvocation(message)
		return
	}

	val bubbleColor = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
	val textColor = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
	val align = if (isUser) Alignment.End else Alignment.Start

	Column(
		modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 12.dp),
		horizontalAlignment = align,
	) {
		Box(
			modifier = Modifier
				.clip(RoundedCornerShape(16.dp))
				.background(bubbleColor)
				.padding(12.dp),
		) {
			val segments = CitationRenderer.parse(message.content.ifEmpty { if (isAssistant) "…" else "" })
			FlowContent(segments, textColor, onCitationClick)
		}
		if (isAssistant && message.content.isNotBlank()) {
			MedicalAdviceDisclaimer()
		}
	}
}

@Composable
private fun FlowContent(
	segments: List<CitationSegment>,
	textColor: androidx.compose.ui.graphics.Color,
	onCitationClick: (String, String) -> Unit,
) {
	// Flow-ish rendering: wrap Text + CitationChip inline. Compose's FlowRow from foundation works.
	androidx.compose.foundation.layout.FlowRow(
		verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
	) {
		segments.forEach { seg ->
			when (seg) {
				is CitationSegment.Text -> Text(
					text = seg.text,
					style = MaterialTheme.typography.bodyMedium,
					color = textColor,
				)
				is CitationSegment.Cite -> {
					CitationChip(
						sourceSystem = seg.sourceSystem,
						fhirRef = seg.fhirRef,
						label = seg.sourceSystem.take(20),
						onClick = onCitationClick,
					)
				}
			}
		}
	}
}

@Composable
private fun ToolInvocation(message: ChatMessage) {
	Row(
		modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp, horizontal = 12.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Icon(
			imageVector = Icons.Outlined.Build,
			contentDescription = null,
			modifier = Modifier.height(14.dp),
			tint = MaterialTheme.colorScheme.secondary,
		)
		Spacer(Modifier.height(4.dp))
		Text(
			text = "tool call " + (message.toolCallId ?: ""),
			style = MaterialTheme.typography.labelSmall,
			color = MaterialTheme.colorScheme.secondary,
		)
	}
}

@Preview(showBackground = true, backgroundColor = 0xFF09090B)
@Composable
private fun PreviewUser() = HealthAggregatorTheme {
	MessageBubble(
		message = ChatMessage(
			id = "1", conversationId = "c", role = "user",
			content = "How is my A1c trending?", createdAt = java.time.Instant.EPOCH,
		),
		onCitationClick = { _, _ -> },
	)
}

@Preview(showBackground = true, backgroundColor = 0xFF09090B, widthDp = 360)
@Composable
private fun PreviewAssistantWithCitations() = HealthAggregatorTheme {
	MessageBubble(
		message = ChatMessage(
			id = "2", conversationId = "c", role = "assistant",
			content = "Your A1c has crossed the abnormal threshold — 6.1 in 2024 [cite:summa-health/Observation/ghi789], then 6.3 recently [cite:summa-health/Observation/jkl012].",
			createdAt = java.time.Instant.EPOCH,
		),
		onCitationClick = { _, _ -> },
	)
}
