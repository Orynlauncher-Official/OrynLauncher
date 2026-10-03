package net.kdt.pojavlaunch.game.recorder;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Captures Android playback audio (game sound) without capturing the device screen.
 * Android 10+ only. The MediaProjection token is used for audio permission.
 */
final class GameAudioCapture {
    private static final String TAG = "OrynGameAudio";
    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNELS = 2;

    private final Context context;
    private final MediaProjection projection;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private AudioRecord audioRecord;
    private MediaCodec encoder;
    private Thread thread;
    private File sampleFile;
    private MediaFormat format;

    GameAudioCapture(Context context, MediaProjection projection) {
        this.context = context.getApplicationContext();
        this.projection = projection;
    }

    void start() throws Exception {
        if (running.get()) return;

        AudioPlaybackCaptureConfiguration captureConfig =
                new AudioPlaybackCaptureConfiguration.Builder(projection)
                        .addMatchingUsage(AudioAttributes.USAGE_GAME)
                        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                        .build();

        int minBuffer = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
        );
        if (minBuffer <= 0) throw new IOException("Invalid audio buffer size");

        int bufferSize = Math.max(minBuffer * 2, 8192);

        audioRecord = new AudioRecord.Builder()
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                        .build())
                .setBufferSizeInBytes(bufferSize)
                .setAudioPlaybackCaptureConfig(captureConfig)
                .build();

        MediaFormat audioFormat = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, CHANNELS);
        audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE,
                MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, 128000);
        audioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, bufferSize);

        encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
        encoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        encoder.start();

        sampleFile = new File(context.getCacheDir(),
                "oryn_audio_" + System.currentTimeMillis() + ".bin");

        running.set(true);
        audioRecord.startRecording();
        thread = new Thread(() -> captureLoop(bufferSize), "OrynGameAudio");
        thread.start();
    }

    private void captureLoop(int bufferSize) {
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(sampleFile))) {
            short[] pcm = new short[bufferSize / 2];
            long samples = 0L;
            boolean eosQueued = false;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

            while (running.get() || !eosQueued) {
                if (running.get()) {
                    int read = audioRecord.read(pcm, 0, pcm.length);
                    if (read > 0) {
                        int bytes = read * 2;
                        int index = encoder.dequeueInputBuffer(10000);
                        if (index >= 0) {
                            ByteBuffer input = encoder.getInputBuffer(index);
                            if (input != null) {
                                input.clear();
                                input.asShortBuffer().put(pcm, 0, read);
                                long ptsUs = samples * 1000000L / SAMPLE_RATE;
                                encoder.queueInputBuffer(index, 0, bytes, ptsUs, 0);
                                samples += read / CHANNELS;
                            }
                        }
                    }
                } else if (!eosQueued) {
                    int index = encoder.dequeueInputBuffer(10000);
                    if (index >= 0) {
                        encoder.queueInputBuffer(index, 0, 0,
                                samples * 1000000L / SAMPLE_RATE,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        eosQueued = true;
                    }
                }

                drainEncoder(out, info, !running.get());
            }

            // Drain any final encoded packets.
            for (int i = 0; i < 100; i++) {
                drainEncoder(out, info, true);
                if (!running.get() && encoder == null) break;
            }
        } catch (Throwable t) {
            Log.e(TAG, "Game audio capture failed", t);
        }
    }

    private void drainEncoder(DataOutputStream out, MediaCodec.BufferInfo info,
                              boolean waitForEos) throws IOException {
        if (encoder == null) return;
        while (true) {
            int index = encoder.dequeueOutputBuffer(info, waitForEos ? 10000 : 0);
            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                format = encoder.getOutputFormat();
                continue;
            }
            if (index < 0) return;

            if (info.size > 0 && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                ByteBuffer data = encoder.getOutputBuffer(index);
                if (data != null) {
                    data.position(info.offset);
                    data.limit(info.offset + info.size);
                    byte[] bytes = new byte[info.size];
                    data.get(bytes);
                    out.writeLong(info.presentationTimeUs);
                    out.writeInt(bytes.length);
                    out.write(bytes);
                }
            }

            boolean eos = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
            encoder.releaseOutputBuffer(index, false);
            if (eos) {
                // Keep encoder alive until stopAndWait() releases it.
                return;
            }
            if (!waitForEos) return;
        }
    }

    void stopAndWait() {
        if (!running.getAndSet(false)) return;
        try { audioRecord.stop(); } catch (Throwable ignored) {}
        if (thread != null) {
            try { thread.join(3000L); } catch (InterruptedException ignored) {}
        }
        try { encoder.stop(); } catch (Throwable ignored) {}
        try { encoder.release(); } catch (Throwable ignored) {}
        try { audioRecord.release(); } catch (Throwable ignored) {}
        encoder = null;
        audioRecord = null;
    }

    boolean hasUsableAudio() {
        return sampleFile != null && sampleFile.exists() && sampleFile.length() > 16
                && format != null;
    }

    MediaFormat getFormat() {
        return format;
    }

    void writeSamplesTo(android.media.MediaMuxer muxer, int track) throws IOException {
        if (!hasUsableAudio()) return;
        try (DataInputStream in = new DataInputStream(new FileInputStream(sampleFile))) {
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            while (true) {
                try {
                    long pts = in.readLong();
                    int size = in.readInt();
                    if (size <= 0 || size > 1024 * 1024) throw new IOException("Invalid audio sample");
                    byte[] data = new byte[size];
                    in.readFully(data);
                    ByteBuffer buffer = ByteBuffer.wrap(data);
                    info.offset = 0;
                    info.size = size;
                    info.presentationTimeUs = pts;
                    info.flags = 0;
                    muxer.writeSampleData(track, buffer, info);
                } catch (java.io.EOFException end) {
                    break;
                }
            }
        } finally {
            runCatchingDelete();
        }
    }

    private void runCatchingDelete() {
        try { if (sampleFile != null) sampleFile.delete(); } catch (Throwable ignored) {}
    }
}
