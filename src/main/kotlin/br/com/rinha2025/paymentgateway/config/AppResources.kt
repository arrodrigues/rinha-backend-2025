package br.com.rinha2025.paymentgateway.config

import br.com.rinha2025.paymentgateway.client.PaymentProcessorClient
import br.com.rinha2025.paymentgateway.client.repository.PaymentRepository
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.pgclient.PgBuilder
import io.vertx.pgclient.PgConnectOptions
import io.vertx.sqlclient.Pool
import io.vertx.sqlclient.PoolOptions

object AppResources {

    fun init(vertx: Vertx) {
        Repositories.init(vertx)
        Clients.init(vertx)
    }

  fun destroy(): Future<Void> {
      // todo: destroy Client also and return the futures combined
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

    object Clients {
        lateinit var paymentProcessorClient: PaymentProcessorClient
        fun init(vertx: Vertx){
            paymentProcessorClient = PaymentProcessorClient(vertx)
        }

        fun destroy(): Future<Void> {
            return Future.succeededFuture()
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
