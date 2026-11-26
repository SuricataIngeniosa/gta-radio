package ar.nallib.gtaradio.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.res.ResourcesCompat
import java.io.ByteArrayOutputStream

/**
 * Portada de cada emisora, centrada sobre el color de la emisora: la imagen que el usuario puso junto al .mp3
 * (FLASH.png, etc.) si existe; si no, el logo incluido en la app (assets/logos/<JUEGO>/<ARCHIVO>.png); y si
 * tampoco hay, una tarjeta con el color y el nombre de la emisora.
 */
object Artwork {
    private const val SIZE = 512
    // Bitmap listo (para la pantalla, sin pasar por PNG) y PNG (para la sesión de medios / Android Auto), por separado.
    private val bitmaps = java.util.concurrent.ConcurrentHashMap<Pair<String, Uri?>, Bitmap>()
    private val pngs = java.util.concurrent.ConcurrentHashMap<Pair<String, Uri?>, ByteArray>()

    fun bitmap(context: Context, station: Station, logo: Uri?): Bitmap = bitmaps.getOrPut(station.id to logo) {
        val src = logo?.let { uri -> runCatching { context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) } }.getOrNull() }
            ?: bundled(context, station)
        // Logo cuadrado con fondo propio (sin transparencia y casi cuadrado, como los de la rueda de GTA VI): ocupa todo el
        // botón; uno rectangular entra entero.
        // GTA V: un poco más chico, para que entre en el círculo de la rueda sin que se corten las esquinas.
        src?.let {
            val square = it.width.toFloat() / it.height in 0.85f..1.15f
            onColor(it, station.color.toInt(), when { !it.hasAlpha() && square -> 1f; station.game == Game.GTA_V -> 0.7f; else -> 0.82f })
        }
            ?: generated(context, station)
    }

    fun png(context: Context, station: Station, logo: Uri?): ByteArray = pngs.getOrPut(station.id to logo) {
        ByteArrayOutputStream().also { bitmap(context, station, logo).compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    private fun bundled(context: Context, station: Station): Bitmap? = runCatching {
        val stem = station.fileName.substringBeforeLast('.')
        context.assets.open("logos/${station.game.name}/$stem.png").use { BitmapFactory.decodeStream(it) }
    }.getOrNull()

    private fun onColor(src: Bitmap, background: Int, fill: Float): Bitmap {
        val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(background)
        val scale = if (fill >= 1f) maxOf(SIZE.toFloat() / src.width, SIZE.toFloat() / src.height)
            else minOf(SIZE * fill / src.width, SIZE * fill / src.height)
        val w = src.width * scale; val h = src.height * scale
        c.drawBitmap(src, Rect(0, 0, src.width, src.height), RectF((SIZE - w) / 2, (SIZE - h) / 2, (SIZE + w) / 2, (SIZE + h) / 2),
            Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }

    private fun generated(context: Context, station: Station): Bitmap {
        val bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(station.color.toInt())
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt(); textAlign = Paint.Align.CENTER
            typeface = station.game.titleFont?.let { ResourcesCompat.getFont(context, it) } ?: Typeface.DEFAULT_BOLD
            setShadowLayer(18f, 0f, 0f, 0xAA000000.toInt())
        }
        val words = station.name.split(" ")
        // Entre 124 px y lo que haga falta para que la palabra más larga y todas las líneas entren en la tarjeta.
        p.textSize = 100f
        val widest = words.maxOf { p.measureText(it) }
        p.textSize = minOf(124f, 100f * SIZE * 0.88f / widest, SIZE * 0.85f / (words.size * 1.05f))
        val lineH = p.textSize * 1.05f
        var y = SIZE / 2f - (words.size - 1) * lineH / 2 + p.textSize / 3
        for (w in words) { c.drawText(w, SIZE / 2f, y, p); y += lineH }
        return bmp
    }
}
