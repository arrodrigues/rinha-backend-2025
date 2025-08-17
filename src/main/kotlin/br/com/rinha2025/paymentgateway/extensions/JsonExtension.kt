package br.com.rinha2025.paymentgateway.extensions

import io.vertx.core.json.Json.encode
import io.vertx.core.json.JsonObject


inline fun <reified T> T.toJson(): String = encode(this)

inline fun <reified T> T.toJsonObject(): JsonObject = JsonObject.mapFrom(this)

fun ByteArray.toJsonObject(): JsonObject = JsonObject(String(this))

