package com.alar.rinha2025.payment_gateway.setup

import org.testcontainers.containers.ComposeContainer
import org.testcontainers.containers.wait.strategy.Wait
import java.io.File

class TestContainerSetup {

  companion object {

    private val compose: ComposeContainer =
      ComposeContainer(File("docker-compose.yml"))
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
