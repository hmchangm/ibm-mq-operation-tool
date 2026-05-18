package com.acme.mqops.mq

import com.acme.mqops.config.MqTarget
import com.ibm.msg.client.jms.JmsConstants
import com.ibm.msg.client.jms.JmsConnectionFactory
import com.ibm.msg.client.jms.JmsFactoryFactory
import com.ibm.msg.client.wmq.WMQConstants
import jakarta.enterprise.context.ApplicationScoped
import javax.jms.Connection
import javax.jms.DeliveryMode
import javax.jms.Message
import javax.jms.Session
import javax.jms.TextMessage

private const val DEFAULT_RECEIVE_TIMEOUT_MS = 500L

@ApplicationScoped
class IbmMqGateway : MqGateway {
    override fun browse(target: MqTarget, limit: Int): List<MessageRow> {
        if (limit <= 0) return emptyList()

        return withSession(target) { session ->
            val queue = session.createQueue(target.queueName)
            val browser = session.createBrowser(queue)
            try {
                val rows = mutableListOf<MessageRow>()
                val messages = browser.enumeration
                while (messages.hasMoreElements() && rows.size < limit) {
                    rows += (messages.nextElement() as Message).toRow()
                }
                rows
            } finally {
                browser.close()
            }
        }
    }

    override fun delete(target: MqTarget, jmsMessageId: String): Boolean =
        withSession(target) { session ->
            val queue = session.createQueue(target.queueName)
            val selector = "JMSMessageID = '${jmsMessageId.selectorLiteral()}'"
            val consumer = session.createConsumer(queue, selector)
            try {
                consumer.receive(DEFAULT_RECEIVE_TIMEOUT_MS) != null
            } finally {
                consumer.close()
            }
        }

    override fun putText(target: MqTarget, body: String) {
        withSession(target) { session ->
            val queue = session.createQueue(target.queueName)
            val message = session.createTextMessage(body)
            val producer = session.createProducer(queue)
            try {
                producer.deliveryMode = DeliveryMode.PERSISTENT
                producer.send(message)
            } finally {
                producer.close()
            }
        }
    }

    override fun clean(target: MqTarget): Int =
        withSession(target) { session ->
            val queue = session.createQueue(target.queueName)
            val consumer = session.createConsumer(queue)
            try {
                var removed = 0
                while (consumer.receive(DEFAULT_RECEIVE_TIMEOUT_MS) != null) removed++
                removed
            } finally {
                consumer.close()
            }
        }

    private fun <T> withSession(target: MqTarget, block: (Session) -> T): T {
        val connection = createConnection(target)
        try {
            connection.start()
            val session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
            try {
                return block(session)
            } finally {
                session.close()
            }
        } finally {
            connection.close()
        }
    }

    private fun createConnection(target: MqTarget): Connection {
        val factory = JmsFactoryFactory
            .getInstance(JmsConstants.WMQ_PROVIDER)
            .createConnectionFactory()
            .applyTarget(target)

        return if (target.username != null && target.password != null) {
            factory.createConnection(target.username, target.password)
        } else {
            factory.createConnection()
        }
    }

    private fun JmsConnectionFactory.applyTarget(target: MqTarget): JmsConnectionFactory {
        setStringProperty(WMQConstants.WMQ_HOST_NAME, target.host)
        setIntProperty(WMQConstants.WMQ_PORT, target.port)
        setStringProperty(WMQConstants.WMQ_CHANNEL, target.channelName)
        setIntProperty(WMQConstants.WMQ_CONNECTION_MODE, WMQConstants.WMQ_CM_CLIENT)
        setStringProperty(WMQConstants.WMQ_QUEUE_MANAGER, target.queueManagerName)
        return this
    }

    private fun Message.toRow(): MessageRow =
        MessageRow(
            jmsMessageId = getJMSMessageID(),
            correlationId = getJMSCorrelationID(),
            timestamp = getJMSTimestamp().takeUnless { it == 0L },
            expiration = getJMSExpiration().takeUnless { it == 0L },
            priority = getJMSPriority(),
            type = getJMSType() ?: javaClass.simpleName,
            preview = preview()
        )

    private fun Message.preview(): String =
        if (this is TextMessage) getText()?.take(TEXT_PREVIEW_LIMIT).orEmpty()
        else UNSUPPORTED_PREVIEW

    private fun String.selectorLiteral(): String = replace("'", "''")
}
