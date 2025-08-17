package br.com.rinha2025.paymentgateway.config

import io.vertx.core.json.JsonObject
import java.util.*

object AppConfig {

    enum class PropertyKeys {
        DB_HOST,
        DB_PORT,
        DB_NAME,
        DB_USERNAME,
        DB_PASSWORD,
        SERVER_PORT,
        PAYMENTS_PROCESSOR_DEFAULT_URI,
        PAYMENTS_PROCESSOR_FALLBACK_URI
    }

    val config: JsonObject by lazy {
        val props = Properties().apply {
            AppConfig::class.java.classLoader.getResourceAsStream("application.properties")?.use { load(it) }
        }
        JsonObject().apply {
            PropertyKeys.entries.forEach { value ->
                val key = value.name
                put(key, System.getenv(key) ?: props.getProperty(key))
            }
        }
    }

    fun getDbHost() = config.getString(PropertyKeys.DB_HOST.name)
    fun getDbPort() = config.getString(PropertyKeys.DB_PORT.name).toInt()
    fun getDbName() = config.getString(PropertyKeys.DB_NAME.name)
    fun getDbUsername() = config.getString(PropertyKeys.DB_USERNAME.name)
    fun getDbPassword() = config.getString(PropertyKeys.DB_PASSWORD.name)

    fun getServerPort() = config.getString(PropertyKeys.SERVER_PORT.name).toInt()

    fun getDefaultPaymentProcessorUri() = config.getString(PropertyKeys.PAYMENTS_PROCESSOR_DEFAULT_URI.name)
    fun getFallbackPaymentProcessorUri() = config.getString(PropertyKeys.PAYMENTS_PROCESSOR_FALLBACK_URI.name)
}
