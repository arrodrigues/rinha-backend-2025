package com.alar.rinha2025.payment_gateway.domain

import io.vertx.core.json.JsonObject
import java.math.BigDecimal
import java.util.*

data class Payment(
  val correlationId: UUID,
  val amount: BigDecimal,
  val requestedAt: String
) {

  fun toJsonObject(): JsonObject {
    return JsonObject()
      .put("correlationId", correlationId)
      .put("amount", amount)
      .put("requestedAt", requestedAt)

  }
}
