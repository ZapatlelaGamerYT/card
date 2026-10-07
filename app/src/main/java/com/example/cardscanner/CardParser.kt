package com.example.cardscanner

import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Free on-device reading: ML Kit OCR + simple rules to sort the text into fields. */
object CardParser {
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    private suspend fun ocr(jpeg: ByteArray): String {
        val bmp = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: return ""
        val image = InputImage.fromBitmap(bmp, 0)
        return suspendCancellableCoroutine { c ->
            recognizer.process(image)
                .addOnSuccessListener { c.resume(it.text) }
                .addOnFailureListener { c.resumeWithException(it) }
        }
    }

    /** All images passed together are treated as ONE card (front and back). */
    suspend fun extract(images: List<ByteArray>): List<Contact> {
        val text = images.joinToString("\n") { ocr(it) }
        if (text.isBlank()) throw IOException("No text found. Retake the photo in good light.")
        return listOf(parse(text))
    }

    private val emailRe = Regex("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}")
    private val webRe = Regex("(?i)\\b(?:https?://)?(?:www\\.)?[a-z0-9\\-]+(?:\\.[a-z0-9\\-]+)*\\.(?:com|in|co|org|net|io|biz|info|edu|gov|ai|app|dev|me)(?:\\.[a-z]{2})?(?:/\\S*)?")
    private val phoneRe = Regex("\\+?\\d[\\d\\s\\-().]{6,}\\d")
    private val pinRe = Regex("\\b\\d{5,6}\\b")
    private val labelRe = Regex("(?i)\\b(tel|telephone|ph|phone|mob|mobile|cell|email|e-mail|web|website|fax|office|contact)\\b[:.\\-]?")
    private val spaces = Regex("\\s+")

    private val desig = listOf("director", "manager", "ceo", "cto", "cfo", "coo", "founder", "co-founder", "engineer",
        "officer", "president", "vice", "partner", "consultant", "executive", "head", "developer", "proprietor",
        "owner", "md", "chairman", "sales", "marketing", "lead", "architect", "analyst", "designer", "advocate",
        "associate", "supervisor", "specialist", "administrator", "secretary", "professor", "principal", "agent",
        "representative", "proprietress", "manager", "incharge", "in-charge")
    private val org = listOf("pvt", "ltd", "limited", "llp", "inc", "llc", "corp", "corporation", "company", "co",
        "technologies", "technology", "solutions", "industries", "enterprises", "group", "systems", "services",
        "associates", "consultants", "traders", "trading", "hospital", "clinic", "bank", "university", "institute",
        "college", "school", "studio", "labs", "global", "international", "infotech", "softech", "software",
        "pharma", "agency", "foundation", "works", "exports", "imports", "motors", "engineering", "constructions",
        "builders", "electricals", "electronics", "marketing", "logistics", "events", "media")
    private val addr = listOf("road", "rd", "street", "st", "lane", "nagar", "floor", "flr", "plot", "sector", "phase",
        "near", "opp", "opposite", "building", "bldg", "tower", "chowk", "colony", "society", "park", "india",
        "maharashtra", "gujarat", "karnataka", "delhi", "pune", "mumbai", "bangalore", "bengaluru", "hyderabad",
        "chennai", "kolkata", "ahmedabad", "pin", "marg", "complex", "estate", "wing", "shop", "office no")

    private fun has(line: String, words: List<String>) =
        words.any { Regex("(?i)(?<![A-Za-z])" + Regex.escape(it) + "(?![A-Za-z])").containsMatchIn(line) }

    fun parse(text: String): Contact {
        val emails = LinkedHashSet<String>()
        val phones = LinkedHashSet<String>()
        val webs = LinkedHashSet<String>()
        val rest = mutableListOf<String>()

        for (raw in text.lines().map { it.trim() }.filter { it.length > 1 }) {
            var l = raw
            emailRe.findAll(l).forEach { emails.add(it.value.lowercase()) }
            l = emailRe.replace(l, " ")
            webRe.findAll(l).forEach { webs.add(it.value.lowercase().trimEnd('.', ',')) }
            l = webRe.replace(l, " ")
            val ph = phoneRe.findAll(l).map { it.value.trim() }.filter { v -> v.count { it.isDigit() } in 8..15 }.toList()
            ph.forEach { phones.add(it.replace(spaces, " ")); l = l.replace(it, " ") }
            l = labelRe.replace(l, " ").replace(spaces, " ").trim(' ', ':', '|', '-', ',', '.', '/')
            if (l.count { it.isLetter() } >= 2) rest.add(l)
        }

        val used = BooleanArray(rest.size)
        val address = mutableListOf<String>()
        var designation = ""
        var organisation = ""
        var name = ""

        rest.forEachIndexed { i, l ->
            if (has(l, addr) || pinRe.containsMatchIn(l) || (l.contains(",") && l.any { it.isDigit() })) {
                address.add(l); used[i] = true
            }
        }
        rest.forEachIndexed { i, l -> if (!used[i] && designation.isEmpty() && has(l, desig)) { designation = l; used[i] = true } }
        rest.forEachIndexed { i, l -> if (!used[i] && organisation.isEmpty() && has(l, org)) { organisation = l; used[i] = true } }

        val ni = rest.indices.firstOrNull { i ->
            !used[i] && rest[i].split(" ").size in 2..4 && rest[i].all { it.isLetter() || it == ' ' || it == '.' || it == '\'' }
        } ?: rest.indices.firstOrNull { !used[it] }
        if (ni != null) { name = rest[ni]; used[ni] = true }

        if (organisation.isEmpty()) {
            val oi = rest.indices.firstOrNull { !used[it] }
            if (oi != null) { organisation = rest[oi]; used[oi] = true }
        }

        return Contact(
            name = name, designation = designation, organisation = organisation,
            phone = phones.joinToString(", "), email = emails.joinToString(", "),
            website = webs.joinToString(", "), address = address.joinToString(", ")
        )
    }
}
