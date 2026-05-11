package com.acme.mqops.web

import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.security.TestSecurity
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.hamcrest.CoreMatchers.equalTo
import org.junit.jupiter.api.Test

@QuarkusTest
class MqApiResourceTest {
    @Test
    fun `api endpoints require authentication`() {
        given().`when`().get("/api/me").then().statusCode(401)
        given().`when`().get("/api/topology").then().statusCode(401)
    }

    @Test
    @TestSecurity(user = "alice")
    fun `me returns current user`() {
        given()
            .`when`().get("/api/me")
            .then()
            .statusCode(200)
            .body("user", equalTo("alice"))
    }

    @Test
    @TestSecurity(user = "alice")
    fun `topology returns configured queue managers and channels`() {
        given()
            .`when`().get("/api/topology")
            .then()
            .statusCode(200)
            .body("queueManagers.QM1.name", equalTo("QM1"))
            .body("queueManagers.QM1.channels.APP_SVRCONN.name", equalTo("APP.SVRCONN"))
    }

    @Test
    @TestSecurity(user = "alice")
    fun `browse returns 400 for unknown queue manager`() {
        given()
            .contentType(ContentType.JSON)
            .body("""{"queueManager":"UNKNOWN","channel":"APP_SVRCONN","queue":"DEV.QUEUE.1"}""")
            .`when`().post("/api/browse")
            .then()
            .statusCode(400)
    }

    @Test
    @TestSecurity(user = "alice")
    fun `bulk-delete returns 400 for unknown queue manager`() {
        given()
            .contentType(ContentType.JSON)
            .body("""{"queueManager":"UNKNOWN","channel":"APP_SVRCONN","queue":"DEV.QUEUE.1","jmsMessageIds":["ID:1"]}""")
            .`when`().post("/api/bulk-delete")
            .then()
            .statusCode(400)
    }

    @Test
    @TestSecurity(user = "alice")
    fun `export returns 400 for unknown queue manager`() {
        given()
            .contentType(ContentType.JSON)
            .body("""{"queueManager":"UNKNOWN","channel":"APP_SVRCONN","queue":"DEV.QUEUE.1"}""")
            .`when`().post("/api/export")
            .then()
            .statusCode(400)
    }

    @Test
    @TestSecurity(user = "alice")
    fun `browse returns 400 for unknown channel`() {
        given()
            .contentType(ContentType.JSON)
            .body("""{"queueManager":"QM1","channel":"UNKNOWN","queue":"DEV.QUEUE.1"}""")
            .`when`().post("/api/browse")
            .then()
            .statusCode(400)
    }
}
