package com.acme.mqops.mq

import com.acme.mqops.config.MqTarget

const val TEXT_PREVIEW_LIMIT = 240
const val UNSUPPORTED_PREVIEW = "[payload preview unsupported]"

data class MessageRow(
    val jmsMessageId: String,
    val correlationId: String?,
    val timestamp: Long?,
    val expiration: Long?,
    val priority: Int?,
    val type: String,
    val preview: String
)

interface MqGateway {
    fun browse(target: MqTarget, limit: Int): List<MessageRow>
    fun delete(target: MqTarget, jmsMessageId: String): Boolean
    fun putText(target: MqTarget, body: String)
    fun clean(target: MqTarget): Int
}

class MessageGoneException(message: String) : RuntimeException(message)
