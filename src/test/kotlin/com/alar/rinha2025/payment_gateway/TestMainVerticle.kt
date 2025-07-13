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
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.Connection
import java.sql.DriverManager


@Testcontainers
@ExtendWith(VertxExtension::class)
class TestMainVerticle {


  companion object {
    private val DB_PASSWORD: String = "integration_pw"
    private val DB_USERNAME: String = "integration_username"
    private val DB_NAME: String = "integration_dbname"
    private val DB_PORT = 5432
    private var connection: Connection

    @Container
    private val postgresqlContainer: PostgreSQLContainer<Nothing> = PostgreSQLContainer<Nothing>("postgres:16.9")
      .apply {
        withDatabaseName(DB_NAME)
        withUsername(DB_USERNAME)
        withPassword(DB_PASSWORD)
      }

    init {
      postgresqlContainer.start()
      connection = DriverManager.getConnection(
        postgresqlContainer.jdbcUrl,
        postgresqlContainer.username,
        postgresqlContainer.password
      )

      connection.createStatement().use { statement ->
        statement.execute(
          """
            CREATE TABLE payments (
              correlationId UUID primary key,
              amount BIGINT,
              requestedAt TIMESTAMP,
              fallback boolean
            )
          """.trimIndent()
        )
      }
    }
  }

  @BeforeEach
  fun setUp(vertx: Vertx, testContext: VertxTestContext) {
    val cfg =
      JsonObject()
        .put("payments-processor-uri", "http://localhost:9999/payments")
        .put("db-user", postgresqlContainer.username)
        .put("db-pass", postgresqlContainer.password)
        .put("db-host", "localhost")
        .put("db-port", postgresqlContainer.getMappedPort(DB_PORT))
        .put("db-name", DB_NAME)
    vertx
      .deployVerticle(MainVerticle(), DeploymentOptions().setConfig(cfg))
      .onComplete(testContext.succeedingThenComplete())

  }

  @Test
  fun test_payment(vertx: Vertx, testContext: VertxTestContext) {

    val server = MockWebServer()
    server.start(9999)
    server.enqueue(
      MockResponse.Builder()
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
    server.start(9999)
    server.enqueue(MockResponse.Builder().code(200).build())
    server.enqueue(MockResponse.Builder().code(200).build())
    server.enqueue(MockResponse.Builder().code(200).build())

    val req = WebClient.create(vertx).post(8888, "localhost", "/payments")

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
        WebClient.create(vertx).get(8888, "localhost", "/payments-summary")
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
          server.close();
        }
      }


  }
}
