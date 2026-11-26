package ar.nallib.gtaradio.ui

import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.res.ResourcesCompat
import ar.nallib.gtaradio.R
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * "GTA IV" con el "IV" del logo del juego recreado: las letras I y V (polígonos sacados de images/IV/gtaIV-logo.png con
 * tools/logo4/trace.py, en unidades de esa imagen: 425 de alto) en capas, de afuera hacia adentro:
 *  - contorno negro de 22;
 *  - bisel de 16 cuyo tono depende de hacia dónde mira cada cara (izquierda 248, arriba 192, abajo 184, derecha 116);
 *  - franja blanca de 4 pegada a la letra;
 *  - relleno negro con el reflejo: degradé vertical que sube a gris 63 al 45 % del alto y vuelve a negro.
 * "gta" en Pricedown (la letra de siempre, en minúscula como en los logos), blanca con contorno negro, a la altura del IV.
 */
@Composable
fun Gta4Logo(gtaHeight: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val logo = remember { built(context) }
    // "gta" mide H de alto en el dibujo: el logo entero mide box.height/H veces el alto pedido para "gta".
    Canvas(modifier.height(gtaHeight * (logo.box.height() / H)).aspectRatio(logo.box.width() / logo.box.height())) {
        drawIntoCanvas { c ->
            val n = c.nativeCanvas
            val k = minOf(size.width / logo.box.width(), size.height / logo.box.height())
            n.save(); n.scale(k, k); n.translate(-logo.box.left, -logo.box.top)
            logo.draw(n)
            n.restore()
        }
    }
}

private const val H = 425f          // alto de las letras en el logo original
private const val RING = 22f        // contorno negro
private const val FACET = 16f       // bisel
private const val INNER = 4f        // franja blanca

private val I = floatArrayOf(0f, 0f, 188f, 0f, 188f, 98f, 144f, 99f, 144f, 321f, 188f, 322f, 188f, 425f, 0f, 425f, 0f, 322f, 44f, 321f, 44f, 99f, 0f, 98f)
private const val C = 204f          // eje de la V (relativo a su borde izquierdo)
private val V = floatArrayOf(0f, 0f, C - 36, 0f, C - 36, 98f, C - 67, 99f, C - 14, 277f, C + 14, 277f, C + 67, 99f, C + 36, 98f, C + 36, 0f,
    C + 204, 0f, C + 204, 98f, C + 166, 99f, C + 71, 425f, C - 71, 425f, C - 166, 99f, 0f, 98f)

private class Logo(val box: RectF, val draw: (android.graphics.Canvas) -> Unit)

private var cached: Logo? = null
private fun built(context: android.content.Context) = cached ?: build(context).also { cached = it }

/** Ancho del logo por cada unidad de alto de "gta" (ver gta5WidthPerGta). */
fun gta4WidthPerGta(context: android.content.Context) = built(context).box.width() / H

private fun build(context: android.content.Context): Logo {
    // "GTA" en Pricedown, escalada a la altura del IV.
    val font = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = ResourcesCompat.getFont(context, R.font.iii_title); textSize = 400f }
    val gta = Path().also { font.getTextPath("gta", 0, 3, 0f, 0f, it) }
    val gb = RectF().also { gta.computeBounds(it, true) }
    val s = H / gb.height()
    gta.transform(android.graphics.Matrix().apply { setTranslate(-gb.left, -gb.top); postScale(s, s) })
    val gtaW = gb.width() * s
    val x0 = gtaW + (RING + FACET + INNER) * 2 + 60f  // contornos de GTA y del IV más 60 de aire entre ellos
    val glyphs = listOf(poly(I, x0), poly(V, x0 + 248f))
    val all = Path().apply { addPath(gta); glyphs.forEach { addPath(it.first) } }
    val box = RectF().also { all.computeBounds(it, true) }.apply { inset(-(RING + FACET + INNER), -(RING + FACET + INNER)) }

    val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; style = Paint.Style.FILL_AND_STROKE; strokeJoin = Paint.Join.MITER; strokeMiter = 4f }
    val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFAFAFC.toInt(); style = Paint.Style.FILL_AND_STROKE; strokeJoin = Paint.Join.MITER; strokeMiter = 4f }
    val face = Paint(Paint.ANTI_ALIAS_FLAG)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        // El reflejo: medido en la columna de la I del original (gris por punto de alto).
        val stops = floatArrayOf(0f, .25f, .28f, .31f, .34f, .37f, .395f, .42f, .45f, .48f, .51f, .54f, .565f, .59f, .62f, .65f, .68f, .71f, .73f, .76f, .79f, .85f, 1f)
        val grays = intArrayOf(0, 0, 5, 10, 15, 22, 32, 47, 63, 59, 49, 43, 36, 29, 23, 17, 14, 11, 8, 6, 3, 0, 0)
        shader = LinearGradient(0f, 0f, 0f, H, grays.map { android.graphics.Color.rgb(it, (it * 0.93f).toInt(), (it * 0.94f).toInt()) }.toIntArray(), stops, Shader.TileMode.CLAMP)
    }
    return Logo(box) { n ->
        // GTA: contorno negro y relleno blanco.
        ring.strokeWidth = (RING + FACET + INNER) * 2; n.drawPath(gta, ring)
        n.drawPath(gta, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFAFAFC.toInt() })
        // IV: contorno, bisel por caras, franja blanca y relleno con el reflejo.
        for ((path, pts) in glyphs) { ring.strokeWidth = (RING + FACET + INNER) * 2; n.drawPath(path, ring) }
        for ((_, pts) in glyphs) drawFacets(n, pts, face)
        for ((path, _) in glyphs) { white.strokeWidth = INNER * 2; n.drawPath(path, white) }
        for ((path, _) in glyphs) n.drawPath(path, fill)
    }
}

/** Polígono desplazado en x; devuelve el Path y los puntos (para el bisel). */
private fun poly(p: FloatArray, dx: Float): Pair<Path, List<Pair<Float, Float>>> {
    val pts = (p.indices step 2).map { p[it] + dx to p[it + 1] }
    val path = Path().apply { moveTo(pts[0].first, pts[0].second); pts.drop(1).forEach { lineTo(it.first, it.second) }; close() }
    return path to pts
}

/**
 * Bisel: una cara por lado, entre el borde a INNER y a INNER+FACET de la letra (esquinas a inglete), con el tono según
 * hacia dónde mira la cara (medido en el logo: derecha 116, arriba 192, izquierda 248, abajo 184; entre medio, lineal).
 */
private fun drawFacets(n: android.graphics.Canvas, pts: List<Pair<Float, Float>>, paint: Paint) {
    val cnt = pts.size
    val area = (0 until cnt).sumOf { val a = pts[it]; val b = pts[(it + 1) % cnt]; (a.first * b.second - b.first * a.second).toDouble() }
    val sgn = if (area > 0) 1f else -1f
    val normals = (0 until cnt).map {
        val a = pts[it]; val b = pts[(it + 1) % cnt]
        val dx = b.first - a.first; val dy = b.second - a.second; val l = hypot(dx, dy)
        sgn * dy / l to -sgn * dx / l
    }
    fun off(i: Int, d: Float): Pair<Float, Float> {
        val a = normals[(i - 1 + cnt) % cnt]; val b = normals[i]
        val k = 1 + a.first * b.first + a.second * b.second
        return pts[i].first + d * (a.first + b.first) / k to pts[i].second + d * (a.second + b.second) / k
    }
    for (i in 0 until cnt) {
        val j = (i + 1) % cnt
        val (nx, ny) = normals[i]
        var ang = Math.toDegrees(atan2(-ny.toDouble(), nx.toDouble())).toFloat(); if (ang < 0) ang += 360f
        val stops = floatArrayOf(0f, 90f, 180f, 270f, 360f); val tones = floatArrayOf(116f, 192f, 248f, 184f, 116f)
        val seg = (0 until 4).first { ang >= stops[it] && ang <= stops[it + 1] }
        val g = (tones[seg] + (tones[seg + 1] - tones[seg]) * (ang - stops[seg]) / 90f).toInt()
        paint.color = android.graphics.Color.rgb(g, g, g)
        val a1 = off(i, INNER); val b1 = off(j, INNER); val b2 = off(j, INNER + FACET); val a2 = off(i, INNER + FACET)
        n.drawPath(Path().apply { moveTo(a1.first, a1.second); lineTo(b1.first, b1.second); lineTo(b2.first, b2.second); lineTo(a2.first, a2.second); close() }, paint)
    }
}
