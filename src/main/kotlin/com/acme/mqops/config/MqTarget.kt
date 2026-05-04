package com.acme.mqops.config

data class MqTarget(
    val queueManagerKey: String,
    val queueManagerName: String,
    val host: String,
    val port: Int,
    val channelKey: String,
    val channelName: String,
    val queueName: String,
    val username: String?,
    val password: String?
)

class InvalidMqTargetException(message: String) : RuntimeException(message)
