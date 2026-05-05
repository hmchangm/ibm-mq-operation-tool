package com.acme.mqops.mq

import com.acme.mqops.config.MqTarget
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@QuarkusTest
@QuarkusTestResource(MqContainerResource::class)
class IbmMqGatewayTest {
    @Inject
    lateinit var gateway: IbmMqGateway

    @ConfigProperty(name = "mq.queue-managers.QM1.host")
    lateinit var mqHost: String

    @ConfigProperty(name = "mq.queue-managers.QM1.port")
    var mqPort: Int = 0

    private lateinit var target: MqTarget

    @BeforeEach
    fun setUp() {
        target = MqTarget(
            queueManagerKey = "QM1",
            queueManagerName = "QM1",
            host = mqHost,
            port = mqPort,
            channelKey = "DEV_SVRCONN",
            channelName = "DEV.APP.SVRCONN",
            queueName = "DEV.QUEUE.1",
            username = null,
            password = null
        )
        gateway.clean(target)
    }

    @Test
    fun `browse returns empty list when queue is empty`() {
        assertTrue(gateway.browse(target, 10).isEmpty())
    }

    @Test
    fun `putText then browse returns the message`() {
        gateway.putText(target, """{"test":"hello"}""")

        val rows = gateway.browse(target, 10)

        assertEquals(1, rows.size)
        assertTrue(rows[0].preview.contains("hello"))
    }

    @Test
    fun `delete removes a specific message`() {
        gateway.putText(target, """{"test":"delete-me"}""")
        val before = gateway.browse(target, 10)
        assertEquals(1, before.size)

        val deleted = gateway.delete(target, before[0].jmsMessageId)

        assertTrue(deleted)
        assertTrue(gateway.browse(target, 10).isEmpty())
    }

    @Test
    fun `clean returns removed count and empties the queue`() {
        gateway.putText(target, """{"n":1}""")
        gateway.putText(target, """{"n":2}""")

        val removed = gateway.clean(target)

        assertEquals(2, removed)
        assertTrue(gateway.browse(target, 10).isEmpty())
    }
}
