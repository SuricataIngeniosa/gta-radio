package ar.nallib.gtaradio.service

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player

/**
 * Lo que ven la pantalla de bloqueo, la notificación, Android Auto y la app.
 * La cola son los temas de la emisora puesta; en una radio no se saltan temas: anterior/siguiente siempre cambian
 * de emisora (circular), y eso lo resuelve el servicio con [switchStation].
 */
class StationSwitchingPlayer(
    player: Player,
    private val switchStation: (delta: Int) -> Unit,
    private val stationCount: () -> Int,
) : ForwardingPlayer(player) {

    override fun seekToPrevious() = switchStation(-1)
    override fun seekToPreviousMediaItem() = switchStation(-1)
    override fun seekToNext() = switchStation(1)
    override fun seekToNextMediaItem() = switchStation(1)

    override fun hasPreviousMediaItem() = stationCount() > 1
    override fun hasNextMediaItem() = stationCount() > 1

    override fun isCommandAvailable(command: Int) = when (command) {
        COMMAND_SEEK_TO_PREVIOUS, COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
        COMMAND_SEEK_TO_NEXT, COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> stationCount() > 1
        else -> super.isCommandAvailable(command)
    }

    override fun getAvailableCommands(): Player.Commands {
        val b = super.getAvailableCommands().buildUpon()
        val nav = intArrayOf(COMMAND_SEEK_TO_PREVIOUS, COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, COMMAND_SEEK_TO_NEXT, COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
        if (stationCount() > 1) b.addAll(*nav) else b.removeAll(*nav)
        return b.build()
    }
}
