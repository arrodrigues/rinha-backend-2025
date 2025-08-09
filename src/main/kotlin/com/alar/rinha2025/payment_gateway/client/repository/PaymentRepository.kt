package com.alar.rinha2025.payment_gateway.client.repository

import com.alar.rinha2025.payment_gateway.domain.Payment
import com.alar.rinha2025.payment_gateway.domain.PaymentSummary
import com.alar.rinha2025.payment_gateway.domain.ProcessorSummary
import io.vertx.core.Future
import io.vertx.sqlclient.Pool
import io.vertx.sqlclient.Row
import io.vertx.sqlclient.RowSet
import io.vertx.sqlclient.Tuple
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime

class PaymentRepository(val pgPool: Pool) {
    companion object {
        private const val AMOUNT_MULTIPLIER = 1000L
        private val AMOUNT_MULTIPLIER_BIG = BigDecimal(AMOUNT_MULTIPLIER)
        private val logger: Logger = LoggerFactory.getLogger(PaymentRepository::class.java)
        private val emptySummary = ProcessorSummary(totalRequests = 0, totalAmount = BigDecimal.ZERO)
    }

    fun savePayment(payment: Payment, requestedAt: LocalDateTime, fallback: Boolean): Future<Unit> {
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
                .onFailure { error ->
                    logger.error("Failed to save payment {}: {}", payment.correlationId, error.message, error)
                }
                .map { }
        }
    }

    fun getSummary(from: LocalDateTime, to: LocalDateTime): Future<PaymentSummary> {
        return pgPool.withConnection { connection ->
            connection
                .preparedQuery(
                    "SELECT COUNT(*) as count, SUM(amount) total_amount, fallback as fallback FROM payments " +
                            " WHERE requestedAt >= $1 AND requestedAt <=   $2 group by fallback"
                )
                .execute(Tuple.of(from, to))
                .onComplete { connection.close() }
                .compose { rows: RowSet<Row> ->
                    var fallback: ProcessorSummary? = null
                    var default: ProcessorSummary? = null
                    for (row: Row in rows) {
                        val count = row.getLong("count")
                        val isFallBack = row.getBoolean("fallback")
                        val totalAmountLong = row.getLong("total_amount")
                        val totalAmountDouble = BigDecimal(totalAmountLong)
                            .divide(AMOUNT_MULTIPLIER_BIG, 2, RoundingMode.HALF_UP)

                        if (isFallBack) {
                            fallback = ProcessorSummary(count, totalAmountDouble)
                        } else {
                            default = ProcessorSummary(count, totalAmountDouble)
                        }
                    }
                    Future<PaymentSummary>.succeededFuture(PaymentSummary(default ?: emptySummary, fallback ?: emptySummary))
                }
        }

    }
}
