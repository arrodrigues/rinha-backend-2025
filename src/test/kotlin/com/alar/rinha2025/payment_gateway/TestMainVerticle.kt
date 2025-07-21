package com.alar.rinha2025.payment_gateway

import com.alar.rinha2025.payment_gateway.config.AppConfig
import com.alar.rinha2025.payment_gateway.setup.TestContainerSetup
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
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.sql.Connection
import java.sql.DriverManager


@ExtendWith(VertxExtension::class)
class TestMainVerticle {


  companion object {
    private lateinit var connection: Connection
    private val JDBC_URL: String =
      "jdbc:postgresql://${AppConfig.getDbHost()}:${AppConfig.getDbPort()}/${AppConfig.getDbName()}"

    @BeforeAll
    @JvmStatic
    fun beforeAll() {
      TestContainerSetup.start()
      connection = DriverManager
        .getConnection(JDBC_URL, AppConfig.getDbUsername(), AppConfig.getDbPassword())
    }

    @JvmStatic
    @AfterAll
    fun afterAll() {
      TestContainerSetup.stop()
    }
  }

  @BeforeEach
  fun setUp(vertx: Vertx, testContext: VertxTestContext) {
    connection.createStatement().use { statement ->
      statement.execute(
        "TRUNCATE TABLE payments".trimIndent()
      )
    }

    vertx
      .deployVerticle(MainVerticle(), DeploymentOptions().setConfig(AppConfig.config))
      .onComplete(testContext.succeedingThenComplete())

  }

  @Test
  fun test_payment(vertx: Vertx, testContext: VertxTestContext) {

    val server = MockWebServer()
    server.start(9000)
    server.enqueue(
      MockResponse.Builder()
        .code(200)
        .body("resp")
        .build()
    )
    val req =
      WebClient.create(vertx)
        .post(AppConfig.getServerPort(), "localhost", "/payments")

    req
      .putHeader("Content-Type", "application/json")
      .sendJson(
        JsonObject(""" { "correlationId": "4a7901b8-7d26-4d9d-aa19-4dc1c7cf60b3", "amount": 19.90 } """)
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
          assertThat(jsonObject.get<Double>("amount")).isEqualTo(19.9)

          connection.createStatement()
            .use { statement ->
              val rs = statement.executeQuery("select correlationId, amount, requestedAt from payments")
              assertThat(rs.next()).isTrue
              assertThat(rs.getString("correlationId")).isEqualTo("4a7901b8-7d26-4d9d-aa19-4dc1c7cf60b3")
              assertThat(rs.getInt("amount")).isEqualTo(19900)
              assertThat(rs.getString("requestedAt")).isNotNull()
            }
          testContext.completeNow()
          server.close();
        }
      }
  }

  @Test
  fun test_summary_after_payments(vertx: Vertx, testContext: VertxTestContext) {

    val server = MockWebServer()
    server.start(9000)
    server.enqueue(MockResponse.Builder().code(200).build())
    server.enqueue(MockResponse.Builder().code(200).build())
    server.enqueue(MockResponse.Builder().code(200).build())

    val req = WebClient.create(vertx).post(AppConfig.getServerPort(), "localhost", "/payments")

    Future.all(
      req
        .putHeader("Content-Type", "application/json")
        .sendJson(
          JsonObject(""" { "correlationId": "4a7901b8-7d26-4d9d-aa19-4dc1c7cf60b3", "amount": 515542345.98 } """)
        ),

      req
        .putHeader("Content-Type", "application/json")
        .sendJson(
          JsonObject(""" { "correlationId": "5a7901b8-7d26-4d9d-aa19-4dc1c7cf60b4", "amount": 615542345.99 } """)
        ),
      req
        .putHeader("Content-Type", "application/json")
        .sendJson(
          JsonObject(""" { "correlationId": "6a7901b8-7d26-4d9d-aa19-4dc1c7cf60b5", "amount": 715542345.10 } """)
        )
    )
      .compose {
        WebClient.create(vertx).get(AppConfig.getServerPort(), "localhost", "/payments-summary")
          .putHeader("Content-Type", "application/json")
          .addQueryParam("from", "2018-07-10T00:00:00.000Z")
          .addQueryParam("to", "2070-07-10T00:00:00.000Z")
          .send()
      }


      .onFailure { t -> testContext.failNow(t) }
      .onSuccess { response ->
        testContext.verify {
          assertThat(response.statusCode()).isEqualTo(200)

          val bodyAsJsonObject: JsonObject = response!!.bodyAsJsonObject()

          val defaultPaymentProcessor = bodyAsJsonObject.get<JsonObject>("default")
          assertThat(defaultPaymentProcessor.getInteger("totalRequests")).isEqualTo(3)
          assertThat(defaultPaymentProcessor.getDouble("totalAmount")).isEqualTo(1846627037.07)


          testContext.completeNow()
          server.close()
        }
      }


  }
}
