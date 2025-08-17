package br.com.rinha2025.paymentgateway.client

import br.com.rinha2025.paymentgateway.config.AppConfig
import br.com.rinha2025.paymentgateway.domain.Payment
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.ext.web.client.HttpRequest
import io.vertx.ext.web.client.HttpResponse
import io.vertx.ext.web.client.WebClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class PaymentProcessorClient(private val vertx: Vertx) {

  val paymentProcessorRequest: HttpRequest<Buffer> =
    WebClient.create(vertx).postAbs(AppConfig.getDefaultPaymentProcessorUri())

  companion object {
    private val logger: Logger = LoggerFactory.getLogger(PaymentProcessorClient::class.java)
  }

  fun makePayment(payment: Payment): Future<HttpResponse<Buffer>> {
    return paymentProcessorRequest.sendJson(payment.toJsonObject())
      .onFailure { error ->
        logger.error("Failed to send request to payment processor: ${error.message}", error)
      }
      .map { response ->
        logger.debug("Payment processed successfully. Status code: ${response.statusCode()}   Payment Server: ${response.headers()["X-Served-By"]}")
        response
      }
  }
}
