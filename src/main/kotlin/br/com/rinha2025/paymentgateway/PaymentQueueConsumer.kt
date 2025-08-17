package br.com.rinha2025.paymentgateway

import br.com.rinha2025.paymentgateway.MainVerticle.Companion.HEADER_SERVER_NAME
import br.com.rinha2025.paymentgateway.MainVerticle.Companion.logger
import br.com.rinha2025.paymentgateway.config.AppResources.Clients.paymentProcessorClient
import br.com.rinha2025.paymentgateway.config.AppResources.Repositories.paymentRepository
import br.com.rinha2025.paymentgateway.domain.Payment
import io.netty.handler.codec.http.HttpResponseStatus.OK
import io.netty.handler.codec.http.HttpResponseStatus.GATEWAY_TIMEOUT
import io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR
import io.netty.handler.codec.http.HttpResponseStatus.SERVICE_UNAVAILABLE
import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.buffer.Buffer
import io.vertx.ext.web.client.HttpResponse
import java.util.Deque
import java.util.Queue
import java.util.concurrent.LinkedBlockingQueue

class PaymentQueueConsumer(private val paymentQueue: Deque<Payment>) : AbstractVerticle() {


  companion object {
    const val SERVER_NAME_DEFAULT = "default"
  }

  val retryQueue: Queue<Payment> = LinkedBlockingQueue(500)

  override fun start(startPromise: Promise<Void>?) {
    vertx.setPeriodic(100) { // Check for new payments every 100ms
      while (true) {
        var payment = retryQueue.poll()
        if (payment == null) {
          payment = paymentQueue.poll()
          if (payment == null) {
            break // No more payments in the queue
          }
        }
        // Process the payment here (e.g., send to a payment gateway, update database)
        paymentProcessorClient
          .makePayment(payment)
          .compose { resp: HttpResponse<Buffer>? ->
            if (resp?.statusCode() == OK.code()) {
              val serverName = resp.getHeader(HEADER_SERVER_NAME)
              logger.debug("Payment processed by server={}, status={}", serverName, resp.statusCode())
              save(payment, serverName)
            } else if (
              resp?.statusCode() == GATEWAY_TIMEOUT.code() ||
              resp?.statusCode() == SERVICE_UNAVAILABLE.code() ||
              resp?.statusCode() == INTERNAL_SERVER_ERROR.code()
            ) {
              val enqueuedForRetry = retryQueue.offer(payment)
              if (!enqueuedForRetry) {
                Future.failedFuture("Payment cannot be retried, queue is full")
              } else {
                Future.succeededFuture()
              }
            } else {
              Future.failedFuture("Payment processing failed status=${resp?.statusCode()}")
            }
          }
          .onSuccess { _ ->
            logger.debug("Payment executed and saved successfully")
          }
          .onFailure { ex ->
            logger.debug("Failed to process payment: ${ex.cause?.message}", ex)
          }
      }
    }
    startPromise?.complete()
  }

  private fun save(
    payment: Payment,
    serverName: String
  ): Future<Unit> =
    paymentRepository.savePayment(
      payment, fallback = serverName != SERVER_NAME_DEFAULT
    )
}
