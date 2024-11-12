package dev.kosmx.kowoconfig

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.ksp.toTypeName
import io.wispforest.owo.config.Option
import io.wispforest.owo.config.annotation.Config
import io.wispforest.owo.config.annotation.Hook
import io.wispforest.owo.config.annotation.Nest

class ConfigAnnotationProcessor(val environment: SymbolProcessorEnvironment) : SymbolProcessor {
    override fun process(resolver: Resolver): List<KSAnnotated> {

        val (toProcess, delayed) = resolver.getSymbolsWithAnnotation(Config::class.qualifiedName!!)
            .mapNotNull { it as? KSClassDeclaration }
            .partition { it.validate() }

        environment.logger.info("Processing ${toProcess.size} annotations.")
        environment.logger.info("Skipping ${delayed.size} annotations.")
        for (kClass in toProcess) {
            KConfigGenerator(kClass)()
        }

        return delayed
    }

    inner class KConfigGenerator(private val kClass: KSClassDeclaration) {

        private val logger: KSPLogger
            get() = environment.logger

        private val currentPackage = kClass.qualifiedName!!.getQualifier()

        operator fun invoke() {
            val className = kClass.qualifiedName!! // Config model can't be anonymous
            val configAnnotation = kClass.getAnnotationsByType<Config>().first()

            environment.codeGenerator.createNewFile(
                dependencies = Dependencies(true, kClass.containingFile!!),
                packageName = className.getQualifier(),
                fileName = configAnnotation.wrapperName
            ).bufferedWriter().use { fileWriter ->
                makeWrapper(configAnnotation, className, collectFields(kClass, configAnnotation.defaultHook))
                    .let { fileWriter.append(it) }
            }
        }

        private fun collectFields(clazz: KSClassDeclaration, defaultHook: Boolean): Pair<List<ConfigField>, Set<NestedClass>> {

            val list = mutableListOf<ConfigField>()
            val nestClasses = mutableSetOf<NestedClass>()

            for (field in clazz.getAllProperties()) {
                if (!field.hasBackingField) {
                    logger.warn("Property does not have backing field, not generating config", field)
                    continue
                }

                val fieldType = field.type.resolve()
                val fieldName = field.simpleName.asString()
                if (fieldType.declaration is KSTypeParameter) {
                    logger.error(
                        "Generic field types are not allowed in config classes",
                        field
                    )
                }
                val typeElement: KSClassDeclaration? = (fieldType.declaration as? KSClassDeclaration)?.let {
                    it.takeIf { it !== clazz } ?: run {
                        logger.error("Illegal self-reference in nested config object", field)
                        null
                    }
                }

                if (typeElement != null && field.hasAnnotation<Nest>()) {
                    val (subProps, subClasses) =
                        collectFields(typeElement, defaultHook || field.hasAnnotation<Hook>())
                    nestClasses += subClasses
                    nestClasses += NestedClass(typeElement.simpleName, subProps)
                    list += NestField(fieldName, Option.Key(fieldName), typeElement.simpleName.asString())
                } else {
                    list += ValueField(fieldName, Option.Key(fieldName), field, defaultHook || field.hasAnnotation<Hook>(), currentPackage)
                }

            }

            return list to nestClasses
        }

        private fun makeWrapper(config: Config, sourceName: KSName, parameters: Pair<List<ConfigField>, Set<NestedClass>>): String {
            val writer = Writer()

            val properties = parameters.first.asString()

            val classes = parameters.second.map {
                Writer().apply {
                    it.appendClassDeclaration(this)
                }
            }.joinToString(separator = "\n") { it.toString() }

            writer += ("""
                package ${sourceName.getQualifier()}

                import io.wispforest.owo.config.ConfigWrapper
                import io.wispforest.owo.config.Option
                import kotlin.reflect.KProperty

                class ${config.wrapperName} private constructor(builder: BuilderConsumer) : ConfigWrapper<${sourceName.getShortName()}>(${sourceName.getShortName()}::class.java, builder) {
                    private val parentKey = Option.Key.ROOT

                    constructor() : this({})

                    companion object {
                        fun createAndLoad(builder: BuilderConsumer = BuilderConsumer {}): ${config.wrapperName} {
                            return ${config.wrapperName}(builder).apply {
                                load()
                            }
                        }
                    }
                    
                    $properties
                    
                    $classes
                }
                
                private operator fun <T> Option<T>.getValue(hisRef: Any?, property: KProperty<*>): T = this.value()
                private operator fun <T> Option<T>.setValue(hisRef: Any?, property: KProperty<*>, value: T): Unit = this.set(value)
            """)

            return writer.toString()
        }
    }
}

sealed class ConfigField {
    abstract fun appendProperties(writer: Writer)
}

class NestField(
    private val name: String,
    private val key: Option.Key,
    private val className: String,
) : ConfigField() {
    override fun appendProperties(writer: Writer) {
        writer += """
            val $name = ${className}(parentKey.child("${key.asString()}"))
        """.trimIndent()
    }

}

class ValueField(
    private val fieldName: String,
    private val key: Option.Key,
    private val field: KSPropertyDeclaration,
    private val makeSubscribe: Boolean,
    private val currentPackage: String,
) : ConfigField() {

    override fun appendProperties(writer: Writer) {
        // Kotlin delegated properties
        val simplifiedType = simplifyTypeName(field.type.resolve().toTypeName())

        writer += """
            ${if (field.isMutable) "var" else "val"} $fieldName: $simplifiedType by optionForKey(parentKey.child("${key.asString()}"))!!
        """.trimIndent()

        writer.nl()
        if (makeSubscribe) {
            writer += """
                fun subscribeTo${fieldName.replaceFirstChar { it.uppercase() }}(subscriber: ($simplifiedType) -> Unit) {
                    optionForKey<$simplifiedType>(Option.Key("${key.asString()}"))!!.observe(subscriber)
                }
            """.trimIndent()
        }
    }

    private fun simplifyTypeName(type: TypeName): String {
        return when (type) {
            is ParameterizedTypeName -> {
                val rawType = simplifyClassName(type.rawType)
                val typeArguments = type.typeArguments.joinToString(", ") { simplifyTypeName(it) }
                "$rawType<$typeArguments>"
            }
            is ClassName -> simplifyClassName(type)
            else -> type.toString()
        }
    }

    private fun simplifyClassName(className: ClassName): String {
        // If the class is in the same package, or in the kotlin package or its subpackages, use simple name
        return if (className.packageName == currentPackage || className.packageName == "kotlin" || className.packageName.startsWith("kotlin.")) {
            className.simpleNames.joinToString(".")
        } else {
            className.canonicalName
        }
    }
}

class NestedClass(
    val name: KSName,
    private val props: List<ConfigField>,
) {
    fun appendClassDeclaration(writer: Writer) {
        writer += """
            inner class ${name.asString()}(parentKey: Option.Key) {
                
                ${props.asString()}
                
            }
        """.trimIndent()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as NestedClass

        return name == other.name
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }

}

fun Iterable<ConfigField>.asString() =
    map {
        Writer().apply {
            it.appendProperties(this)
        }
    }.joinToString(separator = "\n") { it.toString() }

@OptIn(KspExperimental::class)
private inline fun <reified T : Annotation> KSAnnotated.getAnnotationsByType() =
    getAnnotationsByType(T::class)

private inline fun <reified T : Annotation> KSAnnotated.hasAnnotation() =
    getAnnotationsByType<T>().any()
