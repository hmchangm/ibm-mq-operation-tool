package com.acme.mqops.audit

import com.acme.mqops.config.MqTarget
import jakarta.enterprise.context.ApplicationScoped
import org.jboss.logging.Logger
import java.time.Instant

interface AuditLogger {
    fun delete(user: String, target: MqTarget, messageId: String, result: String, error: String? = null)
    fun put(user: String, target: MqTarget, result: String, error: String? = null)
    fun clean(user: String, target: MqTarget, removedCount: Int, result: String, error: String? = null)
}

@ApplicationScoped
class StructuredAuditLogger : AuditLogger {
    private val log = Logger.getLogger("mqops.audit")

    override fun delete(user: String, target: MqTarget, messageId: String, result: String, error: String?) {
        log.info(auditLine(user, "delete", target, result, "messageId=$messageId", 0, error))
    }

    override fun put(user: String, target: MqTarget, result: String, error: String?) {
        log.info(auditLine(user, "put", target, result, "messageId=", 0, error))
    }

    override fun clean(user: String, target: MqTarget, removedCount: Int, result: String, error: String?) {
        log.info(auditLine(user, "clean", target, result, "messageId=", removedCount, error))
    }

    private fun auditLine(
        user: String,
        operation: String,
        target: MqTarget,
        result: String,
        messagePart: String,
        removedCount: Int,
        error: String?
    ): String {
        val errorPart = error?.replace('\n', ' ') ?: ""
        return "event=mq_operation timestamp=${Instant.now()} user=$user operation=$operation " +
            "queueManager=${target.queueManagerName} channel=${target.channelName} queue=${target.queueName} " +
            "$messagePart removedCount=$removedCount result=$result error=\"$errorPart\""
    }
}
