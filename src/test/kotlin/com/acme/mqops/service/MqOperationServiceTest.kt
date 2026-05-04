package com.acme.mqops.service

import com.acme.mqops.audit.AuditLogger
import com.acme.mqops.config.MqTarget
import com.acme.mqops.mq.MessageGoneException
import com.acme.mqops.mq.MessageRow
import com.acme.mqops.mq.MqGateway
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

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
        private val cleanCount: Int = 0
    ) : MqGateway {
        var lastBrowseLimit: Int = 0
        var lastPutBody: String = ""

        override fun browse(target: MqTarget, limit: Int): List<MessageRow> {
            lastBrowseLimit = limit
            return emptyList()
        }

        override fun delete(target: MqTarget, jmsMessageId: String): Boolean = !deleteMissing

        override fun putText(target: MqTarget, body: String) {
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
            entries.add("put:$result:$user:${target.queueName}")
        }

        override fun clean(user: String, target: MqTarget, removedCount: Int, result: String, error: String?) {
            entries.add("clean:$result:$user:${target.queueName}:$removedCount")
        }
    }
}
