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
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.math.BigDecimal

class MainVerticle : VerticleBase() {
  val logger: Logger = LoggerFactory.getLogger(MainVerticle::class.java)
  override fun start(): Future<*> {

    logger.info("aqui")
    val router = Router.router(vertx)
    val config = vertx.orCreateContext.config()
    val paymentsProcessorUri = config.getString("payments-processor-uri", "default")
    val webClient = WebClient.create(vertx);
    val paymentProcessorRequest: HttpRequest<Buffer> = webClient!!.postAbs(paymentsProcessorUri)

    router.route().handler(BodyHandler.create())

    router
      .route(HttpMethod.POST, "/payments")
      .handler { context ->

        val body = context.body()
        val bodyAsJson = body.asJsonObject()
        val correlationId = bodyAsJson.getString("correlationId")
        val amount = bodyAsJson.getString("amount")

        val reqObject = JsonObject()
        reqObject.put("correlationId", correlationId)
        reqObject.put("amount", amount)
        paymentProcessorRequest
          .sendJson(reqObject)
          .onSuccess { httpResponse: HttpResponse<Buffer>? ->
            context.response().statusCode = 200
            //todo: leave the body as empty as possible
            context.end()
          }
          .onFailure {
            context.response().statusCode = 505
            context.end()
          }

      }

    router.route(HttpMethod.GET, "/payments-summary")
      .handler { context ->

        val from: String = context.request().getParam("from")
        val to: String = context.request().getParam("to")

        context.end()

      }


    return vertx.createHttpServer()
      .requestHandler(router)
      .listen(8888).onSuccess { http ->
        logger.info("HTTP server started on port 8888")
      }
  }
}
