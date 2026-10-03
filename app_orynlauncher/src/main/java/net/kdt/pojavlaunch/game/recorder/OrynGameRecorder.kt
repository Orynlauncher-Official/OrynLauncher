package net.kdt.pojavlaunch.game.recorder

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
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
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * New OrynLauncher game-only recorder.
 * Captures the registered Minecraft surface, not the Android device screen.
 */
object OrynGameRecorder {
    private const val TAG = "OrynGameRecorderV2"
    private const val DEFAULT_FPS = 30

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _state = kotlinx.coroutines.flow.MutableStateFlow(RecordingState.IDLE)
    val state = _state
    private val _elapsedMs = kotlinx.coroutines.flow.MutableStateFlow(0L)
    val elapsedMs = _elapsedMs

    private var encodeJob: Job? = null
    private var timerJob: Job? = null
    private var captureThread: android.os.HandlerThread? = null
    private var captureHandler: android.os.Handler? = null
    private var codec: MediaCodec? = null
    private var encoderSurface: Surface? = null
    private var muxer: MediaMuxer? = null
    private var muxerStarted = false
    private var videoTrack = -1
    private var tempFile: File? = null
    private var appContext: Context? = null
    private var bitmap: Bitmap? = null
    private var width = 0
    private var height = 0
    private var fps = DEFAULT_FPS
    private var startedAt = 0L
    private var nextFrameNs = 0L
    private val frameBusy = AtomicBoolean(false)
    private val stopRequested = AtomicBoolean(false)

    @JvmStatic fun isRecording(): Boolean = _state.value == RecordingState.RECORDING
    @JvmStatic fun isIdle(): Boolean = _state.value == RecordingState.IDLE

    @JvmStatic
    fun start(context: Context): Boolean {
        if (!isIdle()) return false
        val source = GameSurfaceRegistry.getView()
        if (!GameSurfaceRegistry.isReady() || source == null || source.width < 2 || source.height < 2) {
            Log.e(TAG, "Game surface is not ready")
            return false
        }

        val prefs = net.kdt.pojavlaunch.prefs.LauncherPreferences.DEFAULT_PREF
        val quality = prefs?.getString("recorder_quality", "100")
            ?.toIntOrNull()?.coerceIn(25, 100)?.div(100f) ?: 1f
        fps = prefs?.getString("recorder_fps", DEFAULT_FPS.toString())
            ?.toIntOrNull()?.coerceIn(24, 60) ?: DEFAULT_FPS

        width = even((source.width * quality).toInt().coerceAtLeast(2))
        height = even((source.height * quality).toInt().coerceAtLeast(2))

        try {
            appContext = context.applicationContext
            tempFile = File(context.cacheDir, "oryn_game_recording_" + System.currentTimeMillis() + ".mp4")
            muxer = MediaMuxer(tempFile!!.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val bitrate = when {
                quality <= 0.50f -> 3_000_000
                quality <= 0.75f -> 5_000_000
                else -> 8_000_000
            }

            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            format.setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)

            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            codec!!.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoderSurface = codec!!.createInputSurface()
            codec!!.start()

            captureThread = android.os.HandlerThread("OrynGameRecorder").also { it.start() }
            captureHandler = android.os.Handler(captureThread!!.looper)
            frameBusy.set(false)
            stopRequested.set(false)
            nextFrameNs = System.nanoTime()
            startedAt = System.currentTimeMillis()
            _elapsedMs.value = 0L
            _state.value = RecordingState.RECORDING
            startTimer()

            encodeJob = scope.launch { drainEncoder() }
            captureHandler!!.post { scheduleCapture() }
            Log.i(TAG, "Started game recording " + width + "x" + height + " @ " + fps + "fps")
            return true
        } catch (t: Throwable) {
            Log.e(TAG, "Unable to start game recorder", t)
            cleanup()
            return false
        }
    }

    fun stopAndSave(context: Context) {
        if (!isRecording() && _state.value != RecordingState.PAUSED) return
        _state.value = RecordingState.STOPPING
        stopRequested.set(true)
        timerJob?.cancel()
        captureHandler?.post {
            runCatching { codec?.signalEndOfInputStream() }
                .onFailure { Log.w(TAG, "EOS signal failed: " + it.message) }
        }
    }

    fun pause() {
        if (!isRecording()) return
        _state.value = RecordingState.PAUSED
        timerJob?.cancel()
    }

    fun resume() {
        if (_state.value != RecordingState.PAUSED) return
        _state.value = RecordingState.RECORDING
        startTimer()
        nextFrameNs = System.nanoTime()
        captureHandler?.post { scheduleCapture() }
    }

    fun toggleMicrophone() {
        Log.i(TAG, "Microphone is not part of the new game-only recorder")
    }

    private suspend fun drainEncoder() {
        val c = codec ?: return
        val info = MediaCodec.BufferInfo()
        var eos = false
        try {
            while (!eos) {
                when (val index = c.dequeueOutputBuffer(info, 10_000L)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (!muxerStarted) {
                            videoTrack = muxer!!.addTrack(c.outputFormat)
                            muxer!!.start()
                            muxerStarted = true
                        }
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (stopRequested.get()) runCatching { c.signalEndOfInputStream() }
                    }
                    else -> if (index >= 0) {
                        val end = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        val config = (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                        if (muxerStarted && !config && info.size > 0) {
                            c.getOutputBuffer(index)?.let { muxer!!.writeSampleData(videoTrack, it, info) }
                        }
                        c.releaseOutputBuffer(index, false)
                        if (end) eos = true
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Encoder drain failed", t)
        } finally {
            finishAndPublish()
        }
    }

    private fun scheduleCapture() {
        if (_state.value != RecordingState.RECORDING || stopRequested.get()) return
        val handler = captureHandler ?: return
        val now = System.nanoTime()
        val interval = 1_000_000_000L / fps
        if (nextFrameNs <= 0L || nextFrameNs < now - interval * 2) nextFrameNs = now
        val delayMs = ((nextFrameNs - now) / 1_000_000L).coerceAtLeast(0L)
        handler.postDelayed({ captureFrame() }, delayMs)
    }

    private fun captureFrame() {
        if (_state.value != RecordingState.RECORDING || stopRequested.get()) return
        if (!frameBusy.compareAndSet(false, true)) {
            scheduleCapture()
            return
        }

        val source = GameSurfaceRegistry.getView()
        val handler = captureHandler
        if (source == null || handler == null) {
            frameBusy.set(false)
            scheduleCapture()
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
                    if (result == PixelCopy.SUCCESS && _state.value == RecordingState.RECORDING) {
                        writeBitmapFrame(bmp)
                    } else if (result != PixelCopy.SUCCESS) {
                        Log.w(TAG, "PixelCopy failed: " + result)
                    }
                    frameBusy.set(false)
                    scheduleCapture()
                }, handler)
            }
            is TextureView -> {
                try {
                    val bmp = source.getBitmap(source.width.coerceAtLeast(2), source.height.coerceAtLeast(2))
                    if (bmp != null && _state.value == RecordingState.RECORDING) writeBitmapFrame(bmp)
                    if (bmp != null && bmp !== bitmap) bmp.recycle()
                } catch (t: Throwable) {
                    Log.w(TAG, "Texture capture failed: " + t.message)
                } finally {
                    frameBusy.set(false)
                    scheduleCapture()
                }
            }
            else -> {
                frameBusy.set(false)
                scheduleCapture()
            }
        }
    }

    private fun writeBitmapFrame(source: Bitmap) {
        val surface = encoderSurface ?: return
        try {
            val canvas = surface.lockCanvas(null)
            try {
                canvas.drawColor(android.graphics.Color.BLACK)
                canvas.drawBitmap(source, null, android.graphics.Rect(0, 0, width, height), null)
            } finally {
                surface.unlockCanvasAndPost(canvas)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Encoder surface frame failed: " + t.message)
        }
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

    private fun finishAndPublish() {
        try {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            codec = null
            runCatching { encoderSurface?.release() }
            encoderSurface = null
            if (muxerStarted) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            muxer = null
            muxerStarted = false

            val file = tempFile
            val context = appContext
            if (file == null || !file.exists() || file.length() <= 0L || context == null) {
                throw IOException("Recorder produced no MP4")
            }
            if (!validVideo(file)) throw IOException("Recorder produced an invalid MP4")

            val name = "OrynLauncher_Recording_" +
                SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(Date()) + ".mp4"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                publishMediaStore(context, file, name)
            } else {
                publishLegacy(context, file, name)
            }
            Log.i(TAG, "Recording saved: " + name)
        } catch (t: Throwable) {
            Log.e(TAG, "Recording save failed", t)
        } finally {
            cleanup()
        }
    }

    private fun publishMediaStore(context: Context, file: File, name: String) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/OrynLauncher Recordings")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        var uri: Uri? = null
        try {
            uri = resolver.insert(collection, values) ?: throw IOException("MediaStore insert failed")
            resolver.openOutputStream(uri, "w")?.use { out ->
                FileInputStream(file).use { input -> input.copyTo(out, 1024 * 1024) }
            } ?: throw IOException("MediaStore output stream unavailable")
            resolver.update(uri, ContentValues().apply {
                put(MediaStore.Video.Media.IS_PENDING, 0)
            }, null, null)
            remember(context, uri, name)
            Log.i(TAG, "Published MediaStore URI: " + uri)
        } catch (t: Throwable) {
            if (uri != null) runCatching { resolver.delete(uri, null, null) }
            throw t
        }
    }

    @Suppress("DEPRECATION")
    private fun publishLegacy(context: Context, file: File, name: String) {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            "OrynLauncher Recordings"
        )
        if (!dir.exists() && !dir.mkdirs()) throw IOException("Cannot create recording directory")
        val destination = File(dir, name)
        FileInputStream(file).use { input ->
            destination.outputStream().use { output -> input.copyTo(output, 1024 * 1024) }
        }
        if (!destination.exists() || destination.length() <= 0L) {
            throw IOException("Legacy recording copy failed")
        }
        MediaScannerConnection.scanFile(
            context, arrayOf(destination.absolutePath), arrayOf("video/mp4")
        ) { _, uri -> if (uri != null) remember(context, uri, name) }
    }

    private fun remember(context: Context, uri: Uri, name: String) {
        val prefs = context.getSharedPreferences("oryn_recorder_library", Context.MODE_PRIVATE)
        val old = prefs.getStringSet("recordings", emptySet())?.toMutableSet() ?: mutableSetOf()
        old.add(uri.toString() + "|" + name)
        prefs.edit().putStringSet("recordings", old).apply()
    }

    private fun validVideo(file: File): Boolean {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            (0 until extractor.trackCount).any {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
            }
        } catch (_: Throwable) {
            false
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun even(value: Int): Int = (value.coerceAtLeast(2) / 2) * 2

    private fun cleanup() {
        frameBusy.set(false)
        stopRequested.set(false)
        timerJob?.cancel()
        timerJob = null
        encodeJob = null
        captureThread?.quitSafely()
        captureThread = null
        captureHandler = null
        runCatching { bitmap?.recycle() }
        bitmap = null
        runCatching { tempFile?.delete() }
        tempFile = null
        appContext = null
        _elapsedMs.value = 0L
        _state.value = RecordingState.IDLE
    }
}
