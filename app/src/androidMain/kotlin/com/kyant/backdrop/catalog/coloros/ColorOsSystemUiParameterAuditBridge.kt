package com.kyant.backdrop.catalog.coloros

import android.content.Context
import java.lang.reflect.Modifier

/**
 * Safe runtime inspection for CAPABILITY_ONLY SystemUI material classes.
 *
 * This does not call arbitrary business methods. It reads enum values/static constants and only
 * invokes zero-argument bean-style getters on a Kotlin singleton or zero-arg data holder when the
 * return type is a small scalar/enum/String. This gives the comparison lab real vendor-owned
 * parameter evidence without fabricating values or triggering controller actions.
 */
internal class ColorOsSystemUiParameterAuditBridge(context: Context) {
    companion object {
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        private const val MAX_SIGNATURES = 80
        private const val MAX_VALUES = 80
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

    @Suppress("DEPRECATION")
    private val systemUiContext = context.applicationContext.createPackageContext(
        SYSTEM_UI_PACKAGE,
        Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY,
    )
    private val loader = systemUiContext.classLoader

    fun inspect(className: String): Result<Snapshot> = runCatching {
        val clazz = loader.loadClass(className)
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

    private fun safeInstance(clazz: Class<*>): Pair<Any?, String?> {
        runCatching {
            val instanceField = clazz.getDeclaredField("INSTANCE").apply { isAccessible = true }
            val instance = instanceField.get(null)
            if (instance != null) return instance to "Kotlin object INSTANCE"
        }

        val simpleName = clazz.simpleName.lowercase()
        val looksLikeDataHolder = listOf(
            "params", "param", "config", "group", "state", "mixcolor", "adapter",
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
