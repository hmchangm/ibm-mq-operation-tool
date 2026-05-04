package com.acme.mqops.audit

import com.acme.mqops.config.MqTarget
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class AuditLineFormatterTest {
    private val target = MqTarget(
        queueManagerKey = "QM1",
        queueManagerName = "QM 1\nforged=true",
        host = "host",
        port = 1414,
        channelKey = "APP",
        channelName = "APP.SVRCONN\" forged=true",
        queueName = "DEV.QUEUE.1 key=value",
        username = "app",
        password = "mq-password-secret"
    )

    @Test
    fun `formatter emits escaped json for field content that could forge key value logs`() {
        val line = AuditLineFormatter.format(
            timestamp = Instant.parse("2026-05-04T00:00:00Z"),
            user = "alice admin=true\nsecond-line",
            operation = "delete",
            target = target,
            messageId = "ID:123 key=value\nnext",
            removedCount = 0,
            result = "failure",
            error = "IllegalStateException"
        )

        assertTrue(line.startsWith("{"))
        assertTrue(line.endsWith("}"))
        assertTrue(line.contains("\"user\":\"alice admin=true\\nsecond-line\""))
        assertTrue(line.contains("\"queueManager\":\"QM 1\\nforged=true\""))
        assertTrue(line.contains("\"channel\":\"APP.SVRCONN\\\" forged=true\""))
        assertTrue(line.contains("\"queue\":\"DEV.QUEUE.1 key=value\""))
        assertTrue(line.contains("\"messageId\":\"ID:123 key=value\\nnext\""))
        assertTrue(line.contains("\"errorType\":\"IllegalStateException\""))
        assertFalse(line.contains("alice admin=true\nsecond-line"))
        assertFalse(line.contains("ID:123 key=value\nnext"))
    }

    @Test
    fun `formatter does not include message body or password when only sanitized error type is supplied`() {
        val line = AuditLineFormatter.format(
            timestamp = Instant.parse("2026-05-04T00:00:00Z"),
            user = "alice",
            operation = "put",
            target = target,
            messageId = null,
            removedCount = 0,
            result = "failure",
            error = "RuntimeException"
        )

        assertTrue(line.contains("\"errorType\":\"RuntimeException\""))
        assertFalse(line.contains("sensitive message body"))
        assertFalse(line.contains("mq-password-secret"))
    }

    @Test
    fun `formatter defensively reduces arbitrary raw error text`() {
        val line = AuditLineFormatter.format(
            timestamp = Instant.parse("2026-05-04T00:00:00Z"),
            user = "alice",
            operation = "put",
            target = target,
            messageId = null,
            removedCount = 0,
            result = "failure",
            error = "failed with password=mq-password-secret and sensitive message body"
        )

        assertTrue(line.contains("\"errorType\":\"RuntimeException\""))
        assertFalse(line.contains("password=mq-password-secret"))
        assertFalse(line.contains("sensitive message body"))
    }
}
