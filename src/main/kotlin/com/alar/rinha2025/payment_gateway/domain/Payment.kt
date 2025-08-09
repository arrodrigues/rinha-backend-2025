package com.alar.rinha2025.payment_gateway.domain

import java.math.BigDecimal
import java.util.UUID

data class Payment(
  val correlationId: UUID,
  val amount: BigDecimal
)
