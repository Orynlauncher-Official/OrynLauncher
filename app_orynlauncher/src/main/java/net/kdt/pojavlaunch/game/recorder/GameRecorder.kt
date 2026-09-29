/*
 * OrynLauncher Recorder
 * Video recording implementation adapted from Zalith Launcher 2
 * under the GNU GPL v3.0.
 */

package net.kdt.pojavlaunch.game.recorder

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.view.PixelCopy
import android.view.Surface
import android.view.SurfaceView
import android.view.TextureView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "OrynLauncherRecorder"
private const val DEFAULT_FPS = 30

object GameRecorder {
    private val _state = kotlinx.coroutines.flow.MutableStateFlow(RecordingState.IDLE)
    val state = _state.asStateFlow()
    private val _elapsedMs = kotlinx.coroutines.flow.MutableStateFlow(0L)
    val elapsedMs = _elapsedMs.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var encodeJob: Job? = null
    private var timerJob: Job? = null
    private var captureThread: android.os.HandlerThread? = null
    private var captureHandler: android.os.Handler? = null
    private var codec: MediaCodec? = null
    private var inputSurface: Surface? = null
    private var glEncoderSurface: GlVideoEncoderSurface? = null
    private var recordingStartNs = 0L
    private var muxer: MediaMuxer? = null
    private var tempOutputFile: File? = null
    private var outputUri: Uri? = null
    private var bitmap: Bitmap? = null
    private var width = 0
    private var height = 0
    private var fps = DEFAULT_FPS
    private val captureRunning = AtomicBoolean(false)
    private val frameInFlight = AtomicBoolean(false)
    private var nextFrameNs = 0L
    private var startedAt = 0L
    private var appContext: Context? = null

    @JvmStatic fun isRecording(): Boolean = _state.value == RecordingState.RECORDING
    @JvmStatic fun isIdle(): Boolean = _state.value == RecordingState.IDLE

    @JvmStatic
    fun start(context: Context): Boolean {
        if (!isIdle()) return false
        val source = GameSurfaceRegistry.getView()
        if (!GameSurfaceRegistry.isReady() || source == null || source.width < 2 || source.height < 2) {
            Log.e(TAG, "OrynLauncher game surface is not ready")
            return false
        }

        val prefs = net.kdt.pojavlaunch.prefs.LauncherPreferences.DEFAULT_PREF
        val quality = prefs?.getString("recorder_quality", "100")
            ?.toIntOrNull()?.coerceIn(25, 100)?.div(100f) ?: 1f
        fps = prefs?.getString("recorder_fps", DEFAULT_FPS.toString())
            ?.toIntOrNull()?.coerceIn(24, 60) ?: DEFAULT_FPS
        width = ((source.width * quality).toInt().coerceAtLeast(2) / 2) * 2
        height = ((source.height * quality).toInt().coerceAtLeast(2) / 2) * 2
        val bitrate = when {
            quality <= 0.5f -> 3_000_000
            quality <= 0.75f -> 5_000_000
            else -> 6_000_000
        }

        try {
            appContext = context.applicationContext
            tempOutputFile = File(
                context.cacheDir,
                "oryn_recording_${System.currentTimeMillis()}.mp4"
            )
            muxer = MediaMuxer(
                tempOutputFile!!.absolutePath,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            )

            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            codec!!.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = codec!!.createInputSurface()
            glEncoderSurface = GlVideoEncoderSurface(inputSurface!!)
            codec!!.start()

            captureThread = android.os.HandlerThread("OrynLauncher-Recorder").also { it.start() }
            captureHandler = android.os.Handler(captureThread!!.looper)
            captureRunning.set(true)
            frameInFlight.set(false)
            nextFrameNs = System.nanoTime()
            startedAt = System.currentTimeMillis()
            _elapsedMs.value = 0L
            _state.value = RecordingState.RECORDING
            startTimer()

            encodeJob = scope.launch { drainEncoder() }
            captureHandler!!.post { scheduleFrame() }
            Log.i(TAG, "OrynLauncher recording started: ${width}x${height}@@${fps}")
            return true
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to start OrynLauncher recording", e)
            cleanup(deleteOutput = true)
            return false
        }
    }

    fun stopAndSave(context: Context) {
        if (!isRecording() && _state.value != RecordingState.PAUSED) return
        _state.value = RecordingState.STOPPING
        captureRunning.set(false)
        frameInFlight.set(false)
        nextFrameNs = 0L
        timerJob?.cancel()
        runCatching { codec?.signalEndOfInputStream() }
        if (encodeJob == null) finishRecording()
    }

    fun pause() {
        if (!isRecording()) return
        captureRunning.set(false)
        _state.value = RecordingState.PAUSED
        timerJob?.cancel()
    }

    fun resume() {
        if (_state.value != RecordingState.PAUSED) return
        captureRunning.set(true)
        _state.value = RecordingState.RECORDING
        startTimer()
        captureHandler?.post { scheduleFrame() }
    }

    fun toggleMicrophone() {
        Log.i(TAG, "Microphone capture is not enabled for OrynLauncher video-only recorder")
    }

    private suspend fun drainEncoder() {
        val c = codec ?: return
        val info = MediaCodec.BufferInfo()
        var track = -1
        var muxerStarted = false
        var eos = false

        while (currentCoroutineContext().isActive && !eos) {
            when (val index = c.dequeueOutputBuffer(info, 10_000L)) {
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (!muxerStarted) {
                        track = muxer!!.addTrack(c.outputFormat)
                        muxer!!.start()
                        muxerStarted = true
                    }
                }
                MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (_state.value == RecordingState.STOPPING) {
                        runCatching { c.signalEndOfInputStream() }
                    }
                }
                else -> if (index >= 0) {
                    val eosFlag = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    val configFlag = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    if (muxerStarted && !configFlag && info.size > 0) {
                        c.getOutputBuffer(index)?.let { buffer ->
                            muxer!!.writeSampleData(track, buffer, info)
                        }
                    }
                    c.releaseOutputBuffer(index, false)
                    if (eosFlag) eos = true
                }
            }
        }
        finishRecording()
    }

    private fun scheduleFrame() {
        if (!captureRunning.get() || _state.value != RecordingState.RECORDING) return
        val handler = captureHandler ?: return
        val now = System.nanoTime()
        val intervalNs = 1_000_000_000L / fps
        if (nextFrameNs <= 0L) nextFrameNs = now
        while (nextFrameNs <= now) nextFrameNs += intervalNs
        val delayNs = nextFrameNs - now
        handler.postDelayed({ captureFrame() }, (delayNs / 1_000_000L).coerceAtLeast(0L))
    }

    private fun captureFrame() {
        if (!captureRunning.get() || _state.value != RecordingState.RECORDING) return
        if (!frameInFlight.compareAndSet(false, true)) {
            scheduleFrame()
            return
        }

        val source = GameSurfaceRegistry.getView()
        val out = inputSurface
        if (source == null || out == null) {
            frameInFlight.set(false)
            scheduleFrame()
            return
        }

        nextFrameNs += 1_000_000_000L / fps

        when (source) {
            is SurfaceView -> {
                val w = source.width.coerceAtLeast(2)
                val h = source.height.coerceAtLeast(2)
                val bmp = bitmap?.takeIf { !it.isRecycled && it.width == w && it.height == h }
                    ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bitmap = it }

                PixelCopy.request(source, bmp, { result ->
                    if (result == PixelCopy.SUCCESS &&
                        captureRunning.get() &&
                        _state.value == RecordingState.RECORDING
                    ) {
                        drawFrame(bmp, out)
                    }
                    frameInFlight.set(false)
                    scheduleFrame()
                }, captureHandler!!)
            }
            is TextureView -> {
                try {
                    val bmp = source.getBitmap(source.width.coerceAtLeast(2), source.height.coerceAtLeast(2))
                    if (bmp != null &&
                        captureRunning.get() &&
                        _state.value == RecordingState.RECORDING
                    ) {
                        drawFrame(bmp, out)
                    }
                    bmp?.recycle()
                } finally {
                    frameInFlight.set(false)
                    scheduleFrame()
                }
            }
            else -> {
                frameInFlight.set(false)
                scheduleFrame()
            }
        }
    }

    private fun drawFrame(bmp: Bitmap, out: Surface) {
        runCatching {
            val renderer = glEncoderSurface ?: return
            if (recordingStartNs == 0L) recordingStartNs = System.nanoTime()
            val ptsNs = System.nanoTime() - recordingStartNs
            renderer.draw(bmp, width, height, ptsNs.coerceAtLeast(1L))
        }.onFailure { Log.w(TAG, "OrynLauncher frame encode failed: ${it.message}") }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive && (_state.value == RecordingState.RECORDING || _state.value == RecordingState.PAUSED)) {
                _elapsedMs.value = System.currentTimeMillis() - startedAt
                delay(250L)
            }
        }
    }

    private fun finishRecording() {
        try {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            codec = null
            glEncoderSurface?.release()
            glEncoderSurface = null
            inputSurface?.release()
            inputSurface = null
            runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            muxer = null
            val completedFile = tempOutputFile
            val context = appContext
            if (completedFile == null || !completedFile.exists() || completedFile.length() <= 0L || context == null) {
                throw IOException("OrynLauncher recording did not produce a valid MP4")
            }

            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, "OrynLauncher_Recording_${ts}.mp4")
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/OrynLauncher Recordings/")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }

            val uri = context.contentResolver.insert(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values
            ) ?: throw IOException("Failed to create OrynLauncher MediaStore entry")

            try {
                context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                    FileInputStream(completedFile).use { input ->
                        input.copyTo(output, 1024 * 1024)
                    }
                } ?: throw IOException("Failed to open OrynLauncher MediaStore output")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val publish = ContentValues().apply {
                        put(MediaStore.Video.Media.IS_PENDING, 0)
                    }
                    context.contentResolver.update(uri, publish, null, null)
                }

                outputUri = uri
                Log.i(TAG, "OrynLauncher recording saved: $uri (${completedFile.length()} bytes)")
            } catch (e: Throwable) {
                runCatching { context.contentResolver.delete(uri, null, null) }
                throw e
            } finally {
                runCatching { completedFile.delete() }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to save OrynLauncher recording", e)
        } finally {
            cleanup(deleteOutput = false)
        }
    }

    private fun cleanup(deleteOutput: Boolean) {
        captureRunning.set(false)
        timerJob?.cancel()
        timerJob = null
        encodeJob = null
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        codec = null
        runCatching { glEncoderSurface?.release() }
        glEncoderSurface = null
        runCatching { inputSurface?.release() }
        inputSurface = null
        runCatching { muxer?.release() }
        muxer = null

        if (deleteOutput) {
            outputUri?.let { uri -> runCatching { appContext?.contentResolver?.delete(uri, null, null) } }
        }

        captureThread?.quitSafely()
        captureThread = null
        captureHandler = null
        runCatching { bitmap?.recycle() }
        bitmap = null
        outputUri = null
        runCatching { tempOutputFile?.delete() }
        tempOutputFile = null
        recordingStartNs = 0L
        appContext = null
        _elapsedMs.value = 0L
        _state.value = RecordingState.IDLE
    }

}
