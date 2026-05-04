package com.acme.mqops.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.Optional

class MqTopologyServiceTest {
    private val config = TestMqTopologyConfig(
        browseLimit = 100,
        receiveTimeoutMs = 500,
        queueManagers = mapOf(
            "QM1" to TestQueueManagerConfig(
                host = "mq.example.test",
                port = 1414,
                channels = mapOf(
                    "APP_SVRCONN" to TestChannelConfig(
                        name = "APP.SVRCONN",
                        username = Optional.empty(),
                        password = Optional.empty(),
                        allowedQueues = listOf("DEV.QUEUE.1", "DEV.QUEUE.2")
                    )
                )
            )
        )
    )

    @Test
    fun `resolve accepts configured queue manager channel and queue`() {
        val service = MqTopologyService(config)

        val target = service.resolve("QM1", "APP_SVRCONN", "DEV.QUEUE.2")

        assertEquals("QM1", target.queueManagerName)
        assertEquals("APP.SVRCONN", target.channelName)
        assertEquals("DEV.QUEUE.2", target.queueName)
        assertEquals("mq.example.test", target.host)
        assertEquals(1414, target.port)
    }

    @Test
    fun `resolve uses configured queue manager name when it differs from key`() {
        val service = MqTopologyService(
            TestMqTopologyConfig(
                browseLimit = 100,
                receiveTimeoutMs = 500,
                queueManagers = mapOf(
                    "PRIMARY" to TestQueueManagerConfig(
                        host = "mq.example.test",
                        port = 1414,
                        channels = mapOf(
                            "APP_SVRCONN" to TestChannelConfig(
                                name = "APP.SVRCONN",
                                username = Optional.empty(),
                                password = Optional.empty(),
                                allowedQueues = listOf("DEV.QUEUE.1")
                            )
                        ),
                        name = Optional.of("QM1")
                    )
                )
            )
        )

        val target = service.resolve("PRIMARY", "APP_SVRCONN", "DEV.QUEUE.1")

        assertEquals("PRIMARY", target.queueManagerKey)
        assertEquals("QM1", target.queueManagerName)
    }

    @Test
    fun `queue managers returns topology without credentials`() {
        val service = MqTopologyService(
            TestMqTopologyConfig(
                browseLimit = 100,
                receiveTimeoutMs = 500,
                queueManagers = mapOf(
                    "QM1" to TestQueueManagerConfig(
                        host = "mq.example.test",
                        port = 1414,
                        channels = mapOf(
                            "APP_SVRCONN" to TestChannelConfig(
                                name = "APP.SVRCONN",
                                username = Optional.of("app"),
                                password = Optional.of("secret"),
                                allowedQueues = listOf("DEV.QUEUE.1")
                            )
                        )
                    )
                )
            )
        )

        val queueManager = service.queueManagers().getValue("QM1")
        val channel = queueManager.channels.getValue("APP_SVRCONN")

        assertEquals("QM1", queueManager.name)
        assertEquals("APP.SVRCONN", channel.name)
        assertEquals(listOf("DEV.QUEUE.1"), channel.allowedQueues)
    }

    @Test
    fun `resolve rejects unknown queue manager`() {
        val service = MqTopologyService(config)

        assertThrows(InvalidMqTargetException::class.java) {
            service.resolve("UNKNOWN", "APP_SVRCONN", "DEV.QUEUE.1")
        }
    }

    @Test
    fun `resolve rejects unknown channel`() {
        val service = MqTopologyService(config)

        assertThrows(InvalidMqTargetException::class.java) {
            service.resolve("QM1", "UNKNOWN", "DEV.QUEUE.1")
        }
    }

    @Test
    fun `resolve rejects queue outside configured channel`() {
        val service = MqTopologyService(config)

        assertThrows(InvalidMqTargetException::class.java) {
            service.resolve("QM1", "APP_SVRCONN", "SYSTEM.ADMIN.COMMAND.QUEUE")
        }
    }

    @Test
    fun `resolve normalizes blank credentials to null`() {
        val service = MqTopologyService(
            TestMqTopologyConfig(
                browseLimit = 100,
                receiveTimeoutMs = 500,
                queueManagers = mapOf(
                    "QM1" to TestQueueManagerConfig(
                        host = "mq.example.test",
                        port = 1414,
                        channels = mapOf(
                            "APP_SVRCONN" to TestChannelConfig(
                                name = "APP.SVRCONN",
                                username = Optional.of(""),
                                password = Optional.of(" "),
                                allowedQueues = listOf("DEV.QUEUE.1")
                            )
                        )
                    )
                )
            )
        )

        val target = service.resolve("QM1", "APP_SVRCONN", "DEV.QUEUE.1")

        assertNull(target.username)
        assertNull(target.password)
    }
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
    private val channels: Map<String, TestChannelConfig>,
    private val name: Optional<String> = Optional.empty()
) : QueueManagerView {
    override fun name() = name
    override fun host() = host
    override fun port() = port
    override fun channels(): Map<String, ChannelView> = channels
}

data class TestChannelConfig(
    private val name: String,
    private val username: Optional<String>,
    private val password: Optional<String>,
    private val allowedQueues: List<String>
) : ChannelView {
    override fun name() = name
    override fun username() = username
    override fun password() = password
    override fun allowedQueues() = allowedQueues
}
