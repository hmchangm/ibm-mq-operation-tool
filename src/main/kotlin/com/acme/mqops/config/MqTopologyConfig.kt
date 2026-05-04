package com.acme.mqops.config

import io.quarkus.runtime.annotations.StaticInitSafe
import io.smallrye.config.ConfigMapping
import jakarta.enterprise.context.ApplicationScoped
import java.util.Optional

interface MqTopologyView {
    fun browseLimit(): Int
    fun receiveTimeoutMs(): Long
    fun queueManagers(): Map<String, QueueManagerView>
}

interface QueueManagerView {
    fun name(): Optional<String>
    fun host(): String
    fun port(): Int
    fun channels(): Map<String, ChannelView>
}

interface ChannelView {
    fun name(): String
    fun username(): Optional<String>
    fun password(): Optional<String>
    fun allowedQueues(): List<String>
}

data class MqQueueManagerTopology(
    val key: String,
    val name: String,
    val host: String,
    val port: Int,
    val channels: Map<String, MqChannelTopology>
)

data class MqChannelTopology(
    val key: String,
    val name: String,
    val allowedQueues: List<String>
)

@StaticInitSafe
@ConfigMapping(prefix = "mq")
interface MqTopologyConfig : MqTopologyView

@ApplicationScoped
class MqTopologyConfigHolder(val config: MqTopologyConfig) {
    fun view(): MqTopologyView = config
}
