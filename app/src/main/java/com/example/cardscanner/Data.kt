package com.example.cardscanner

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray

data class Contact(
    val name: String = "", val designation: String = "", val organisation: String = "",
    val phone: String = "", val email: String = "", val website: String = "", val address: String = ""
) {
    fun values() = listOf(name, designation, organisation, phone, email, website, address)
    companion object {
        val LABELS = listOf("Name", "Designation", "Organisation", "Phone", "Email", "Website", "Address")
        fun of(v: List<String>) = Contact(v[0], v[1], v[2], v[3], v[4], v[5], v[6])
    }
}

object Store {
    fun save(ctx: Context, list: List<Contact>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONArray(it.values())) }
        ctx.getSharedPreferences("cards", Context.MODE_PRIVATE).edit().putString("c", arr.toString()).apply()
    }

    fun load(ctx: Context): List<Contact> = try {
        val arr = JSONArray(ctx.getSharedPreferences("cards", Context.MODE_PRIVATE).getString("c", "[]"))
        (0 until arr.length()).map { i ->
            val a = arr.getJSONArray(i)
            Contact.of((0 until 7).map { a.optString(it, "") })
        }
    } catch (e: Exception) { emptyList() }

    fun secure(ctx: Context): SharedPreferences = try {
        val mk = MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            ctx, "secure", mk,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) { ctx.getSharedPreferences("secure_fallback", Context.MODE_PRIVATE) }
}
