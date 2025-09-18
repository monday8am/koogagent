package com.monday8am.agent

class PromptTemplate private constructor(
    val template: String,
    private val defaultValues: Map<String, String> = emptyMap()
) {
    fun fill(values: Map<String, String>): String {
        var result = template
        val allValues = defaultValues + values
        allValues.forEach { (key, value) ->
            // Using a regex to match placeholders like {key}
            // Ensure your placeholder syntax is consistent
            val placeholderRegex = Regex("\\{$key\\}")
            result = placeholderRegex.replace(result, value)
        }
        return result
    }

    class Builder(private val template: String) {
        private val defaultValues = mutableMapOf<String, String>()

        fun addDefaultValue(key: String, value: String) = apply {
            defaultValues[key] = value
        }

        fun build(): PromptTemplate {
            return PromptTemplate(template, defaultValues.toMap()) // Create immutable map
        }
    }
}
