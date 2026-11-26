package ar.nallib.gtaradio.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Una versión publicada en GitHub Releases con su APK. */
data class Release(val version: String, val apkUrl: String)

/**
 * Busca versiones nuevas en los Releases del repo y las instala con el instalador de Android (el usuario confirma).
 * Sin internet o si GitHub no responde, no pasa nada: la app sigue igual.
 */
object Updates {
    private const val LATEST = "https://api.github.com/repos/SuricataIngeniosa/gta-radio/releases/latest"

    /** La última versión si es más nueva que la instalada; si no, o ante cualquier error, null. */
    suspend fun check(context: Context): Release? = withContext(Dispatchers.IO) {
        runCatching {
            val json = JSONObject(get(LATEST).bufferedReader().use { it.readText() })
            val version = json.getString("tag_name").removePrefix("v")
            val assets = json.getJSONArray("assets")
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.getString("name").endsWith(".apk") }?.getString("browser_download_url")
                ?: return@runCatching null
            val installed = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"
            if (newer(version, installed)) Release(version, apk) else null
        }.getOrNull()
    }

    /** "0.10.0" > "0.9.1": compara número por número. */
    fun newer(a: String, b: String): Boolean {
        val x = a.split('.').map { it.toIntOrNull() ?: 0 }
        val y = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
            if (d != 0) return d > 0
        }
        return false
    }

    /** Baja el APK a la caché de la app; progress recibe de 0 a 1 (o -1 si no se sabe el tamaño). */
    suspend fun download(context: Context, release: Release, progress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val out = File(dir, "GTA-Radio-${release.version}.apk")
        val conn = URL(release.apkUrl).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000; conn.readTimeout = 30_000
        val total = conn.contentLengthLong
        conn.inputStream.use { input ->
            out.outputStream().use { output ->
                val buf = ByteArray(1 shl 16)
                var done = 0L
                while (true) {
                    val n = input.read(buf); if (n < 0) break
                    output.write(buf, 0, n); done += n
                    progress(if (total > 0) done.toFloat() / total else -1f)
                }
            }
        }
        out
    }

    /** ¿Android deja que esta app abra el instalador? Si no, hay que mandar al usuario a darle el permiso. */
    fun canInstall(context: Context) = Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    fun installPermissionIntent(context: Context) =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    fun installIntent(context: Context, apk: File): Intent =
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(FileProvider.getUriForFile(context, "${context.packageName}.updates", apk), "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun get(url: String) = (URL(url).openConnection() as HttpURLConnection).run {
        connectTimeout = 5_000; readTimeout = 5_000
        setRequestProperty("Accept", "application/vnd.github+json")
        if (responseCode != 200) error("HTTP $responseCode")
        inputStream
    }
}
