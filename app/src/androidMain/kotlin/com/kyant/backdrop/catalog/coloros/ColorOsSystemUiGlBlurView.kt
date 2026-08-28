package com.kyant.backdrop.catalog.coloros

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Minimal GLES 3.0 host for the shipping SystemUI blur pipeline.
 *
 * Shader text is loaded from the installed com.android.systemui APK:
 *   blur_down -> gaussian(horizontal) -> gaussian(vertical) -> blur_up -> display
 *
 * This is intentionally independent from Kyant, COUI RenderEffect blur and the
 * com.oplus.posteffect RuntimeShader graph so the demo can compare all three
 * actual execution paths.
 */
internal class ColorOsSystemUiGlBlurView(context: Context) : GLSurfaceView(context), GLSurfaceView.Renderer {
    companion object {
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        private const val DOWN_VERTEX = "blur_down_vertex_shader.glsl"
        private const val DOWN_FRAGMENT = "blur_down_fragment_shader.glsl"
        private const val UP_VERTEX = "blur_up_vertex_shader.glsl"
        private const val UP_FRAGMENT = "blur_up_fragment_shader.glsl"
        private const val GAUSSIAN_VERTEX = "gaussian_blur_vertex_shader.glsl"
        private const val GAUSSIAN_FRAGMENT = "gaussian_blur_fragment_shader.glsl"
        private const val DISPLAY_VERTEX = "display_vertex_shader.glsl"
        private const val DISPLAY_FRAGMENT = "display_fragment_shader.glsl"
    }

    @Suppress("DEPRECATION")
    private val systemUiContext = context.applicationContext.createPackageContext(
        SYSTEM_UI_PACKAGE,
        Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY,
    )

    private val shaderSources = mapOf(
        DOWN_VERTEX to loadAsset(DOWN_VERTEX),
        DOWN_FRAGMENT to loadAsset(DOWN_FRAGMENT),
        UP_VERTEX to loadAsset(UP_VERTEX),
        UP_FRAGMENT to loadAsset(UP_FRAGMENT),
        GAUSSIAN_VERTEX to loadAsset(GAUSSIAN_VERTEX),
        GAUSSIAN_FRAGMENT to loadAsset(GAUSSIAN_FRAGMENT),
        DISPLAY_VERTEX to loadAsset(DISPLAY_VERTEX),
        DISPLAY_FRAGMENT to loadAsset(DISPLAY_FRAGMENT),
    )

    private val quad: FloatBuffer = ByteBuffer
        .allocateDirect(4 * 4 * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            // triangle strip: x, y, u, v
            put(
                floatArrayOf(
                    -1f, -1f, 0f, 1f,
                    1f, -1f, 1f, 1f,
                    -1f, 1f, 0f, 0f,
                    1f, 1f, 1f, 0f,
                ),
            )
            position(0)
        }

    @Volatile
    private var inputBitmap: Bitmap? = null
    @Volatile
    private var requestedBlendMode: Int = 4
    @Volatile
    private var requestedBlurRadius: Int = 8

    private var lastBitmapIdentity = 0
    private var surfaceWidth = 1
    private var surfaceHeight = 1

    private var downProgram = 0
    private var upProgram = 0
    private var gaussianProgram = 0
    private var displayProgram = 0

    private var inputTexture = 0
    private var halfTextureA = 0
    private var halfTextureB = 0
    private var fullTexture = 0
    private var halfFboA = 0
    private var halfFboB = 0
    private var fullFbo = 0

    var onStatus: ((String) -> Unit)? = null

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 8, 0, 0)
        setRenderer(this)
        renderMode = RENDERMODE_WHEN_DIRTY
        preserveEGLContextOnPause = true
    }

    fun configure(bitmap: Bitmap, blurRadius: Int = 8, blendMode: Int = 4) {
        inputBitmap = bitmap
        requestedBlurRadius = blurRadius.coerceIn(1, 24)
        requestedBlendMode = blendMode.coerceIn(0, 4)
        requestRender()
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        runCatching {
            downProgram = createProgram(shaderSources.getValue(DOWN_VERTEX), shaderSources.getValue(DOWN_FRAGMENT))
            upProgram = createProgram(shaderSources.getValue(UP_VERTEX), shaderSources.getValue(UP_FRAGMENT))
            gaussianProgram = createProgram(shaderSources.getValue(GAUSSIAN_VERTEX), shaderSources.getValue(GAUSSIAN_FRAGMENT))
            displayProgram = createProgram(shaderSources.getValue(DISPLAY_VERTEX), shaderSources.getValue(DISPLAY_FRAGMENT))
            inputTexture = createTexture()
            postStatus("PASS — SystemUI GLES shaders compiled from installed APK")
        }.onFailure {
            postStatus("UNAVAILABLE — SystemUI GLES compile: ${describe(it)}")
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        surfaceWidth = width.coerceAtLeast(1)
        surfaceHeight = height.coerceAtLeast(1)
        rebuildTargets()
        requestRender()
    }

    override fun onDrawFrame(gl: GL10?) {
        if (downProgram == 0 || upProgram == 0 || gaussianProgram == 0 || displayProgram == 0) return
        val bitmap = inputBitmap ?: return
        runCatching {
            uploadInputIfNeeded(bitmap)
            val halfW = (surfaceWidth / 2).coerceAtLeast(1)
            val halfH = (surfaceHeight / 2).coerceAtLeast(1)

            // 1. SystemUI blur_down: input -> half A.
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, halfFboA)
            GLES30.glViewport(0, 0, halfW, halfH)
            GLES30.glUseProgram(downProgram)
            bindTexture(inputTexture)
            uniform2f(downProgram, "u_tex_offset", 0.5f / bitmap.width.coerceAtLeast(1), 0.5f / bitmap.height.coerceAtLeast(1))
            drawQuad(downProgram)

            // 2. SystemUI gaussian horizontal: half A -> half B.
            val radius = requestedBlurRadius
            val sumWeight = gaussianSumWeight(radius)
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, halfFboB)
            GLES30.glUseProgram(gaussianProgram)
            bindTexture(halfTextureA)
            uniform1i(gaussianProgram, "blurRadius", radius)
            uniform2f(gaussianProgram, "blurOffset", 1f / halfW, 0f)
            uniform1f(gaussianProgram, "blurSumWeight", sumWeight)
            drawQuad(gaussianProgram)

            // 3. SystemUI gaussian vertical: half B -> half A.
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, halfFboA)
            GLES30.glUseProgram(gaussianProgram)
            bindTexture(halfTextureB)
            uniform1i(gaussianProgram, "blurRadius", radius)
            uniform2f(gaussianProgram, "blurOffset", 0f, 1f / halfH)
            uniform1f(gaussianProgram, "blurSumWeight", sumWeight)
            drawQuad(gaussianProgram)

            // 4. SystemUI blur_up: half A -> full-size blur texture.
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fullFbo)
            GLES30.glViewport(0, 0, surfaceWidth, surfaceHeight)
            GLES30.glUseProgram(upProgram)
            bindTexture(halfTextureA)
            uniform2f(upProgram, "u_tex_offset", 0.5f / halfW, 0.5f / halfH)
            drawQuad(upProgram)

            // 5. SystemUI display: brightness + dither + material blend.
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            GLES30.glViewport(0, 0, surfaceWidth, surfaceHeight)
            GLES30.glClearColor(0f, 0f, 0f, 0f)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            GLES30.glUseProgram(displayProgram)
            bindTexture(fullTexture)
            uniform2f(displayProgram, "u_resolution", surfaceWidth.toFloat(), surfaceHeight.toFloat())
            uniform1f(displayProgram, "u_brightness", 1.02f)
            uniform2f(displayProgram, "u_ditherRange", -1f / 255f, 1f / 255f)
            uniform1f(displayProgram, "u_mirroredScale", 0f)
            uniform1i(displayProgram, "u_blendMode", requestedBlendMode)
            uniform4f(displayProgram, "u_blend_color_1", 1f, 1f, 1f, 0.10f)
            uniform4f(displayProgram, "u_blend_color_2", 0.90f, 0.94f, 1f, 0.10f)
            uniform1i(displayProgram, "u_revert_y", 0)
            drawQuad(displayProgram)
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            postStatus("PASS — blur_down → gaussian H/V → blur_up → display; radius=$radius blend=$requestedBlendMode")
        }.onFailure {
            postStatus("UNAVAILABLE — SystemUI GLES pipeline: ${describe(it)}")
        }
    }

    override fun onDetachedFromWindow() {
        queueEvent { releaseGl() }
        super.onDetachedFromWindow()
    }

    private fun uploadInputIfNeeded(bitmap: Bitmap) {
        val identity = System.identityHashCode(bitmap)
        if (identity == lastBitmapIdentity) return
        lastBitmapIdentity = identity
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, inputTexture)
        configureTextureParameters()
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
    }

    private fun rebuildTargets() {
        deleteTarget(halfFboA, halfTextureA)
        deleteTarget(halfFboB, halfTextureB)
        deleteTarget(fullFbo, fullTexture)
        val halfW = (surfaceWidth / 2).coerceAtLeast(1)
        val halfH = (surfaceHeight / 2).coerceAtLeast(1)
        val a = createTarget(halfW, halfH)
        halfFboA = a.first
        halfTextureA = a.second
        val b = createTarget(halfW, halfH)
        halfFboB = b.first
        halfTextureB = b.second
        val full = createTarget(surfaceWidth, surfaceHeight)
        fullFbo = full.first
        fullTexture = full.second
    }

    private fun createTarget(width: Int, height: Int): Pair<Int, Int> {
        val texture = createTexture()
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D,
            0,
            GLES30.GL_RGBA,
            width,
            height,
            0,
            GLES30.GL_RGBA,
            GLES30.GL_UNSIGNED_BYTE,
            null,
        )
        val ids = IntArray(1)
        GLES30.glGenFramebuffers(1, ids, 0)
        val fbo = ids[0]
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER,
            GLES30.GL_COLOR_ATTACHMENT0,
            GLES30.GL_TEXTURE_2D,
            texture,
            0,
        )
        val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        require(status == GLES30.GL_FRAMEBUFFER_COMPLETE) { "Framebuffer incomplete: 0x${status.toString(16)}" }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        return fbo to texture
    }

    private fun createTexture(): Int {
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ids[0])
        configureTextureParameters()
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        return ids[0]
    }

    private fun configureTextureParameters() {
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertex = compileShader(GLES30.GL_VERTEX_SHADER, vertexSource)
        val fragment = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource)
        val program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vertex)
        GLES30.glAttachShader(program, fragment)
        GLES30.glLinkProgram(program)
        val ok = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, ok, 0)
        val log = GLES30.glGetProgramInfoLog(program)
        GLES30.glDeleteShader(vertex)
        GLES30.glDeleteShader(fragment)
        require(ok[0] == GLES30.GL_TRUE) { "Program link failed: $log" }
        return program
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        val ok = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, ok, 0)
        val log = GLES30.glGetShaderInfoLog(shader)
        require(ok[0] == GLES30.GL_TRUE) { "Shader compile failed: $log" }
        return shader
    }

    private fun bindTexture(texture: Int) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
        val program = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_CURRENT_PROGRAM, program, 0)
        val location = GLES30.glGetUniformLocation(program[0], "u_texture_2D")
        if (location >= 0) GLES30.glUniform1i(location, 0)
    }

    private fun drawQuad(program: Int) {
        quad.position(0)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 16, quad)
        quad.position(2)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 16, quad)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
        quad.position(0)
        checkGl("draw program=$program")
    }

    private fun gaussianSumWeight(radius: Int): Float {
        val sigma = radius.toFloat() / 3f
        fun raw(i: Int): Double =
            (1.0 / sqrt(2.0 * PI * sigma * sigma)) * exp(-(i * i).toDouble() / (2.0 * sigma * sigma))
        var sum = raw(0)
        for (i in 1..radius) sum += raw(i) * 2.0
        return sum.toFloat().coerceAtLeast(1e-6f)
    }

    private fun uniform1f(program: Int, name: String, value: Float) {
        val loc = GLES30.glGetUniformLocation(program, name)
        if (loc >= 0) GLES30.glUniform1f(loc, value)
    }

    private fun uniform1i(program: Int, name: String, value: Int) {
        val loc = GLES30.glGetUniformLocation(program, name)
        if (loc >= 0) GLES30.glUniform1i(loc, value)
    }

    private fun uniform2f(program: Int, name: String, x: Float, y: Float) {
        val loc = GLES30.glGetUniformLocation(program, name)
        if (loc >= 0) GLES30.glUniform2f(loc, x, y)
    }

    private fun uniform4f(program: Int, name: String, x: Float, y: Float, z: Float, w: Float) {
        val loc = GLES30.glGetUniformLocation(program, name)
        if (loc >= 0) GLES30.glUniform4f(loc, x, y, z, w)
    }

    private fun checkGl(stage: String) {
        val error = GLES30.glGetError()
        require(error == GLES30.GL_NO_ERROR) { "$stage GL error=0x${error.toString(16)}" }
    }

    private fun releaseGl() {
        if (downProgram != 0) GLES30.glDeleteProgram(downProgram)
        if (upProgram != 0) GLES30.glDeleteProgram(upProgram)
        if (gaussianProgram != 0) GLES30.glDeleteProgram(gaussianProgram)
        if (displayProgram != 0) GLES30.glDeleteProgram(displayProgram)
        deleteTarget(halfFboA, halfTextureA)
        deleteTarget(halfFboB, halfTextureB)
        deleteTarget(fullFbo, fullTexture)
        if (inputTexture != 0) GLES30.glDeleteTextures(1, intArrayOf(inputTexture), 0)
        downProgram = 0
        upProgram = 0
        gaussianProgram = 0
        displayProgram = 0
        inputTexture = 0
    }

    private fun deleteTarget(fbo: Int, texture: Int) {
        if (fbo != 0) GLES30.glDeleteFramebuffers(1, intArrayOf(fbo), 0)
        if (texture != 0) GLES30.glDeleteTextures(1, intArrayOf(texture), 0)
    }

    private fun loadAsset(name: String): String =
        systemUiContext.assets.open(name).bufferedReader().use { it.readText() }

    private fun postStatus(message: String) {
        post { onStatus?.invoke(message) }
    }

    private fun describe(t: Throwable): String {
        val root = generateSequence(t) { it.cause }.last()
        return "${root.javaClass.simpleName}:${root.message}"
    }
}
