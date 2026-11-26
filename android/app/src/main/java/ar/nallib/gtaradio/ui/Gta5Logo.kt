package ar.nallib.gtaradio.ui

import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
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

/** Alto de "gta" respecto del número (V, VI). */
private const val GTA_FRAC = 0.6f

/**
 * "gta V": "gta" en Pricedown (minúscula, como en los logos), blanca con contorno negro, y la V del logo oficial (la imagen
 * images/V/gtav-logo-png.webp: V verde con patrón de billete y la cinta "FIVE"; no se puede armar con una fuente).
 * "gta" mide la mitad del alto de la V y va centrada a la altura de la cinta.
 */
@Composable
fun Gta5Logo(gtaHeight: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) =
    ImageTitleLogo(R.drawable.logo_v, android.graphics.Color.BLACK, 0.47f, gtaHeight, modifier)

/**
 * "gta VI": "gta" en Pricedown blanca con contorno violeta oscuro (el del borde del logo) y el "VI" oficial (palmeras sobre
 * el atardecer; images/VI/official-gta-vi-logo.png achicada a 600 px de alto, sin el ™).
 */
@Composable
fun Gta6Logo(gtaHeight: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) =
    ImageTitleLogo(R.drawable.logo_vi, 0xFF340056.toInt(), 0.5f, gtaHeight, modifier)

/**
 * "gta" (Pricedown, blanca con contorno [outline]) a la izquierda de la imagen del número; [center] = altura relativa.
 * [gtaHeight] = alto de "gta" (el mismo en IV, V y VI); el número mide 1/GTA_FRAC de eso.
 */
@Composable
private fun ImageTitleLogo(image: Int, outlineColor: Int, center: Float, gtaHeight: androidx.compose.ui.unit.Dp, modifier: Modifier) {
    val context = LocalContext.current
    val logo = remember(image) { geometry(context, image, outlineColor, center) }
    val (box, draw) = logo
    Canvas(modifier.height(gtaHeight / GTA_FRAC).aspectRatio(box.width() / box.height())) {
        drawIntoCanvas { c ->
            val n = c.nativeCanvas
            val k = minOf(size.width / box.width(), size.height / box.height())
            n.save(); n.scale(k, k); draw(n); n.restore()
        }
    }
}

/** Ancho del logo por cada unidad de alto de "gta" (para que IV, V y VI compartan el alto de "gta" y quepan). */
fun gta5WidthPerGta(context: android.content.Context) = widthPerGta(geometry(context, R.drawable.logo_v, android.graphics.Color.BLACK, 0.47f).first)
fun gta6WidthPerGta(context: android.content.Context) = widthPerGta(geometry(context, R.drawable.logo_vi, 0xFF340056.toInt(), 0.5f).first)
private fun widthPerGta(box: RectF) = box.width() / box.height() / GTA_FRAC

private val geometries = mutableMapOf<Int, Pair<RectF, (android.graphics.Canvas) -> Unit>>()

private fun geometry(context: android.content.Context, image: Int, outlineColor: Int, center: Float) = geometries.getOrPut(image) {
    run {
        val v = BitmapFactory.decodeResource(context.resources, image, BitmapFactory.Options().apply { inScaled = false })
        val vh = v.height.toFloat()
        val font = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = ResourcesCompat.getFont(context, R.font.iii_title); textSize = 400f }
        val gta = Path().also { font.getTextPath("gta", 0, 3, 0f, 0f, it) }
        val gb = RectF().also { gta.computeBounds(it, true) }
        val s = vh * GTA_FRAC / gb.height()
        val stroke = vh * 0.03f                         // como el contorno negro de la V
        val cy = vh * center                            // altura a la que va "gta" (V: la cinta "FIVE")
        gta.transform(android.graphics.Matrix().apply {
            setTranslate(-gb.left, -gb.top); postScale(s, s); postTranslate(stroke, cy - gb.height() * s / 2)
        })
        val gw = gb.width() * s + stroke * 2
        val vx = gw + vh * 0.06f                        // aire entre "gta" y la V
        val box = RectF(0f, 0f, vx + v.width, vh)
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = outlineColor; style = Paint.Style.FILL_AND_STROKE; strokeWidth = stroke * 2; strokeJoin = Paint.Join.ROUND
        }
        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF5F5F5.toInt() }
        val img = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        box to { n: android.graphics.Canvas ->
            n.drawPath(gta, outline); n.drawPath(gta, white); n.drawBitmap(v, vx, 0f, img)
        }
    }
}
