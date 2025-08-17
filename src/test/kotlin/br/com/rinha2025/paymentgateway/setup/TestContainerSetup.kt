package br.com.rinha2025.paymentgateway.setup

import org.testcontainers.containers.ComposeContainer
import org.testcontainers.containers.wait.strategy.Wait
import java.io.File

class TestContainerSetup {

  companion object {

    private val compose: ComposeContainer =
      ComposeContainer(File("dev/docker-compose-dev.yml"))
        .apply {
          waitingFor("db", Wait.forHealthcheck())
        }

    fun start() {
      compose.start()
    }

    fun stop() {
      compose.stop()
    }
  }
}
