package dev.raul.tvhome

import android.app.Activity
import android.graphics.Color
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaFormat
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Full-screen receiver for the Mac's screen. Started by scripts/mirror-tv over ADB:
 *   am start -n dev.raul.tvhome/.MirrorActivity --es from <mac ip>
 *
 * Listens on [PORT] and only accepts the Mac whose address came in "from".
 * Closes itself when the Mac stops streaming, or if nothing connects in [WAIT_MS].
 *
 * Wire format, one packet after another (big-endian):
 *   type: u8, ptsUs: i64, size: i32, payload: size bytes
 *   CONFIG: codec u8 (0 = H.264, 1 = HEVC), width u16, height u16, then Annex B parameter sets
 *   VIDEO:  one Annex B access unit
 *   AUDIO:  PCM s16le, 48 kHz, stereo
 * Back to the Mac: the ptsUs (i64) of each video frame as it is shown, to measure the delay.
 */
/** A smooth-mode frame waiting to be decoded at [at] (System.nanoTime). */
private class Frame(val at: Long, val data: ByteArray, val pts: Long)

class MirrorActivity : Activity(), SurfaceHolder.Callback {
    private lateinit var video: SurfaceView
    private lateinit var status: TextView
    private val main = Handler(Looper.getMainLooper())
    private var server: ServerSocket? = null
    private var client: Socket? = null
    @Volatile private var surface: Surface? = null
    @Volatile private var running = true
    private var wifiLock: WifiManager.WifiLock? = null
    /** 0 = show frames the moment they arrive; otherwise hold them this long and play at an even pace. */
    private var smoothMs = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        video = SurfaceView(this).apply { holder.addCallback(this@MirrorActivity) }
        status = TextView(this).apply {
            text = "Waiting for the Mac…"
            setTextColor(Color.WHITE)
            textSize = 24f
            gravity = Gravity.CENTER
        }
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(video, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
            addView(status, FrameLayout.LayoutParams(-1, -1))
        })
        main.postDelayed({ if (client == null) finish() }, WAIT_MS)
    }

    override fun onResume() {
        super.onResume()
        // Without this the TV's Wi-Fi naps between router beacons and every ~2 s a frame arrives
        // ~100 ms late, which makes video hitch. Only held while the Mirror screen is in front.
        val wifi = applicationContext.getSystemService(WifiManager::class.java)
        wifiLock = wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, TAG).apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    override fun onPause() {
        wifiLock?.release()
        wifiLock = null
        super.onPause()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        surface = holder.surface
        if (server == null) {
            val allowed = intent.getStringExtra("from")
            smoothMs = intent.getIntExtra("smoothMs", 0)
            thread(name = "mirror") { serve(allowed) }
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surface = null
        finish()
    }

    override fun onDestroy() {
        running = false
        runCatching { client?.close() }
        runCatching { server?.close() }
        super.onDestroy()
    }

    private fun serve(allowed: String?) {
        try {
            val srv = ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(PORT)) }
            server = srv
            while (running) {
                val s = srv.accept()
                val from = s.inetAddress.hostAddress
                if (allowed != null && from != allowed) {
                    Log.w(TAG, "Refused connection from $from")
                    s.close()
                    continue
                }
                client = s
                // A connection that closes before sending anything is mirror-tv checking the port.
                if (stream(s)) break
                client = null
            }
        } catch (e: Exception) {
            if (running) Log.e(TAG, "Mirror stopped", e)
        }
        main.post { finish() }
    }

    /** Returns true once anything was received, so the caller knows the stream really ran. */
    private fun stream(s: Socket): Boolean {
        s.tcpNoDelay = true
        s.receiveBufferSize = 1 shl 20
        val input = DataInputStream(s.getInputStream().buffered(1 shl 16))
        val sink = VideoSink(DataOutputStream(s.getOutputStream()))
        val audio = AudioSink(smoothMs)
        var received = false
        var audioPackets = 0
        var statsAt = System.nanoTime()
        try {
            var buf = ByteArray(1 shl 20)
            while (running) {
                if (System.nanoTime() - statsAt > 2_000_000_000L) {
                    Log.i(TAG, "last 2s: ${sink.stats()}, audio $audioPackets")
                    audioPackets = 0
                    statsAt = System.nanoTime()
                }
                val type = input.readUnsignedByte()
                if (!received) {
                    received = true
                    main.post { status.text = "" }
                }
                val pts = input.readLong()
                val size = input.readInt()
                if (size > buf.size) buf = ByteArray(size)
                input.readFully(buf, 0, size)
                when (type) {
                    CONFIG -> {
                        val codec = buf[0].toInt()
                        val w = ((buf[1].toInt() and 0xff) shl 8) or (buf[2].toInt() and 0xff)
                        val h = ((buf[3].toInt() and 0xff) shl 8) or (buf[4].toInt() and 0xff)
                        val csd = buf.copyOfRange(5, size)
                        if (sink.configure(codec, w, h, csd)) main.post { fit(w, h) }
                    }
                    VIDEO -> sink.decode(buf, size, pts)
                    AUDIO -> { audio.play(buf, size); audioPackets++ }
                }
            }
        } catch (_: EOFException) {
            // The Mac stopped streaming.
        } finally {
            sink.close()
            audio.release()
        }
        return received
    }

    /** Letterboxes the picture so a 16:10 Mac screen keeps its shape on the 16:9 TV. */
    private fun fit(w: Int, h: Int) {
        val root = video.parent as FrameLayout
        val scale = minOf(root.width / w.toFloat(), root.height / h.toFloat())
        video.layoutParams = FrameLayout.LayoutParams((w * scale).toInt(), (h * scale).toInt(), Gravity.CENTER)
    }

    /**
     * Hardware decoder in low-latency mode; frames are shown the moment they are decoded.
     * With smooth mode, frames first wait in [pending] and are decoded on the Mac's own timing plus
     * [smoothMs], so Wi-Fi pauses shorter than that don't show as stutter.
     * [acks] gets each shown frame's timestamp back, so the Mac can log the real delay.
     */
    private inner class VideoSink(private val acks: DataOutputStream) {
        private val pending = LinkedBlockingQueue<Frame>()
        /** Smallest (arrival - Mac timestamp) seen: the fastest a frame has ever made it here. */
        private var base = Long.MAX_VALUE
        private val pacer = if (smoothMs > 0) thread(name = "mirror-pace") { pace() } else null
        @Volatile private var codec: MediaCodec? = null
        private var queued = 0
        private var noInput = 0
        @Volatile private var shown = 0

        fun stats(): String {
            val s = "frames in $queued, no decoder slot $noInput, shown $shown"
            queued = 0; noInput = 0; shown = 0
            return s
        }
        private var csd: ByteArray? = null

        /** Returns true when the picture size or codec changed. */
        fun configure(kind: Int, w: Int, h: Int, params: ByteArray): Boolean {
            if (codec != null && params.contentEquals(csd)) return false
            release()
            val target = surface ?: return false
            val mime = if (kind == 1) MediaFormat.MIMETYPE_VIDEO_HEVC else MediaFormat.MIMETYPE_VIDEO_AVC
            val format = MediaFormat.createVideoFormat(mime, w, h).apply {
                setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
                // A full-resolution Mac keyframe can be bigger than the decoder's 2 MB default.
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, MAX_FRAME_BYTES)
                setInteger(MediaFormat.KEY_PRIORITY, 0)
                setByteBuffer("csd-0", ByteBuffer.wrap(params))
            }
            val c = MediaCodec.createDecoderByType(mime).apply {
                configure(format, target, null, 0)
                start()
            }
            codec = c
            csd = params
            thread(name = "mirror-out") { drain(c) }
            return true
        }

        fun decode(data: ByteArray, size: Int, pts: Long) {
            if (pacer == null) return feed(data, size, pts)
            val offset = System.nanoTime() - pts * 1000
            // Creeps up slowly so the Mac's and TV's clocks drifting apart can't build up delay.
            base = if (base == Long.MAX_VALUE) offset else minOf(offset, base + BASE_CREEP_NS)
            pending.put(Frame(pts * 1000 + base + smoothMs * 1_000_000L, data.copyOf(size), pts))
        }

        private fun pace() {
            try {
                while (running) {
                    val f = pending.take()
                    val wait = f.at - DECODE_LEAD_NS - System.nanoTime()
                    if (wait > 0) Thread.sleep(wait / 1_000_000, (wait % 1_000_000).toInt())
                    feed(f.data, f.data.size, f.pts)
                }
            } catch (_: InterruptedException) {
            }
        }

        private fun feed(data: ByteArray, size: Int, pts: Long) {
            val c = codec ?: return
            val index = c.dequeueInputBuffer(INPUT_TIMEOUT_US)
            if (index < 0) {
                noInput++
                return
            }
            queued++
            val input = c.getInputBuffer(index)!!.apply { clear() }
            if (size > input.capacity()) {
                // Too big to decode: skip it and queue an empty buffer so the slot is returned.
                c.queueInputBuffer(index, 0, 0, pts, 0)
                return
            }
            input.put(data, 0, size)
            c.queueInputBuffer(index, 0, size, pts, 0)
        }

        /** Own thread, so a decoded frame is shown without waiting for the next packet. */
        private fun drain(c: MediaCodec) {
            val info = MediaCodec.BufferInfo()
            try {
                while (codec === c) {
                    val out = c.dequeueOutputBuffer(info, OUTPUT_TIMEOUT_US)
                    if (out >= 0) {
                        c.releaseOutputBuffer(out, true)
                        shown++
                        runCatching { acks.writeLong(info.presentationTimeUs) }
                    }
                }
            } catch (_: IllegalStateException) {
                // Released while waiting.
            }
        }

        /** End of the stream: also stops the smooth-mode pacer. */
        fun close() {
            pacer?.interrupt()
            release()
        }

        /** Frees the decoder; [configure] calls this before setting up a new one. */
        fun release() {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            codec = null
            csd = null
        }
    }

    /**
     * Plays sound through a small jitter buffer on its own thread. Wi-Fi delivers packets in bursts;
     * the queue evens them out, and if it grows past [LATE_CHUNKS] the oldest audio is dropped so
     * sound never drifts behind the picture.
     */
    /** [smoothMs]: extra sound held back so it stays in sync with smooth-mode video. */
    private class AudioSink(smoothMs: Int) {
        private val prime = PRIME_CHUNKS + smoothMs / CHUNK_MS
        private val late = prime + LATE_CHUNKS - PRIME_CHUNKS
        private val queue = ArrayBlockingQueue<ByteArray>(late + 4)
        @Volatile private var playing = true
        private val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(2 * AudioTrack.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT
            ))
            .build()
        private val player = thread(name = "mirror-audio") { run() }

        fun play(data: ByteArray, size: Int) {
            val chunk = data.copyOf(size)
            while (queue.size >= late) queue.poll()
            queue.offer(chunk)
        }

        private fun run() {
            try {
                while (playing) {
                    // Build up a little sound before starting, and again after running dry.
                    while (playing && queue.size < prime) Thread.sleep(5)
                    track.play()
                    while (playing) {
                        val chunk = queue.poll(100, TimeUnit.MILLISECONDS) ?: break
                        track.write(chunk, 0, chunk.size)
                    }
                    track.pause()
                }
            } catch (_: InterruptedException) {
            }
        }

        fun release() {
            playing = false
            player.interrupt()
            player.join(500)
            runCatching { track.stop() }
            track.release()
        }
    }

    private companion object {
        const val TAG = "Mirror"
        const val PORT = 7070
        const val WAIT_MS = 30_000L
        const val CONFIG = 1
        const val VIDEO = 2
        const val AUDIO = 3
        const val INPUT_TIMEOUT_US = 50_000L
        const val OUTPUT_TIMEOUT_US = 20_000L
        const val MAX_FRAME_BYTES = 8 shl 20
        const val SAMPLE_RATE = 48_000
        /** The Mac sends sound in 20 ms chunks: start with 60 ms queued, never hold more than 200 ms. */
        const val PRIME_CHUNKS = 3
        const val LATE_CHUNKS = 10
        const val CHUNK_MS = 20
        /** Start decoding this long before a smooth-mode frame is due, to cover decode time. */
        const val DECODE_LEAD_NS = 8_000_000L
        /** 2 µs per frame: far more than real clock drift, far less than a frame. */
        const val BASE_CREEP_NS = 2_000L
    }
}
