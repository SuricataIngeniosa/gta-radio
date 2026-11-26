package ar.nallib.gtaradio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.nallib.gtaradio.R
import ar.nallib.gtaradio.data.Game

/**
 * Estética de cada juego. title = color del título y del borde de la emisora elegida; accent = botones e íconos;
 * night = fondo; border = degradé de los bordes; wallpaper = imagen de fondo (null = sólo el degradé del cielo);
 * tiled = el wallpaper es un patrón que se repite (VC) o una ilustración que se recorta para llenar la pantalla (III).
 */
data class GameStyle(
    val title: Color,
    val accent: Color,
    val night: Color,
    val nightHigh: Color,
    val text: Color,
    val textDim: Color,
    val border: Brush,
    val sky: Brush,
    val titleFont: FontFamily,
    val menuFont: FontFamily,
    val wallpaper: Int?,
    /** Wallpaper para la pantalla horizontal (null = el de vertical, recortado). */
    val wallpaperLand: Int? = null,
    val tiled: Boolean = true,
    /** Contorno de los botones de emisora (null = sin contorno salvo la emisora elegida). */
    val tileOutline: Color? = null,
    /** Color del número romano del título ("GTA III" con el III en rojo); null = todo en el color del título. */
    val numeral: Color? = null,
    /** Cómo se eligen las emisoras: grilla (la mayoría) o la rueda de radio de GTA V. */
    val layout: StationLayout = StationLayout.GRID,
    /** Esquinas de los botones de emisora (SA cuadrados, IV casi rectos, VI muy redondeados). */
    val tileCorner: Dp = 16.dp,
    /** Con una emisora puesta, su logo grande arriba y las demás en dos filas que se scrollean (IV y VI, vertical). */
    val bigNowPlaying: Boolean = false,
    val tileOutlineWidth: Dp = 2.dp,
    /**
     * Título como el logo del juego, en tres capas: relleno, contorno grueso y sombra corrida (VC: blanco, contorno
     * rosa #F275F5 y sombra negra, los colores del logo de Vice City). null = título de un color con resplandor.
     */
    val titleOutline: Color? = null,
    val titleFill: Color = Color.White,
    val titleShadow: Color = Color.Black,
    /** Título con degradé (GTA VI); null = color plano. */
    val titleBrush: Brush? = null,
    /** Panel de lo que suena: tarjeta, texto suelto estilo HUD del juego o vidrio. */
    val nowPlaying: NowPlayingStyle = NowPlayingStyle.CARD,
    /** Alineación del texto del panel (IV: a la derecha, como el nombre de la radio en el HUD). */
    val nowPlayingEnd: Boolean = false,
    /** La fuente de menús tiene negrita propia (además de la del sistema): los títulos van en Bold. */
    val menuBold: Boolean = false,
    /** Separación entre el título y "RADIO" (V: más abajo, apartado del logo). */
    val radioGap: Dp = 0.dp,
    /** Tamaño de "RADIO" bajo el título. */
    val radioSize: androidx.compose.ui.unit.TextUnit = 14.sp,
    /** Título y nombre de la emisora en mayúsculas (IV y V). */
    val upper: Boolean = false,
    /** Ilustración cuadrada al ancho de la pantalla, apoyada abajo; arriba se estira su cielo (skyFill). SA. */
    val bottomArt: Boolean = false,
    val skyFill: Color = Color.Black,
    /** Fondo de la fila del juego en el selector (null = el cielo del juego). */
    val pickerBg: Brush? = null,
    /** Opacidad del velo oscuro sobre el wallpaper (arriba, al 60 % y abajo). */
    val veilAlpha: Triple<Float, Float, Float> = Triple(0.45f, 0.6f, 0.9f),
) {
    /** Velo sobre el wallpaper: deja ver el patrón arriba y oscurece abajo, donde va el texto. */
    val veil = Brush.verticalGradient(0f to night.copy(alpha = veilAlpha.first), 0.6f to night.copy(alpha = veilAlpha.second),
        1f to night.copy(alpha = veilAlpha.third))
}

enum class StationLayout { GRID, WHEEL, POSTER, PANEL }
enum class NowPlayingStyle { CARD, HUD, GLASS }

/** Vice City: noche de Miami, neón rosa y celeste, atardecer naranja. */
val ViceStyle = run {
    val night = Color(0xFF140A26); val nightHigh = Color(0xFF2A1450); val pink = Color(0xFFFF4FA3); val sunset = Color(0xFFFF9E57)
    GameStyle(
        title = pink, accent = Color(0xFF3DE0F2), night = night, nightHigh = nightHigh,
        text = Color(0xFFFCE7F3), textDim = Color(0xFFC9A9D6),
        border = Brush.horizontalGradient(listOf(pink, sunset)),
        sky = Brush.verticalGradient(0f to night, 0.55f to nightHigh, 0.85f to Color(0xFF7A1E6E), 1f to Color(0xFFE0566A)),
        titleFont = FontFamily(Font(R.font.vc_title)), // Rage Italic
        menuFont = FontFamily(Font(R.font.vc_menu)),   // SF Arborcrest Heavy, un solo peso
        wallpaper = R.drawable.wallpaper_vc,
        titleOutline = Color(0xFFF275F5), radioGap = 8.dp,   // RADIO un poco más abajo: no toca la sombra del título
        tileOutline = pink,
    )
}

/** GTA III: fondo negro, blanco y ámbar de farola; Pricedown. */
val LibertyStyle = run {
    val night = Color.Black; val nightHigh = Color(0xFF23262D); val amber = Color(0xFFE8B04B)
    GameStyle(
        title = Color(0xFFF2F2F2), accent = amber, night = night, nightHigh = nightHigh,
        text = Color(0xFFEDEDED), textDim = Color(0xFFA7ADB5),
        border = Brush.horizontalGradient(listOf(Color(0xFFF2F2F2), amber)),
        sky = androidx.compose.ui.graphics.SolidColor(Color.Black),
        titleFont = FontFamily(Font(R.font.iii_title)), // Pricedown Bl (Typodermic, gratis)
        menuFont = FontFamily(Font(R.font.iii_title)),
        wallpaper = null,
        numeral = Color(0xFF9B1F17), // el rojo del logo de GTA III
        radioSize = 30.sp,
    )
}

// SA, IV, V y VI: PROVISORIOS (colores aproximados, Pricedown o fuente del sistema, sin wallpaper) hasta que lleguen
// las fuentes y las imágenes de cada juego. Para completar uno: fuentes en res/font, wallpaper en res/drawable-nodpi,
// logos en assets/logos/<JUEGO>/<ARCHIVO>.png.

/**
 * San Andreas: póster. La ilustración de la portada abajo (la chica de Vinewood), el cielo crema estirado hacia arriba,
 * "San Andreas" en Diploma (la letra del logo) blanco con contorno negro y el resto en Bank Gothic (la de los menús).
 * Los logos flotan como calcomanías con el trazo negro de la ilustración, en L alrededor de ella. El nombre de la radio
 * en dorado con contorno negro, como al cambiar de emisora en el juego. Los paneles (ajustes, juegos) en negro como el menú.
 */
val SanAndreasStyle = run {
    val night = Color.Black; val nightHigh = Color(0xFF15181C); val gold = Color(0xFFE1B451); val menuBlue = Color(0xFFACCBF1)
    GameStyle(
        title = Color.White, accent = gold, night = night, nightHigh = nightHigh,
        text = menuBlue, textDim = Color(0xFF7F95B0),
        border = androidx.compose.ui.graphics.SolidColor(Color(0xFF3A3F45)),
        sky = Brush.verticalGradient(0f to night, 1f to nightHigh),
        titleFont = FontFamily(Font(R.font.sa_title)),   // Diploma
        menuFont = FontFamily(Font(R.font.sa_menu)),     // Bank Gothic Md BT
        wallpaper = R.drawable.wallpaper_sa, bottomArt = true, skyFill = Color(0xFFFFF9C7),
        tileCorner = 0.dp, layout = StationLayout.POSTER,
        nowPlaying = NowPlayingStyle.HUD,
    )
}

/**
 * GTA IV: menú azul noche de Liberty City (degradé de azul pizarra a casi negro), botones casi rectos con filo blanco
 * fino, texto blanco en mayúsculas y el nombre de la radio a la derecha, como en el HUD del juego.
 */
val LibertyIVStyle = run {
    val night = Color(0xFF070B10); val nightHigh = Color(0xFF1B2A3A); val ice = Color(0xFF9CC3E6)
    GameStyle(
        title = Color.White, accent = ice, night = night, nightHigh = nightHigh,
        text = Color(0xFFEFF3F7), textDim = Color(0xFF8E9BA8),
        border = androidx.compose.ui.graphics.SolidColor(Color.White.copy(alpha = 0.35f)),
        sky = Brush.verticalGradient(0f to nightHigh, 0.55f to Color(0xFF0E1621), 1f to night),
        titleFont = FontFamily(Font(R.font.iii_title)),
        // Texto en Helvetica Neue (la de los menús de GTA IV); el logo no la usa.
        menuFont = FontFamily(Font(R.font.iv_roman, FontWeight.Normal), Font(R.font.iv_medium, FontWeight.Medium), Font(R.font.iv_bold, FontWeight.Bold)),
        menuBold = true,
        tileOutline = Color.White.copy(alpha = 0.25f), tileOutlineWidth = 1.dp, tileCorner = 2.dp, bigNowPlaying = true,
        nowPlaying = NowPlayingStyle.HUD, nowPlayingEnd = true, upper = true, layout = StationLayout.PANEL,
        // Algonquin con la Rotterdam Tower (el "Empire State") centrada (images/IV/gtaiv-wallpaper.webp, x 1306–2044);
        // es muy claro: velo oscuro para que se lean el logo, los íconos y el texto.
        wallpaper = R.drawable.wallpaper_iv, wallpaperLand = R.drawable.wallpaper_iv_land, tiled = false, veilAlpha = Triple(0.55f, 0.6f, 0.85f),
    )
}

/**
 * GTA V: la rueda de radio. Fondo oscuro, las emisoras en círculo (la elegida más grande con aro blanco) y en el
 * centro emisora, tema y artista en blanco. Verde de la plata como acento.
 */
val LosSantosStyle = run {
    val night = Color(0xFF050505); val nightHigh = Color(0xFF1E2621)
    GameStyle(
        // Acento blanco (como la interfaz del juego): el verde no se leía sobre el atardecer.
        title = Color.White, accent = Color.White, night = night, nightHigh = nightHigh,
        text = Color.White, textDim = Color(0xFFB4B4B4),
        border = androidx.compose.ui.graphics.SolidColor(Color.White.copy(alpha = 0.3f)),
        sky = Brush.radialGradient(listOf(nightHigh, night)),
        titleFont = FontFamily(Font(R.font.iii_title)), menuFont = FontFamily.Default,
        // Atardecer en el desierto de Blaine County (images/V/toma-que-quiero.jpg, con el cielo continuado hacia arriba
        // para la pantalla alargada); velo suave: se ve el atardecer y abajo se oscurece un poco para los controles.
        wallpaper = R.drawable.wallpaper_v, wallpaperLand = R.drawable.wallpaper_v_land, tiled = false, veilAlpha = Triple(0.25f, 0.15f, 0.5f),
        // En el selector, los colores del atardecer del wallpaper (medidos en la imagen): cielo, sol, horizonte, desierto.
        pickerBg = Brush.verticalGradient(0f to Color(0xFFB8673B), 0.45f to Color(0xFFFDA55D), 0.75f to Color(0xFFEDBE82), 1f to Color(0xFF754A45)),
        layout = StationLayout.WHEEL, upper = true, radioGap = 14.dp,
    )
}

/** GTA VI: atardecer de Leonida; título con degradé rosa-naranja, botones y panel de vidrio esmerilado. */
val LeonidaStyle = run {
    val night = Color(0xFF1A0B2E); val nightHigh = Color(0xFF3B1550); val pink = Color(0xFFFF5FA2); val orange = Color(0xFFFFB347)
    GameStyle(
        title = pink, accent = orange, night = night, nightHigh = nightHigh,
        text = Color(0xFFFFEAF3), textDim = Color(0xFFD0A9C4),
        border = Brush.horizontalGradient(listOf(pink, orange)),
        sky = Brush.verticalGradient(0f to night, 0.55f to nightHigh, 0.85f to Color(0xFFB0357A), 1f to orange),
        titleFont = FontFamily.Default, menuFont = FontFamily.Default,
        wallpaper = null,
        titleBrush = Brush.horizontalGradient(listOf(Color(0xFFFF4FA0), Color(0xFFFF8A5B), Color(0xFFFFC46B))),
        tileOutline = Color.White.copy(alpha = 0.22f), tileOutlineWidth = 1.dp, tileCorner = 6.dp, bigNowPlaying = true,
        nowPlaying = NowPlayingStyle.GLASS, radioGap = 14.dp,
    )
}

fun styleOf(game: Game) = when (game) {
    Game.VICE_CITY -> ViceStyle
    Game.GTA_III -> LibertyStyle
    Game.SAN_ANDREAS -> SanAndreasStyle
    Game.GTA_IV -> LibertyIVStyle
    Game.GTA_V -> LosSantosStyle
    Game.GTA_VI -> LeonidaStyle
}

val LocalStyle = staticCompositionLocalOf { ViceStyle }

/** Wallpaper en mosaico (el patrón se repite sin costuras) + velo oscuro; sin wallpaper, el degradé del cielo. */
@Composable
fun GameBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val st = LocalStyle.current
    val land = androidx.compose.ui.platform.LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val wp = if (land && st.wallpaperLand != null) st.wallpaperLand else st.wallpaper
    when {
        wp == null -> Box(modifier.background(st.sky)) { Provide(content) }
        st.bottomArt -> Box(modifier.background(st.skyFill)) {
            // Vertical: al ancho, apoyada abajo. Horizontal: a todo el alto, pegada a la derecha.
            androidx.compose.foundation.Image(
                androidx.compose.ui.res.painterResource(wp), null,
                if (land) Modifier.fillMaxHeight().aspectRatio(1f).align(androidx.compose.ui.Alignment.BottomEnd)
                else Modifier.fillMaxWidth().aspectRatio(1f).align(androidx.compose.ui.Alignment.BottomCenter),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            )
            Provide(content)
        }
        st.tiled -> {
            val img = ImageBitmap.imageResource(wp)
            val tiles = remember(img) { ShaderBrush(ImageShader(img, TileMode.Repeated, TileMode.Repeated)) }
            Box(modifier.background(tiles).background(st.veil)) { Provide(content) }
        }
        else -> Box(modifier.background(st.night)) {
            androidx.compose.foundation.Image(
                androidx.compose.ui.res.painterResource(wp), null, Modifier.matchParentSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
            Box(Modifier.matchParentSize().background(st.veil))
            Provide(content)
        }
    }
}

@Composable
private fun Provide(content: @Composable () -> Unit) {
    val st = LocalStyle.current
    CompositionLocalProvider(LocalContentColor provides st.text) { content() }
}

@Composable
fun GameTheme(game: Game, content: @Composable () -> Unit) {
    val st = styleOf(game)
    val base = Typography()
    fun TextStyle.menu() = copy(fontFamily = st.menuFont)
    // Con la fuente del sistema la negrita es real; las fuentes de VC tienen un solo peso y no se engrosan.
    val bold = if (st.menuFont == FontFamily.Default || st.menuBold) FontWeight.Bold else null
    CompositionLocalProvider(LocalStyle provides st) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = st.title, onPrimary = st.night, secondary = st.accent, onSecondary = st.night,
                background = st.night, surface = st.night, onSurface = st.text,
                onSurfaceVariant = st.textDim, surfaceVariant = st.nightHigh, outline = st.title,
            ),
            typography = Typography(
                displaySmall = TextStyle(fontFamily = st.titleFont, fontWeight = bold, fontSize = 56.sp, color = st.title),
                titleLarge = base.titleLarge.menu().copy(fontWeight = bold), titleMedium = base.titleMedium.menu().copy(fontWeight = bold),
                bodyLarge = base.bodyLarge.menu(), bodyMedium = base.bodyMedium.menu(), bodySmall = base.bodySmall.menu(),
                labelLarge = base.labelLarge.menu(), labelMedium = base.labelMedium.menu(),
            ),
            content = content,
        )
    }
}
