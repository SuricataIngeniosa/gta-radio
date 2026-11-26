package ar.nallib.gtaradio.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.edit
import androidx.core.net.toUri
import java.io.File

/** Preferencias: carpeta de audios (SAF) por juego, modo radio en vivo, posición por emisora y duraciones medidas. */
class Prefs(private val context: Context) {
    private val sp = context.getSharedPreferences("gtaradio", Context.MODE_PRIVATE)

    /** Juego elegido (el "disco" puesto). */
    var game: Game
        get() = runCatching { Game.valueOf(sp.getString("game", null)!!) }.getOrDefault(Game.VICE_CITY)
        set(v) = sp.edit { putString("game", v.name) }

    /** ¿Ya se eligió un juego alguna vez? Si no (primera vez), la app abre con el selector de juegos. */
    val gameChosen get() = sp.contains("game")

    /** Carpeta de audios del juego elegido: cada juego tiene la suya. null al asignar = volver a la predefinida. */
    var folderUri: Uri?
        get() = folderOf(game)
        set(v) = sp.edit { putString(game.folderKey, v?.toString()) }

    /** La elegida con el selector o, si no hay, la predefinida que exista (ver [defaultFolder]). */
    fun folderOf(g: Game): Uri? = sp.getString(g.folderKey, null)?.toUri() ?: defaultFolder(g)?.let { Uri.fromFile(it) }
    fun isChosen(g: Game = game) = sp.getString(g.folderKey, null) != null

    /**
     * Carpeta predefinida, para sistemas sin selector de archivos (BYD DiLink): "<almacenamiento>/Music/<GTA X Radios>"
     * o "<almacenamiento>/<GTA X Radios>", en la memoria interna o en cualquier pendrive / SD montado en /storage.
     */
    fun defaultFolder(g: Game): File? {
        // Raíces de cada volumen (memoria interna, SD, pendrives). /storage no se puede listar desde una app: se sacan de
        // StorageManager y, en Android viejos, de las carpetas propias de la app en cada volumen (<raíz>/Android/data/...).
        val roots = buildList {
            add(File("/storage/emulated/0"))
            if (android.os.Build.VERSION.SDK_INT >= 30) context.getSystemService(android.os.storage.StorageManager::class.java)
                ?.storageVolumes?.mapNotNull { it.directory }?.let(::addAll)
            context.getExternalFilesDirs(null).filterNotNull().mapNotNull { it.path.substringBefore("/Android/").takeIf { r -> r.isNotEmpty() } }
                .forEach { add(File(it)) }
            File("/storage").listFiles()?.filter { it.isDirectory && it.name != "emulated" && it.name != "self" }?.let(::addAll)
        }.distinctBy { it.absolutePath }
        for (root in roots) for (name in DIR_NAMES.getValue(g)) for (base in listOf(File(root, "Music"), root)) {
            val d = File(base, name)
            if (d.isDirectory && d.listFiles() != null) return d
        }
        return null
    }

    /** Última emisora sintonizada (de cualquier juego): se carga al abrir la app y se retoma con "play". */
    var lastStation: String?
        get() = sp.getString("last_station", null)
        set(v) = sp.edit { putString("last_station", v) }

    /** Emisoras secretas ya adivinadas. */
    fun isUnlocked(s: Station) = !s.secret || sp.getBoolean("unlocked_${s.id}", false)
    fun unlock(s: Station) = sp.edit { putBoolean("unlocked_${s.id}", true) }

    var liveMode: Boolean
        get() = sp.getBoolean("live", true)
        set(v) = sp.edit { putBoolean("live", v) }

    /** Duración medida del archivo, para las emisoras que no la traen en Stations. */
    /** La clave incluye el archivo: si se cambia (p. ej. de .mp3 a .flac), se vuelve a medir. */
    fun measuredDuration(id: String, file: Uri) = sp.getLong(durKey(id, file), 0L).takeIf { it > 0 }
    fun saveDuration(id: String, file: Uri, ms: Long) = sp.edit { putLong(durKey(id, file), ms) }
    private fun durKey(id: String, file: Uri) = "dur_${id}_${file.toString().hashCode()}"

    /** Posición guardada (modo no en vivo): tema y milisegundo dentro del tema. */
    fun position(id: String): Pair<Int, Long> = sp.getString("at_$id", null)?.split(":")
        ?.let { (i, ms) -> i.toInt() to ms.toLong() } ?: (0 to 0L)
    fun savePosition(id: String, index: Int, ms: Long) = sp.edit { putString("at_$id", "$index:$ms") }

    /**
     * Arma la biblioteca del juego elegido: en la carpeta del juego, una subcarpeta por emisora con un archivo por
     * tema, comentario o tanda, nombrados "[orden]_[Emisora]_[Tema]_[Artista].ext". La subcarpeta se reconoce por su
     * nombre o por el nombre de emisora de sus archivos. El logo es <ARCHIVO>.png en la carpeta del juego (FLASH.png)
     * o logo.png dentro de la de la emisora.
     */
    fun resolveFiles(game: Game = this.game): Library {
        val tree = folderOf(game) ?: return Library()
        val root = list(tree, rootId(tree))
        val stations = Stations.of(game)
        val tracks = mutableMapOf<String, List<Track>>()
        val logos = mutableMapOf<String, Uri>()
        for (d in root.filter { it.dir }) {
            val files = list(tree, d.id)
            val audio = files.filter { !it.dir && it.name.substringAfterLast('.').lowercase() in AUDIO_EXT }
                .mapNotNull { Track.parse(it.name, docUri(tree, it.id)) }
                .sortedBy { it.order }
            if (audio.isEmpty()) continue
            val key = norm(d.name)
            val st = stations.firstOrNull { norm(it.name) == key || norm(it.fileName.substringBeforeLast('.')) == key }
                ?: stations.firstOrNull { norm(it.name) == norm(audio.first().station) }
                ?: continue
            tracks[st.id] = audio
            files.firstOrNull { it.name.lowercase().substringBeforeLast('.') == "logo" && it.name.substringAfterLast('.').lowercase() in IMAGE_EXT }
                ?.let { logos[st.id] = docUri(tree, it.id) }
        }
        for (s in stations) {
            if (s.id in logos) continue
            val stem = s.fileName.substringBeforeLast('.').lowercase()
            root.firstOrNull { !it.dir && it.name.lowercase().substringBeforeLast('.') == stem && it.name.substringAfterLast('.').lowercase() in IMAGE_EXT }
                ?.let { logos[s.id] = docUri(tree, it.id) }
        }
        return Library(tracks, logos)
    }

    private data class Doc(val id: String, val name: String, val dir: Boolean)

    // Carpeta como archivo (file://, la predefinida) o como árbol del selector (content://, SAF).
    private fun isFile(tree: Uri) = tree.scheme == "file"
    private fun rootId(tree: Uri) = if (isFile(tree)) tree.path!! else DocumentsContract.getTreeDocumentId(tree)
    private fun docUri(tree: Uri, id: String) = if (isFile(tree)) Uri.fromFile(File(id)) else DocumentsContract.buildDocumentUriUsingTree(tree, id)

    private fun list(tree: Uri, parentId: String): List<Doc> = if (isFile(tree)) {
        File(parentId).listFiles()?.map { Doc(it.absolutePath, it.name, it.isDirectory) }.orEmpty()
    } else runCatching {
        val out = mutableListOf<Doc>()
        context.contentResolver.query(
            DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId),
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE),
            null, null, null,
        )?.use { c -> while (c.moveToNext()) out += Doc(c.getString(0), c.getString(1), c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) }
        out
    }.getOrDefault(emptyList())

    companion object {
        fun dirName(g: Game) = DIR_NAMES.getValue(g).first()

        private val IMAGE_EXT = listOf("png", "jpg", "jpeg", "webp")
        /** Nombres de la carpeta de cada juego que se buscan solos (los de tools/push_split.sh). */
        private val DIR_NAMES = mapOf(
            Game.GTA_III to listOf("GTA III Radios", "GTA 3 Radios"),
            Game.VICE_CITY to listOf("GTA Vice City Radios", "GTA VC Radios"),
            Game.SAN_ANDREAS to listOf("GTA SA Radios", "GTA San Andreas Radios"),
            Game.GTA_IV to listOf("GTA IV Radios", "GTA 4 Radios"),
            Game.GTA_V to listOf("GTA V Radios", "GTA 5 Radios"),
            Game.GTA_VI to listOf("GTA VI Radios", "GTA 6 Radios"),
        )
        private val AUDIO_EXT = listOf("flac", "mp3", "ogg", "opus", "m4a", "wav")
        private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
    }
}

/** Emisoras del juego con sus temas en orden, y el logo de cada una. */
data class Library(val tracks: Map<String, List<Track>> = emptyMap(), val logos: Map<String, Uri> = emptyMap())

/**
 * Un archivo de la emisora: "[orden]_[Emisora]_[Tema / Comentario / Tanda - X]_[Artista / locutor].ext".
 * Lo que no es canción (Comentario, Intro, Tanda...) se muestra como radio + locutor.
 */
data class Track(val uri: Uri, val order: Int, val station: String, val title: String, val artist: String) {
    val isSong get() = !NOT_SONG.containsMatchIn(title)

    companion object {
        private val NOT_SONG = Regex("^(comentario|intro|outro|tanda|ad\\b|anuncio|jingle)", RegexOption.IGNORE_CASE)
        fun parse(name: String, uri: Uri): Track? {
            val parts = name.substringBeforeLast('.').split('_')
            val order = parts.firstOrNull()?.toIntOrNull() ?: return null
            return Track(uri, order, parts.getOrElse(1) { "" }, parts.getOrElse(2) { "" }, parts.drop(3).joinToString("_"))
        }
    }
}
