package com.acme.mqops.web

import com.acme.mqops.config.InvalidMqTargetException
import com.acme.mqops.config.MqTopologyService
import com.acme.mqops.mq.MessageGoneException
import com.acme.mqops.service.MqOperationService
import io.quarkus.qute.Location
import io.quarkus.qute.Template
import io.quarkus.qute.TemplateInstance
import io.quarkus.security.Authenticated
import io.quarkus.security.identity.SecurityIdentity
import jakarta.ws.rs.BeanParam
import jakarta.ws.rs.FormParam
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType

@Path("/")
@Authenticated
class QueueOpsResource(
    private val topology: MqTopologyService,
    private val operations: MqOperationService,
    private val identity: SecurityIdentity,
    @Location("QueueOpsResource/index") private val index: Template,
    @Location("QueueOpsResource/browse") private val browse: Template,
    @Location("QueueOpsResource/notice") private val notice: Template
) {
    @GET
    @Produces(MediaType.TEXT_HTML)
    fun index(): TemplateInstance =
        index.data("queueManagers", topology.queueManagers())

    @POST
    @Path("/browse")
    @Produces(MediaType.TEXT_HTML)
    fun browse(@BeanParam form: TargetForm): TemplateInstance =
        withTarget(form) { target ->
            browse.data("messages", operations.browse(user(), target, topology.browseLimit()))
                .data("target", target)
        }

    @POST
    @Path("/delete")
    @Produces(MediaType.TEXT_HTML)
    fun delete(@BeanParam form: DeleteForm): TemplateInstance =
        withTarget(form) { target ->
            try {
                operations.delete(user(), target, form.jmsMessageId)
                notice.data("kind", "success").data("message", "Message deleted.")
            } catch (_: MessageGoneException) {
                notice.data("kind", "warning").data("message", "Message no longer available. Refresh the browse table.")
            }
        }

    @POST
    @Path("/put")
    @Produces(MediaType.TEXT_HTML)
    fun put(@BeanParam form: PutForm): TemplateInstance =
        withTarget(form) { target ->
            operations.putText(user(), target, form.body)
            notice.data("kind", "success").data("message", "Message put successfully.")
        }

    @POST
    @Path("/clean")
    @Produces(MediaType.TEXT_HTML)
    fun clean(@BeanParam form: CleanForm): TemplateInstance =
        withTarget(form) { target ->
            if (form.confirmQueueName != target.queueName) {
                notice.data("kind", "error").data("message", "Typed queue name does not match.")
            } else {
                val removed = operations.clean(user(), target)
                notice.data("kind", "success").data("message", "Clean removed $removed messages.")
            }
        }

    private fun <T> withTarget(form: TargetForm, block: (com.acme.mqops.config.MqTarget) -> T): T =
        try {
            block(topology.resolve(form.queueManager, form.channel, form.queue))
        } catch (ex: InvalidMqTargetException) {
            throw jakarta.ws.rs.BadRequestException(ex.message)
        }

    private fun user(): String = identity.principal.name
}

open class TargetForm {
    @FormParam("queueManager")
    lateinit var queueManager: String

    @FormParam("channel")
    lateinit var channel: String

    @FormParam("queue")
    lateinit var queue: String
}

class DeleteForm : TargetForm() {
    @FormParam("jmsMessageId")
    lateinit var jmsMessageId: String
}

class PutForm : TargetForm() {
    @FormParam("body")
    lateinit var body: String
}

class CleanForm : TargetForm() {
    @FormParam("confirmQueueName")
    lateinit var confirmQueueName: String
}
