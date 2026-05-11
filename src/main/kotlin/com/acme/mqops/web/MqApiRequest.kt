package com.acme.mqops.web

data class BrowseRequest(val queueManager: String, val channel: String, val queue: String)
data class DeleteRequest(val queueManager: String, val channel: String, val queue: String, val jmsMessageId: String)
data class PutRequest(val queueManager: String, val channel: String, val queue: String, val body: String)
data class CleanRequest(val queueManager: String, val channel: String, val queue: String)
data class ExportRequest(val queueManager: String, val channel: String, val queue: String)
data class BulkDeleteRequest(val queueManager: String, val channel: String, val queue: String, val jmsMessageIds: List<String>)
