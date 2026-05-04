package com.acme.mqops.service

import com.acme.mqops.audit.AuditLogger
import com.acme.mqops.config.MqTarget
import com.acme.mqops.mq.MessageGoneException
import com.acme.mqops.mq.MessageRow
import com.acme.mqops.mq.MqGateway
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class MqOperationService(
    private val gateway: MqGateway,
    private val audit: AuditLogger
) {
    fun browse(user: String, target: MqTarget, limit: Int): List<MessageRow> = gateway.browse(target, limit)

    fun delete(user: String, target: MqTarget, jmsMessageId: String) {
        try {
            val deleted = gateway.delete(target, jmsMessageId)
            if (!deleted) {
                audit.delete(user, target, jmsMessageId, "not_found")
                throw MessageGoneException("message no longer available")
            }
            audit.delete(user, target, jmsMessageId, "success")
        } catch (ex: MessageGoneException) {
            throw ex
        } catch (ex: RuntimeException) {
            audit.delete(user, target, jmsMessageId, "failure", errorSummary(ex))
            throw ex
        }
    }

    fun putText(user: String, target: MqTarget, body: String) {
        try {
            gateway.putText(target, body)
            audit.put(user, target, "success")
        } catch (ex: RuntimeException) {
            audit.put(user, target, "failure", errorSummary(ex))
            throw ex
        }
    }

    fun clean(user: String, target: MqTarget): Int {
        var removedCount = 0
        try {
            removedCount = gateway.clean(target)
            audit.clean(user, target, removedCount, "success")
            return removedCount
        } catch (ex: RuntimeException) {
            audit.clean(user, target, removedCount, "failure", errorSummary(ex))
            throw ex
        }
    }

    private fun errorSummary(ex: RuntimeException): String = ex.javaClass.simpleName.ifBlank { "RuntimeException" }
}
