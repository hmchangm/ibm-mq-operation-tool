package com.acme.mqops.service

import com.acme.mqops.audit.AuditLogger
import com.acme.mqops.config.MqTarget
import com.acme.mqops.mq.MessageGoneException
import com.acme.mqops.mq.MessageRow
import com.acme.mqops.mq.MqGateway
import jakarta.enterprise.context.ApplicationScoped
import java.util.concurrent.CancellationException

@ApplicationScoped
class MqOperationService(
    private val gateway: MqGateway,
    private val audit: AuditLogger
) {
    fun browse(user: String, target: MqTarget, limit: Int): List<MessageRow> = gateway.browse(target, limit)

    fun export(user: String, target: MqTarget): List<MessageRow> = gateway.browse(target, Int.MAX_VALUE)

    fun delete(user: String, target: MqTarget, jmsMessageId: String) {
        withMqCall({ error -> audit.delete(user, target, jmsMessageId, "failure", error) }) {
            val deleted = gateway.delete(target, jmsMessageId)
            if (!deleted) {
                audit.delete(user, target, jmsMessageId, "not_found")
                throw MessageGoneException("message no longer available")
            }
            audit.delete(user, target, jmsMessageId, "success")
        }
    }

    fun putText(user: String, target: MqTarget, body: String) {
        withMqCall({ error -> audit.put(user, target, "failure", error) }) {
            gateway.putText(target, body)
            audit.put(user, target, "success")
        }
    }

    fun clean(user: String, target: MqTarget): Int {
        var removedCount = 0
        withMqCall({ error -> audit.clean(user, target, removedCount, "failure", error) }) {
            removedCount = gateway.clean(target)
            audit.clean(user, target, removedCount, "success")
        }
        return removedCount
    }

    private inline fun withMqCall(onFailure: (String) -> Unit, block: () -> Unit) {
        try {
            block()
        } catch (ex: InterruptedException) {
            Thread.currentThread().interrupt()
            throw ex
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: MessageGoneException) {
            throw ex
        } catch (ex: Exception) {
            onFailure(errorSummary(ex))
            throw ex
        }
    }

    private fun errorSummary(ex: Exception): String = ex.javaClass.simpleName.ifBlank { "Exception" }
}
