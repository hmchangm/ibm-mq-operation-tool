package com.acme.mqops.web

import com.acme.mqops.config.InvalidMqTargetException
import com.acme.mqops.config.MqTarget
import com.acme.mqops.config.MqTopologyService
import com.acme.mqops.mq.MessageGoneException
import com.acme.mqops.mq.MessageRow
import com.acme.mqops.service.MqOperationService
import io.quarkus.security.Authenticated
import io.quarkus.security.identity.SecurityIdentity
import jakarta.ws.rs.BadRequestException
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/api")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class MqApiResource(
    private val topology: MqTopologyService,
    private val operations: MqOperationService,
    private val identity: SecurityIdentity
) {
    @GET
    @Path("/me")
    fun me(): UserResponse = UserResponse(user())

    @GET
    @Path("/topology")
    fun topology(): TopologyResponse =
        TopologyResponse(
            queueManagers = topology.queueManagers().mapValues { (_, queueManager) ->
                QueueManagerResponse(
                    name = queueManager.name,
                    channels = queueManager.channels.mapValues { (_, channel) ->
                        ChannelResponse(name = channel.name)
                    }
                )
            }
        )

    @POST
    @Path("/browse")
    fun browse(request: BrowseRequest): List<MessageRow> =
        withTarget(request.queueManager, request.channel, request.queue) { target ->
            operations.browse(user(), target, topology.browseLimit())
        }

    @POST
    @Path("/delete")
    fun delete(request: DeleteRequest): Response =
        withTarget(request.queueManager, request.channel, request.queue) { target ->
            try {
                operations.delete(user(), target, request.jmsMessageId)
                Response.noContent().build()
            } catch (_: MessageGoneException) {
                Response.status(Response.Status.GONE).build()
            }
        }

    @POST
    @Path("/put")
    fun put(request: PutRequest): Response =
        withTarget(request.queueManager, request.channel, request.queue) { target ->
            operations.putText(user(), target, request.body)
            Response.noContent().build()
        }

    @POST
    @Path("/clean")
    fun clean(request: CleanRequest): CleanResponse =
        withTarget(request.queueManager, request.channel, request.queue) { target ->
            CleanResponse(removed = operations.clean(user(), target))
        }

    private fun <T> withTarget(queueManager: String, channel: String, queue: String, block: (MqTarget) -> T): T =
        try {
            block(topology.resolve(queueManager, channel, queue))
        } catch (ex: InvalidMqTargetException) {
            throw BadRequestException(ex.message)
        }

    private fun user(): String = identity.principal.name
}

data class UserResponse(val user: String)
data class TopologyResponse(val queueManagers: Map<String, QueueManagerResponse>)
data class QueueManagerResponse(val name: String, val channels: Map<String, ChannelResponse>)
data class ChannelResponse(val name: String)
data class CleanResponse(val removed: Int)
