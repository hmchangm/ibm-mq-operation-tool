package com.acme.mqops.mq

import com.acme.mqops.config.MqTarget
import com.ibm.msg.client.jakarta.jms.JmsConnectionFactory
import com.ibm.msg.client.jakarta.jms.JmsFactoryFactory
import com.ibm.msg.client.jakarta.wmq.WMQConstants
import jakarta.enterprise.context.ApplicationScoped
import jakarta.jms.DeliveryMode
import jakarta.jms.JMSContext
import jakarta.jms.Message
import jakarta.jms.Queue
import jakarta.jms.TextMessage

@ApplicationScoped
class IbmMqGateway : MqGateway {
    override fun browse(target: MqTarget, limit: Int): List<MessageRow> {
        if (limit <= 0) {
            return emptyList()
        }

        return createContext(target).use { context ->
            val queue = context.queue(target)
            context.createBrowser(queue).use { browser ->
                val rows = mutableListOf<MessageRow>()
                val messages = browser.enumeration

                while (messages.hasMoreElements() && rows.size < limit) {
                    rows += (messages.nextElement() as Message).toRow()
                }

                rows
            }
        }
    }

    override fun delete(target: MqTarget, jmsMessageId: String): Boolean =
        createContext(target).use { context ->
            val queue = context.queue(target)
            val selector = "JMSMessageID = '${jmsMessageId.selectorLiteral()}'"

            context.createConsumer(queue, selector).use { consumer ->
                consumer.receiveNoWait() != null
            }
        }

    override fun putText(target: MqTarget, body: String) {
        createContext(target).use { context ->
            val queue = context.queue(target)
            val message = context.createTextMessage(body)

            context.createProducer()
                .setDeliveryMode(DeliveryMode.PERSISTENT)
                .send(queue, message)
        }
    }

    override fun clean(target: MqTarget): Int =
        createContext(target).use { context ->
            val queue = context.queue(target)
            context.createConsumer(queue).use { consumer ->
                var removed = 0

                while (consumer.receiveNoWait() != null) {
                    removed += 1
                }

                removed
            }
        }

    private fun createContext(target: MqTarget): JMSContext {
        val factory = JmsFactoryFactory
            .getInstance(WMQConstants.WMQ_PROVIDER)
            .createConnectionFactory()
            .applyTarget(target)

        return if (target.username != null && target.password != null) {
            factory.createContext(target.username, target.password)
        } else {
            factory.createContext()
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

    private fun JMSContext.queue(target: MqTarget): Queue = createQueue(target.queueName)

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
        if (this is TextMessage) {
            getText()?.take(TEXT_PREVIEW_LIMIT).orEmpty()
        } else {
            UNSUPPORTED_PREVIEW
        }

    private fun String.selectorLiteral(): String = replace("'", "''")
}
