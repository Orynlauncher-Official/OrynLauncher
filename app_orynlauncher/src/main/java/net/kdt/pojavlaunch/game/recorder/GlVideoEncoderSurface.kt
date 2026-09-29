package net.kdt.pojavlaunch.game.recorder

import android.graphics.Bitmap
import android.opengl.EGL14
import android.opengl.EGLExt
import android.opengl.GLES20
import android.opengl.GLUtils
import android.view.Surface
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Hardware accelerated renderer for a MediaCodec input Surface.
 *
 * MediaCodec input surfaces are intended to be rendered with OpenGL ES.
 * Using Surface.lockCanvas() can produce unstable timestamps/frames on
 * some Android devices, so OrynLauncher Recorder uses EGL here.
 */
class GlVideoEncoderSurface(private val surface: Surface) {
    private var display = EGL14.EGL_NO_DISPLAY
    private var context = EGL14.EGL_NO_CONTEXT
    private var eglSurface = EGL14.EGL_NO_SURFACE
    private var textureId = 0
    private var program = 0
    private var positionLoc = -1
    private var texCoordLoc = -1
    private var textureLoc = -1
    private val released = AtomicBoolean(false)

    init {
        setupEgl()
    }

    private fun setupEgl() {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(display != EGL14.EGL_NO_DISPLAY) { "Unable to get EGL display" }

        val version = IntArray(2)
        check(EGL14.eglInitialize(display, version, 0, version, 1)) { "Unable to initialize EGL" }

        val attribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, 4, // EGL_OPENGL_ES2_BIT
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
        val num = IntArray(1)
        check(EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, num, 0) && num[0] > 0) {
            "Unable to choose EGL config"
        }

        val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        context = EGL14.eglCreateContext(
            display, configs[0], EGL14.EGL_NO_CONTEXT, contextAttribs, 0
        )
        check(context != EGL14.EGL_NO_CONTEXT) { "Unable to create EGL context" }

        eglSurface = EGL14.eglCreateWindowSurface(
            display, configs[0], surface, intArrayOf(EGL14.EGL_NONE), 0
        )
        check(eglSurface != EGL14.EGL_NO_SURFACE) { "Unable to create EGL window surface" }
        makeCurrent()

        val vertex = """
            attribute vec4 aPosition;
            attribute vec2 aTexCoord;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = aPosition;
                vTexCoord = aTexCoord;
            }
        """.trimIndent()

        val fragment = """
            precision mediump float;
            uniform sampler2D uTexture;
            varying vec2 vTexCoord;
            void main() {
                gl_FragColor = texture2D(uTexture, vTexCoord);
            }
        """.trimIndent()

        program = linkProgram(compileShader(GLES20.GL_VERTEX_SHADER, vertex),
            compileShader(GLES20.GL_FRAGMENT_SHADER, fragment))
        positionLoc = GLES20.glGetAttribLocation(program, "aPosition")
        texCoordLoc = GLES20.glGetAttribLocation(program, "aTexCoord")
        textureLoc = GLES20.glGetUniformLocation(program, "uTexture")

        val tex = IntArray(1)
        GLES20.glGenTextures(1, tex, 0)
        textureId = tex[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    }

    fun draw(bitmap: Bitmap, width: Int, height: Int, presentationTimeNs: Long) {
        if (released.get() || bitmap.isRecycled) return
        makeCurrent()

        GLES20.glViewport(0, 0, width, height)
        GLES20.glUseProgram(program)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)

        val positions = floatArrayOf(
            -1f, -1f,  1f, -1f,
            -1f,  1f,  1f,  1f
        )
        // Flip Y because Android Bitmap origin is top-left.
        val texCoords = floatArrayOf(
            0f, 1f,  1f, 1f,
            0f, 0f,  1f, 0f
        )

        val p = java.nio.ByteBuffer.allocateDirect(positions.size * 4)
            .order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer()
        p.put(positions).position(0)
        val t = java.nio.ByteBuffer.allocateDirect(texCoords.size * 4)
            .order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer()
        t.put(texCoords).position(0)

        GLES20.glEnableVertexAttribArray(positionLoc)
        GLES20.glVertexAttribPointer(positionLoc, 2, GLES20.GL_FLOAT, false, 0, p)
        GLES20.glEnableVertexAttribArray(texCoordLoc)
        GLES20.glVertexAttribPointer(texCoordLoc, 2, GLES20.GL_FLOAT, false, 0, t)
        GLES20.glUniform1i(textureLoc, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(positionLoc)
        GLES20.glDisableVertexAttribArray(texCoordLoc)

        EGLExt.eglPresentationTimeANDROID(display, eglSurface, presentationTimeNs)
        check(EGL14.eglSwapBuffers(display, eglSurface)) { "eglSwapBuffers failed" }
    }

    private fun makeCurrent() {
        check(EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context)) {
            "eglMakeCurrent failed"
        }
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        check(shader != 0) { "glCreateShader failed" }
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            error("Shader compile failed: $log")
        }
        return shader
    }

    private fun linkProgram(vertex: Int, fragment: Int): Int {
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, vertex)
        GLES20.glAttachShader(p, fragment)
        GLES20.glLinkProgram(p)
        val status = IntArray(1)
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, status, 0)
        GLES20.glDeleteShader(vertex)
        GLES20.glDeleteShader(fragment)
        if (status[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(p)
            GLES20.glDeleteProgram(p)
            error("Program link failed: $log")
        }
        return p
    }

    fun release() {
        if (!released.compareAndSet(false, true)) return
        runCatching {
            if (display != EGL14.EGL_NO_DISPLAY && context != EGL14.EGL_NO_CONTEXT) {
                makeCurrent()
                if (textureId != 0) GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
                if (program != 0) GLES20.glDeleteProgram(program)
            }
        }
        runCatching { EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT) }
        runCatching { if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, eglSurface) }
        runCatching { if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context) }
        runCatching { if (display != EGL14.EGL_NO_DISPLAY) EGL14.eglTerminate(display) }
        display = EGL14.EGL_NO_DISPLAY
        context = EGL14.EGL_NO_CONTEXT
        eglSurface = EGL14.EGL_NO_SURFACE
    }
}
