package com.alar.rinha2025.payment_gateway

import io.vertx.core.DeploymentOptions
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.client.HttpResponse
import io.vertx.ext.web.client.WebClient
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import io.vertx.kotlin.core.json.get
import mockwebserver3.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith


@ExtendWith(VertxExtension::class)
class TestMainVerticle {


  @BeforeEach
  fun setUp(vertx: Vertx, testContext: VertxTestContext) {
    val cfg =
      JsonObject().put("payments-processor-uri", "http://localhost:9999/payments")
    vertx.deployVerticle(MainVerticle(), DeploymentOptions().setConfig(cfg))
      .onComplete(testContext.succeedingThenComplete())
  }

  @Test
  fun test_payment(vertx: Vertx, testContext: VertxTestContext) {

    val server = MockWebServer()
    server.start(9999)
    server.enqueue(
      mockwebserver3.MockResponse.Builder()
        .code(200)
        .body("resp")
        .build()
    )
    val req =
      WebClient.create(vertx)
        .post(8888, "localhost", "/payments")

    req
      .putHeader("Content-Type", "application/json")
      .sendJson(
        JsonObject(""" { "correlationId": "4a7901b8-7d26-4d9d-aa19-4dc1c7cf60b3", "amount": "19.90" } """)
      )
      .compose<Buffer> { response: HttpResponse<Buffer>? -> Future.succeededFuture<Buffer>(response!!.body()) }
      .onFailure { t -> testContext.failNow(t) }
      .onSuccess { response ->
        testContext.verify {
          val recordedRequest = server.takeRequest()
          assertThat(recordedRequest.method).isEqualTo("POST")
          assertThat(recordedRequest.target).isEqualTo("/payments")
          val jsonObject = JsonObject(recordedRequest.body!!.utf8())
          assertThat(jsonObject.get<String>("correlationId")).isEqualTo("4a7901b8-7d26-4d9d-aa19-4dc1c7cf60b3")
          assertThat(jsonObject.get<String>("amount")).isEqualTo("19.90")
          testContext.completeNow()
          server.close();
        }
      }


  }
}
