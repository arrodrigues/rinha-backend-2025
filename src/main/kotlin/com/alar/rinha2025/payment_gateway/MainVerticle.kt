package com.alar.rinha2025.payment_gateway

import com.alar.rinha2025.payment_gateway.config.AppConfig
import com.alar.rinha2025.payment_gateway.config.AppResources
import com.alar.rinha2025.payment_gateway.config.AppResources.Repositories.paymentRepository
import com.alar.rinha2025.payment_gateway.domain.Payment
import com.alar.rinha2025.payment_gateway.extensions.toJson
import io.netty.handler.codec.http.HttpResponseStatus.*
import io.vertx.core.Future
import io.vertx.core.VerticleBase
import io.vertx.core.http.HttpMethod
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.BodyHandler
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.LinkedBlockingDeque


class MainVerticle : VerticleBase() {

  companion object {
    val logger: Logger = LoggerFactory.getLogger(MainVerticle::class.java)
    val isoFormatter: DateTimeFormatter = DateTimeFormatter.ISO_INSTANT.withZone(ZoneId.of("UTC"))

    const val HEADER_SERVER_NAME = "X-Served-By"
    val paymentQueue = LinkedBlockingDeque<Payment>(1000)
  }

  override fun start(): Future<*> {
    AppResources.init(vertx)

    val router = Router.router(vertx)

    // Deploy the queue consumer verticle
    vertx.deployVerticle(PaymentQueueConsumer(paymentQueue))

    router.route().handler(BodyHandler.create())

    router
      .route(HttpMethod.POST, "/payments")
      .handler { context ->
        val body = context.body()
        val bodyAsJson = body.asJsonObject()

        val payment = Payment(
          correlationId = UUID.fromString(bodyAsJson.getString("correlationId")),
          amount = BigDecimal(bodyAsJson.getString("amount")),
          requestedAt = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC)
        )

        val enqueued = paymentQueue.offer(payment)
        context.response().statusCode = if (enqueued) ACCEPTED.code() else SERVICE_UNAVAILABLE.code()
        context.response().end()

      }

    router.route(HttpMethod.GET, "/payments-summary")
      .handler { context ->

        val request = context.request()
        val paramFrom: String? = request.getParam("from")
        val paramTo: String? = request.getParam("to")

        val from: LocalDateTime =
          if (paramFrom != null) ZonedDateTime.parse(paramFrom, isoFormatter)
            .toLocalDateTime() else LocalDateTime.MIN
        val to: LocalDateTime =
          if (paramTo != null) ZonedDateTime.parse(paramTo, isoFormatter).toLocalDateTime() else LocalDateTime.MAX

        paymentRepository
          .getSummary(from, to)
          .onSuccess { resp ->
            context.response().statusCode = OK.code()
            context.response().putHeader("content-type", "application/json")
            context.response().end(resp.toJson())
          }
          .onFailure { ex ->
            logger.error("Failed to get payment summary from DB: ${ex.cause?.message}", ex)
            context.response().statusCode = INTERNAL_SERVER_ERROR.code()
            context.response().end("Internal Server Error: Could not retrieve payment summary.")
          }
      }


    router.route(HttpMethod.DELETE, "/payments")
      .handler { context ->
        paymentRepository.deleteAll()
          .onSuccess {
            context.response().statusCode = NO_CONTENT.code()
            context.response().end()
          }
          .onFailure { ex ->
            logger.error("Failed to delete all payments from DB: ${ex.cause?.message}", ex)
            context.response().statusCode = INTERNAL_SERVER_ERROR.code()
            context.response().end("Internal Server Error: Could not delete payments.")
          }
      }

    val port = AppConfig.getServerPort()
    return vertx.createHttpServer()
      .requestHandler(router)
      .listen(port).onSuccess { _ ->
        logger.info("HTTP server started on port $port")
      }
  }

}
