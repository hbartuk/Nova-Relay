package com.radiantbyte.novarelay.config

data class EnhancedServerConfig(

    val maxRetryAttempts: Int = 5,

    val initialRetryDelay: Long = 2000L,

    val maxRetryDelay: Long = 30000L,

    val backoffMultiplier: Double = 2.0,

    val connectionTimeout: Long = 15000L,

    val sessionTimeout: Long = 20000L,

    val timeBetweenConnectionAttempts: Long = 2000L,

    val compatibilityMode: Boolean = true,

    val initialConnectionDelay: Long = 1000L,

    val enableConnectionThrottling: Boolean = true,

    val connectionThrottleDelay: Long = 5000L
) {

    companion object {
        val DEFAULT = EnhancedServerConfig()

        val AGGRESSIVE = EnhancedServerConfig(
            maxRetryAttempts = 8,
            initialRetryDelay = 5000L,
            maxRetryDelay = 60000L,
            backoffMultiplier = 2.5,
            connectionTimeout = 25000L,
            sessionTimeout = 30000L,
            timeBetweenConnectionAttempts = 3000L,
            initialConnectionDelay = 3000L,
            connectionThrottleDelay = 10000L
        )

        val FAST = EnhancedServerConfig(
            maxRetryAttempts = 3,
            initialRetryDelay = 1000L,
            maxRetryDelay = 10000L,
            backoffMultiplier = 1.5,
            connectionTimeout = 10000L,
            sessionTimeout = 15000L,
            timeBetweenConnectionAttempts = 1500L,
            initialConnectionDelay = 500L,
            connectionThrottleDelay = 2000L
        )
    }

    fun calculateRetryDelay(attemptNumber: Int): Long {
        val delay = (initialRetryDelay * Math.pow(backoffMultiplier, attemptNumber.toDouble())).toLong()
        return minOf(delay, maxRetryDelay)
    }

    fun isProtectedServer(hostname: String): Boolean {
        return hostname.contains("aternos.me") ||
                hostname.contains("aternos.org") ||
                hostname.contains("aternos.net")
    }
}