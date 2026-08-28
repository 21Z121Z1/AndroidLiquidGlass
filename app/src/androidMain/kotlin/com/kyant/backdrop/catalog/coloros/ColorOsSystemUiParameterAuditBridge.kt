package com.kyant.backdrop.catalog.coloros

import android.content.Context
import java.lang.reflect.Modifier

/**
 * Safe runtime inspection for CAPABILITY_ONLY material classes/resources used by SystemUI.
 *
 * The strict inventory spans com.android.systemui, com.oplus.uxdesign and the personality-clocks
 * plugin. Classes are resolved through their owning package. external:// shader rows are read from
 * the owning APK and expose only interface evidence (size/uniform/sampler/version lines); unknown
 * shader inputs are never fabricated or executed.
 */
internal class ColorOsSystemUiParameterAuditBridge(context: Context) {
    companion object {
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        private const val UX_PACKAGE = "com.oplus.uxdesign"
        private const val CLOCK_PACKAGE = "com.oplus.keyguard.personality.clocks"
        private const val MAX_SIGNATURES = 80
        private const val MAX_VALUES = 80
        private const val MAX_SHADER_CHARS = 128 * 1024
    }

    data class Snapshot(
        val className: String,
        val superClass: String?,
        val enumConstants: List<String>,
        val staticConstants: List<String>,
        val getterValues: List<String>,
        val methodSignatures: List<String>,
        val instanceSource: String?,
    ) {
        val evidenceCount: Int
            get() = enumConstants.size + staticConstants.size + getterValues.size + methodSignatures.size
    }

    private val appContext = context.applicationContext

    @Suppress("DEPRECATION")
    private val systemUiContext = runCatching {
        appContext.createPackageContext(
            SYSTEM_UI_PACKAGE,
            Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY,
        )
    }

    @Suppress("DEPRECATION")
    private val uxContext = runCatching {
        appContext.createPackageContext(
            UX_PACKAGE,
            Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY,
        )
    }

    @Suppress("DEPRECATION")
    private val clockContext = runCatching {
        appContext.createPackageContext(
            CLOCK_PACKAGE,
            Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY,
        )
    }

    fun inspect(implementation: String): Result<Snapshot> =
        if (implementation.startsWith("external://")) {
            inspectExternalShader(implementation)
        } else {
            inspectClass(implementation)
        }

    private fun inspectClass(className: String): Result<Snapshot> = runCatching {
        val clazz = loadClass(className)
        val enumConstants = clazz.enumConstants?.map { (it as Enum<*>).name }.orEmpty()

        val staticConstants = clazz.declaredFields
            .asSequence()
            .filter { field ->
                Modifier.isStatic(field.modifiers) &&
                    Modifier.isFinal(field.modifiers) &&
                    isSafeValueType(field.type)
            }
            .take(MAX_VALUES)
            .map { field ->
                val value = runCatching {
                    field.isAccessible = true
                    field.get(null)
                }.fold({ formatValue(it) }, { "<${describe(it)}>" })
                "${field.name}=$value"
            }
            .toList()

        val instance = safeInstance(clazz)
        val getterValues = if (instance.first == null) {
            emptyList()
        } else {
            val receiver = instance.first!!
            (clazz.methods.asSequence() + clazz.declaredMethods.asSequence())
                .distinctBy {
                    methodSignature(it.name, it.parameterTypes.map { type -> type.name }, it.returnType.name)
                }
                .filter { method ->
                    !Modifier.isStatic(method.modifiers) &&
                        method.parameterCount == 0 &&
                        method.returnType != Void.TYPE &&
                        isSafeValueType(method.returnType) &&
                        (method.name.startsWith("get") || method.name.startsWith("is")) &&
                        method.name != "getClass"
                }
                .sortedBy { it.name }
                .take(MAX_VALUES)
                .map { method ->
                    val value = runCatching {
                        method.isAccessible = true
                        method.invoke(receiver)
                    }.fold({ formatValue(it) }, { "<${describe(it)}>" })
                    "${method.name}()=$value"
                }
                .toList()
        }

        val signatures = (clazz.methods.asSequence() + clazz.declaredMethods.asSequence())
            .distinctBy {
                methodSignature(it.name, it.parameterTypes.map { type -> type.name }, it.returnType.name)
            }
            .filterNot { it.declaringClass == Any::class.java }
            .sortedWith(compareBy({ it.name }, { it.parameterCount }))
            .take(MAX_SIGNATURES)
            .map { method ->
                val static = if (Modifier.isStatic(method.modifiers)) "static " else ""
                val args = method.parameterTypes.joinToString(",") { it.simpleName }
                "$static${method.returnType.simpleName} ${method.name}($args)"
            }
            .toList()

        Snapshot(
            className = clazz.name,
            superClass = clazz.superclass?.name,
            enumConstants = enumConstants,
            staticConstants = staticConstants,
            getterValues = getterValues,
            methodSignatures = signatures,
            instanceSource = instance.second,
        )
    }

    private fun inspectExternalShader(uri: String): Result<Snapshot> = runCatching {
        val parsed = parseExternalUri(uri)
        val packageContext = packageContextFor(parsed.packageName).getOrThrow()
        val source = when (parsed.kind) {
            ExternalKind.ASSET -> packageContext.assets.open(parsed.path).use(::readShaderPrefix)
            ExternalKind.RAW -> {
                val rawName = parsed.path.substringBeforeLast('.')
                val id = packageContext.resources.getIdentifier(rawName, "raw", parsed.packageName)
                require(id != 0) { "raw resource $rawName not found in ${parsed.packageName}" }
                packageContext.resources.openRawResource(id).use(::readShaderPrefix)
            }
        }
        require(source.isNotBlank()) { "shader resource is empty" }

        val interfaceLines = source.lineSequence()
            .map(String::trim)
            .filter { line ->
                line.startsWith("uniform ") ||
                    line.startsWith("layout(") ||
                    line.startsWith("#version") ||
                    "sampler2D" in line ||
                    " shader " in " $line "
            }
            .distinct()
            .take(MAX_SIGNATURES)
            .toList()

        val lower = source.lowercase()
        val features = buildList {
            if ("uniform shader" in lower) add("feature=AGSL child shader input")
            if ("sampler2d" in lower) add("feature=GLSL sampler2D input")
            if ("smoothstep" in lower || "distance" in lower || "sdf" in lower) add("feature=distance/SDF-style field")
            if ("blur" in lower) add("feature=blur-related source")
            if ("chromatic" in lower || "dispersion" in lower) add("feature=chromatic/dispersion-related source")
            if ("refract" in lower) add("feature=refraction-related source")
            if ("stroke" in lower || "edge" in lower) add("feature=edge/stroke-related source")
            if ("shadow" in lower) add("feature=shadow-related source")
        }

        Snapshot(
            className = uri,
            superClass = null,
            enumConstants = emptyList(),
            staticConstants = listOf(
                "package=${parsed.packageName}",
                "resource=${parsed.kind.name.lowercase()}:${parsed.path}",
                "sourceChars=${source.length}",
            ) + features,
            getterValues = emptyList(),
            methodSignatures = interfaceLines.map { "shader: $it" },
            instanceSource = "APK shader resource audit",
        )
    }

    private enum class ExternalKind { ASSET, RAW }

    private data class ExternalResource(
        val packageName: String,
        val kind: ExternalKind,
        val path: String,
    )

    private fun parseExternalUri(uri: String): ExternalResource {
        val payload = uri.removePrefix("external://")
        val slash = payload.indexOf('/')
        require(slash > 0) { "invalid external shader URI: $uri" }
        val packageName = payload.substring(0, slash)
        val resource = payload.substring(slash + 1)
        return when {
            resource.startsWith("assets/") -> ExternalResource(
                packageName,
                ExternalKind.ASSET,
                resource.removePrefix("assets/"),
            )
            resource.startsWith("res/raw/") -> ExternalResource(
                packageName,
                ExternalKind.RAW,
                resource.removePrefix("res/raw/"),
            )
            else -> error("unsupported external resource URI: $uri")
        }
    }

    private fun packageContextFor(packageName: String): Result<Context> = when (packageName) {
        SYSTEM_UI_PACKAGE -> systemUiContext
        UX_PACKAGE -> uxContext
        CLOCK_PACKAGE -> clockContext
        else -> Result.failure(IllegalArgumentException("unsupported material package: $packageName"))
    }

    private fun readShaderPrefix(input: java.io.InputStream): String {
        val reader = input.bufferedReader()
        val buffer = CharArray(4096)
        val out = StringBuilder(minOf(MAX_SHADER_CHARS, 16 * 1024))
        while (out.length < MAX_SHADER_CHARS) {
            val count = reader.read(buffer, 0, minOf(buffer.size, MAX_SHADER_CHARS - out.length))
            if (count <= 0) break
            out.append(buffer, 0, count)
        }
        return out.toString()
    }

    private fun loadClass(className: String): Class<*> {
        val preferred = when {
            className.startsWith("com.coui.") -> listOf(uxContext, systemUiContext, clockContext)
            className.startsWith("com.oplus.keyguard.clock.") -> listOf(clockContext, systemUiContext, uxContext)
            else -> listOf(systemUiContext, uxContext, clockContext)
        }

        var last: Throwable? = null
        preferred.forEach { contextResult ->
            contextResult.getOrNull()?.let { packageContext ->
                runCatching { packageContext.classLoader.loadClass(className) }
                    .onSuccess { return it }
                    .onFailure { last = it }
            }
        }
        runCatching { Class.forName(className, false, appContext.classLoader) }
            .onSuccess { return it }
            .onFailure { last = it }
        throw last ?: ClassNotFoundException(className)
    }

    private fun safeInstance(clazz: Class<*>): Pair<Any?, String?> {
        runCatching {
            val instanceField = clazz.getDeclaredField("INSTANCE").apply { isAccessible = true }
            val instance = instanceField.get(null)
            if (instance != null) return instance to "Kotlin object INSTANCE"
        }

        val simpleName = clazz.simpleName.lowercase()
        val looksLikeDataHolder = listOf(
            "params", "param", "config", "group", "state", "mixcolor", "adapter", "effect",
        ).any { token -> token in simpleName }
        if (!looksLikeDataHolder) return null to null

        val ctor = clazz.declaredConstructors.firstOrNull { it.parameterCount == 0 } ?: return null to null
        return runCatching {
            ctor.isAccessible = true
            ctor.newInstance() to "zero-arg data/config holder"
        }.getOrElse { null to null }
    }

    private fun isSafeValueType(type: Class<*>): Boolean =
        type.isPrimitive ||
            type.isEnum ||
            type == String::class.java ||
            type == java.lang.Boolean::class.java ||
            type == java.lang.Integer::class.java ||
            type == java.lang.Long::class.java ||
            type == java.lang.Float::class.java ||
            type == java.lang.Double::class.java ||
            type == java.lang.Short::class.java ||
            type == java.lang.Byte::class.java ||
            type == java.lang.Character::class.java

    private fun formatValue(value: Any?): String = when (value) {
        null -> "null"
        is Float -> "%.4f".format(value)
        is Double -> "%.4f".format(value)
        else -> value.toString()
    }

    private fun methodSignature(name: String, args: List<String>, result: String): String =
        "$result $name(${args.joinToString(",")})"

    private fun describe(t: Throwable): String {
        val root = generateSequence(t) { it.cause }.last()
        return "${root.javaClass.simpleName}:${root.message}"
    }
}
