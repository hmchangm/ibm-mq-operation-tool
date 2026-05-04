package com.acme.mqops.config

import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class MqTopologyService(private val config: MqTopologyView) {
    fun queueManagers(): Map<String, QueueManagerView> = config.queueManagers()

    fun browseLimit(): Int = config.browseLimit()

    fun receiveTimeoutMs(): Long = config.receiveTimeoutMs()

    fun resolve(queueManagerKey: String, channelKey: String, queueName: String): MqTarget {
        val queueManager = config.queueManagers()[queueManagerKey]
            ?: throw InvalidMqTargetException("Unknown queue manager: $queueManagerKey")
        val channel = queueManager.channels()[channelKey]
            ?: throw InvalidMqTargetException("Unknown channel: $channelKey")
        if (!channel.allowedQueues().contains(queueName)) {
            throw InvalidMqTargetException("Queue is not allowed for selected channel: $queueName")
        }

        return MqTarget(
            queueManagerKey = queueManagerKey,
            queueManagerName = queueManager.name().orElse(queueManagerKey),
            host = queueManager.host(),
            port = queueManager.port(),
            channelKey = channelKey,
            channelName = channel.name(),
            queueName = queueName,
            username = channel.username()?.takeIf { it.isNotBlank() },
            password = channel.password()?.takeIf { it.isNotBlank() }
        )
    }
}
