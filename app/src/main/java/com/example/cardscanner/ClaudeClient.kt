package com.example.cardscanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

object Img {
    fun encode(bmp: Bitmap, rot: Int): ByteArray {
        var b = bmp
        if (rot != 0) {
            val m = Matrix(); m.postRotate(rot.toFloat())
            b = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        }
        val s = minOf(1f, 1600f / maxOf(b.width, b.height))
        if (s < 1f) b = Bitmap.createScaledBitmap(b, (b.width * s).toInt(), (b.height * s).toInt(), true)
        val o = ByteArrayOutputStream()
        b.compress(Bitmap.CompressFormat.JPEG, 88, o)
        return o.toByteArray()
    }

    fun fromUri(ctx: Context, uri: Uri): ByteArray? {
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val opts = BitmapFactory.Options().apply { inSampleSize = if (bytes.size > 3_000_000) 2 else 1 }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
        val ori = try { ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) } catch (e: Exception) { 1 }
        val rot = when (ori) { 6 -> 90; 3 -> 180; 8 -> 270; else -> 0 }
        return encode(bmp, rot)
    }
}
