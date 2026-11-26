package ar.nallib.gtaradio.data

import ar.nallib.gtaradio.R

/** Juegos con radios. folderKey = preferencia donde se guarda su carpeta; titleFont = fuente de la portada generada (null = la del sistema). */
enum class Game(val title: String, val folderKey: String, val titleFont: Int?) {
    GTA_III("GTA III", "folder_gta3", R.font.iii_title),
    VICE_CITY("Vice City", "folder", R.font.vc_title),
    // Provisorio: Pricedown / fuente del sistema hasta que lleguen las fuentes de cada juego.
    SAN_ANDREAS("San Andreas", "folder_sa", R.font.sa_title),
    GTA_IV("GTA IV", "folder_gta4", R.font.iii_title),
    GTA_V("GTA V", "folder_gta5", R.font.iii_title),
    GTA_VI("GTA VI", "folder_gta6", null),
}

/**
 * Una emisora. color = fondo del botón (en Vice City, rosa oscuro semitransparente bajo el contorno rosa). fileName = nombre del audio en la carpeta
 * del juego; durationMs = fija (null = se mide del archivo al reproducir y se guarda por archivo; Vice City ahora usa los FLAC de la caja de CD).
 * Los id no se repiten entre juegos (Flash FM y Flashback 95.6 usan los dos FLASH.mp3).
 */
data class Station(
    val id: String,
    val game: Game,
    val name: String,
    val fileName: String,
    /** Locutor (null = sin locutor fijo o no confirmado: se muestra el nombre de la emisora). */
    val host: String?,
    val color: Long,
    val durationMs: Long?,
    /**
     * Emisora secreta (The Vault en VI): nombres que la desbloquean (sin mayúsculas). Hasta adivinarla es un "?" en la
     * última celda de la grilla, abajo a la derecha con un hueco antes, y no aparece en Android Auto ni en anterior/siguiente.
     */
    val answers: List<String>? = null,
) {
    val secret get() = answers != null
}

/** Fondo de los botones de Vice City: el rosa del borde (0xFF4FA3) un tono más oscuro y semitransparente, para que resalten los logos. */
private const val VC_TILE = 0x4D9E2466L // alfa 30 %

object Stations {
    private val vc = Game.VICE_CITY
    private val iii = Game.GTA_III

    val all = listOf(
        Station("flash", vc, "Flash FM", "FLASH.mp3", "Toni", VC_TILE, null),
        Station("vrock", vc, "V-Rock", "VROCK.mp3", "Lazlow", VC_TILE, null),
        Station("wave103", vc, "Wave 103", "WAVE.mp3", "Adam First", VC_TILE, null),
        Station("emotion", vc, "Emotion 98.3", "EMOTION.mp3", "Fernando Martinez", VC_TILE, null),
        Station("fever", vc, "Fever 105", "FEVER.mp3", "Oliver \"Ladykiller\" Biscuit", VC_TILE, null),
        Station("wildstyle", vc, "Wildstyle", "WILD.mp3", "Mr. Magic", VC_TILE, null),
        Station("espantoso", vc, "Radio Espantoso", "ESPANT.mp3", "Pepe", VC_TILE, null),
        Station("kchat", vc, "K-Chat", "KCHAT.mp3", "Amy Sheckenhausen", VC_TILE, null),
        Station("vcpr", vc, "VCPR", "VCPR.mp3", "Maurice Chavez", VC_TILE, null),

        // GTA III: nombres de archivo del juego de PC (carpeta audio/), en el orden del dial. Fondo negro, como en el juego.
        Station("iii_head", iii, "Head Radio", "HEAD.mp3", "Michael Hunt", 0xFF000000, null),
        Station("iii_class", iii, "Double Clef FM", "CLASS.mp3", "Morgan Merryweather", 0xFF000000, null),
        Station("iii_kjah", iii, "K-Jah", "KJAH.mp3", "Horace \"Pepper\" Walker", 0xFF000000, null),
        Station("iii_rise", iii, "Rise FM", "RISE.mp3", "Andre the Accelerator", 0xFF000000, null),
        Station("iii_lips", iii, "Lips 106", "LIPS.mp3", "Andee", 0xFF000000, null),
        Station("iii_game", iii, "Game Radio FM", "GAME.mp3", "Stretch Armstrong y Lord Sear", 0xFF000000, null),
        Station("iii_msx", iii, "MSX FM", "MSX.mp3", "MC Codebreaker", 0xFF000000, null),
        Station("iii_flash", iii, "Flashback 95.6", "FLASH.mp3", "Toni", 0xFF000000, null),
        Station("iii_chat", iii, "Chatterbox FM", "CHAT.mp3", "Lazlow", 0xFF000000, null),

        // San Andreas, IV, V y VI: en el juego no hay un archivo por emisora, así que el nombre del .mp3 es nuestro.
        // Fondo negro hasta tener los logos. Listas y locutores: grandtheftwiki.com (SA/IV/V) y keengamer.com (VI, 2026-11-20).
        sa("playback", "Playback FM", "PLAYBACK", "Forth Right MC"),
        sa("krose", "K-Rose", "KROSE", "Mary-Beth Maybell"),
        sa("kdst", "K-DST", "KDST", "Tommy \"The Nightmare\" Smith"),
        sa("bounce", "Bounce FM", "BOUNCE", "The Funktipus"),
        sa("sfur", "SF-UR", "SFUR", "Hans Oberlander"),
        sa("rls", "Radio Los Santos", "RLS", "Julio G"),
        sa("radiox", "Radio X", "RADIOX", "Sage"),
        sa("csr", "CSR 103.9", "CSR", "Phillip \"PM\" Michaels"),
        sa("kjah", "K-Jah West", "KJAH", "Marshall Peters y Johnny Lawton"),
        sa("master", "Master Sounds 98.3", "MASTER", "Johnny \"The Love Giant\" Parkinson"),
        sa("wctr", "WCTR", "WCTR", null),

        // GTA IV (juego base, con Independence FM de PC) y las tres que suma Episodes from Liberty City.
        iv("massiveb", "Massive B Soundsystem 96.9", "MASSIVEB", "Bobby Konders"),
        iv("lrr", "Liberty Rock Radio 97.8", "LRR", "Iggy Pop"),
        iv("vibe", "The Vibe 98.8", "VIBE", "Vaughn Harper"),
        iv("if99", "IF99", "IF99", "Femi Kuti"),
        iv("beat", "The Beat 102.7", "BEAT", "DJ Green Lantern y Mister Cee"),
        iv("classics", "The Classics 104.1", "CLASSICS", "DJ Premier"),
        iv("jazz", "Jazz Nation Radio 108.5", "JAZZ", "Roy Haynes"),
        iv("k109", "K109 The Studio", "K109", "Karl Lagerfeld"),
        iv("vlad", "Vladivostok FM", "VLAD", "Ruslana"),
        iv("wktt", "WKTT Radio", "WKTT", null),
        iv("electro", "Electro-Choc", "ELECTRO", "François K"),
        iv("lchc", "L.C.H.C.", "LCHC", "Jimmy Gestapo"),
        iv("fusion", "Fusion FM", "FUSION", "Roy Ayers"),
        iv("journey", "The Journey", "JOURNEY", null),
        iv("broker", "Radio Broker", "BROKER", "Juliette Lewis"),
        iv("sanjuan", "San Juan Sounds", "SANJUAN", "Daddy Yankee"),
        iv("tuffgong", "Tuff Gong Radio", "TUFFGONG", "Carl Bradshaw"),
        iv("integrity", "Integrity 2.0", "INTEGRITY", "Lazlow"),
        iv("plr", "Public Liberty Radio", "PLR", null),
        iv("indep", "Independence FM", "INDEP", "Gary Sheen"),
        // Episodes from Liberty City.
        iv("ramjam", "RamJam FM", "RAMJAM", "David Rodigan"),
        iv("vcfm", "Vice City FM", "VCFM", "Fernando Martinez"),
        iv("selfact", "Self-Actualization FM", "SELFACT", "Audrey"),

        // GTA V: las de 2013 más The Lab, blonded y Los Santos Underground Radio (agregadas después).
        v("lsrr", "Los Santos Rock Radio", "LSRR", "Kenny Loggins"),
        v("nonstop", "Non Stop Pop FM", "NONSTOP", "Cara Delevingne"),
        v("rls", "Radio Los Santos", "RLS", "Big Boy"),
        v("channelx", "Channel X", "CHANNELX", "Keith Morris"),
        v("wctr", "WCTR", "WCTR", null),
        v("rebel", "Rebel Radio", "REBEL", "Jesco White"),
        v("soulwax", "Soulwax FM", "SOULWAX", "Soulwax"),
        v("eastlos", "East Los FM", "EASTLOS", "Don Cheto y Camilo Lara"),
        v("wcc", "West Coast Classics", "WCC", "DJ Pooh"),
        v("blueark", "The Blue Ark", "BLUEARK", "Lee \"Scratch\" Perry"),
        v("worldwide", "Worldwide FM", "WORLDWIDE", "Gilles Peterson"),
        v("flylo", "FlyLo FM", "FLYLO", "Flying Lotus"),
        v("lowdown", "The Lowdown 91.1", "LOWDOWN", "Mama G"),
        v("mirror", "Radio Mirror Park", "MIRROR", "Twin Shadow"),
        v("space", "Space 103.2", "SPACE", "Bootsy Collins"),
        v("vinewood", "Vinewood Boulevard Radio", "VINEWOOD", "Nate y Steve"),
        v("blaine", "Blaine County Radio", "BLAINE", null),
        v("lab", "The Lab", "LAB", null),
        v("blonded", "blonded Los Santos 97.8", "BLONDED", null),
        v("lsur", "Los Santos Underground Radio", "LSUR", null),

        // GTA VI (salió el 2026-11-19): locutores en null donde las fuentes no coinciden (Honey FM, Emotion 98.3).
        vi("vcrr", "VCRR", "VCRR", "JT y DJ Stevie J"),
        vi("dirtysouth", "Dirty South Classics", "DIRTYSOUTH", "Trick Daddy y Trina"),
        vi("honey", "Honey FM", "HONEY", null),
        vi("cocoteo", "Cocoteo FM", "COCOTEO", "Bad Bunny y RaiNao"),
        vi("laola", "Radio La Ola", "LAOLA", "Gloria y Emilio Estefan"),
        vi("afrobank", "AfroBank FM", "AFROBANK", "Burna Boy y Palms Trax"),
        vi("onu", "Radio On-U", "ONU", "Adrian Sherwood"),
        vi("flash", "Flash FM", "FLASH", "Robyn y Alex Consani"),
        vi("emotion", "Emotion 98.3", "EMOTION", null),
        vi("vcfm", "Vice City FM", "VCFM", "\"Weird Al\" Yankovic y Brigitte Nielsen"),
        vi("vrock", "V-Rock", "VROCK", "Lindsey Buckingham y Michael McDonald"),
        vi("chamber", "The Chamber 106.6", "CHAMBER", "Kerry King y Tom Araya"),
        vi("backcountry", "Back Country Radio", "BACKCOUNTRY", "Morgan Wallen y Lana Del Rey"),
        vi("gloriana", "Gloriana Blues Radio", "GLORIANA", "Buddy Guy y Keith Richards"),
        vi("stockyard", "Stockyard FM", "STOCKYARD", "The Dare y PinkPantheress"),
        vi("circoloco", "CircoLoco Records Radio", "CIRCOLOCO", "Seth Troxler y Antonio Carbonaro"),
        vi("worldwide", "Worldwide FM", "WORLDWIDE", "Gilles Peterson"),
        vi("kaleido", "Kaleidoscope FM", "KALEIDO", "Jeremy Irons"),
        vi("jazz", "Leonida Jazz Radio", "JAZZ", "Randy Brecker y Stanley Clarke"),
        vi("symphony", "Symphony FM", "SYMPHONY", "Yuja Wang"),
        vi("wktt", "WKTT", "WKTT", null),
        vi("wipe", "The Wipe", "WIPE", null),
        vi("sniper", "The Sniper of La Perle", "SNIPER", null),
        vi("thrillbilly", "Thrillbilly Mud Club Radio", "THRILLBILLY", null),
        vi("vault", "The Vault Radio", "VAULT", "DJ Pretzel", answers = listOf("vault", "the vault", "the vault radio")),
    )
    // Fondo transparente: los logos de SA flotan sobre la ilustración.
    private fun sa(id: String, name: String, file: String, host: String?) = Station("sa_$id", Game.SAN_ANDREAS, name, "$file.mp3", host, 0x00000000, null)
    private fun iv(id: String, name: String, file: String, host: String?) = Station("iv_$id", Game.GTA_IV, name, "$file.mp3", host, 0xD90F1924, null)
    private fun v(id: String, name: String, file: String, host: String?) = Station("v_$id", Game.GTA_V, name, "$file.mp3", host, 0xE6101010, null)
    private fun vi(id: String, name: String, file: String, host: String?, answers: List<String>? = null) = Station("vi_$id", Game.GTA_VI, name, "$file.mp3", host, 0x26FFFFFF, null, answers)
    private val byId = all.associateBy { it.id }
    operator fun get(id: String?) = byId[id]
    fun of(game: Game) = all.filter { it.game == game }
}
