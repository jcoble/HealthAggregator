package com.healthaggregator.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Opens the Android share sheet for a PDF file. The file MUST live under the app's cache
 * dir (see `res/xml/file_paths.xml`) so the bundled FileProvider authority can expose it.
 */
object SharePdf {
	fun share(context: Context, file: File, subject: String) {
		val authority = "${context.packageName}.fileprovider"
		val uri = FileProvider.getUriForFile(context, authority, file)
		val send = Intent(Intent.ACTION_SEND).apply {
			type = "application/pdf"
			putExtra(Intent.EXTRA_STREAM, uri)
			putExtra(Intent.EXTRA_SUBJECT, subject)
			addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
		}
		val chooser = Intent.createChooser(send, "Share $subject").apply {
			addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
		}
		context.startActivity(chooser)
	}
}
