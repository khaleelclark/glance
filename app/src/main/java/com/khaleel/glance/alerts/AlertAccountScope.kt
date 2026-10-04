package com.khaleel.glance.alerts

/** Backend-supplied SHA-256 account identity; never a raw provider account ID. */
internal object AlertAccountScope {
    private val pattern = Regex("[0-9a-f]{64}")

    fun requireValid(value: String): String = value.also {
        require(pattern.matches(it)) { "Invalid account scope key" }
    }
}
