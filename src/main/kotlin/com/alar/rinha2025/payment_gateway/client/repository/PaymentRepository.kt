package com.alar.rinha2025.payment_gateway.client.repository

import com.alar.rinha2025.payment_gateway.MainVerticle
import com.alar.rinha2025.payment_gateway.domain.Payment
import io.vertx.core.Future
import io.vertx.core.json.JsonObject
import io.vertx.sqlclient.Pool
import io.vertx.sqlclient.Row
import io.vertx.sqlclient.RowSet
import io.vertx.sqlclient.Tuple
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime

class PaymentRepository(val pgPool: Pool) { // Made pgPool public for MainVerticle
    companion object {
        private const val AMOUNT_MULTIPLIER = 1000L // Removed redundant semicolon
        private val AMOUNT_MULTIPLIER_BIG = BigDecimal(AMOUNT_MULTIPLIER)
        private val logger: Logger = LoggerFactory.getLogger(PaymentRepository::class.java)
    }

    fun savePayment(payment: Payment, requestedAt: LocalDateTime, fallback: Boolean): Future<Unit> {
        // withConnection is a great pattern that correctly handles resource cleanup.
        val amountAsLong = payment.amount.multiply(AMOUNT_MULTIPLIER_BIG).toLong()
        return pgPool.withConnection { connection ->

            connection
                .preparedQuery(
                    """
          INSERT INTO payments (correlationId, amount, requestedAt, fallback)
          VALUES ($1, $2, $3, $4)
          """
                )
                .execute(Tuple.of(payment.correlationId, amountAsLong, requestedAt, fallback))
                .onSuccess {
                    logger.info("Payment saved successfully: {}", payment.correlationId)
                }
                // This is the crucial fix: log the actual error.
                .onFailure { error ->
                    logger.error("Failed to save payment {}: {}", payment.correlationId, error.message, error)
                }
                // Map the result to Future<Unit> for idiomatic Kotlin.
                .map { }
        }
    }

    fun getSummary(from: LocalDateTime, to: LocalDateTime): Future<JsonObject> {
        return pgPool.withConnection { connection ->
            connection
                .preparedQuery(
                    "SELECT COUNT(*) as count, SUM(amount) total_amount, fallback as fallback FROM payments " +
                            " WHERE requestedAt >= $1 AND requestedAt <=   $2 group by fallback"
                )
                .execute(Tuple.of(from, to))
                .onComplete { connection.close() }
                .compose { rows: RowSet<Row> ->
                    val paymentSummaryResult: JsonObject = JsonObject()
                    for (row: Row in rows) {

                        val count = row.getLong("count")
                        val fallBack = row.getBoolean("fallback")
                        val totalAmountLong = row.getLong("total_amount")
                        val totalAmountDouble = BigDecimal(totalAmountLong)
                            .divide(MainVerticle.Companion.AMOUNT_MULTIPLIER_BIG, 2, RoundingMode.HALF_UP).toDouble()

                        val paymentProcessor = JsonObject()
                        paymentProcessor.put("totalRequests", count)
                        paymentProcessor.put(
                            "totalAmount", totalAmountDouble
                        )

                        if (fallBack) {
                            paymentSummaryResult.put("fallback", paymentProcessor)
                        } else {
                            paymentSummaryResult.put("default", paymentProcessor)
                        }
                    }
                    Future<JsonObject>.succeededFuture(paymentSummaryResult)
                }
        }

    }
}
