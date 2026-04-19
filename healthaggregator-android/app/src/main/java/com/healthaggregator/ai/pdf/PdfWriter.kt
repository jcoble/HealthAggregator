package com.healthaggregator.ai.pdf

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream

/**
 * Thin wrapper around [PdfDocument] that maintains a running y-cursor and auto-paginates when a
 * draw would exceed the page. Units are PDF points (72 pt/inch). Page size is US Letter.
 *
 * Usage:
 *   val w = PdfWriter()
 *   w.startPage()
 *   w.h1("Comprehensive Report")
 *   w.body("Generated 2026-04-18")
 *   w.writeTo(file)
 */
class PdfWriter {
	private val doc = PdfDocument()
	private var page: PdfDocument.Page? = null
	private var canvas: Canvas? = null
	private var cursorY = 0f
	private var pageNumber = 0

	private val pageWidth = 612     // Letter: 8.5" × 72
	private val pageHeight = 792    // Letter: 11" × 72
	private val marginX = 36f       // 0.5"
	private val marginTop = 48f
	private val marginBottom = 48f
	private val usableWidth: Float = pageWidth - 2 * marginX

	private val body = Paint().apply { textSize = 10f; isAntiAlias = true; color = Color.BLACK }
	private val bodyBold = Paint().apply {
		textSize = 10f; isAntiAlias = true; color = Color.BLACK; typeface = Typeface.DEFAULT_BOLD
	}
	private val h1 = Paint().apply {
		textSize = 18f; isAntiAlias = true; color = Color.BLACK; typeface = Typeface.DEFAULT_BOLD
	}
	private val h2 = Paint().apply {
		textSize = 13f; isAntiAlias = true; color = Color.BLACK; typeface = Typeface.DEFAULT_BOLD
	}
	private val h3 = Paint().apply {
		textSize = 11f; isAntiAlias = true; color = Color.BLACK; typeface = Typeface.DEFAULT_BOLD
	}
	private val muted = Paint().apply { textSize = 9f; isAntiAlias = true; color = 0xFF555555.toInt() }
	private val flagHigh = Paint().apply {
		textSize = 10f; isAntiAlias = true; color = 0xFFB30000.toInt(); typeface = Typeface.DEFAULT_BOLD
	}
	private val flagLow = Paint().apply {
		textSize = 10f; isAntiAlias = true; color = 0xFF00458C.toInt(); typeface = Typeface.DEFAULT_BOLD
	}
	private val rule = Paint().apply { color = 0xFFCCCCCC.toInt(); strokeWidth = 0.5f }

	fun startPage() {
		finishPage()
		pageNumber++
		val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
		page = doc.startPage(info)
		canvas = page!!.canvas
		cursorY = marginTop
	}

	private fun ensureSpace(needed: Float) {
		if (page == null || cursorY + needed > pageHeight - marginBottom) startPage()
	}

	fun h1(text: String) {
		ensureSpace(h1.textSize * 1.4f)
		canvas!!.drawText(text, marginX, cursorY + h1.textSize, h1)
		cursorY += h1.textSize * 1.4f
	}

	fun h2(text: String) {
		ensureSpace(h2.textSize * 1.8f)
		cursorY += h2.textSize * 0.4f
		canvas!!.drawText(text, marginX, cursorY + h2.textSize, h2)
		cursorY += h2.textSize * 1.3f
		canvas!!.drawLine(marginX, cursorY, pageWidth - marginX, cursorY, rule)
		cursorY += 4f
	}

	fun h3(text: String) {
		ensureSpace(h3.textSize * 1.6f)
		cursorY += h3.textSize * 0.3f
		canvas!!.drawText(text, marginX, cursorY + h3.textSize, h3)
		cursorY += h3.textSize * 1.3f
	}

	fun body(text: String, bold: Boolean = false) {
		wrapAndDraw(text, if (bold) bodyBold else body, indent = 0f)
	}

	fun bullet(text: String) {
		wrapAndDraw("• $text", body, indent = 10f)
	}

	fun bulletWithFlag(dateValue: String, trailing: String, flag: ValueFlag) {
		val dvPaint = when (flag) {
			ValueFlag.HIGH -> flagHigh
			ValueFlag.LOW -> flagLow
			ValueFlag.NORMAL -> body
		}
		val lineHeight = body.textSize * 1.3f
		ensureSpace(lineHeight)
		val bulletX = marginX + 2f
		canvas!!.drawText("•", bulletX, cursorY + body.textSize, body)
		val textX = marginX + 14f
		canvas!!.drawText(dateValue, textX, cursorY + body.textSize, dvPaint)
		val dateValueWidth = dvPaint.measureText(dateValue)
		if (trailing.isNotEmpty()) {
			canvas!!.drawText("  $trailing", textX + dateValueWidth, cursorY + body.textSize, muted)
		}
		cursorY += lineHeight
	}

	fun muted(text: String) {
		wrapAndDraw(text, muted, indent = 0f)
	}

	fun spacer(h: Float = 6f) {
		cursorY += h
	}

	/** Two-column row: label on left (bold), value on right. For "key: value" pairs. */
	fun kv(label: String, value: String) {
		val lineHeight = body.textSize * 1.3f
		ensureSpace(lineHeight)
		canvas!!.drawText(label, marginX, cursorY + body.textSize, bodyBold)
		val labelWidth = bodyBold.measureText("$label ")
		val remaining = usableWidth - labelWidth
		val lines = wrapLines(value, body, remaining)
		canvas!!.drawText(lines.first(), marginX + labelWidth, cursorY + body.textSize, body)
		cursorY += lineHeight
		lines.drop(1).forEach { line ->
			ensureSpace(lineHeight)
			canvas!!.drawText(line, marginX + labelWidth, cursorY + body.textSize, body)
			cursorY += lineHeight
		}
	}

	private fun wrapAndDraw(text: String, paint: Paint, indent: Float) {
		val lineHeight = paint.textSize * 1.3f
		val x = marginX + indent
		val maxWidth = usableWidth - indent
		val lines = wrapLines(text, paint, maxWidth)
		for (line in lines) {
			ensureSpace(lineHeight)
			canvas!!.drawText(line, x, cursorY + paint.textSize, paint)
			cursorY += lineHeight
		}
	}

	private fun wrapLines(text: String, paint: Paint, maxWidth: Float): List<String> {
		val out = mutableListOf<String>()
		text.split('\n').forEach { para ->
			if (para.isEmpty()) { out.add(""); return@forEach }
			var current = StringBuilder()
			for (word in para.split(' ')) {
				val trial = if (current.isEmpty()) word else "$current $word"
				if (paint.measureText(trial) <= maxWidth) {
					current = StringBuilder(trial)
				} else {
					if (current.isNotEmpty()) out.add(current.toString())
					current = StringBuilder(word)
				}
			}
			if (current.isNotEmpty()) out.add(current.toString())
		}
		return out
	}

	private fun finishPage() {
		page?.let { doc.finishPage(it) }
		page = null
		canvas = null
	}

	fun writeTo(file: File) {
		finishPage()
		FileOutputStream(file).use { out -> doc.writeTo(out) }
		doc.close()
	}

	enum class ValueFlag { HIGH, LOW, NORMAL }
}
