@file:JvmName("DJVM")
@file:Suppress("unused")
package sandbox.java.lang

import org.objectweb.asm.Opcodes.ACC_ENUM

private const val SANDBOX_PREFIX = "sandbox."

fun Any.unsandbox(): Any {
    return when (this) {
        is Enum<*> -> fromDJVMEnum()
        is Object -> fromDJVM()
        is Array<*> -> fromDJVMArray()
        else -> this
    }
}

fun Any.sandbox(): Any {
    return when (this) {
        is kotlin.String -> String.toDJVM(this)
        is kotlin.Char -> Character.toDJVM(this)
        is kotlin.Long -> Long.toDJVM(this)
        is kotlin.Int -> Integer.toDJVM(this)
        is kotlin.Short -> Short.toDJVM(this)
        is kotlin.Byte -> Byte.toDJVM(this)
        is kotlin.Float -> Float.toDJVM(this)
        is kotlin.Double -> Double.toDJVM(this)
        is kotlin.Boolean -> Boolean.toDJVM(this)
        is kotlin.Enum<*> -> toDJVMEnum()
        is Array<*> -> toDJVMArray<Object>()
        else -> this
    }
}

private fun Array<*>.fromDJVMArray(): Array<*> = Object.fromDJVM(this)

/**
 * These functions use the "current" classloader, i.e. classloader
 * that owns this DJVM class.
 */
private fun Class<*>.toDJVMType(): Class<*> = Class.forName(name.toSandboxPackage())
private fun Class<*>.fromDJVMType(): Class<*> = Class.forName(name.fromSandboxPackage())

private fun kotlin.String.toSandboxPackage(): kotlin.String {
    return if (startsWith(SANDBOX_PREFIX)) {
        this
    } else {
        SANDBOX_PREFIX + this
    }
}

private fun kotlin.String.fromSandboxPackage(): kotlin.String {
    return if (startsWith(SANDBOX_PREFIX)) {
        substring(8)
    } else {
        this
    }
}

private inline fun <reified T : Object> Array<*>.toDJVMArray(): Array<out T?> {
    @Suppress("unchecked_cast")
    return (java.lang.reflect.Array.newInstance(javaClass.componentType.toDJVMType(), size) as Array<T?>).also {
        for ((i, item) in withIndex()) {
            it[i] = item?.sandbox() as T
        }
    }
}

private fun Enum<*>.fromDJVMEnum(): kotlin.Enum<*> {
    return javaClass.fromDJVMType().enumConstants[ordinal()] as kotlin.Enum<*>
}

private fun kotlin.Enum<*>.toDJVMEnum(): Enum<*> {
    @Suppress("unchecked_cast")
    return (getEnumConstants(javaClass.toDJVMType() as Class<in Enum<*>>) as Array<Enum<*>>)[ordinal]
}

/**
 * Replacement functions for the members of Class<*> that support Enums.
 */
fun isEnum(clazz: Class<*>): kotlin.Boolean
        = (clazz.modifiers and ACC_ENUM != 0) && (clazz.superclass == sandbox.java.lang.Enum::class.java)

fun getEnumConstants(clazz: Class<in Enum<*>>): Array<*>? {
    return getEnumConstantsShared(clazz)?.clone()
}

internal fun enumConstantDirectory(clazz: Class<in Enum<*>>): sandbox.java.util.Map<String, Enum<*>>? {
    // DO NOT replace get with Kotlin's [] because Kotlin would use java.util.Map.
    return allEnumDirectories.get(clazz) ?: createEnumDirectory(clazz)
}

@Suppress("unchecked_cast")
private fun getEnumConstantsShared(clazz: Class<in Enum<*>>): Array<out Enum<*>>? {
    return if (isEnum(clazz)) {
        // DO NOT replace get with Kotlin's [] because Kotlin would use java.util.Map.
        allEnums.get(clazz) ?: createEnum(clazz)
    } else {
        null
    }
}

@Suppress("unchecked_cast")
private fun createEnum(clazz: Class<in Enum<*>>): Array<out Enum<*>>? {
    return clazz.getMethod("values").let { method ->
        method.isAccessible = true
        method.invoke(null) as? Array<Enum<*>>
    // DO NOT replace put with Kotlin's [] because Kotlin would use java.util.Map.
    }?.apply { allEnums.put(clazz, this) }
}

private fun createEnumDirectory(clazz: Class<in Enum<*>>): sandbox.java.util.Map<String, Enum<*>> {
    val universe = getEnumConstantsShared(clazz) ?: throw IllegalArgumentException("${clazz.name} is not an enum type")
    val directory = sandbox.java.util.LinkedHashMap<String, Enum<*>>(2 * universe.size)
    for (entry in universe) {
        // DO NOT replace put with Kotlin's [] because Kotlin would use java.util.Map.
        directory.put(entry.name(), entry)
    }
    // DO NOT replace put with Kotlin's [] because Kotlin would use java.util.Map.
    allEnumDirectories.put(clazz, directory)
    return directory
}

private val allEnums: sandbox.java.util.Map<Class<in Enum<*>>, Array<out Enum<*>>> = sandbox.java.util.LinkedHashMap()
private val allEnumDirectories: sandbox.java.util.Map<Class<in Enum<*>>, sandbox.java.util.Map<String, Enum<*>>> = sandbox.java.util.LinkedHashMap()