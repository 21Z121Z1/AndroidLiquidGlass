package com.kyant.backdrop.catalog.coloros

import android.content.Context
import dalvik.system.DexFile

/**
 * High-recall runtime discovery for material/glass implementations that SystemUI consumes from
 * packages outside com.android.systemui itself.
 *
 * Known visual entry points are still supplied by ColorOsSystemUiCompleteInventory with precise
 * DIRECT_VIEW semantics. Anything newly discovered here defaults to CAPABILITY_ONLY until a real
 * execution bridge is proven; this keeps the strict audit exhaustive without turning a class-name
 * match into a false pixel-equivalence claim.
 */
internal class ColorOsExternalLiquidGlassCatalog(context: Context) {
    companion object {
        private const val UX_PACKAGE = "com.oplus.uxdesign"
        private const val CLOCK_PACKAGE = "com.oplus.keyguard.personality.clocks"

        private val KEYWORDS = listOf(
            "material",
            "blur",
            "stroke",
            "spotlight",
            "caustic",
            "shadow",
            "edge",
            "optic",
            "glass",
            "refract",
            "dispersion",
            "chromatic",
            "shader",
            "rendereffect",
            "effect",
            "backdrop",
            "gradient",
        )

        private val SHADER_KEYWORDS = listOf(
            "blur",
            "stroke",
            "shadow",
            "edge",
            "optic",
            "glass",
            "refract",
            "dispersion",
            "chromatic",
            "caustic",
            "sdf",
            "smoothstep",
            "distance",
            "blend",
        )
    }

    private data class Target(
        val packageName: String,
        val label: String,
        val prefixes: List<String>,
    )

    private data class ShaderResource(
        val packageName: String,
        val implementation: String,
        val source: String,
    )

    private val appContext = context.applicationContext
    private val targets = listOf(
        Target(
            packageName = UX_PACKAGE,
            label = "uxdesign/COUI",
            prefixes = listOf(
                "com.coui.appcompat.",
                "com.oplus.view.material.",
                "com.oplus.graphics.",
                "com.oplus.view.",
            ),
        ),
        Target(
            packageName = CLOCK_PACKAGE,
            label = "personality-clocks",
            prefixes = listOf("com.oplus.keyguard.clock."),
        ),
    )

    @Suppress("DEPRECATION")
    private val packageContexts: Map<String, Result<Context>> = targets.associate { target ->
        target.packageName to runCatching {
            appContext.createPackageContext(
                target.packageName,
                Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY,
            )
        }
    }

    private val cache by lazy { discover() }

    fun mappings(): List<ColorOsSystemUiLiquidGlassCatalog.Mapping> = cache

    private fun discover(): List<ColorOsSystemUiLiquidGlassCatalog.Mapping> = buildList {
        targets.forEach { target ->
            val packageContext = packageContexts.getValue(target.packageName).getOrNull() ?: return@forEach
            discoverClasses(target, packageContext).forEach { className ->
                add(classMapping(target, packageContext, className))
            }
            discoverShaders(target, packageContext).forEach { shader ->
                add(shaderMapping(target, shader))
            }
        }
    }.distinctBy { it.systemUiImplementation }

    @Suppress("DEPRECATION")
    private fun discoverClasses(target: Target, packageContext: Context): Set<String> = runCatching {
        val info = packageContext.applicationInfo
        val paths = buildList {
            info.sourceDir?.let(::add)
            info.splitSourceDirs?.forEach(::add)
        }.distinct()

        buildSet {
            paths.forEach { path ->
                val dex = DexFile(path)
                try {
                    val entries = dex.entries()
                    while (entries.hasMoreElements()) {
                        val raw = entries.nextElement()
                        val name = raw.substringBefore('$')
                        val lower = name.lowercase()
                        if (target.prefixes.any(name::startsWith) && KEYWORDS.any(lower::contains)) {
                            add(name)
                        }
                    }
                } finally {
                    runCatching { dex.close() }
                }
            }
        }
    }.getOrDefault(emptySet())

    private fun discoverShaders(target: Target, packageContext: Context): Set<ShaderResource> = buildSet {
        addAll(discoverAssetShaders(target, packageContext))
        addAll(discoverRawShaders(target, packageContext))
    }

    private fun discoverAssetShaders(target: Target, packageContext: Context): Set<ShaderResource> = runCatching {
        buildSet {
            fun walk(prefix: String) {
                val children = packageContext.assets.list(prefix).orEmpty()
                children.forEach { child ->
                    val path = if (prefix.isBlank()) child else "$prefix/$child"
                    val nested = packageContext.assets.list(path).orEmpty()
                    if (nested.isNotEmpty()) {
                        walk(path)
                    } else if (path.endsWith(".agsl", true) || path.endsWith(".glsl", true)) {
                        val source = runCatching {
                            packageContext.assets.open(path).use(::readTextPrefix)
                        }.getOrDefault("")
                        if (isRelevantShader(path, source)) {
                            add(
                                ShaderResource(
                                    packageName = target.packageName,
                                    implementation = "external://${target.packageName}/assets/$path",
                                    source = source,
                                ),
                            )
                        }
                    }
                }
            }
            walk("")
        }
    }.getOrDefault(emptySet())

    private fun discoverRawShaders(target: Target, packageContext: Context): Set<ShaderResource> = runCatching {
        val rawClass = packageContext.classLoader.loadClass("${target.packageName}.R\$raw")
        buildSet {
            rawClass.declaredFields.forEach { field ->
                if (field.type != Int::class.javaPrimitiveType) return@forEach
                field.isAccessible = true
                val id = field.getInt(null)
                val source = runCatching {
                    packageContext.resources.openRawResource(id).use(::readTextPrefix)
                }.getOrDefault("")
                if (source.isNotBlank() && isRelevantShader(field.name, source)) {
                    val extension = if (
                        "sampler2d" in source.lowercase() || "#version" in source.lowercase()
                    ) "glsl" else "agsl"
                    add(
                        ShaderResource(
                            packageName = target.packageName,
                            implementation = "external://${target.packageName}/res/raw/${field.name}.$extension",
                            source = source,
                        ),
                    )
                }
            }
        }
    }.getOrDefault(emptySet())

    private fun classMapping(
        target: Target,
        packageContext: Context,
        className: String,
    ): ColorOsSystemUiLiquidGlassCatalog.Mapping = ColorOsSystemUiLiquidGlassCatalog.Mapping(
        group = "自动发现 · 外部 ${target.label}",
        systemUiImplementation = className,
        kyantCounterpart = inferKyant(className),
        executionMode = ColorOsSystemUiLiquidGlassCatalog.ExecutionMode.CAPABILITY_ONLY,
        status = runCatching {
            packageContext.classLoader.loadClass(className)
            "available:external-dex:${target.packageName}"
        }.getOrElse { "unavailable:${describe(it)}" },
        note = "运行时从 ${target.packageName} DEX 自动发现。未单独证明普通第三方 View 可直接执行前，仅做真实类/参数审计；不仿制未知业务 shader。",
    )

    private fun shaderMapping(
        target: Target,
        shader: ShaderResource,
    ): ColorOsSystemUiLiquidGlassCatalog.Mapping = ColorOsSystemUiLiquidGlassCatalog.Mapping(
        group = "自动发现 · 外部 ${target.label} 着色器",
        systemUiImplementation = shader.implementation,
        kyantCounterpart = inferKyant(shader.implementation + "\n" + shader.source),
        executionMode = ColorOsSystemUiLiquidGlassCatalog.ExecutionMode.CAPABILITY_ONLY,
        status = "available:external-shader:${shader.packageName}",
        note = "运行时从 ${shader.packageName} 自动扫描到 AGSL/GLSL。未知 uniform/业务输入保持资源审计，不凭文件名伪造直接执行器。",
    )

    private fun inferKyant(value: String): String {
        val lower = value.lowercase()
        return when {
            "refract" in lower || "dispersion" in lower || "chromatic" in lower || "glass" in lower ->
                "lens(refraction/chromaticAberration) + blur/highlight 组合；具体 shader 需逐项验证"
            "spotlight" in lower -> "InteractiveHighlight nearest mechanism"
            "caustic" in lower -> "Shadow/Highlight nearest mechanism；不宣称物理焦散等价"
            "innershadow" in lower -> "InnerShadow"
            "stroke" in lower || "edge" in lower -> "Shape/SDF + Highlight/Shadow"
            "blur" in lower || "backdrop" in lower -> "Backdrop + blur()"
            "shadow" in lower -> "InnerShadow / Shadow"
            "material" in lower -> "drawBackdrop material composition"
            "shader" in lower || "effect" in lower -> "runtimeShaderEffect / Backdrop effect graph"
            else -> "无 1:1；仅实现邻接对照"
        }
    }

    private fun isRelevantShader(path: String, source: String): Boolean {
        val pathLower = path.lowercase()
        if (SHADER_KEYWORDS.any(pathLower::contains)) return true
        val sourceLower = source.lowercase()
        return SHADER_KEYWORDS.count(sourceLower::contains) >= 2
    }

    private fun readTextPrefix(input: java.io.InputStream, maxChars: Int = 64 * 1024): String {
        val reader = input.bufferedReader()
        val buffer = CharArray(4096)
        val out = StringBuilder(minOf(maxChars, 16 * 1024))
        while (out.length < maxChars) {
            val count = reader.read(buffer, 0, minOf(buffer.size, maxChars - out.length))
            if (count <= 0) break
            out.append(buffer, 0, count)
        }
        return out.toString()
    }

    private fun describe(t: Throwable): String {
        val root = generateSequence(t) { it.cause }.last()
        return "${root.javaClass.simpleName}:${root.message}"
    }
}
