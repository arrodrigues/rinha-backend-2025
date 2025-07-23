package com.alar.rinha2025.payment_gateway.config;

import com.alar.rinha2025.payment_gateway.client.repository.PaymentRepository
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.pgclient.PgBuilder
import io.vertx.pgclient.PgConnectOptions
import io.vertx.sqlclient.Pool
import io.vertx.sqlclient.PoolOptions

object AppResources {

  fun init(vertx: Vertx) {
    Repositories.init(vertx)
  }

  fun destroy(): Future<Void> {
    return Repositories.destroy()
  }

  object Repositories {
    lateinit var pgPool: Pool
    lateinit var paymentRepository: PaymentRepository
    fun init(vertx: Vertx) {
      pgPool =
        PgBuilder
          .pool()
          .with(PoolOptions().setMaxSize(10))
          .connectingTo(createPgConnectionOptions())
          .using(vertx).build()

      paymentRepository = PaymentRepository(pgPool)
    }

    fun destroy(): Future<Void> {
     return pgPool.close()
    }
  }

  private fun createPgConnectionOptions(): PgConnectOptions {
    val connectOptions = PgConnectOptions()
    connectOptions.password = AppConfig.getDbPassword()
    connectOptions.user = AppConfig.getDbUsername()
    connectOptions.host = AppConfig.getDbHost()
    connectOptions.port = AppConfig.getDbPort()
    connectOptions.database = AppConfig.getDbName()
    connectOptions.cachePreparedStatements = true
    return connectOptions
  }


}
