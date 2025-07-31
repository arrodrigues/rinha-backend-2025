package com.alar.rinha2025.payment_gateway.client

import com.alar.rinha2025.payment_gateway.config.AppConfig
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.client.HttpRequest
import io.vertx.ext.web.client.HttpResponse
import io.vertx.ext.web.client.WebClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicInteger

class PaymentProcessorClient(private val vertx: Vertx) {

  val paymentProcessorRequest: HttpRequest<Buffer> =
    WebClient.create(vertx).postAbs(AppConfig.getDefaultPaymentProcessorUri())

  companion object {
    private val logger: Logger = LoggerFactory.getLogger(PaymentProcessorClient::class.java)
    private val randomnessFallback: AtomicInteger = AtomicInteger()
  }



  fun makePayment(reqObject: JsonObject): Future<HttpResponse<Buffer>> {
    return paymentProcessorRequest.sendJson(reqObject).onSuccess { response ->
      response.headers().add("fallback",if (randomnessFallback.getAndIncrement()%2==0 ) "false"  else "true")
      logger.info("Payment processed successfully. Status code: ${response.statusCode()}")
    }
    .onFailure { error ->
      logger.error("Payment processing failed: ${error.message}")
    }
  }


}
