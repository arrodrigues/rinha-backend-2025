package com.alar.rinha2025.payment_gateway

import com.alar.rinha2025.payment_gateway.config.AppConfig
import com.alar.rinha2025.payment_gateway.config.AppResources
import com.alar.rinha2025.payment_gateway.domain.Payment
import com.alar.rinha2025.payment_gateway.extensions.toJson
import io.vertx.core.Future
import io.vertx.core.VerticleBase
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.client.HttpRequest
import io.vertx.ext.web.client.HttpResponse
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.handler.BodyHandler
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.*


class MainVerticle : VerticleBase() {

  companion object {
    val logger: Logger = LoggerFactory.getLogger(MainVerticle::class.java)
    const val AMOUNT_MULTIPLIER = 1000L
    val AMOUNT_MULTIPLIER_BIG = BigDecimal(AMOUNT_MULTIPLIER)
    val isoFormatter: DateTimeFormatter = DateTimeFormatter.ISO_INSTANT.withZone(ZoneId.of("UTC"))
  }

  override fun start(): Future<*> {
    AppResources.init(vertx)

    val router = Router.router(vertx)

    val paymentsProcessorUri = AppConfig.getDefaultPaymentProcessorUri()
    val paymentProcessorRequest: HttpRequest<Buffer> = WebClient.create(vertx)!!.postAbs(paymentsProcessorUri)

    router.route().handler(BodyHandler.create())

    router
      .route(HttpMethod.POST, "/payments")
      .handler { context ->
        val body = context.body()
        val bodyAsJson = body.asJsonObject()
        val correlationId = bodyAsJson.getString("correlationId")
        val requestedAt = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC)
        val reqObject = JsonObject()
        reqObject.put("correlationId", correlationId)
        reqObject.put("amount", bodyAsJson.getDouble("amount"))
        paymentProcessorRequest
          .sendJson(reqObject)
          .compose({ _: HttpResponse<Buffer>? ->
            //todo: leave the body as empty as possible
            //todo: get from payment process circuit breaker if it was executed on default or fallback
            val fallback = false
            AppResources.Repositories.paymentRepository.savePayment(
              Payment(
                UUID.fromString(correlationId), BigDecimal(bodyAsJson.getString("amount"))
              ), requestedAt, fallback
            )
          })
          .onComplete({ ar ->
            if (ar.succeeded()) {
              context.response().statusCode = 200
              context.response().end() // End response here
            } else {
              logger.error("Failed to insert payment into DB: ${ar.cause().message}", ar.cause())
              context.response().statusCode = 500
              context.response().end("Internal Server Error: Database insertion failed.")
            }
          })

      }

    router.route(HttpMethod.GET, "/payments-summary")
      .handler { context ->

        val request = context.request()
        val paramFrom: String? = request.getParam("from")
        val paramTo: String? = request.getParam("to")

        val from: LocalDateTime =
          if (paramFrom != null) ZonedDateTime.parse(paramFrom, isoFormatter).toLocalDateTime() else LocalDateTime.MIN
        val to: LocalDateTime =
          if (paramTo != null) ZonedDateTime.parse(paramTo, isoFormatter).toLocalDateTime() else LocalDateTime.MAX

        AppResources.Repositories
          .paymentRepository
          .getSummary(from, to)
          .onComplete { ar ->
            if (ar.succeeded()) {
              context.response().statusCode = 200
              context.response().putHeader("content-type", "application/json")
              context.response().end(ar.result().toJson())
            } else {
              logger.error("Failed to get payment summary from DB: ${ar.cause().message}", ar.cause())
              context.response().statusCode = 500
              context.response().end("Internal Server Error: Could not retrieve payment summary.")
            }
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
