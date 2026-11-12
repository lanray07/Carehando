package app.carehando.ui

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.carehando.R
import app.carehando.data.*
import app.carehando.domain.occurrences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.time.LocalDate
import java.time.LocalDateTime

private enum class Range(val label: Int) { DAILY(R.string.today), WEEKLY(R.string.last_seven_days), HISTORY(R.string.activity_history) }
private enum class Export { PDF, CSV, TEXT }

@Composable
fun CareReport(recipient: Recipient, entries: List<Entry>, activity: List<ActivityRecord>, close: () -> Unit) {
    var range by rememberSaveable { mutableStateOf(Range.DAILY) }
    val today = LocalDate.now()
    val now = LocalDateTime.now()
    val first = if (range == Range.DAILY) today else today.minusDays(6)
    Editor(R.string.care_report, close) {
        Choices(R.string.report_period, Range.entries.map { it to it.label }, range) { range = it }
        val rows = buildList {
            add(listOf(stringResource(R.string.date), stringResource(R.string.title), stringResource(R.string.status)))
            if (range == Range.HISTORY) {
                activity.forEach { add(listOf(DateText(it.day), it.title, stringResource(it.outcome.label()))) }
            } else {
                occurrences(entries, activity, first, today).forEach {
                    val status = if (it.entry.kind == Kind.MEDICATION && it.isLate(now)) R.string.missed_unconfirmed else it.outcome.label()
                    add(listOf(DateText(it.date.toString()), it.entry.title, stringResource(status)))
                }
                entries.filter { it.kind == Kind.JOURNAL && it.day in first.toString()..today.toString() }.forEach {
                    add(listOf(DateText(it.day), it.title, stringResource(R.string.journal)))
                }
            }
        }
        val lines = listOf(recipient.name, stringResource(range.label)) + rows.drop(1).map { it.joinToString(" · ") }
        if (rows.size == 1) Note(R.string.no_items)
        ExportActions(R.string.care_report, lines, rows)
        PdfPreview(R.string.care_report, lines)
        Panel {
            Heading(R.string.report_preview)
            lines.take(50).forEach { Text(it) }
        }
    }
}

@Composable
fun ExportActions(title: Int, lines: List<String>, csv: List<List<String>>?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val textTitle = stringResource(title)
    var confirmation by remember { mutableStateOf<Export?>(null) }
    var notice by remember { mutableStateOf<Int?>(null) }
    val currentLines by rememberUpdatedState(lines)
    val currentRows by rememberUpdatedState(csv)
    val pdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) scope.launch {
            val snapshot = currentLines
            notice = withContext(Dispatchers.IO) {
                runCatching {
                    requireNotNull(context.contentResolver.openOutputStream(uri)).use { writePdf(it, textTitle, snapshot) }
                }.fold({ R.string.export_saved }, { R.string.export_failed })
            }
        }
    }
    val spreadsheet = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            val snapshot = currentRows.orEmpty()
            notice = withContext(Dispatchers.IO) {
                runCatching {
                    requireNotNull(context.contentResolver.openOutputStream(uri)).bufferedWriter(Charsets.UTF_8).use { writer ->
                        snapshot.forEach { row ->
                            writer.write(row.joinToString(",") { csvCell(it) })
                            writer.write("\r\n")
                        }
                    }
                }.fold({ R.string.export_saved }, { R.string.export_failed })
            }
        }
    }
    OutlinedButton({ confirmation = Export.PDF }) { Text(stringResource(R.string.export_pdf)) }
    if (csv != null) OutlinedButton({ confirmation = Export.CSV }) { Text(stringResource(R.string.export_csv)) }
    OutlinedButton({ confirmation = Export.TEXT }) { Text(stringResource(R.string.share_text)) }
    notice?.let { Note(it) }
    confirmation?.let { format ->
        Confirm(R.string.export_confirm, R.string.export_notice, { confirmation = null }) {
            confirmation = null
            when (format) {
                Export.PDF -> pdf.launch("carehando.pdf")
                Export.CSV -> spreadsheet.launch("carehando.csv")
                Export.TEXT -> {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, (listOf(textTitle) + lines).joinToString("\n"))
                    runCatching { context.startActivity(Intent.createChooser(send, textTitle)) }
                        .onFailure { notice = R.string.export_failed }
                }
            }
        }
    }
}

internal fun csvCell(value: String): String {
    val first = value.trimStart().firstOrNull()
    val safe = if (first in listOf('=', '+', '-', '@') || value.startsWith('\t') || value.startsWith('\r')) "'$value" else value
    return "\"" + safe.replace("\"", "\"\"") + "\""
}

internal fun writePdf(output: OutputStream, title: String, lines: List<String>) {
    val document = PdfDocument()
    try {
        var pageNumber = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
        val paint = TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(31, 62, 62) }
        var y = 36
        (listOf(title) + lines).forEachIndexed { index, line ->
            paint.textSize = if (index == 0) 22f else 12f
            val layout = StaticLayout.Builder.obtain(line, 0, line.length, paint, 515)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).build()
            for (i in 0 until layout.lineCount) {
                val top = layout.getLineTop(i)
                val height = layout.getLineBottom(i) - top
                if (y + height > 806) {
                    document.finishPage(page)
                    page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, ++pageNumber).create())
                    y = 36
                }
                val canvas: Canvas = page.canvas
                canvas.save()
                canvas.clipRect(40, y, 555, y + height)
                canvas.translate(40f, (y - top).toFloat())
                layout.draw(canvas)
                canvas.restore()
                y += height
            }
            y += 7
        }
        document.finishPage(page)
        document.writeTo(output)
    } finally {
        document.close()
    }
}


