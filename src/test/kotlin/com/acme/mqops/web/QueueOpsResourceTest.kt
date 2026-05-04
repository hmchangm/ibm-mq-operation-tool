package com.acme.mqops.web

import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.security.TestSecurity
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.containsString
import org.junit.jupiter.api.Test

@QuarkusTest
class QueueOpsResourceTest {
    @Test
    fun `workspace requires authentication`() {
        given()
            .`when`().get("/")
            .then()
            .statusCode(401)
    }

    @Test
    @TestSecurity(user = "alice")
    fun `workspace renders configured queue manager`() {
        given()
            .`when`().get("/")
            .then()
            .statusCode(200)
            .body(containsString("IBM MQ Operation Tool"))
            .body(containsString("QM1"))
    }

    @Test
    @TestSecurity(user = "alice")
    fun `workspace page load does not perform MQ operation`() {
        given()
            .`when`().get("/")
            .then()
            .statusCode(200)
            .body(containsString("IBM MQ Operation Tool"))
    }
}
