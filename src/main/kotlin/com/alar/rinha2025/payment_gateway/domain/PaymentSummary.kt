package com.alar.rinha2025.payment_gateway.domain

import java.math.BigDecimal

data class ProcessorSummary(
  val totalRequests: Long,
  val totalAmount: BigDecimal
)

data class PaymentSummary(
  val default: ProcessorSummary?,
  val fallback: ProcessorSummary?
)
