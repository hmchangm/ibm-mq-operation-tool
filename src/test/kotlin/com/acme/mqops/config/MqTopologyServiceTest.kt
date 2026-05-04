package com.acme.mqops.config

import org.junit.jupiter.api.Assertions.assertEquals
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
                        username = "",
                        password = "",
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
                                username = "",
                                password = "",
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
    fun `resolve rejects queue outside configured channel`() {
        val service = MqTopologyService(config)

        assertThrows(InvalidMqTargetException::class.java) {
            service.resolve("QM1", "APP_SVRCONN", "SYSTEM.ADMIN.COMMAND.QUEUE")
        }
    }
}
