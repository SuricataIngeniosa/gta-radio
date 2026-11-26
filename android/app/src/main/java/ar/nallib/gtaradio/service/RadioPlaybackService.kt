package ar.nallib.gtaradio.service

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import ar.nallib.gtaradio.data.Artwork
import ar.nallib.gtaradio.data.Library
import ar.nallib.gtaradio.data.Prefs
import ar.nallib.gtaradio.data.Station
import ar.nallib.gtaradio.data.Stations
import ar.nallib.gtaradio.data.Track
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Servicio de medios: lo usan Android Auto y la pantalla del teléfono.
 * Árbol: root -> las emisoras del juego elegido que tienen su carpeta (grilla en el auto).
 * Cada emisora es una carpeta de temas en orden; el reproductor tiene en la cola los temas de la emisora puesta
 * (en loop) y "anterior/siguiente" cambian de emisora. Cada tema trae su título y artista en el nombre del archivo.
 * mediaId de cada tema: "<emisora>#<índice>"; el de la emisora (grilla, Android Auto): "<emisora>".
 */
class RadioPlaybackService : MediaLibraryService() {
    private var session: MediaLibrarySession? = null
    private lateinit var player: ExoPlayer
    private lateinit var prefs: Prefs
    private val scope = MainScope()
    // Biblioteca de cada juego (recorrer las carpetas es lento): se rehace sólo si cambió la carpeta de ese juego.
    private val libraries = mutableMapOf<ar.nallib.gtaradio.data.Game, Pair<String, Library>>()

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        player.repeatMode = Player.REPEAT_MODE_ALL // la emisora da la vuelta y sigue
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { if (!isPlaying) savePosition() }
        })
        session = MediaLibrarySession.Builder(this, StationSwitchingPlayer(player, ::switchStation, ::stationCount), Callback()).build()
        getSharedPreferences("gtaradio", MODE_PRIVATE).registerOnSharedPreferenceChangeListener(gameListener)
    }

    /**
     * Si se cambia de juego (o de carpeta) en el teléfono, Android Auto vuelve a pedir la grilla de emisoras. Lo que
     * suena sigue sonando hasta que se elija otra emisora.
     */
    private val gameListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "game" || key?.startsWith("folder") == true) session?.notifyChildrenChanged(ROOT, Int.MAX_VALUE, null)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        getSharedPreferences("gtaradio", MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(gameListener)
        savePosition()
        scope.cancel()
        player.release()
        session?.release()
        session = null
        super.onDestroy()
    }

    private fun currentStation(): Station? = player.currentMediaItem?.mediaId?.substringBefore('#')?.let { Stations[it] }

    private fun savePosition() {
        val s = currentStation() ?: return
        prefs.savePosition(s.id, player.currentMediaItemIndex, player.currentPosition)
    }

    private fun libraryFor(g: ar.nallib.gtaradio.data.Game): Library {
        val key = "${prefs.folderOf(g)}"
        libraries[g]?.takeIf { it.first == key }?.let { return it.second }
        return prefs.resolveFiles(g).also { libraries[g] = key to it }
    }

    /** Emisoras con carpeta del juego dado (por defecto, el de la pantalla). */
    private fun available(g: ar.nallib.gtaradio.data.Game = prefs.game): List<Station> =
        Stations.of(g).filter { prefs.isUnlocked(it) && !libraryFor(g).tracks[it.id].isNullOrEmpty() }

    /** Anterior/siguiente recorren las emisoras del juego de lo que suena (o el de la pantalla si no suena nada). */
    private fun dialGame() = currentStation()?.game ?: prefs.game

    private fun stationCount() = available(dialGame()).size

    /** Duración de un tema: medida una vez del archivo y guardada (clave = archivo). */
    private fun durationOf(s: Station, uri: Uri): Long = prefs.measuredDuration(s.id, uri) ?: runCatching {
        android.media.MediaMetadataRetriever().run {
            try { setDataSource(this@RadioPlaybackService, uri); extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() }
            finally { release() }
        }
    }.getOrNull()?.takeIf { it > 0 }?.also { prefs.saveDuration(s.id, uri, it) } ?: 0L

    /**
     * Dónde arranca la emisora: en vivo, según el reloj (la emisora "siguió sonando": la hora módulo la duración total,
     * repartida entre los temas); si no, donde se dejó. Mide las duraciones en segundo plano la primera vez.
     */
    private suspend fun startFor(s: Station, tracks: List<Track>): Pair<Int, Long> {
        if (!prefs.liveMode) return prefs.position(s.id).let { (i, ms) -> if (i in tracks.indices) i to ms else 0 to 0L }
        val durs = withContext(Dispatchers.IO) { tracks.map { durationOf(s, it.uri) } }
        val total = durs.sum().takeIf { it > 0 } ?: return 0 to 0L
        var t = System.currentTimeMillis() % total
        durs.forEachIndexed { i, d -> if (t < d) return i to t; t -= d }
        return 0 to 0L
    }

    private fun items(s: Station, tracks: List<Track>) = tracks.mapIndexed { i, t -> trackItem(s, t, i) }

    private fun trackItem(s: Station, t: Track, i: Int): MediaItem {
        // Canción: su título y artista. Comentario, tanda o intro: el nombre de la radio y el locutor.
        val title = if (t.isSong) t.title else s.name
        val artist = if (t.isSong) t.artist else s.host
        val md = MediaMetadata.Builder()
            .setTitle(title).setDisplayTitle(title).setArtist(artist)
            // Android Auto muestra el subtítulo (DISPLAY_SUBTITLE) antes que el artista: tiene que ser el artista.
            .setAlbumTitle(s.name).setStation(s.name).setSubtitle(artist)
            .setArtworkData(Artwork.png(this, s, libraryFor(s.game).logos[s.id]), MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            .setIsBrowsable(false).setIsPlayable(true)
            .setMediaType(if (t.isSong) MediaMetadata.MEDIA_TYPE_MUSIC else MediaMetadata.MEDIA_TYPE_RADIO_STATION)
            .build()
        return MediaItem.Builder().setMediaId("${s.id}#$i").setUri(t.uri).setMediaMetadata(md).build()
    }

    /** Ítem de la grilla (una emisora): sin URI; al elegirlo se arma la cola con sus temas. */
    private fun stationItem(s: Station) = MediaItem.Builder().setMediaId(s.id).setMediaMetadata(
        MediaMetadata.Builder().setTitle(s.name).setDisplayTitle(s.name).setArtist(s.host).setStation(s.name)
            .setArtworkData(Artwork.png(this, s, libraryFor(s.game).logos[s.id]), MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            .setIsBrowsable(false).setIsPlayable(true).setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION).build()
    ).build()

    /** Anterior/siguiente: la emisora de al lado (circular), en su posición. */
    private fun switchStation(delta: Int) {
        val stations = available(dialGame())
        if (stations.size < 2) return
        savePosition()
        val cur = stations.indexOfFirst { it.id == currentStation()?.id }.coerceAtLeast(0)
        val next = stations[((cur + delta) % stations.size + stations.size) % stations.size]
        val tracks = libraryFor(next.game).tracks[next.id] ?: return
        prefs.lastStation = next.id
        scope.launch {
            val (i, ms) = startFor(next, tracks)
            player.setMediaItems(items(next, tracks), i, ms)
            player.prepare()
            player.play()
        }
    }

    private inner class Callback : MediaLibrarySession.Callback {
        private fun rootItem() = MediaItem.Builder().setMediaId(ROOT).setMediaMetadata(
            MediaMetadata.Builder().setTitle(prefs.game.title).setIsBrowsable(true).setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS).build()
        ).build()

        override fun onGetLibraryRoot(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            // Pide a Android Auto mostrar las emisoras como grilla.
            val extras = Bundle().apply { putInt(CONTENT_STYLE_PLAYABLE, CONTENT_STYLE_GRID) }
            return Futures.immediateFuture(LibraryResult.ofItem(rootItem(), LibraryParams.Builder().setExtras(extras).build()))
        }

        override fun onGetChildren(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, parentId: String,
            page: Int, pageSize: Int, params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            if (parentId != ROOT) return Futures.immediateFuture(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE))
            return Futures.immediateFuture(LibraryResult.ofItemList(available().map(::stationItem), params))
        }

        override fun onGetItem(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val s = Stations[mediaId.substringBefore('#')] ?: return Futures.immediateFuture(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE))
            return Futures.immediateFuture(LibraryResult.ofItem(stationItem(s), null))
        }

        /** El auto o el teléfono eligen una emisora: la cola son sus temas, arrancando donde corresponde. */
        override fun onSetMediaItems(
            mediaSession: MediaSession, controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            // La emisora pedida puede ser de cualquier juego (la que quedó sonando o la última recordada).
            val wanted = mediaItems.getOrNull(if (startIndex == C.INDEX_UNSET) 0 else startIndex)?.mediaId?.substringBefore('#')
            val s = Stations[wanted]?.takeIf { !libraryFor(it.game).tracks[it.id].isNullOrEmpty() } ?: available().firstOrNull()
            val tracks = s?.let { libraryFor(it.game).tracks[it.id] }
            if (s == null || tracks.isNullOrEmpty()) return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0))
            savePosition()
            prefs.lastStation = s.id
            val f = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            scope.launch {
                val (i, ms) = startFor(s, tracks)
                f.set(MediaSession.MediaItemsWithStartPosition(items(s, tracks), i, ms))
            }
            return f
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            val s = mediaItems.firstNotNullOfOrNull { Stations[it.mediaId.substringBefore('#')] }
            val tracks = s?.let { libraryFor(it.game).tracks[it.id] } ?: return Futures.immediateFuture(mutableListOf())
            return Futures.immediateFuture(items(s, tracks).toMutableList())
        }

        /** "Play" sin nada cargado (auto, auriculares, pantalla de bloqueo): retoma la última emisora. */
        override fun onPlaybackResumption(
            mediaSession: MediaSession, controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val s = Stations[prefs.lastStation]
            val tracks = s?.let { libraryFor(it.game).tracks[it.id] }
            if (s == null || tracks.isNullOrEmpty()) return Futures.immediateFailedFuture(UnsupportedOperationException("sin emisora"))
            val f = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            scope.launch { val (i, ms) = startFor(s, tracks); f.set(MediaSession.MediaItemsWithStartPosition(items(s, tracks), i, ms)) }
            return f
        }
    }

    companion object {
        const val ROOT = "root"
        private const val CONTENT_STYLE_PLAYABLE = "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"
        private const val CONTENT_STYLE_GRID = 2
    }
}
