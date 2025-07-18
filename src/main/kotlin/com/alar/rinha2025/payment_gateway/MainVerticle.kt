package com.alar.rinha2025.payment_gateway

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
import io.vertx.pgclient.PgBuilder
import io.vertx.pgclient.PgConnectOptions
import io.vertx.sqlclient.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.*
import java.time.format.DateTimeFormatter


class MainVerticle : VerticleBase() {

  companion object {
    val logger: Logger = LoggerFactory.getLogger(MainVerticle::class.java)
    val AMOUNT_MULTIPLIER = 1000L
    val AMOUNT_MULTIPLIER_BIG = BigDecimal(AMOUNT_MULTIPLIER)
    val isoFormatter: DateTimeFormatter = DateTimeFormatter.ISO_INSTANT.withZone(ZoneId.of("UTC"))
  }

  override fun start(): Future<*> {
    val router = Router.router(vertx)
    val config = vertx.orCreateContext.config()

    val paymentsProcessorUri = config.getString("payments-processor-uri")
    val paymentProcessorRequest: HttpRequest<Buffer> = WebClient.create(vertx)!!.postAbs(paymentsProcessorUri)

    val pgPool: Pool =
      PgBuilder
        .pool()
        .with(PoolOptions().setMaxSize(10))
        .connectingTo(createPgConnectionOptions(config))
        .using(vertx)!!.build()

    router.route().handler(BodyHandler.create())

    router
      .route(HttpMethod.POST, "/payments")
      .handler { context ->
        val body = context.body()
        val bodyAsJson = body.asJsonObject()
        val correlationId = bodyAsJson.getString("correlationId")
        val amount = bodyAsJson.getDouble("amount") * AMOUNT_MULTIPLIER
        val requestedAt = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC)
        val reqObject = JsonObject()
        reqObject.put("correlationId", correlationId)
        reqObject.put("amount", bodyAsJson.getDouble("amount"))
        paymentProcessorRequest
          .sendJson(reqObject)
          .onSuccess { httpResponse: HttpResponse<Buffer>? ->
            context.response().statusCode = 200
            //todo: leave the body as empty as possible
            //todo: get from payment process circuit breaker if it was executed on default or fallback
            val fallback = false
            pgPool
              .connection
              .compose { connection ->
                connection
                  .preparedQuery(
                    "INSERT INTO payments (correlationId, amount, requestedAt, fallback) " +
                      " VALUES ($1, $2, $3, $4)"
                  )
                  .execute(Tuple.of(correlationId, amount, requestedAt, fallback))
                  .onComplete { connection.close() }
              }
              .onComplete { ar ->
                if (ar.succeeded()) {
                  context.response().statusCode = 200
                  context.response().end() // End response here
                } else {
                  logger.error("Failed to insert payment into DB: ${ar.cause().message}", ar.cause())
                  context.response().statusCode = 500
                  context.response().end("Internal Server Error: Database insertion failed.")
                }
              }
          }
          .onFailure {
            context.response().statusCode = 500
            context.end()
          }

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

        pgPool
          .connection
          .compose { connection ->
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
                    .divide(AMOUNT_MULTIPLIER_BIG, 2, RoundingMode.HALF_UP).toDouble()

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
          .onComplete { ar ->
            if (ar.succeeded()) {
              context.response().statusCode = 200
              context.response().putHeader("content-type", "application/json")
              context.response().end(ar.result().encode())
            } else {
              logger.error("Failed to get payment summary from DB: ${ar.cause().message}", ar.cause())
              context.response().statusCode = 500
              context.response().end("Internal Server Error: Could not retrieve payment summary.")
            }
          }
      }


    return vertx.createHttpServer()
      .requestHandler(router)
      .listen(8888).onSuccess { http ->
        logger.info("HTTP server started on port 8888")
      }
  }

  private fun createPgConnectionOptions(config: JsonObject): PgConnectOptions {
    val connectOptions = PgConnectOptions()
    connectOptions.password = config.getString("db-pass")
    connectOptions.user = config.getString("db-user")
    connectOptions.host = config.getString("db-host")
    connectOptions.port = config.getInteger("db-port")
    connectOptions.database = config.getString("db-name")
    connectOptions.cachePreparedStatements = true
    return connectOptions
  }
}
