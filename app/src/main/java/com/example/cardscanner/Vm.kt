package com.example.cardscanner

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.File

class Vm(private val app: Application) : AndroidViewModel(app) {
    val cards = mutableStateListOf<Contact>()
    var status by mutableStateOf("")
    var isError by mutableStateOf(false)
    var both by mutableStateOf(true)
    var sheetUrl by mutableStateOf("")
    var sheetSecret by mutableStateOf("")
    private var waiting = 0
    private var added = 0
    private var sent = 0
    private var errMsg: String? = null
    private val queue = Channel<List<ByteArray>>(Channel.UNLIMITED)

    init {
        cards.addAll(Store.load(app))
        val p = Store.secure(app)
        sheetUrl = p.getString("sheetUrl", "") ?: ""
        sheetSecret = p.getString("sheetSecret", "") ?: ""
        viewModelScope.launch {
            for (job in queue) {
                try {
                    val found = CardParser.extract(job)
                    cards.addAll(found); added += found.size
                    Store.save(app, cards)
                    if (sheetUrl.isNotBlank() && found.isNotEmpty()) {
                        try {
                            SheetClient.append(sheetUrl, sheetSecret, found.map { it.values() })
                            sent += found.size
                        } catch (e: Exception) {
                            errMsg = "Saved on phone, but Google Sheet failed: ${e.message}"
                        }
                    }
                } catch (e: Exception) {
                    errMsg = "Could not read a card: ${e.message}"
                }
                waiting--
                if (waiting > 0 && errMsg == null) status = "Reading... $waiting waiting"
                if (waiting == 0) {
                    status = errMsg ?: ("Done. $added card(s) added" + (if (sent > 0) " and sent to Google Sheet" else "") + ". Please check the details.")
                    isError = errMsg != null
                    added = 0; sent = 0
                } else if (errMsg != null) { status = errMsg!!; isError = true }
            }
        }
    }

    fun saveSettings(url: String, secret: String) {
        sheetUrl = url.trim(); sheetSecret = secret.trim()
        Store.secure(app).edit().putString("sheetUrl", sheetUrl).putString("sheetSecret", sheetSecret).apply()
    }

    fun enqueue(images: List<ByteArray>) {
        if (images.isEmpty()) return
        if (waiting == 0) errMsg = null
        waiting++; isError = false; status = "Reading... $waiting waiting"
        queue.trySend(images)
    }

    fun addFromUris(uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            val imgs = uris.mapNotNull { try { Img.fromUri(app, it) } catch (e: Exception) { null } }
            val groups = if (both) imgs.chunked(2) else imgs.map { listOf(it) }
            groups.forEach { enqueue(it) }
        }
    }

    fun loadFromSheet() {
        viewModelScope.launch {
            isError = false; status = "Loading from Google Sheet..."
            try {
                val rows = SheetClient.fetch(sheetUrl, sheetSecret)
                cards.clear(); cards.addAll(rows); Store.save(app, cards)
                status = "Loaded ${rows.size} row(s) from Google Sheet"
            } catch (e: Exception) {
                isError = true; status = "Could not load from Google Sheet: ${e.message}"
            }
        }
    }

    fun update(i: Int, c: Contact) { cards[i] = c; Store.save(app, cards) }
    fun remove(i: Int) { cards.removeAt(i); Store.save(app, cards) }
    fun clear() { cards.clear(); Store.save(app, cards) }

    fun exportCsv(): Intent? {
        if (cards.isEmpty()) return null
        fun q(v: String) = "\"" + v.replace("\"", "\"\"") + "\""
        val sb = StringBuilder("\uFEFF")
        sb.append(Contact.LABELS.joinToString(",") { q(it) }).append("\r\n")
        cards.forEach { sb.append(it.values().joinToString(",") { v -> q(v) }).append("\r\n") }
        val f = File(app.cacheDir, "visiting-cards.csv")
        f.writeText(sb.toString())
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.files", f)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"; putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Export visiting cards")
    }
}
