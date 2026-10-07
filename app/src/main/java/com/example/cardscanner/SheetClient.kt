package com.example.cardscanner

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object SheetClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()

    private fun check(code: Int, text: String): JSONObject {
        val o = try { JSONObject(text) } catch (e: Exception) { throw IOException("Check the Web app URL (HTTP $code)") }
        if (o.has("error")) throw IOException(o.getString("error"))
        return o
    }

    suspend fun append(url: String, secret: String, rows: List<List<String>>) = withContext(Dispatchers.IO) {
        val arr = JSONArray(); rows.forEach { arr.put(JSONArray(it)) }
        val body = JSONObject().put("token", secret).put("rows", arr).toString()
        val req = Request.Builder().url(url.trim())
            .post(body.toRequestBody("text/plain".toMediaType())).build()
        http.newCall(req).execute().use { r -> check(r.code, r.body?.string().orEmpty()) }
        Unit
    }

    suspend fun fetch(url: String, secret: String): List<Contact> = withContext(Dispatchers.IO) {
        val u = url.trim() + (if (url.contains("?")) "&" else "?") + "token=" + URLEncoder.encode(secret, "UTF-8")
        http.newCall(Request.Builder().url(u).build()).execute().use { r ->
            val o = check(r.code, r.body?.string().orEmpty())
            val rows = o.getJSONArray("rows")
            (0 until rows.length()).map { i ->
                val a = rows.getJSONArray(i)
                Contact.of((0 until 7).map { a.optString(it, "") })
            }
        }
    }
}
