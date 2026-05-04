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
        log.info(AuditLineFormatter.format(Instant.now(), user, "delete", target, messageId, 0, result, error))
    }

    override fun put(user: String, target: MqTarget, result: String, error: String?) {
        log.info(AuditLineFormatter.format(Instant.now(), user, "put", target, null, 0, result, error))
    }

    override fun clean(user: String, target: MqTarget, removedCount: Int, result: String, error: String?) {
        log.info(AuditLineFormatter.format(Instant.now(), user, "clean", target, null, removedCount, result, error))
    }
}

object AuditLineFormatter {
    fun format(
        timestamp: Instant,
        user: String,
        operation: String,
        target: MqTarget,
        messageId: String?,
        removedCount: Int,
        result: String,
        error: String?
    ): String {
        val fields = mutableListOf(
            "event" to "mq_operation",
            "timestamp" to timestamp.toString(),
            "user" to user,
            "operation" to operation,
            "queueManager" to target.queueManagerName,
            "channel" to target.channelName,
            "queue" to target.queueName,
            "removedCount" to removedCount,
            "result" to result,
            "errorType" to sanitizeErrorSummary(error)
        )
        if (messageId != null) {
            fields.add("messageId" to messageId)
        }
        return fields.joinToString(prefix = "{", postfix = "}") { (key, value) ->
            "\"${escapeJson(key)}\":${jsonValue(value)}"
        }
    }

    private fun sanitizeErrorSummary(error: String?): String {
        if (error.isNullOrBlank()) {
            return ""
        }
        return if (errorSummaryPattern.matches(error)) error else "RuntimeException"
    }

    private fun jsonValue(value: Any): String = when (value) {
        is Number -> value.toString()
        else -> "\"${escapeJson(value.toString())}\""
    }

    private fun escapeJson(value: String): String = buildString {
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> {
                    if (char < ' ') {
                        append("\\u")
                        append(char.code.toString(16).padStart(4, '0'))
                    } else {
                        append(char)
                    }
                }
            }
        }
    }

    private val errorSummaryPattern = Regex("[A-Za-z0-9_.]*(Exception|Error)")
}
