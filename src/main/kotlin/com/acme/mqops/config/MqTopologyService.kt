package com.acme.mqops.config

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.Optional

@ApplicationScoped
class MqTopologyService {
    private val config: MqTopologyView

    @Inject
    constructor(config: MqTopologyConfig) {
        this.config = config
    }

    constructor(config: MqTopologyView) {
        this.config = config
    }

    fun queueManagers(): Map<String, MqQueueManagerTopology> =
        config.queueManagers().mapValues { (queueManagerKey, queueManager) ->
            MqQueueManagerTopology(
                key = queueManagerKey,
                name = queueManager.name().orElse(queueManagerKey),
                host = queueManager.host(),
                port = queueManager.port(),
                channels = queueManager.channels().mapValues { (channelKey, channel) ->
                    MqChannelTopology(
                        key = channelKey,
                        name = channel.name(),
                        allowedQueues = channel.allowedQueues()
                    )
                }
            )
        }

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
            username = channel.username().notBlankOrNull(),
            password = channel.password().notBlankOrNull()
        )
    }

    private fun Optional<String>.notBlankOrNull(): String? =
        if (isPresent) {
            get().takeIf { it.isNotBlank() }
        } else {
            null
        }
}
