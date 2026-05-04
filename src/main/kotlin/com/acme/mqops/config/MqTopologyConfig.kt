package com.acme.mqops.config

import io.quarkus.runtime.annotations.StaticInitSafe
import io.smallrye.config.ConfigMapping
import jakarta.enterprise.context.ApplicationScoped

interface MqTopologyView {
    fun browseLimit(): Int
    fun receiveTimeoutMs(): Long
    fun queueManagers(): Map<String, QueueManagerView>
}

interface QueueManagerView {
    fun host(): String
    fun port(): Int
    fun channels(): Map<String, ChannelView>
}

interface ChannelView {
    fun name(): String
    fun username(): String?
    fun password(): String?
    fun allowedQueues(): List<String>
}

@StaticInitSafe
@ConfigMapping(prefix = "mq")
interface MqTopologyConfig : MqTopologyView

@ApplicationScoped
class MqTopologyConfigHolder(val config: MqTopologyConfig) {
    fun view(): MqTopologyView = config
}

data class TestMqTopologyConfig(
    private val browseLimit: Int,
    private val receiveTimeoutMs: Long,
    private val queueManagers: Map<String, TestQueueManagerConfig>
) : MqTopologyView {
    override fun browseLimit() = browseLimit
    override fun receiveTimeoutMs() = receiveTimeoutMs
    override fun queueManagers(): Map<String, QueueManagerView> = queueManagers
}

data class TestQueueManagerConfig(
    private val host: String,
    private val port: Int,
    private val channels: Map<String, TestChannelConfig>
) : QueueManagerView {
    override fun host() = host
    override fun port() = port
    override fun channels(): Map<String, ChannelView> = channels
}

data class TestChannelConfig(
    private val name: String,
    private val username: String?,
    private val password: String?,
    private val allowedQueues: List<String>
) : ChannelView {
    override fun name() = name
    override fun username() = username
    override fun password() = password
    override fun allowedQueues() = allowedQueues
}
