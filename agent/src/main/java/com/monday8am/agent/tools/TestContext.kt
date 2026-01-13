package com.monday8am.agent.tools

/**
 * Singleton to hold runtime context variables for tests. This allows tools to access test-specific
 * context (e.g., mealType, latitude, longitude) without needing to pass them as parameters.
 */
object TestContext {
    private val _variables = mutableMapOf<String, Any?>()

    /** Get all context variables. */
    val variables: Map<String, Any?>
        get() = synchronized(this) { _variables.toMap() }

    /** Set context variables (replaces all existing variables). */
    fun setVariables(vars: Map<String, Any?>) {
        synchronized(this) {
            _variables.clear()
            _variables.putAll(vars)
        }
    }

    /** Get a context variable by key. */
    fun get(key: String): Any? {
        return synchronized(this) { _variables[key] }
    }

    /** Clear all context variables. */
    fun clear() {
        synchronized(this) { _variables.clear() }
    }
}
