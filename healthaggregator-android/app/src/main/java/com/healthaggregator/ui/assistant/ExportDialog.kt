package com.healthaggregator.ui.assistant

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun ExportDialog(
	onDismiss: () -> Unit,
	onExportMarkdown: ((Uri) -> Unit) -> Unit,
	onExportPdf: ((Uri) -> Unit) -> Unit,
) {
	val ctx = LocalContext.current
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text("Export conversation") },
		text = {
			Text("Markdown preserves citation text; PDF is suitable for sharing with doctors or family.")
		},
		confirmButton = {
			Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(end = 8.dp)) {
				Button(onClick = {
					onExportMarkdown { uri -> share(ctx, uri, "text/markdown") }
					onDismiss()
				}) { Text("Markdown (.md)") }
				Button(onClick = {
					onExportPdf { uri -> share(ctx, uri, "application/pdf") }
					onDismiss()
				}) { Text("PDF") }
			}
		},
		dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
	)
}

private fun share(ctx: android.content.Context, uri: Uri, mime: String) {
	val intent = Intent(Intent.ACTION_SEND).apply {
		type = mime
		putExtra(Intent.EXTRA_STREAM, uri)
		addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
	}
	ctx.startActivity(Intent.createChooser(intent, "Share conversation").apply {
		addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
	})
}
