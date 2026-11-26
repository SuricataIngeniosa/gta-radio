package ar.nallib.gtaradio.ui

import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.res.ResourcesCompat
import ar.nallib.gtaradio.R

/**
 * El logo de GTA III armado con texto: "gta" en Pricedown negro y el "III" rojo (#9B1F17) como en el original —un bloque
 * del alto de la "a" con dos ranuras y una muesca a cada lado, o sea tres I con las serifas pegadas—, todo dentro de un
 * contorno blanco grueso de esquinas rectas. Proporciones medidas sobre images/III/gtaIII-logo.jpg.
 */
@Composable
fun Gta3Logo(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val logo = remember {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = ResourcesCompat.getFont(context, R.font.iii_title); textSize = 200f }
        val gta = Path().also { paint.getTextPath("gta", 0, 3, 0f, 0f, it) }
        val a = android.graphics.Rect().also { paint.getTextBounds("a", 0, 1, it) }
        val g = RectF().also { gta.computeBounds(it, true) }
        val h = a.height().toFloat()                    // el bloque rojo va del alto de la "a" a la línea de base
        val stroke = h * 0.115f                          // contorno blanco: 8–10 px (con el borde suavizado) sobre 82
        val x0 = g.right + stroke; val w = h * 1.54f    // el bloque mide 126 × 82 px en el original
        val iii = Path().apply { addRect(x0, a.top.toFloat(), x0 + w, a.bottom.toFloat(), Path.Direction.CW) }
        // Ranuras (blancas) entre las tres I y las muescas de los costados, en la franja del medio: barras de arriba y de
        // abajo de 24 px cada una (29 %) y ranura de 33 px (40 %); ranuras al 33–39 % y 63–71 % del ancho.
        val mid0 = a.top + h * 0.293f; val mid1 = a.bottom - h * 0.293f
        for ((l, r) in listOf(0f to 0.111f, 0.33f to 0.393f, 0.633f to 0.706f, 0.928f to 1f)) {
            iii.op(Path().apply { addRect(x0 + w * l, mid0, x0 + w * r, mid1, Path.Direction.CW) }, Path.Op.DIFFERENCE)
        }
        // Detrás del III, la silueta del bloque (con las muescas de los costados, sin las ranuras) en blanco, rellena y con
        // un trazo del ancho del contorno: la línea blanca sigue la muesca con el mismo grosor y sin cortarse en las esquinas.
        val back = Path().apply {
            addRect(x0, a.top.toFloat(), x0 + w, a.bottom.toFloat(), Path.Direction.CW)
            op(Path().apply { addRect(x0 + w * 0.928f, mid0, x0 + w + stroke, mid1, Path.Direction.CW) }, Path.Op.DIFFERENCE)
        }
        // Contorno blanco: UNA silueta (la "g" entera con su rabo + la franja de "gta" hasta el III + el bloque del III)
        // trazada con unión redondeada. Al ser un solo contorno, las esquinas de afuera salen redondeadas y las de adentro
        // (escalón del rabo de la g, escalón entre "gta" y el III) rectas, como en el original. Antes se trazaba cada letra
        // y esas esquinas interiores salían curvas.
        val gb = RectF().also { Path().apply { paint.getTextPath("g", 0, 1, 0f, 0f, this) }.computeBounds(it, true) }
        val outline = Path().apply {
            addRect(g.left, g.top, gb.right, gb.bottom, Path.Direction.CW)
            op(Path().apply { addRect(g.left, g.top, x0, a.bottom.toFloat(), Path.Direction.CW) }, Path.Op.UNION)
            op(back, Path.Op.UNION)
        }
        val box = RectF(g).apply { union(x0 + w + stroke, a.top - stroke); union(x0 + w + stroke, a.bottom + stroke); inset(-stroke, -stroke) }
        Triple(Triple(gta, iii, outline), box, stroke)
    }
    val (paths, box, stroke) = logo
    val (gta, iii, outline) = paths
    val white = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE; style = Paint.Style.FILL_AND_STROKE; strokeWidth = stroke * 2
        strokeJoin = Paint.Join.ROUND                   // sólo afecta las esquinas de afuera: las de adentro quedan rectas
    } }
    val black = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK } }
    val red = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF9B1F17.toInt() } }
    Canvas(modifier.aspectRatio(box.width() / box.height())) {
        drawIntoCanvas { c ->
            val k = minOf(size.width / box.width(), size.height / box.height())
            c.nativeCanvas.save()
            c.nativeCanvas.scale(k, k)
            c.nativeCanvas.translate(-box.left, -box.top)
            c.nativeCanvas.drawPath(outline, white)   // contorno blanco único (esquinas de afuera redondeadas, de adentro rectas)
            c.nativeCanvas.drawPath(gta, black)
            c.nativeCanvas.drawPath(iii, red)
            c.nativeCanvas.restore()
        }
    }
}
