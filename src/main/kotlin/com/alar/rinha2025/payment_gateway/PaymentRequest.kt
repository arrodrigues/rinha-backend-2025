package com.alar.rinha2025.payment_gateway

import java.math.BigDecimal

data class PaymentRequest(val correlationId:String, val amount: BigDecimal)
