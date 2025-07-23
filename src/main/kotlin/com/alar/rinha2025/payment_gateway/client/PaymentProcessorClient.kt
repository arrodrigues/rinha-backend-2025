package com.alar.rinha2025.payment_gateway.client

import io.vertx.ext.web.client.WebClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class PaymentProcessorClient (private val client: WebClient) {
  companion object {
    private val logger: Logger = LoggerFactory.getLogger(PaymentProcessorClient::class.java)
  }

}
