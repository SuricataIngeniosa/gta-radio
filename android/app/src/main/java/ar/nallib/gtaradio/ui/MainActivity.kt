package ar.nallib.gtaradio.ui

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import ar.nallib.gtaradio.R
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import ar.nallib.gtaradio.data.Artwork
import ar.nallib.gtaradio.data.Game
import ar.nallib.gtaradio.data.Prefs
import ar.nallib.gtaradio.data.Station
import ar.nallib.gtaradio.data.Stations
import ar.nallib.gtaradio.data.Updates
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import ar.nallib.gtaradio.service.RadioPlaybackService
import com.google.common.util.concurrent.MoreExecutors

class MainActivity : ComponentActivity() {
    private var controller by mutableStateOf<MediaController?>(null)
    private var nowTitle by mutableStateOf<String?>(null)
    private var nowArtist by mutableStateOf<String?>(null)
    private var nowStation by mutableStateOf<String?>(null)
    private var playing by mutableStateOf(false)
    // Estado de los botones: se guarda acá (no se le pregunta al controller al dibujar) para que se redibujen al cambiar.
    private var hasItem by mutableStateOf(false)
    private var canSkip by mutableStateOf(false)

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh()
    }

    /** Versión nueva en GitHub Releases (se busca una vez al abrir la app). */
    private var update by mutableStateOf<ar.nallib.gtaradio.data.Release?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) lifecycleScope.launch { update = Updates.check(this@MainActivity) }
        // Notificaciones y lectura de las carpetas predefinidas (para los sistemas sin selector de archivos).
        requestPermissions(
            if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_IMAGES)
            else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 1,
        )
        val prefs = Prefs(this)
        setContent {
            var game by remember { mutableStateOf(prefs.game) }
            GameTheme(game) {
                val light = styleOf(game).bottomArt
                SideEffect { androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = light }
                // Deslizar a los costados cambia de juego (a la izquierda, el siguiente). La rueda de V consume su arrastre.
                val swipe = Modifier.pointerInput(game) {
                    var dx = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { dx = 0f },
                        onDragEnd = {
                            if (kotlin.math.abs(dx) > 96.dp.toPx()) {
                                val all = Game.entries
                                val i = (all.indexOf(game) + if (dx < 0) 1 else -1 + all.size) % all.size
                                game = switchGame(prefs, all[i])
                            }
                        },
                    ) { _, d -> dx += d }
                }
                GameBackground(Modifier.fillMaxSize().then(swipe)) {
                    // key: al cambiar de juego se rearma la pantalla con su carpeta y sus emisoras.
                    key(game) { Screen(prefs, onPickGame = { g -> if (g != game) game = switchGame(prefs, g) }) }
                }
                update?.let { UpdateDialog(it, onDismiss = { update = null }) }
            }
        }
    }

    /** Sube cuando se concede el permiso de almacenamiento: vuelve a buscar las carpetas predefinidas. */
    private var storageTick by mutableStateOf(0)

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        storageTick++
    }

    override fun onStart() {
        super.onStart()
        storageTick++
        val token = SessionToken(this, ComponentName(this, RadioPlaybackService::class.java))
        val f = MediaController.Builder(this, token).buildAsync()
        f.addListener({
            val c = f.get().also { it.addListener(listener) }
            controller = c
            // Al abrir la app sin nada cargado: la última emisora (de cualquier juego), lista para darle play.
            if (c.mediaItemCount == 0) Prefs(this).lastStation?.let { c.setMediaItem(MediaItem.Builder().setMediaId(it).build()); c.prepare() }
            refresh()
        }, MoreExecutors.directExecutor())
    }

    override fun onStop() {
        controller?.run { removeListener(listener); release() }
        controller = null
        super.onStop()
    }

    /** Ofrece la versión nueva: la baja y abre el instalador de Android, que pide confirmar. */
    @Composable
    private fun UpdateDialog(release: ar.nallib.gtaradio.data.Release, onDismiss: () -> Unit) {
        val scope = rememberCoroutineScope()
        var progress by remember { mutableStateOf<Float?>(null) }
        var message by remember { mutableStateOf<Int?>(null) }
        var installing by remember { mutableStateOf(false) }
        // Si Android rechaza la instalación, se vuelve a ofrecer con el error.
        val failed by Updates.failure.collectAsState()
        LaunchedEffect(failed) { if (failed != null) { installing = false; message = R.string.update_failed } }
        val install: () -> Unit = {
            if (!Updates.canInstall(this)) {
                message = R.string.update_permission
                startActivity(Updates.installPermissionIntent(this))
            } else {
                message = null; progress = 0f
                scope.launch {
                    runCatching {
                        val apk = Updates.download(this@MainActivity, release) { p -> progress = p }
                        installing = true
                        Updates.install(this@MainActivity, apk)
                    }.onFailure { message = R.string.update_failed; installing = false }
                    progress = null
                }
            }
        }
        AlertDialog(
            onDismissRequest = { if (progress == null && !installing) onDismiss() },
            title = { Text(stringResource(R.string.update_title, release.version)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(when {
                        progress != null -> stringResource(R.string.update_downloading)
                        installing -> stringResource(R.string.update_installing)
                        else -> stringResource(message ?: R.string.update_text)
                    })
                    if (installing) LinearProgressIndicator(Modifier.fillMaxWidth())
                    progress?.let { p ->
                        if (p < 0f) LinearProgressIndicator(Modifier.fillMaxWidth())
                        else LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = { TextButton(onClick = install, enabled = progress == null && !installing) { Text(stringResource(R.string.update_install)) } },
            dismissButton = { TextButton(onClick = onDismiss, enabled = progress == null && !installing) { Text(stringResource(R.string.update_later)) } },
        )
    }

    private fun refresh() {
        val c = controller ?: return
        nowTitle = c.mediaMetadata.title?.toString()
        nowArtist = c.mediaMetadata.artist?.toString()
        nowStation = c.currentMediaItem?.mediaId?.substringBefore('#')
        playing = c.isPlaying
        hasItem = c.currentMediaItem != null
        canSkip = c.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Screen(prefs: Prefs, onPickGame: (Game) -> Unit) {
        val st = LocalStyle.current
        val game = prefs.game
        val stations = remember(game) { Stations.of(game) }
        var folder by remember(storageTick) { mutableStateOf(prefs.folderUri) }
        var noPicker by remember { mutableStateOf(false) }
        var live by remember { mutableStateOf(prefs.liveMode) }
        var showSettings by remember { mutableStateOf(prefs.folderUri == null) }
        var showGames by remember { mutableStateOf(false) }
        // Recorrer las carpetas de las emisoras (SAF) es lento: en segundo plano; mientras, la pantalla se dibuja igual.
        val library by produceState(ar.nallib.gtaradio.data.Library(), folder, game, storageTick) {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { prefs.resolveFiles() }
        }
        val found = library.tracks.filterValues { it.isNotEmpty() }
        val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                prefs.folderUri = uri; folder = uri
            }
        }

        // Horizontal (pantalla del auto, BYD Dolphin Mini girada) o vertical: cada diseño tiene las dos versiones.
        val land = androidx.compose.ui.platform.LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val header = @Composable { m: Modifier -> Header(game, m, onSettings = { showSettings = true }, onGames = { showGames = true }) }
        val area = @Composable { m: Modifier -> StationsArea(stations, library, found.keys, land, m) }
        val bottom = @Composable { if (st.layout == StationLayout.WHEEL) Controls(big = land) else NowPlaying(big = land) }
        when {
            st.layout == StationLayout.POSTER && land ->
                SaPosterLand(game, stations, library.logos, found.keys, onSettings = { showSettings = true }, onGames = { showGames = true })
            st.layout == StationLayout.POSTER ->
                SaPoster(game, stations, library.logos, found.keys, onSettings = { showSettings = true }, onGames = { showGames = true })
            land -> Row(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                // Izquierda: logo, íconos y abajo lo que suena; derecha: las emisoras a todo el alto.
                Column(Modifier.weight(0.42f).fillMaxHeight()) {
                    header(Modifier.fillMaxWidth())
                    Spacer(Modifier.weight(1f))
                    bottom()
                }
                area(Modifier.weight(0.58f).fillMaxHeight())
            }
            else -> Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                header(Modifier.fillMaxWidth())
                area(Modifier.weight(1f).fillMaxWidth())
                if (st.layout == StationLayout.PANEL) Spacer(Modifier.height(2.dp))
                bottom()
            }
        }
        // Expulsar el disco: panel con los juegos, cada uno con su fuente y sus colores.
        if (showGames) {
            ModalBottomSheet(onDismissRequest = { showGames = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = st.night, contentColor = st.text) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Game.entries.forEach { g ->
                        val gs = styleOf(g)
                        val current = g == game
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).then(
                                when { gs.bottomArt -> Modifier.background(gs.skyFill); gs.pickerBg != null -> Modifier.background(gs.pickerBg)
                                    else -> Modifier.background(gs.sky) })
                                .border(if (current) 3.dp else 1.dp, if (current) st.accent else gs.title.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                                .clickable { showGames = false; onPickGame(g) }
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (g == Game.GTA_III || g == Game.GTA_IV || g == Game.GTA_V || g == Game.GTA_VI) {
                                Box(Modifier.weight(1f)) {
                                    when (g) {
                                        Game.GTA_III -> Gta3Logo(Modifier.height(44.dp))
                                        Game.GTA_IV -> Gta4Logo(PICKER_GTA)
                                        Game.GTA_V -> Gta5Logo(PICKER_GTA)
                                        else -> Gta6Logo(PICKER_GTA)
                                    }
                                }
                            } else if (gs.titleOutline != null) {
                                LogoTitle(g.title, gs, Modifier.weight(1f), fixed = 34.sp)
                            } else if (gs.bottomArt) {
                                // SA: como en su pantalla, letras negras con borde blanco sobre el crema.
                                OutlinedText(g.title, androidx.compose.ui.text.TextStyle(fontFamily = gs.titleFont, fontSize = 34.sp),
                                    Color.Black, 8f, Color.White, Modifier.weight(1f))
                            } else androidx.compose.foundation.text.BasicText(
                                gameTitle(g, gs), Modifier.weight(1f), maxLines = 1, softWrap = false,
                                style = androidx.compose.ui.text.TextStyle(fontFamily = gs.titleFont, fontSize = 34.sp, color = gs.title,
                                    shadow = Shadow(gs.title.copy(alpha = 0.6f), blurRadius = 16f)),
                            )
                            // Disco a la derecha, en blanco: GTA VI lleva el disco tachado (no salió en disco).
                            // Disco blanco a la derecha, igual en todos los juegos; GTA VI lo lleva tachado (no salió en disco).
                            // Sobre fondos claros (SA, V), un círculo negro detrás rellena el centro y asoma un poco como borde
                            // (el disco ocupa 20 de 24 unidades del ícono: 34 dp → 28 dp; el círculo, 31 dp).
                            Box(contentAlignment = Alignment.Center) {
                                if (gs.bottomArt || gs.pickerBg != null) Box(Modifier.size(31.dp).background(Color.Black, androidx.compose.foundation.shape.CircleShape))
                                Icon(painterResource(if (g == Game.GTA_VI) R.drawable.ic_disc_off else R.drawable.ic_disc), null,
                                    Modifier.size(34.dp), tint = Color.White)
                            }
                        }
                    }
                }
            }
        }

        if (showSettings) {
            ModalBottomSheet(onDismissRequest = { showSettings = false }, containerColor = st.night, contentColor = st.text) {
                Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Text("Ajustes", style = MaterialTheme.typography.titleLarge, color = st.title)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (folder == null) "Elegí la carpeta del juego (una subcarpeta por emisora)" else "${found.size} de ${stations.size} emisoras encontradas",
                            Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = st.text,
                        )
                        // Algunos sistemas de auto (BYD DiLink) no tienen selector de carpetas: en vez de cerrarse, avisa.
                        OutlinedButton(onClick = { try { pick.launch(null) } catch (_: android.content.ActivityNotFoundException) { noPicker = true } }, border = androidx.compose.foundation.BorderStroke(1.dp, st.accent),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = st.accent)) { Text(if (folder == null) "Elegir carpeta" else "Cambiar") }
                    }
                    // De dónde salen los audios: la carpeta elegida o la predefinida que encontró sola.
                    Text(
                        when {
                            folder?.scheme == "file" -> "Carpeta: ${folder?.path}"
                            folder != null -> "Carpeta elegida con el selector"
                            else -> "No encontré la carpeta. Copiala a Music/${Prefs.dirName(game)} (memoria interna) o a la raíz de un pendrive."
                        } + if (noPicker) "\nEste sistema no tiene selector de archivos: se usa la carpeta predefinida." else "",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // Android 11+ sin "acceso a todos los archivos": no ve audios en carpetas con .nomedia o sin indexar.
                    if (Build.VERSION.SDK_INT >= 30 && !android.os.Environment.isExternalStorageManager() && folder?.scheme != "content") {
                        TextButton(onClick = {
                            val i = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, android.net.Uri.parse("package:$packageName"))
                            try { startActivity(i) } catch (_: android.content.ActivityNotFoundException) {
                                try { startActivity(Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) } catch (_: Exception) { noPicker = true }
                            }
                        }) { Text("Permitir acceso a todos los archivos", color = st.accent) }
                    }
                    if (prefs.isChosen()) TextButton(onClick = { prefs.folderUri = null; folder = prefs.folderUri }) {
                        Text("Usar la carpeta predefinida", color = st.accent)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Modo radio en vivo", color = st.text, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (live) "La emisora sigue sonando aunque no la escuches" else "Retoma donde la dejaste",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = live, onCheckedChange = { live = it; prefs.liveMode = it })
                    }
                }
            }
        }

    }

    /** Encabezado: logo o título del juego con "RADIO" y, a la derecha, ajustes y expulsar (cambiar de juego). */
    @Composable
    private fun Header(game: Game, modifier: Modifier, onSettings: () -> Unit, onGames: () -> Unit) {
        val st = LocalStyle.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            // "Vice City" ocupa todo el ancho hasta el engranaje; "RADIO" va debajo, alineado a la derecha.
            Column(Modifier.weight(1f)) {
                if (game == Game.GTA_III) Gta3Logo(Modifier.fillMaxWidth(0.92f).padding(vertical = 8.dp))
                // IV, V y VI: "gta" del mismo alto en los tres, el mayor con el que el más ancho llena el lugar; RADIO
                // va pegado al borde derecho del logo.
                else if (game == Game.GTA_IV || game == Game.GTA_V || game == Game.GTA_VI) BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    val widest = remember { maxOf(gta4WidthPerGta(ctx), gta5WidthPerGta(ctx), gta6WidthPerGta(ctx)) }
                    val gtaH = minOf(maxWidth / widest, 72.dp)
                    Column(Modifier.width(IntrinsicSize.Max)) {
                        when (game) {
                            Game.GTA_IV -> Gta4Logo(gtaH, Modifier.padding(vertical = 6.dp))
                            Game.GTA_V -> Gta5Logo(gtaH, Modifier.padding(vertical = 4.dp))
                            else -> Gta6Logo(gtaH, Modifier.padding(vertical = 4.dp))
                        }
                        Text("RADIO", style = MaterialTheme.typography.labelLarge.copy(fontSize = st.radioSize), color = st.accent,
                            letterSpacing = st.radioSize * 0.45f, modifier = Modifier.align(Alignment.End).padding(top = st.radioGap))
                    }
                }
                else if (st.titleOutline != null) LogoTitle(game.title, st, Modifier.fillMaxWidth(), maxSize = 160.sp)
                else androidx.compose.foundation.text.BasicText(
                    gameTitle(game, st).let { if (st.upper) androidx.compose.ui.text.AnnotatedString(it.text.uppercase(), it.spanStyles) else it }, maxLines = 1, softWrap = false,
                    style = MaterialTheme.typography.displaySmall.let { ts ->
                        if (st.titleBrush != null) ts.copy(brush = st.titleBrush, shadow = Shadow(Color(0x66FF4FA0), blurRadius = 28f))
                        else ts.copy(color = st.title, shadow = Shadow(st.title.copy(alpha = 0.8f), blurRadius = 24f))
                    },
                    autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 40.sp, maxFontSize = 160.sp, stepSize = 2.sp),
                )
                if (game != Game.GTA_IV && game != Game.GTA_V && game != Game.GTA_VI)
                Text("RADIO", style = MaterialTheme.typography.labelLarge.copy(fontSize = st.radioSize), color = st.accent,
                    letterSpacing = st.radioSize * 0.45f,
                    modifier = Modifier.align(Alignment.End).padding(end = 8.dp, top = st.radioGap))
            }
            // Columna a la derecha: ajustes arriba, expulsar el disco (pasa al otro juego) debajo, separados del título.
            Spacer(Modifier.width(20.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                IconButton(onClick = onSettings) {
                    Icon(painterResource(R.drawable.ic_settings), "Ajustes", Modifier.size(28.dp), tint = st.accent)
                }
                IconButton(onClick = onGames) {
                    Icon(painterResource(R.drawable.ic_eject), "Cambiar de juego", Modifier.size(30.dp), tint = st.accent)
                }
            }
        }
    }

    /**
     * Las emisoras, sin scroll, en el lugar disponible. Rueda (V); grilla con la cantidad de columnas que deja los botones
     * más grandes (en vertical, IV va fijo en 4 de ancho y apoyado abajo para que se vea el wallpaper arriba).
     */
    @Composable
    private fun StationsArea(stations: List<Station>, library: ar.nallib.gtaradio.data.Library, found: Set<String>, land: Boolean, modifier: Modifier) {
        val st = LocalStyle.current
        if (st.layout == StationLayout.WHEEL) {
            Box(modifier, contentAlignment = Alignment.Center) { Wheel(stations, library.logos, found, onPick = { play(it) }) }
            return
        }
        val ivPortrait = st.layout == StationLayout.PANEL && !land
        BoxWithConstraints(modifier, contentAlignment = if (ivPortrait) Alignment.BottomCenter else Alignment.Center) {
            // Las secretas van al final, en la última celda, con al menos un hueco entre ellas y las demás.
            val (secret, regular) = stations.partition { it.secret }
            val n = (regular.size + if (secret.isEmpty()) 0 else secret.size + 1).coerceAtLeast(1)
            val gap = 8.dp
            fun tileFor(c: Int): androidx.compose.ui.unit.Dp {
                val r = (n + c - 1) / c
                return minOf((maxWidth - gap * (c - 1)) / c, (maxHeight - gap * (r - 1)) / r)
            }
            val cols = if (ivPortrait) 4 else (1..n).maxBy { tileFor(it) }
            val tile = tileFor(cols)
            val rows = (n + cols - 1) / cols
            val prefs = remember { Prefs(this@MainActivity) }
            var unlocked by remember { mutableStateOf(setOf<String>()) }
            var guessing by remember { mutableStateOf<Station?>(null) }
            guessing?.let { s ->
                SecretDialog(s, onDismiss = { guessing = null }) {
                    prefs.unlock(s); unlocked = unlocked + s.id; guessing = null
                    if (s.id in found) play(s)
                }
            }
            val cells: List<Station?> = regular + List(rows * cols - regular.size - secret.size) { null } + secret
            val grid = @Composable { m: Modifier ->
                Column(m, verticalArrangement = Arrangement.spacedBy(gap)) {
                    cells.chunked(cols).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                            row.forEach { s ->
                                if (s == null) Spacer(Modifier.width(tile))
                                else if (s.id !in unlocked && !prefs.isUnlocked(s)) Box(Modifier.width(tile)) {
                                    SecretTile(s) { guessing = s }
                                }
                                else Box(Modifier.width(tile)) {
                                    StationTile(s, library.logos[s.id], enabled = s.id in found, selected = s.id == nowStation) { play(s) }
                                }
                            }
                        }
                    }
                }
            }
            // GTA VI en vertical, con una emisora del juego puesta: su logo grande arriba (lo que ocupaban las primeras
            // filas) y abajo dos filas de emisoras que se scrollean.
            val playingHere = stations.firstOrNull { it.id == nowStation }
            val stripH = tile * 2 + gap
            val boxH = maxHeight
            val bigTarget = minOf(maxWidth, maxHeight - stripH - gap)
            val big by androidx.compose.animation.core.animateDpAsState(
                if (st.bigNowPlaying && !land && playingHere != null && rows > 2) bigTarget else 0.dp, label = "big")
            if (big > 0.dp || (st.bigNowPlaying && !land && playingHere != null && rows > 2)) Column(Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(gap)) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    playingHere?.let { s -> Box(Modifier.size(big)) { StationTile(s, library.logos[s.id], enabled = true, selected = false, tappable = false) {} } }
                }
                grid(Modifier.height(maxOf(stripH, boxH - big - gap)).verticalScroll(rememberScrollState()))
            }
            else grid(Modifier)
        }
    }

    /** Emisora secreta sin adivinar: un "?" sobre el color de la emisora. */
    @Composable
    private fun SecretTile(s: Station, onClick: () -> Unit) {
        val st = LocalStyle.current
        val shape = RoundedCornerShape(st.tileCorner)
        Box(Modifier.aspectRatio(1f).clip(shape).background(Color(s.color.toInt())).then(
            st.tileOutline?.let { Modifier.border(st.tileOutlineWidth, it, shape) } ?: Modifier).clickable(onClick = onClick),
            contentAlignment = Alignment.Center) {
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("?", color = st.accent, fontSize = with(androidx.compose.ui.platform.LocalDensity.current) { (maxWidth * 0.6f).toSp() },
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Black)
            }
        }
    }

    /** Pide el nombre de la emisora secreta; sin distinguir mayúsculas ni espacios de más. */
    @Composable
    private fun SecretDialog(s: Station, onDismiss: () -> Unit, onUnlock: () -> Unit) {
        var text by remember { mutableStateOf("") }
        var wrong by remember { mutableStateOf(false) }
        val submit = {
            val guess = text.trim().lowercase().replace(Regex("\\s+"), " ")
            if (guess in s.answers.orEmpty()) onUnlock() else wrong = true
        }
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.secret_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.secret_prompt))
                    OutlinedTextField(text, { text = it; wrong = false }, singleLine = true, isError = wrong,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { submit() }))
                    if (wrong) Text(stringResource(R.string.secret_wrong), color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = { TextButton(onClick = submit) { Text(stringResource(R.string.secret_enter)) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.secret_cancel)) } },
        )
    }

    @Composable
    private fun StationTile(
        s: Station, logo: android.net.Uri?, enabled: Boolean, selected: Boolean,
        shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(LocalStyle.current.tileCorner), tappable: Boolean = true,
        onClick: () -> Unit,
    ) {
        val st = LocalStyle.current
        val art = rememberArt(s, logo) ?: return
        Column(
            Modifier.alpha(if (enabled) 1f else 0.3f).then(if (tappable) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Elegida: contorno grueso en el color de acento; si el juego usa contorno, las demás lo llevan fino.
            val outline = when {
                selected -> Modifier.border(if (st.layout == StationLayout.WHEEL) 3.dp else 4.dp,
                    if (st.layout == StationLayout.WHEEL) Color.White else st.accent, shape)
                st.tileOutline != null -> Modifier.border(st.tileOutlineWidth, st.tileOutline, shape)
                else -> Modifier
            }
            Image(art, s.name, Modifier.aspectRatio(1f).clip(shape).then(outline))
        }
    }

    @Composable
    private fun NowPlaying(big: Boolean = false) {
        val st = LocalStyle.current
        val stationName = Stations[nowStation]?.name?.let { if (st.upper) it.uppercase() else it }
        when (st.nowPlaying) {
            // Como el juego: el nombre de la radio grande (SA: dorado con contorno; IV: blanco a la derecha), sin tarjeta.
            NowPlayingStyle.HUD -> Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalAlignment = if (st.nowPlayingEnd) Alignment.End else Alignment.CenterHorizontally) {
                val outline = Shadow(Color.Black, offset = androidx.compose.ui.geometry.Offset(2f, 2f), blurRadius = 2f)
                // Con la fuente del texto (Helvetica Neue en IV), no la del logo.
                // El nombre entero: se achica (hasta 16 sp) si no entra.
                if (stationName != null) androidx.compose.foundation.text.BasicText(stationName, maxLines = 1, softWrap = false,
                    style = MaterialTheme.typography.titleLarge.copy(shadow = outline, color = st.accent),
                    autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 16.sp, maxFontSize = if (big) 52.sp else 30.sp, stepSize = 1.sp))
                FitText(nowTitle ?: "Tocá una emisora", MaterialTheme.typography.titleMedium.copy(shadow = outline, color = st.text), big, 44.sp)
                FitText(nowArtist ?: "", MaterialTheme.typography.bodyMedium.copy(color = st.textDim), big, 34.sp)
                Controls(big = big)
            }
            else -> {
                val glass = st.nowPlaying == NowPlayingStyle.GLASS
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(if (glass) 28.dp else 12.dp),
                    colors = CardDefaults.cardColors(containerColor = if (glass) Color.White.copy(alpha = 0.12f) else st.night.copy(alpha = 0.75f), contentColor = st.text),
                    border = if (glass) androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
                    else androidx.compose.foundation.BorderStroke(1.dp, st.border)) {
                    Column(Modifier.padding(if (big) 24.dp else 16.dp)) {
                        FitText(nowTitle ?: "Nada sonando", MaterialTheme.typography.titleMedium.copy(color = st.text), big, 44.sp)
                        FitText(nowArtist ?: "Tocá una emisora", MaterialTheme.typography.bodyMedium.copy(color = st.accent), big, 34.sp)
                        Controls(big = big)
                    }
                }
            }
        }
    }

    /**
     * San Andreas: póster sobre la ilustración (que GameBackground apoya abajo, al ancho de la pantalla: su borde de
     * arriba está a H − W). Los logos van en L alrededor de la chica: dos filas completas en el cielo (4 y 4) y tres a su
     * izquierda sobre la loma (el lugar de abajo a la derecha queda libre: ahí está su hombro), dejando ver el cartel de
     * Vinewood entre los dos grupos. Letras negras con borde blanco como el logo de SA; el nombre de la radio en dorado
     * con contorno negro, como en el juego. Controles abajo a la izquierda, sobre la ruta.
     */
    @Composable
    private fun SaPoster(
        game: Game, stations: List<Station>, logos: Map<String, android.net.Uri>, found: Set<String>,
        onSettings: () -> Unit, onGames: () -> Unit,
    ) {
        val st = LocalStyle.current
        val ins = WindowInsets.safeDrawing.asPaddingValues()
        val top = ins.calculateTopPadding()
        val bottom = ins.calculateBottomPadding()
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val w = maxWidth; val h = maxHeight
            val art = h - w                       // borde de arriba de la ilustración
            val x0 = w * 0.045f
            val gap = w * 0.03f
            val headerBottom = top + 150.dp
            // Logos lo más grandes posible: 4 por fila de borde a borde, sin chocar con el título y el panel de la radio.
            val l = minOf((w - x0 * 2 - gap * 3) / 4, ((art + w * 0.10f) - (headerBottom + 100.dp) - gap * 2) / 2)
            val row2 = art + w * 0.10f - l        // la fila de abajo apoya justo sobre el horizonte (antes del cartel)
            val row1 = row2 - l - gap
            val block = art + w * 0.24f           // bloque 2×2 sobre la loma, debajo del cartel
            val slots = buildList {
                for (i in 0 until 4) add(x0 + (l + gap) * i to row1)
                for (i in 0 until 4) add(x0 + (l + gap) * i to row2)
                add(x0 to block); add(x0 + l + gap to block); add(x0 to block + l + gap)
            }

            SaTitle(game, Modifier.padding(start = x0, top = top + 4.dp, end = 60.dp), 120.sp)
            SaIcons(Modifier.align(Alignment.TopEnd).padding(top = top + 4.dp, end = 4.dp), onSettings, onGames)
            // Lo que suena, entre el título y los logos.
            SaHud(Modifier.padding(start = x0, end = x0).offset(y = headerBottom).height(row1 - headerBottom - gap).fillMaxWidth())
            stations.zip(slots).forEach { (s, pos) -> SaLogo(s, logos[s.id], s.id in found, l, Modifier.offset(pos.first, pos.second)) }

            // Controles sobre la ruta, abajo a la izquierda (no tapan a la chica).
            CompositionLocalProvider(LocalContentColor provides Color(0xFF151515)) {
                Box(Modifier.align(Alignment.BottomStart).padding(start = x0 - 8.dp, bottom = bottom + 4.dp).width(w * 0.55f)) { Controls(st.accent) }
            }
        }
    }

    /**
     * San Andreas en horizontal (pantalla del auto girada): la ilustración a todo el alto pegada a la derecha (la dibuja
     * GameBackground) y, a su izquierda, sobre el cielo crema y la loma, una columna con el título, los logos en grilla,
     * lo que suena y los controles; los íconos arriba a la derecha, sobre el cielo de la ilustración.
     */
    @Composable
    private fun SaPosterLand(
        game: Game, stations: List<Station>, logos: Map<String, android.net.Uri>, found: Set<String>,
        onSettings: () -> Unit, onGames: () -> Unit,
    ) {
        val st = LocalStyle.current
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val w = maxWidth; val h = maxHeight
            // La ilustración es un cuadrado de alto h pegado a la derecha; la chica empieza al 46 % de su ancho.
            val colW = minOf(w * 0.62f, (w - h) + h * 0.42f)
            Column(Modifier.width(colW).fillMaxHeight().safeDrawingPadding().padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SaTitle(game, Modifier.fillMaxWidth().padding(end = 8.dp), 72.sp)
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val n = stations.size; val gap = 10.dp
                    fun tileFor(c: Int): androidx.compose.ui.unit.Dp {
                        val r = (n + c - 1) / c
                        return minOf((maxWidth - gap * (c - 1)) / c, (maxHeight - gap * (r - 1)) / r)
                    }
                    val cols = (1..n).maxBy { tileFor(it) }
                    val l = tileFor(cols)
                    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                        stations.chunked(cols).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(gap)) { row.forEach { s -> SaLogo(s, logos[s.id], s.id in found, l) } }
                        }
                    }
                }
                SaHud(Modifier.fillMaxWidth())
                CompositionLocalProvider(LocalContentColor provides Color(0xFF151515)) { Box(Modifier.fillMaxWidth(0.8f)) { Controls(st.accent) } }
            }
            SaIcons(Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(4.dp), onSettings, onGames)
        }
    }

    /** "San Andreas" en Diploma negro con borde blanco y "RADIO" debajo a la derecha (Bank Gothic). */
    @Composable
    private fun SaTitle(game: Game, modifier: Modifier, maxSize: androidx.compose.ui.unit.TextUnit) {
        val st = LocalStyle.current
        Column(modifier) {
            OutlinedText(game.title, androidx.compose.ui.text.TextStyle(fontFamily = st.titleFont), Color.Black, 12f, Color.White,
                modifier = Modifier.fillMaxWidth(), maxSize = maxSize)
            OutlinedText("RADIO", MaterialTheme.typography.labelLarge.copy(fontSize = 28.sp, letterSpacing = 14.sp), Color.Black, 9f, Color.White,
                modifier = Modifier.align(Alignment.End))
        }
    }

    @Composable
    private fun SaIcons(modifier: Modifier, onSettings: () -> Unit, onGames: () -> Unit) {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(onClick = onSettings) { Icon(painterResource(R.drawable.ic_settings), "Ajustes", Modifier.size(30.dp), tint = Color(0xFF1A1A1A)) }
            IconButton(onClick = onGames) { Icon(painterResource(R.drawable.ic_eject), "Cambiar de juego", Modifier.size(32.dp), tint = Color(0xFF1A1A1A)) }
        }
    }

    /** Lo que suena en SA: radio en dorado con contorno negro (como en el juego), tema y artista en negro con borde blanco. */
    @Composable
    private fun SaHud(modifier: Modifier) {
        val st = LocalStyle.current
        Column(modifier, verticalArrangement = Arrangement.Center) {
            val name = Stations[nowStation]?.name
            OutlinedText(name ?: "Elegí una radio", MaterialTheme.typography.titleLarge, st.accent, 9f,
                modifier = Modifier.fillMaxWidth(), maxSize = 40.sp)
            if (name != null) {
                OutlinedText(nowTitle ?: "", MaterialTheme.typography.titleMedium, Color.Black, 8f, Color.White,
                    modifier = Modifier.fillMaxWidth(), maxSize = 26.sp)
                OutlinedText(nowArtist ?: "", MaterialTheme.typography.bodyMedium, Color.Black, 7f, Color.White,
                    modifier = Modifier.fillMaxWidth(), maxSize = 20.sp)
            }
        }
    }

    /** Logo calcomanía de SA; la elegida, con halo dorado detrás y un poco más grande. */
    @Composable
    private fun SaLogo(s: Station, logo: android.net.Uri?, enabled: Boolean, l: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
        val st = LocalStyle.current
        val selected = s.id == nowStation
        val artBmp = rememberArt(s, logo)
        Box(modifier.size(l), contentAlignment = Alignment.Center) {
            if (artBmp == null) return@Box
            if (selected) Box(Modifier.requiredSize(l * 1.5f).background(Brush.radialGradient(
                0f to st.accent, 0.45f to st.accent.copy(alpha = 0.7f), 1f to Color.Transparent)))
            Image(artBmp, s.name, Modifier.fillMaxSize()
                .graphicsLayer { val k = if (selected) 1.18f else 1f; scaleX = k; scaleY = k; alpha = if (enabled) 1f else 0.35f }
                .clickable(enabled = enabled) { play(s) })
        }
    }

    /** Logo de la emisora: se arma en segundo plano (la primera vez; después sale del caché) y aparece cuando está. */
    @Composable
    private fun rememberArt(s: Station, logo: android.net.Uri?): androidx.compose.ui.graphics.ImageBitmap? =
        produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, s.id, logo) {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { Artwork.bitmap(this@MainActivity, s, logo).asImageBitmap() }
        }.value

    /**
     * Título con los colores del logo del juego (VC): sombra negra corrida abajo a la derecha, contorno grueso rosa y
     * relleno blanco, las tres con la misma fuente y tamaño. maxSize = crece hasta llenar el ancho; fixed = tamaño fijo.
     */
    @Composable
    private fun LogoTitle(
        text: String, st: GameStyle, modifier: Modifier = Modifier,
        maxSize: androidx.compose.ui.unit.TextUnit? = null, fixed: androidx.compose.ui.unit.TextUnit? = null,
    ) {
        val auto = maxSize?.let { androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 24.sp, maxFontSize = it, stepSize = 2.sp) }
        val base = androidx.compose.ui.text.TextStyle(fontFamily = st.titleFont, fontSize = fixed ?: androidx.compose.ui.unit.TextUnit.Unspecified)
        val big = fixed == null
        val stroke = if (big) 16f else 7f
        val shift = if (big) 7.dp else 3.dp
        fun layer(color: Color, w: Float?) = base.copy(color = color,
            drawStyle = w?.let { androidx.compose.ui.graphics.drawscope.Stroke(width = it, join = androidx.compose.ui.graphics.StrokeJoin.Round) })
        Box(modifier) {
            androidx.compose.foundation.text.BasicText(text, Modifier.offset(shift, shift), maxLines = 1, softWrap = false, autoSize = auto,
                style = layer(st.titleShadow, stroke * 1.4f))
            androidx.compose.foundation.text.BasicText(text, maxLines = 1, softWrap = false, autoSize = auto, style = layer(st.titleOutline!!, stroke))
            androidx.compose.foundation.text.BasicText(text, maxLines = 1, softWrap = false, autoSize = auto, style = layer(st.titleFill, null))
        }
    }

    /** Texto con contorno (el estilo de los textos de SA): el trazo debajo y el relleno encima. */
    @Composable
    private fun OutlinedText(
        text: String, style: androidx.compose.ui.text.TextStyle, color: Color, stroke: Float,
        strokeColor: Color = Color.Black, modifier: Modifier = Modifier, maxSize: androidx.compose.ui.unit.TextUnit? = null,
    ) {
        // Con maxSize, la letra crece hasta llenar el ancho disponible (las dos capas calculan el mismo tamaño).
        val auto = maxSize?.let { androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = it, stepSize = 1.sp) }
        Box(modifier) {
            androidx.compose.foundation.text.BasicText(text, maxLines = 1, softWrap = false, autoSize = auto,
                style = style.copy(color = strokeColor, drawStyle = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round)))
            androidx.compose.foundation.text.BasicText(text, maxLines = 1, softWrap = false, autoSize = auto, style = style.copy(color = color))
        }
    }

    /** Una línea: en grande (pantalla del auto) se agranda hasta [max] si entra y se achica si no; si no, tamaño normal. */
    @Composable
    private fun FitText(text: String, style: androidx.compose.ui.text.TextStyle, big: Boolean, max: androidx.compose.ui.unit.TextUnit) {
        if (big) androidx.compose.foundation.text.BasicText(text, Modifier.fillMaxWidth(), style = style, maxLines = 1, softWrap = false,
            overflow = TextOverflow.Ellipsis,
            autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 18.sp, maxFontSize = max, stepSize = 1.sp))
        else Text(text, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }

    /** Anterior / reproducir-pausa / siguiente (anterior y siguiente cambian de emisora). */
    @Composable
    private fun Controls(playColor: Color? = null, big: Boolean = false) {
        val icon = if (big) 52.dp else 36.dp
        val c = controller
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(enabled = canSkip, onClick = { c?.seekToPreviousMediaItem() }, modifier = Modifier.size(icon + 16.dp)) {
                Icon(painterResource(R.drawable.ic_skip_previous), "Emisora anterior", Modifier.size(icon))
            }
            FilledIconButton(enabled = hasItem, onClick = { if (playing) c?.pause() else c?.play() },
                modifier = Modifier.size(if (big) 92.dp else 64.dp),
                colors = if (playColor != null) IconButtonDefaults.filledIconButtonColors(containerColor = playColor, contentColor = Color.Black)
                else IconButtonDefaults.filledIconButtonColors()) {
                Icon(painterResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play),
                    if (playing) "Pausa" else "Reproducir", Modifier.size(icon))
            }
            IconButton(enabled = canSkip, onClick = { c?.seekToNextMediaItem() }, modifier = Modifier.size(icon + 16.dp)) {
                Icon(painterResource(R.drawable.ic_skip_next), "Emisora siguiente", Modifier.size(icon))
            }
        }
    }

    /**
     * Rueda de radio de GTA V: las emisoras en círculo sobre un disco oscuro; la que suena, más grande y con aro blanco.
     * Como en el juego (y la rueda del iPod): se mantiene el dedo y se desliza alrededor; la emisora bajo el dedo se
     * marca, su logo y nombre aparecen grandes en el centro y el teléfono hace un tic cada vez que cambia. Al soltar,
     * se sintoniza. Un toque simple también sintoniza. Sin dedo, el centro muestra emisora, tema y artista.
     */
    @Composable
    private fun Wheel(stations: List<Station>, logos: Map<String, android.net.Uri>, found: Set<String>, onPick: (Station) -> Unit) {
        val st = LocalStyle.current
        val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
        var preview by remember { mutableStateOf<Int?>(null) }
        val n = stations.size.coerceAtLeast(1)
        // Cuadrada y del lado más chico del lugar (en horizontal, el alto; en vertical, el ancho), centrada.
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight)
        BoxWithConstraints(Modifier.size(side).pointerInput(stations, found) {
            // Índice de la emisora según el ángulo del dedo (la 0 arriba, en sentido horario); en el centro, ninguna.
            fun at(p: androidx.compose.ui.geometry.Offset): Int? {
                val dx = p.x - size.width / 2f; val dy = p.y - size.height / 2f
                if (kotlin.math.hypot(dx, dy) < size.width * 0.16f) return null
                var a = kotlin.math.atan2(dy, dx) + Math.PI / 2
                if (a < 0) a += 2 * Math.PI
                return ((a / (2 * Math.PI) * n + 0.5).toInt()) % n
            }
            awaitEachGesture {
                val down = awaitFirstDown()
                var cur = at(down.position)
                preview = cur
                if (cur != null) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.SegmentFrequentTick)
                while (true) {
                    val ev = awaitPointerEvent()
                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                    if (!ch.pressed) break
                    val i = at(ch.position)
                    if (i != null && i != cur) {
                        cur = i; preview = i
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.SegmentFrequentTick)
                    }
                    ch.consume()
                }
                preview = null
                cur?.let { stations[it] }?.takeIf { it.id in found }?.let(onPick)
            }
        }) {
            val size = minOf(maxWidth, maxHeight)
            // Lo más grande posible sin que los círculos se pisen: t = 0,92 · 2π · r / n, con r = size/2 − 0,62 · t.
            val k = 0.92f * 2 * Math.PI.toFloat() / n
            val tile = minOf(size * 0.19f, size / 2 * k / (1 + 0.62f * k))
            val radius = size / 2 - tile * 0.62f
            Box(Modifier.size(size).clip(androidx.compose.foundation.shape.CircleShape)
                .background(Brush.radialGradient(listOf(Color(0xF2000000), Color(0xB3000000)))))
            stations.forEachIndexed { i, s ->
                val a = -Math.PI / 2 + 2 * Math.PI * i / n
                val marked = if (preview != null) i == preview else s.id == nowStation
                val t = if (marked) tile * 1.22f else tile
                // La marcada (con su aro blanco) va por encima de las vecinas.
                Box(Modifier.offset(size / 2 + radius * kotlin.math.cos(a).toFloat() - t / 2, size / 2 + radius * kotlin.math.sin(a).toFloat() - t / 2)
                    .size(t).zIndex(if (marked) 1f else 0f)) {
                    StationTile(s, logos[s.id], enabled = s.id in found, selected = marked,
                        shape = androidx.compose.foundation.shape.CircleShape, tappable = false) { }
                }
            }
            val shown = preview?.let { stations[it] }
            if (shown != null) {
                // Dedo sobre la rueda: el logo de la emisora grande en el centro, con su nombre.
                val art = rememberArt(shown, logos[shown.id])
                Column(Modifier.align(Alignment.Center).width(size * 0.46f), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (art != null) Image(art, shown.name, Modifier.size(size * 0.3f).clip(androidx.compose.foundation.shape.CircleShape)
                        .alpha(if (shown.id in found) 1f else 0.4f))
                    Text(shown.name.uppercase(), style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 2,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
                }
            } else {
                Column(Modifier.align(Alignment.Center).width(size * 0.5f), horizontalAlignment = Alignment.CenterHorizontally) {
                    val u = (size.value / 22f).coerceAtLeast(14f)   // escala con la rueda (pantalla del auto: más grande)
                    Text(Stations[nowStation]?.name?.uppercase() ?: "RADIO", style = MaterialTheme.typography.titleLarge.copy(fontSize = u.sp, lineHeight = (u * 1.15f).sp), color = Color.White,
                        maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Text(nowTitle ?: "", style = MaterialTheme.typography.bodyLarge.copy(fontSize = (u * 0.8f).sp, lineHeight = (u * 0.95f).sp), color = Color.White, maxLines = 2,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center, overflow = TextOverflow.Ellipsis)
                    Text(nowArtist ?: "", style = MaterialTheme.typography.bodyMedium.copy(fontSize = (u * 0.65f).sp), color = st.textDim, maxLines = 1,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        }
    }

    /** Cambia el disco: muestra el otro juego; lo que suena sigue sonando hasta que se elija otra emisora. */
    private fun switchGame(prefs: Prefs, g: Game): Game = g.also { prefs.game = it }

    private fun play(s: Station) {
        val c = controller ?: return
        // Tocar la emisora que ya está puesta no la vuelve a sintonizar (eso hacía un corte); si estaba en pausa, sigue.
        if (c.currentMediaItem?.mediaId?.substringBefore('#') == s.id) { if (!c.isPlaying) c.play(); return }
        c.setMediaItem(MediaItem.Builder().setMediaId(s.id).build())
        c.prepare()
        c.play()
    }
}

/** Título del juego; en "GTA III" el número romano va en el color del estilo (rojo), misma fuente. */
private fun gameTitle(game: Game, st: GameStyle): androidx.compose.ui.text.AnnotatedString {
    val numeral = st.numeral
    return if (numeral != null && game.title.startsWith("GTA ")) {
        androidx.compose.ui.text.buildAnnotatedString {
            append("GTA ")
            pushStyle(androidx.compose.ui.text.SpanStyle(color = numeral)); append(game.title.removePrefix("GTA ")); pop()
        }
    } else androidx.compose.ui.text.AnnotatedString(game.title)
}

/** Alto de "gta" en los logos de IV, V y VI en el selector de juegos (en el encabezado se calcula para llenar el ancho). */
private val PICKER_GTA = 24.dp
