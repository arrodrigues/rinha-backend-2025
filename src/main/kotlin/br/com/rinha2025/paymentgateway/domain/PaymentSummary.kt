package br.com.rinha2025.paymentgateway.domain

import java.math.BigDecimal

data class ProcessorSummary(
  val totalRequests: Long,
  val totalAmount: BigDecimal
)

data class PaymentSummary(
  val default: ProcessorSummary,
  val fallback: ProcessorSummary
)
