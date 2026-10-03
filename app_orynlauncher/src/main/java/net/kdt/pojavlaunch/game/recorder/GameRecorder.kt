package net.kdt.pojavlaunch.game.recorder

import android.content.Context
import kotlinx.coroutines.flow.StateFlow

/**
 * Compatibility facade for the in-game recorder.
 * The actual recorder implementation lives in OrynGameRecorder.
 */
object GameRecorder {
    val state: StateFlow<RecordingState>
        get() = OrynGameRecorder.state

    val elapsedMs: StateFlow<Long>
        get() = OrynGameRecorder.elapsedMs

    @JvmStatic
    fun isRecording(): Boolean = OrynGameRecorder.isRecording()

    @JvmStatic
    fun isIdle(): Boolean = OrynGameRecorder.isIdle()

    @JvmStatic
    fun start(context: Context): Boolean = OrynGameRecorder.start(context)

    @JvmStatic
    fun start(context: Context, liveSource: android.view.View?): Boolean = OrynGameRecorder.start(context, liveSource)

    @JvmStatic
    fun stopAndSave(context: Context) = OrynGameRecorder.stopAndSave(context)

    @JvmStatic
    fun pause() = OrynGameRecorder.pause()

    @JvmStatic
    fun resume() = OrynGameRecorder.resume()

    @JvmStatic
    fun toggleMicrophone() = OrynGameRecorder.toggleMicrophone()
}
