package com.acme.mqops.service

import com.acme.mqops.audit.AuditLogger
import com.acme.mqops.config.MqTarget
import com.acme.mqops.mq.MessageGoneException
import com.acme.mqops.mq.MessageRow
import com.acme.mqops.mq.MqGateway
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.concurrent.CancellationException

class MqOperationServiceTest {
    private val target = MqTarget("QM1", "QM1", "host", 1414, "APP", "APP.SVRCONN", "DEV.QUEUE.1", null, null)

    @Test
    fun `browse passes configured limit to gateway`() {
        val gateway = FakeGateway()
        val service = MqOperationService(gateway, RecordingAuditLogger())

        service.browse("alice", target, 25)

        assertEquals(25, gateway.lastBrowseLimit)
    }

    @Test
    fun `put writes plain text body and audits success`() {
        val audit = RecordingAuditLogger()
        val gateway = FakeGateway()
        val service = MqOperationService(gateway, audit)

        service.putText("alice", target, "hello")

        assertEquals("hello", gateway.lastPutBody)
        assertEquals("put:success:alice:DEV.QUEUE.1", audit.entries.single())
    }

    @Test
    fun `put failure audit does not include submitted body from exception message`() {
        val audit = RecordingAuditLogger()
        val body = "sensitive message body"
        val gateway = FakeGateway(putFailure = IllegalStateException("failed to write $body"))
        val service = MqOperationService(gateway, audit)

        assertThrows(IllegalStateException::class.java) {
            service.putText("alice", target, body)
        }

        val entry = audit.entries.single()
        assertEquals("put:failure:alice:DEV.QUEUE.1:IllegalStateException", entry)
        assertFalse(entry.contains(body))
    }

    @Test
    fun `put non runtime failure is audited with sanitized type and rethrown`() {
        val audit = RecordingAuditLogger()
        val exception = Exception("checked failure includes sensitive body")
        val gateway = FakeGateway(putFailure = exception)
        val service = MqOperationService(gateway, audit)

        val thrown = assertThrows(Exception::class.java) {
            service.putText("alice", target, "sensitive body")
        }

        assertEquals(exception, thrown)
        assertEquals("put:failure:alice:DEV.QUEUE.1:Exception", audit.entries.single())
    }

    @Test
    fun `put interrupted failure restores interrupt status and is not audited`() {
        val audit = RecordingAuditLogger()
        val exception = InterruptedException("interrupted while writing")
        val gateway = FakeGateway(putFailure = exception)
        val service = MqOperationService(gateway, audit)

        try {
            val thrown = assertThrows(InterruptedException::class.java) {
                service.putText("alice", target, "body")
            }

            assertSame(exception, thrown)
            assertTrue(Thread.currentThread().isInterrupted)
            assertEquals(emptyList<String>(), audit.entries)
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun `put cancellation failure is not audited`() {
        val audit = RecordingAuditLogger()
        val exception = CancellationException("cancelled while writing")
        val gateway = FakeGateway(putFailure = exception)
        val service = MqOperationService(gateway, audit)

        val thrown = assertThrows(CancellationException::class.java) {
            service.putText("alice", target, "body")
        }

        assertSame(exception, thrown)
        assertEquals(emptyList<String>(), audit.entries)
    }

    @Test
    fun `delete missing message throws message gone and audits not found`() {
        val audit = RecordingAuditLogger()
        val gateway = FakeGateway(deleteMissing = true)
        val service = MqOperationService(gateway, audit)

        assertThrows(MessageGoneException::class.java) {
            service.delete("alice", target, "ID:123")
        }

        assertEquals("delete:not_found:alice:DEV.QUEUE.1:ID:123", audit.entries.single())
    }

    @Test
    fun `clean returns removed count and audits success`() {
        val audit = RecordingAuditLogger()
        val gateway = FakeGateway(cleanCount = 7)
        val service = MqOperationService(gateway, audit)

        val count = service.clean("alice", target)

        assertEquals(7, count)
        assertEquals("clean:success:alice:DEV.QUEUE.1:7", audit.entries.single())
    }

    private class FakeGateway(
        private val deleteMissing: Boolean = false,
        private val cleanCount: Int = 0,
        private val putFailure: Exception? = null
    ) : MqGateway {
        var lastBrowseLimit: Int = 0
        var lastPutBody: String = ""

        override fun browse(target: MqTarget, limit: Int): List<MessageRow> {
            lastBrowseLimit = limit
            return emptyList()
        }

        override fun delete(target: MqTarget, jmsMessageId: String): Boolean = !deleteMissing

        override fun putText(target: MqTarget, body: String) {
            putFailure?.let { throw it }
            lastPutBody = body
        }

        override fun clean(target: MqTarget): Int = cleanCount
    }

    private class RecordingAuditLogger : AuditLogger {
        val entries = mutableListOf<String>()
        override fun delete(user: String, target: MqTarget, messageId: String, result: String, error: String?) {
            entries.add("delete:$result:$user:${target.queueName}:$messageId")
        }

        override fun put(user: String, target: MqTarget, result: String, error: String?) {
            val errorPart = error?.let { ":$it" }.orEmpty()
            entries.add("put:$result:$user:${target.queueName}$errorPart")
        }

        override fun clean(user: String, target: MqTarget, removedCount: Int, result: String, error: String?) {
            entries.add("clean:$result:$user:${target.queueName}:$removedCount")
        }
    }
}
