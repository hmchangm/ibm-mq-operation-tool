# SPA Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace server-rendered Qute pages with a React + TypeScript SPA served from Quarkus, backed by a JSON REST API at `/api/*`.

**Architecture:** Quarkus exposes a `@Path("/api") @Authenticated` REST resource returning JSON. A separate `frontend/` Vite project builds into `src/main/resources/META-INF/resources/` so Quarkus serves the SPA as static files. In dev, Vite proxies `/api/*` to `http://localhost:8080`; the existing `DevHttpAuthMechanism` provides a dev user without OIDC.

**Tech Stack:** Quarkus 3.34.5 / Kotlin / `quarkus-rest-jackson` / React 18 / TypeScript / Vite / `@tanstack/react-query` v5 / IBM MQ `com.ibm.mq.allclient:9.4.2.1` / Testcontainers

---

## File Map

### Modified
| File | Change |
|------|--------|
| `src/main/kotlin/com/acme/mqops/mq/IbmMqGateway.kt` | Replace `WMQConstants.WMQ_PROVIDER` with `"wmq_provider"` |
| `src/main/kotlin/com/acme/mqops/config/MqTopologyConfig.kt` | Remove `allowedQueues()` from `ChannelView`; remove `allowedQueues` from `MqChannelTopology` |
| `src/main/kotlin/com/acme/mqops/config/MqTopologyService.kt` | Remove allowlist check; remove `allowedQueues` from topology construction |
| `src/test/kotlin/com/acme/mqops/config/MqTopologyServiceTest.kt` | Remove allowlist test case; remove `allowedQueues` from test stubs |
| `src/main/resources/application.properties` | Remove all `allowed-queues` lines; add dev CORS |
| `pom.xml` | Swap `quarkus-rest-qute` → `quarkus-rest-jackson`; add `testcontainers` |

### Deleted
| File | Reason |
|------|--------|
| `src/main/kotlin/com/acme/mqops/web/QueueOpsResource.kt` | Replaced by `MqApiResource` |
| `src/main/resources/templates/QueueOpsResource/` (3 files) | Qute templates no longer used |
| `src/main/resources/META-INF/resources/styles.css` | SPA manages its own styling |
| `src/test/kotlin/com/acme/mqops/web/QueueOpsResourceTest.kt` | Replaced by `MqApiResourceTest` |

### Created
| File | Purpose |
|------|---------|
| `src/main/kotlin/com/acme/mqops/web/MqApiRequest.kt` | Request body data classes |
| `src/main/kotlin/com/acme/mqops/web/MqApiResource.kt` | JSON REST resource |
| `src/test/kotlin/com/acme/mqops/web/MqApiResourceTest.kt` | API endpoint tests |
| `src/test/kotlin/com/acme/mqops/mq/MqContainerResource.kt` | Testcontainers lifecycle manager |
| `src/test/kotlin/com/acme/mqops/mq/IbmMqGatewayTest.kt` | Gateway integration tests |
| `frontend/package.json` | Frontend project manifest |
| `frontend/tsconfig.json` + `frontend/tsconfig.node.json` | TypeScript config |
| `frontend/vite.config.ts` | Build + dev proxy config |
| `frontend/index.html` | SPA entry point |
| `frontend/src/main.tsx` | React root mount |
| `frontend/src/types.ts` | Shared TypeScript types |
| `frontend/src/api/useMe.ts` | React Query hook |
| `frontend/src/api/useTopology.ts` | React Query hook |
| `frontend/src/api/useBrowse.ts` | React Query mutation |
| `frontend/src/api/useDelete.ts` | React Query mutation |
| `frontend/src/api/usePut.ts` | React Query mutation |
| `frontend/src/api/useClean.ts` | React Query mutation |
| `frontend/src/components/Toolbar.tsx` | Queue selector + action buttons |
| `frontend/src/components/MessageTable.tsx` | Browse results table |
| `frontend/src/components/PutModal.tsx` | Put message dialog |
| `frontend/src/components/CleanModal.tsx` | Clean queue dialog |
| `frontend/src/components/StatusBar.tsx` | Message count + error toasts |
| `frontend/src/App.tsx` | Layout shell + state |

---

## Task 1: Fix IbmMqGateway — use string literal for MQ provider

The `WMQConstants.WMQ_PROVIDER` constant resolves to `"wmq_provider"` but using the constant requires the newer `jakarta.jms`-namespaced WMQ client. Using the string literal directly is compatible with older MQ servers.

**Files:**
- Modify: `src/main/kotlin/com/acme/mqops/mq/IbmMqGateway.kt`

- [ ] **Step 1: Change the provider string in `createContext`**

In `IbmMqGateway.kt` line 74–76, change:
```kotlin
private fun createContext(target: MqTarget): JMSContext {
    val factory = JmsFactoryFactory
        .getInstance(WMQConstants.WMQ_PROVIDER)
```
to:
```kotlin
private fun createContext(target: MqTarget): JMSContext {
    val factory = JmsFactoryFactory
        .getInstance("wmq_provider")
```
Leave the `WMQConstants` import — it is still used for `WMQ_HOST_NAME`, `WMQ_PORT`, `WMQ_CHANNEL`, `WMQ_CONNECTION_MODE`, `WMQ_CM_CLIENT`, `WMQ_QUEUE_MANAGER` in `applyTarget`.

- [ ] **Step 2: Build to confirm no compile errors**

```bash
mvn compile -q
```
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/acme/mqops/mq/IbmMqGateway.kt
git commit -m "fix: use string literal wmq_provider instead of WMQConstants"
```

---

## Task 2: Remove queue allowlist

Removes the `allowedQueues` concept from `ChannelView`, `MqChannelTopology`, `MqTopologyService`, and `application.properties`. After this task, `resolve()` accepts any queue name.

**Files:**
- Modify: `src/main/kotlin/com/acme/mqops/config/MqTopologyConfig.kt`
- Modify: `src/main/kotlin/com/acme/mqops/config/MqTopologyService.kt`
- Modify: `src/main/resources/application.properties`
- Modify: `src/test/kotlin/com/acme/mqops/config/MqTopologyServiceTest.kt`

- [ ] **Step 1: Update `MqTopologyConfig.kt`**

Replace the entire file content:

```kotlin
package com.acme.mqops.config

import io.quarkus.runtime.annotations.StaticInitSafe
import io.smallrye.config.ConfigMapping
import jakarta.enterprise.context.ApplicationScoped
import java.util.Optional

interface MqTopologyView {
    fun browseLimit(): Int
    fun receiveTimeoutMs(): Long
    fun queueManagers(): Map<String, QueueManagerView>
}

interface QueueManagerView {
    fun name(): Optional<String>
    fun host(): String
    fun port(): Int
    fun channels(): Map<String, ChannelView>
}

interface ChannelView {
    fun name(): String
    fun username(): Optional<String>
    fun password(): Optional<String>
}

data class MqQueueManagerTopology(
    val key: String,
    val name: String,
    val host: String,
    val port: Int,
    val channels: Map<String, MqChannelTopology>
)

data class MqChannelTopology(
    val key: String,
    val name: String
)

@StaticInitSafe
@ConfigMapping(prefix = "mq")
interface MqTopologyConfig : MqTopologyView

@ApplicationScoped
class MqTopologyConfigHolder(val config: MqTopologyConfig) {
    fun view(): MqTopologyView = config
}
```

- [ ] **Step 2: Update `MqTopologyService.kt`**

Replace the entire file content:

```kotlin
package com.acme.mqops.config

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.Optional

@ApplicationScoped
class MqTopologyService {
    private val config: MqTopologyView

    @Inject
    constructor(config: MqTopologyConfig) {
        this.config = config
    }

    constructor(config: MqTopologyView) {
        this.config = config
    }

    fun queueManagers(): Map<String, MqQueueManagerTopology> =
        config.queueManagers().mapValues { (queueManagerKey, queueManager) ->
            MqQueueManagerTopology(
                key = queueManagerKey,
                name = queueManager.name().orElse(queueManagerKey),
                host = queueManager.host(),
                port = queueManager.port(),
                channels = queueManager.channels().mapValues { (channelKey, channel) ->
                    MqChannelTopology(
                        key = channelKey,
                        name = channel.name()
                    )
                }
            )
        }

    fun browseLimit(): Int = config.browseLimit()

    fun receiveTimeoutMs(): Long = config.receiveTimeoutMs()

    fun resolve(queueManagerKey: String, channelKey: String, queueName: String): MqTarget {
        val queueManager = config.queueManagers()[queueManagerKey]
            ?: throw InvalidMqTargetException("Unknown queue manager: $queueManagerKey")
        val channel = queueManager.channels()[channelKey]
            ?: throw InvalidMqTargetException("Unknown channel: $channelKey")

        return MqTarget(
            queueManagerKey = queueManagerKey,
            queueManagerName = queueManager.name().orElse(queueManagerKey),
            host = queueManager.host(),
            port = queueManager.port(),
            channelKey = channelKey,
            channelName = channel.name(),
            queueName = queueName,
            username = channel.username().notBlankOrNull(),
            password = channel.password().notBlankOrNull()
        )
    }

    private fun Optional<String>.notBlankOrNull(): String? =
        if (isPresent) {
            get().takeIf { it.isNotBlank() }
        } else {
            null
        }
}
```

- [ ] **Step 3: Update `application.properties` — remove `allowed-queues` lines and the OPS channel**

Replace `application.properties` with:

```properties
quarkus.application.name=ibm-mq-operation-tool
quarkus.http.port=8080
quarkus.http.auth.permission.authenticated.paths=/*
quarkus.http.auth.permission.authenticated.policy=authenticated

# Replace these in deployment. Tests override OIDC with @TestSecurity.
quarkus.oidc.application-type=web-app
quarkus.oidc.auth-server-url=${OIDC_AUTH_SERVER_URL:http://localhost:8180/realms/mqops}
quarkus.oidc.client-id=${OIDC_CLIENT_ID:mqops-ui}
quarkus.oidc.credentials.secret=${OIDC_CLIENT_SECRET:dev-secret}
%dev.quarkus.oidc.tenant-enabled=false

# Disable OIDC in tests — @TestSecurity covers authenticated scenarios, HTTP policy covers the 401.
%test.quarkus.oidc.tenant-enabled=false

# CORS for Vite dev server
%dev.quarkus.http.cors=true
%dev.quarkus.http.cors.origins=http://localhost:5173

mq.browse-limit=100
mq.receive-timeout-ms=500
mq.queue-managers.QM1.host=localhost
mq.queue-managers.QM1.port=1414
mq.queue-managers.QM1.channels.APP_SVRCONN.name=APP.SVRCONN
mq.queue-managers.QM1.channels.OPS_SVRCONN.name=OPS.SVRCONN
mq.queue-managers.QM1.channels.OPS_SVRCONN.username=${MQ_QM1_OPS_USER:}
mq.queue-managers.QM1.channels.OPS_SVRCONN.password=${MQ_QM1_OPS_PASSWORD:}
```

- [ ] **Step 4: Update `MqTopologyServiceTest.kt`**

Replace the entire file content:

```kotlin
package com.acme.mqops.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.Optional

class MqTopologyServiceTest {
    private val config = TestMqTopologyConfig(
        browseLimit = 100,
        receiveTimeoutMs = 500,
        queueManagers = mapOf(
            "QM1" to TestQueueManagerConfig(
                host = "mq.example.test",
                port = 1414,
                channels = mapOf(
                    "APP_SVRCONN" to TestChannelConfig(
                        name = "APP.SVRCONN",
                        username = Optional.empty(),
                        password = Optional.empty()
                    )
                )
            )
        )
    )

    @Test
    fun `resolve accepts any queue name for configured queue manager and channel`() {
        val service = MqTopologyService(config)

        val target = service.resolve("QM1", "APP_SVRCONN", "ANY.QUEUE.NAME")

        assertEquals("QM1", target.queueManagerName)
        assertEquals("APP.SVRCONN", target.channelName)
        assertEquals("ANY.QUEUE.NAME", target.queueName)
        assertEquals("mq.example.test", target.host)
        assertEquals(1414, target.port)
    }

    @Test
    fun `resolve uses configured queue manager name when it differs from key`() {
        val service = MqTopologyService(
            TestMqTopologyConfig(
                browseLimit = 100,
                receiveTimeoutMs = 500,
                queueManagers = mapOf(
                    "PRIMARY" to TestQueueManagerConfig(
                        host = "mq.example.test",
                        port = 1414,
                        channels = mapOf(
                            "APP_SVRCONN" to TestChannelConfig(
                                name = "APP.SVRCONN",
                                username = Optional.empty(),
                                password = Optional.empty()
                            )
                        ),
                        name = Optional.of("QM1")
                    )
                )
            )
        )

        val target = service.resolve("PRIMARY", "APP_SVRCONN", "DEV.QUEUE.1")

        assertEquals("PRIMARY", target.queueManagerKey)
        assertEquals("QM1", target.queueManagerName)
    }

    @Test
    fun `queue managers returns topology`() {
        val service = MqTopologyService(config)

        val queueManager = service.queueManagers().getValue("QM1")
        val channel = queueManager.channels.getValue("APP_SVRCONN")

        assertEquals("QM1", queueManager.name)
        assertEquals("APP.SVRCONN", channel.name)
    }

    @Test
    fun `resolve rejects unknown queue manager`() {
        val service = MqTopologyService(config)

        assertThrows(InvalidMqTargetException::class.java) {
            service.resolve("UNKNOWN", "APP_SVRCONN", "DEV.QUEUE.1")
        }
    }

    @Test
    fun `resolve rejects unknown channel`() {
        val service = MqTopologyService(config)

        assertThrows(InvalidMqTargetException::class.java) {
            service.resolve("QM1", "UNKNOWN", "DEV.QUEUE.1")
        }
    }

    @Test
    fun `resolve normalizes blank credentials to null`() {
        val service = MqTopologyService(
            TestMqTopologyConfig(
                browseLimit = 100,
                receiveTimeoutMs = 500,
                queueManagers = mapOf(
                    "QM1" to TestQueueManagerConfig(
                        host = "mq.example.test",
                        port = 1414,
                        channels = mapOf(
                            "APP_SVRCONN" to TestChannelConfig(
                                name = "APP.SVRCONN",
                                username = Optional.of(""),
                                password = Optional.of(" ")
                            )
                        )
                    )
                )
            )
        )

        val target = service.resolve("QM1", "APP_SVRCONN", "DEV.QUEUE.1")

        assertNull(target.username)
        assertNull(target.password)
    }
}

data class TestMqTopologyConfig(
    private val browseLimit: Int,
    private val receiveTimeoutMs: Long,
    private val queueManagers: Map<String, TestQueueManagerConfig>
) : MqTopologyView {
    override fun browseLimit() = browseLimit
    override fun receiveTimeoutMs() = receiveTimeoutMs
    override fun queueManagers(): Map<String, QueueManagerView> = queueManagers
}

data class TestQueueManagerConfig(
    private val host: String,
    private val port: Int,
    private val channels: Map<String, TestChannelConfig>,
    private val name: Optional<String> = Optional.empty()
) : QueueManagerView {
    override fun name() = name
    override fun host() = host
    override fun port() = port
    override fun channels(): Map<String, ChannelView> = channels
}

data class TestChannelConfig(
    private val name: String,
    private val username: Optional<String>,
    private val password: Optional<String>
) : ChannelView {
    override fun name() = name
    override fun username() = username
    override fun password() = password
}
```

- [ ] **Step 5: Run tests**

```bash
mvn test -Dtest=MqTopologyServiceTest
```
Expected: `Tests run: 5, Failures: 0, Errors: 0`

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/acme/mqops/config/ \
        src/main/resources/application.properties \
        src/test/kotlin/com/acme/mqops/config/MqTopologyServiceTest.kt
git commit -m "feat: remove queue allowlist — resolve accepts any queue name"
```

---

## Task 3: Replace Qute with JSON REST API

Swaps `quarkus-rest-qute` for `quarkus-rest-jackson`, creates the new REST resource returning JSON, and removes all Qute files.

**Files:**
- Modify: `pom.xml`
- Create: `src/main/kotlin/com/acme/mqops/web/MqApiRequest.kt`
- Create: `src/main/kotlin/com/acme/mqops/web/MqApiResource.kt`
- Create: `src/test/kotlin/com/acme/mqops/web/MqApiResourceTest.kt`
- Delete: `src/main/kotlin/com/acme/mqops/web/QueueOpsResource.kt`
- Delete: `src/main/resources/templates/QueueOpsResource/browse.html`
- Delete: `src/main/resources/templates/QueueOpsResource/index.html`
- Delete: `src/main/resources/templates/QueueOpsResource/notice.html`
- Delete: `src/main/resources/META-INF/resources/styles.css`
- Delete: `src/test/kotlin/com/acme/mqops/web/QueueOpsResourceTest.kt`

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/com/acme/mqops/web/MqApiResourceTest.kt`:

```kotlin
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
    fun `browse returns 400 for unknown channel`() {
        given()
            .contentType(ContentType.JSON)
            .body("""{"queueManager":"QM1","channel":"UNKNOWN","queue":"DEV.QUEUE.1"}""")
            .`when`().post("/api/browse")
            .then()
            .statusCode(400)
    }
}
```

- [ ] **Step 2: Run the test to confirm it fails (resource not yet created)**

```bash
mvn test -Dtest=MqApiResourceTest 2>&1 | tail -20
```
Expected: compilation or runtime failure because `MqApiResource` doesn't exist yet.

- [ ] **Step 3: Update `pom.xml` — swap Qute for Jackson, add testcontainers**

In `pom.xml`, replace:
```xml
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-rest-qute</artifactId>
        </dependency>
```
with:
```xml
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-rest-jackson</artifactId>
        </dependency>
```

Also add testcontainers (test scope) after the `quarkus-test-security` dependency:
```xml
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>testcontainers</artifactId>
            <scope>test</scope>
        </dependency>
```

- [ ] **Step 4: Create `MqApiRequest.kt`**

Create `src/main/kotlin/com/acme/mqops/web/MqApiRequest.kt`:

```kotlin
package com.acme.mqops.web

data class BrowseRequest(val queueManager: String, val channel: String, val queue: String)
data class DeleteRequest(val queueManager: String, val channel: String, val queue: String, val jmsMessageId: String)
data class PutRequest(val queueManager: String, val channel: String, val queue: String, val body: String)
data class CleanRequest(val queueManager: String, val channel: String, val queue: String)
```

- [ ] **Step 5: Create `MqApiResource.kt`**

Create `src/main/kotlin/com/acme/mqops/web/MqApiResource.kt`:

```kotlin
package com.acme.mqops.web

import com.acme.mqops.config.InvalidMqTargetException
import com.acme.mqops.config.MqTarget
import com.acme.mqops.config.MqTopologyService
import com.acme.mqops.mq.MessageGoneException
import com.acme.mqops.mq.MessageRow
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
import com.acme.mqops.service.MqOperationService

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
    fun me(): Map<String, String> = mapOf("user" to identity.principal.name)

    @GET
    @Path("/topology")
    fun topology(): Map<String, Any> {
        val queueManagers = topology.queueManagers().mapValues { (_, qm) ->
            mapOf(
                "name" to qm.name,
                "channels" to qm.channels.mapValues { (_, ch) ->
                    mapOf("name" to ch.name)
                }
            )
        }
        return mapOf("queueManagers" to queueManagers)
    }

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
                Response.status(410).build()
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
    fun clean(request: CleanRequest): Map<String, Int> =
        withTarget(request.queueManager, request.channel, request.queue) { target ->
            val removed = operations.clean(user(), target)
            mapOf("removed" to removed)
        }

    private fun <T> withTarget(
        queueManagerKey: String,
        channelKey: String,
        queueName: String,
        block: (MqTarget) -> T
    ): T =
        try {
            block(topology.resolve(queueManagerKey, channelKey, queueName))
        } catch (ex: InvalidMqTargetException) {
            throw BadRequestException(ex.message)
        }

    private fun user(): String = identity.principal.name
}
```

- [ ] **Step 6: Run the test — confirm it passes**

```bash
mvn test -Dtest=MqApiResourceTest
```
Expected: `Tests run: 5, Failures: 0, Errors: 0`

- [ ] **Step 7: Delete the Qute resource, templates, and styles**

```bash
rm src/main/kotlin/com/acme/mqops/web/QueueOpsResource.kt
rm -r src/main/resources/templates/QueueOpsResource/
rm src/main/resources/META-INF/resources/styles.css
rm src/test/kotlin/com/acme/mqops/web/QueueOpsResourceTest.kt
```

- [ ] **Step 8: Run all tests**

```bash
mvn test
```
Expected: all tests pass, no reference to `QueueOpsResource`.

- [ ] **Step 9: Commit**

```bash
git add pom.xml \
        src/main/kotlin/com/acme/mqops/web/MqApiRequest.kt \
        src/main/kotlin/com/acme/mqops/web/MqApiResource.kt \
        src/test/kotlin/com/acme/mqops/web/MqApiResourceTest.kt
git rm src/main/kotlin/com/acme/mqops/web/QueueOpsResource.kt \
       src/main/resources/templates/QueueOpsResource/browse.html \
       src/main/resources/templates/QueueOpsResource/index.html \
       src/main/resources/templates/QueueOpsResource/notice.html \
       src/main/resources/META-INF/resources/styles.css \
       src/test/kotlin/com/acme/mqops/web/QueueOpsResourceTest.kt
git commit -m "feat: replace Qute UI with JSON REST API at /api/*"
```

---

## Task 4: Testcontainers integration test for IbmMqGateway

Requires Docker to be running. These tests connect to a real IBM MQ container. The IBM MQ developer image pre-creates queue manager `QM1`, channel `DEV.APP.SVRCONN` (no credentials), and queues `DEV.QUEUE.1`/`.2`/`.3`.

**Files:**
- Create: `src/test/kotlin/com/acme/mqops/mq/MqContainerResource.kt`
- Create: `src/test/kotlin/com/acme/mqops/mq/IbmMqGatewayTest.kt`

- [ ] **Step 1: Create `MqContainerResource.kt`**

```kotlin
package com.acme.mqops.mq

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import java.time.Duration

class MqContainerResource : QuarkusTestResourceLifecycleManager {
    private lateinit var container: GenericContainer<*>

    override fun start(): Map<String, String> {
        container = GenericContainer<Nothing>("icr.io/ibm-messaging/mq:latest")
            .withEnv("LICENSE", "accept")
            .withEnv("MQ_QMGR_NAME", "QM1")
            .withExposedPorts(1414)
            .withStartupTimeout(Duration.ofMinutes(3))
            .waitingFor(Wait.forListeningPort())
        container.start()

        return mapOf(
            "mq.queue-managers.QM1.host" to container.host,
            "mq.queue-managers.QM1.port" to container.getMappedPort(1414).toString()
        )
    }

    override fun stop() {
        if (::container.isInitialized) container.stop()
    }
}
```

- [ ] **Step 2: Create `IbmMqGatewayTest.kt`**

```kotlin
package com.acme.mqops.mq

import com.acme.mqops.config.MqTarget
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@QuarkusTest
@QuarkusTestResource(MqContainerResource::class)
class IbmMqGatewayTest {
    @Inject
    lateinit var gateway: IbmMqGateway

    @ConfigProperty(name = "mq.queue-managers.QM1.host")
    lateinit var mqHost: String

    @ConfigProperty(name = "mq.queue-managers.QM1.port")
    var mqPort: Int = 0

    private lateinit var target: MqTarget

    @BeforeEach
    fun setUp() {
        target = MqTarget(
            queueManagerKey = "QM1",
            queueManagerName = "QM1",
            host = mqHost,
            port = mqPort,
            channelKey = "DEV_SVRCONN",
            channelName = "DEV.APP.SVRCONN",
            queueName = "DEV.QUEUE.1",
            username = null,
            password = null
        )
        gateway.clean(target)
    }

    @Test
    fun `browse returns empty list when queue is empty`() {
        assertTrue(gateway.browse(target, 10).isEmpty())
    }

    @Test
    fun `putText then browse returns the message`() {
        gateway.putText(target, """{"test":"hello"}""")

        val rows = gateway.browse(target, 10)

        assertEquals(1, rows.size)
        assertTrue(rows[0].preview.contains("hello"))
    }

    @Test
    fun `delete removes a specific message`() {
        gateway.putText(target, """{"test":"delete-me"}""")
        val before = gateway.browse(target, 10)
        assertEquals(1, before.size)

        val deleted = gateway.delete(target, before[0].jmsMessageId)

        assertTrue(deleted)
        assertTrue(gateway.browse(target, 10).isEmpty())
    }

    @Test
    fun `clean returns removed count and empties the queue`() {
        gateway.putText(target, """{"n":1}""")
        gateway.putText(target, """{"n":2}""")

        val removed = gateway.clean(target)

        assertEquals(2, removed)
        assertTrue(gateway.browse(target, 10).isEmpty())
    }
}
```

- [ ] **Step 3: Run the integration test (requires Docker)**

```bash
mvn test -Dtest=IbmMqGatewayTest
```
Expected: `Tests run: 4, Failures: 0, Errors: 0`. The IBM MQ container pulls on first run and may take 2–3 minutes.

If Docker is not available, skip this step and note it as a manual verification. The unit tests and API tests still pass without Docker.

- [ ] **Step 4: Commit**

```bash
git add src/test/kotlin/com/acme/mqops/mq/
git commit -m "test: add IbmMqGateway integration test with Testcontainers"
```

---

## Task 5: Scaffold Vite + React frontend project

Creates the frontend project skeleton. Run `npm install` to verify the scaffold is valid before writing any components.

**Files:**
- Create: `frontend/package.json`
- Create: `frontend/tsconfig.json`
- Create: `frontend/tsconfig.node.json`
- Create: `frontend/vite.config.ts`
- Create: `frontend/index.html`
- Create: `frontend/src/main.tsx`

- [ ] **Step 1: Create `frontend/package.json`**

```json
{
  "name": "ibm-mq-operation-tool",
  "version": "0.0.1",
  "private": true,
  "scripts": {
    "dev": "vite",
    "build": "tsc && vite build",
    "preview": "vite preview"
  },
  "dependencies": {
    "@tanstack/react-query": "^5.0.0",
    "react": "^18.2.0",
    "react-dom": "^18.2.0"
  },
  "devDependencies": {
    "@types/react": "^18.2.0",
    "@types/react-dom": "^18.2.0",
    "@vitejs/plugin-react": "^4.0.0",
    "typescript": "^5.0.0",
    "vite": "^5.0.0"
  }
}
```

- [ ] **Step 2: Create `frontend/tsconfig.json`**

```json
{
  "compilerOptions": {
    "target": "ES2020",
    "useDefineForClassFields": true,
    "lib": ["ES2020", "DOM", "DOM.Iterable"],
    "module": "ESNext",
    "skipLibCheck": true,
    "moduleResolution": "bundler",
    "allowImportingTsExtensions": true,
    "resolveJsonModule": true,
    "isolatedModules": true,
    "noEmit": true,
    "jsx": "react-jsx",
    "strict": true,
    "noUnusedLocals": true,
    "noUnusedParameters": true,
    "noFallthroughCasesInSwitch": true
  },
  "include": ["src"],
  "references": [{"path": "./tsconfig.node.json"}]
}
```

- [ ] **Step 3: Create `frontend/tsconfig.node.json`**

```json
{
  "compilerOptions": {
    "composite": true,
    "skipLibCheck": true,
    "module": "ESNext",
    "moduleResolution": "bundler",
    "allowSyntheticDefaultImports": true
  },
  "include": ["vite.config.ts"]
}
```

- [ ] **Step 4: Create `frontend/vite.config.ts`**

The `build.outDir` writes directly into the Quarkus static resources directory. `emptyOutDir: true` clears stale files before each build.

```ts
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
      '/login': 'http://localhost:8080',
    },
  },
  build: {
    outDir: '../src/main/resources/META-INF/resources',
    emptyOutDir: true,
  },
})
```

- [ ] **Step 5: Create `frontend/index.html`**

```html
<!DOCTYPE html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>IBM MQ Operation Tool</title>
  </head>
  <body>
    <div id="root"></div>
    <script type="module" src="/src/main.tsx"></script>
  </body>
</html>
```

- [ ] **Step 6: Create `frontend/src/main.tsx`**

```tsx
import React from 'react'
import ReactDOM from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import App from './App'

const queryClient = new QueryClient()

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <App />
    </QueryClientProvider>
  </React.StrictMode>
)
```

- [ ] **Step 7: Install dependencies**

```bash
cd frontend && npm install
```
Expected: `node_modules/` created, no errors.

- [ ] **Step 8: Commit**

```bash
git add frontend/
git commit -m "feat: scaffold Vite + React frontend project"
```

---

## Task 6: Types and API hooks

**Files:**
- Create: `frontend/src/types.ts`
- Create: `frontend/src/api/useMe.ts`
- Create: `frontend/src/api/useTopology.ts`
- Create: `frontend/src/api/useBrowse.ts`
- Create: `frontend/src/api/useDelete.ts`
- Create: `frontend/src/api/usePut.ts`
- Create: `frontend/src/api/useClean.ts`

- [ ] **Step 1: Create `frontend/src/types.ts`**

These match the JSON shapes returned by the backend. `MessageRow` fields mirror `com.acme.mqops.mq.MessageRow` serialized by Jackson.

```ts
export interface MessageRow {
  jmsMessageId: string
  correlationId: string | null
  timestamp: number | null
  expiration: number | null
  priority: number | null
  type: string
  preview: string
}

export interface ChannelTopology {
  name: string
}

export interface QueueManagerTopology {
  name: string
  channels: Record<string, ChannelTopology>
}

export interface Topology {
  queueManagers: Record<string, QueueManagerTopology>
}

export interface MeResponse {
  user: string
}
```

- [ ] **Step 2: Create `frontend/src/api/useMe.ts`**

`retry: false` prevents repeated 401 loops that would trigger the OIDC redirect multiple times.

```ts
import { useQuery } from '@tanstack/react-query'
import type { MeResponse } from '../types'

export function useMe() {
  return useQuery<MeResponse, Error>({
    queryKey: ['me'],
    queryFn: async () => {
      const res = await fetch('/api/me')
      if (res.status === 401) throw new Error('unauthenticated')
      if (!res.ok) throw new Error('Failed to fetch user')
      return res.json()
    },
    retry: false,
  })
}
```

- [ ] **Step 3: Create `frontend/src/api/useTopology.ts`**

```ts
import { useQuery } from '@tanstack/react-query'
import type { Topology } from '../types'

export function useTopology() {
  return useQuery<Topology, Error>({
    queryKey: ['topology'],
    queryFn: async () => {
      const res = await fetch('/api/topology')
      if (!res.ok) throw new Error('Failed to fetch topology')
      return res.json()
    },
  })
}
```

- [ ] **Step 4: Create `frontend/src/api/useBrowse.ts`**

```ts
import { useMutation } from '@tanstack/react-query'
import type { MessageRow } from '../types'

interface BrowseParams {
  queueManager: string
  channel: string
  queue: string
}

export function useBrowse(
  onSuccess: (rows: MessageRow[]) => void,
  onError: (msg: string) => void
) {
  return useMutation<MessageRow[], Error, BrowseParams>({
    mutationFn: async (params) => {
      const res = await fetch('/api/browse', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(params),
      })
      if (!res.ok) throw new Error(await res.text())
      return res.json()
    },
    onSuccess,
    onError: (e) => onError(e.message),
  })
}
```

- [ ] **Step 5: Create `frontend/src/api/useDelete.ts`**

```ts
import { useMutation } from '@tanstack/react-query'

interface DeleteParams {
  queueManager: string
  channel: string
  queue: string
  jmsMessageId: string
}

export function useDelete(onSuccess: () => void, onError: (msg: string) => void) {
  return useMutation<void, Error, DeleteParams>({
    mutationFn: async (params) => {
      const res = await fetch('/api/delete', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(params),
      })
      if (!res.ok)
        throw new Error(res.status === 410 ? 'Message already gone' : await res.text())
    },
    onSuccess,
    onError: (e) => onError(e.message),
  })
}
```

- [ ] **Step 6: Create `frontend/src/api/usePut.ts`**

```ts
import { useMutation } from '@tanstack/react-query'

interface PutParams {
  queueManager: string
  channel: string
  queue: string
  body: string
}

export function usePut(onSuccess: () => void, onError: (msg: string) => void) {
  return useMutation<void, Error, PutParams>({
    mutationFn: async (params) => {
      const res = await fetch('/api/put', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(params),
      })
      if (!res.ok) throw new Error(await res.text())
    },
    onSuccess,
    onError: (e) => onError(e.message),
  })
}
```

- [ ] **Step 7: Create `frontend/src/api/useClean.ts`**

```ts
import { useMutation } from '@tanstack/react-query'

interface CleanParams {
  queueManager: string
  channel: string
  queue: string
}

interface CleanResult {
  removed: number
}

export function useClean(
  onSuccess: (result: CleanResult) => void,
  onError: (msg: string) => void
) {
  return useMutation<CleanResult, Error, CleanParams>({
    mutationFn: async (params) => {
      const res = await fetch('/api/clean', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(params),
      })
      if (!res.ok) throw new Error(await res.text())
      return res.json()
    },
    onSuccess,
    onError: (e) => onError(e.message),
  })
}
```

- [ ] **Step 8: Commit**

```bash
git add frontend/src/types.ts frontend/src/api/
git commit -m "feat: add TypeScript types and React Query API hooks"
```

---

## Task 7: UI components

**Files:**
- Create: `frontend/src/components/Toolbar.tsx`
- Create: `frontend/src/components/MessageTable.tsx`
- Create: `frontend/src/components/PutModal.tsx`
- Create: `frontend/src/components/CleanModal.tsx`
- Create: `frontend/src/components/StatusBar.tsx`

- [ ] **Step 1: Create `frontend/src/components/Toolbar.tsx`**

The toolbar manages queue manager, channel, and queue selection. Changing the queue manager resets the channel (handled by parent via `onQueueManagerChange`).

```tsx
import type { Topology } from '../types'

interface ToolbarProps {
  topology: Topology
  queueManager: string
  channel: string
  queue: string
  onQueueManagerChange: (v: string) => void
  onChannelChange: (v: string) => void
  onQueueChange: (v: string) => void
  onBrowse: () => void
  onPut: () => void
  onClean: () => void
  browsing: boolean
}

export function Toolbar({
  topology,
  queueManager,
  channel,
  queue,
  onQueueManagerChange,
  onChannelChange,
  onQueueChange,
  onBrowse,
  onPut,
  onClean,
  browsing,
}: ToolbarProps) {
  const qmKeys = Object.keys(topology.queueManagers)
  const channels = queueManager
    ? (topology.queueManagers[queueManager]?.channels ?? {})
    : {}
  const channelKeys = Object.keys(channels)
  const canAct = Boolean(queueManager && channel && queue)

  return (
    <div
      style={{
        display: 'flex',
        gap: 8,
        padding: '8px 12px',
        background: '#fff',
        borderBottom: '1px solid #ddd',
        alignItems: 'center',
        flexWrap: 'wrap',
      }}
    >
      <select value={queueManager} onChange={(e) => onQueueManagerChange(e.target.value)}>
        <option value="">Queue Manager…</option>
        {qmKeys.map((k) => (
          <option key={k} value={k}>
            {topology.queueManagers[k].name}
          </option>
        ))}
      </select>
      <select value={channel} onChange={(e) => onChannelChange(e.target.value)}>
        <option value="">Channel…</option>
        {channelKeys.map((k) => (
          <option key={k} value={k}>
            {channels[k].name}
          </option>
        ))}
      </select>
      <input
        value={queue}
        onChange={(e) => onQueueChange(e.target.value)}
        placeholder="Queue name…"
        style={{ flex: 1, minWidth: 140 }}
      />
      <button onClick={onBrowse} disabled={!canAct || browsing}>
        {browsing ? 'Browsing…' : 'Browse'}
      </button>
      <button onClick={onPut} disabled={!canAct}>
        Put…
      </button>
      <button onClick={onClean} disabled={!canAct} style={{ color: '#d33f49' }}>
        Clean…
      </button>
    </div>
  )
}
```

- [ ] **Step 2: Create `frontend/src/components/MessageTable.tsx`**

```tsx
import type { MessageRow } from '../types'

interface MessageTableProps {
  rows: MessageRow[]
  onDelete: (jmsMessageId: string) => void
  deleting: string | null
}

export function MessageTable({ rows, onDelete, deleting }: MessageTableProps) {
  if (rows.length === 0) {
    return (
      <div style={{ padding: 32, color: '#888', textAlign: 'center' }}>
        No messages. Use Browse to load queue contents.
      </div>
    )
  }

  return (
    <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
      <thead>
        <tr style={{ background: '#f0f4f8' }}>
          <th style={{ padding: '6px 8px', textAlign: 'left', borderBottom: '1px solid #ddd' }}>
            Message ID
          </th>
          <th style={{ padding: '6px 8px', textAlign: 'left', borderBottom: '1px solid #ddd' }}>
            Correlation ID
          </th>
          <th style={{ padding: '6px 8px', textAlign: 'left', borderBottom: '1px solid #ddd' }}>
            Type
          </th>
          <th style={{ padding: '6px 8px', textAlign: 'left', borderBottom: '1px solid #ddd' }}>
            Preview
          </th>
          <th style={{ padding: '6px 8px', borderBottom: '1px solid #ddd' }} />
        </tr>
      </thead>
      <tbody>
        {rows.map((row) => (
          <tr key={row.jmsMessageId} style={{ borderBottom: '1px solid #f0f0f0' }}>
            <td
              style={{ padding: '6px 8px', fontFamily: 'monospace', fontSize: 11, whiteSpace: 'nowrap' }}
            >
              {row.jmsMessageId}
            </td>
            <td
              style={{ padding: '6px 8px', fontFamily: 'monospace', fontSize: 11 }}
            >
              {row.correlationId ?? '—'}
            </td>
            <td style={{ padding: '6px 8px' }}>{row.type}</td>
            <td
              style={{
                padding: '6px 8px',
                maxWidth: 400,
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
              }}
            >
              {row.preview}
            </td>
            <td style={{ padding: '6px 8px' }}>
              <button
                onClick={() => onDelete(row.jmsMessageId)}
                disabled={deleting === row.jmsMessageId}
                style={{
                  background: '#d33f49',
                  color: '#fff',
                  border: 'none',
                  borderRadius: 3,
                  padding: '2px 8px',
                  cursor: 'pointer',
                }}
              >
                {deleting === row.jmsMessageId ? '…' : 'Delete'}
              </button>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}
```

- [ ] **Step 3: Create `frontend/src/components/PutModal.tsx`**

Modal closes on `onClose`. On submit, calls `usePut`; `onSuccess` is `onClose` — the modal disappears when the put succeeds.

```tsx
import { useState } from 'react'
import { usePut } from '../api/usePut'

interface PutModalProps {
  queueManager: string
  channel: string
  queue: string
  onClose: () => void
  onError: (msg: string) => void
}

export function PutModal({ queueManager, channel, queue, onClose, onError }: PutModalProps) {
  const [body, setBody] = useState('')
  const put = usePut(onClose, onError)

  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        background: 'rgba(0,0,0,0.4)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 100,
      }}
    >
      <div
        style={{
          background: '#fff',
          borderRadius: 8,
          padding: 24,
          width: 480,
          boxShadow: '0 4px 24px rgba(0,0,0,0.2)',
        }}
      >
        <h3 style={{ margin: '0 0 12px' }}>Put Message — {queue}</h3>
        <textarea
          value={body}
          onChange={(e) => setBody(e.target.value)}
          rows={8}
          style={{
            width: '100%',
            fontFamily: 'monospace',
            fontSize: 13,
            boxSizing: 'border-box',
            padding: 8,
            border: '1px solid #ccc',
            borderRadius: 4,
            resize: 'vertical',
          }}
          placeholder="Message body…"
          autoFocus
        />
        <div style={{ display: 'flex', gap: 8, marginTop: 12, justifyContent: 'flex-end' }}>
          <button onClick={onClose} disabled={put.isPending}>
            Cancel
          </button>
          <button
            onClick={() => put.mutate({ queueManager, channel, queue, body })}
            disabled={!body.trim() || put.isPending}
            style={{
              background: '#255f85',
              color: '#fff',
              border: 'none',
              borderRadius: 4,
              padding: '6px 16px',
            }}
          >
            {put.isPending ? 'Putting…' : 'Put'}
          </button>
        </div>
      </div>
    </div>
  )
}
```

- [ ] **Step 4: Create `frontend/src/components/CleanModal.tsx`**

After a successful clean, shows the removed count before closing. The submit button is disabled until `confirm === queue`, preventing accidental cleans.

```tsx
import { useState } from 'react'
import { useClean } from '../api/useClean'

interface CleanModalProps {
  queueManager: string
  channel: string
  queue: string
  onClose: () => void
  onError: (msg: string) => void
}

export function CleanModal({ queueManager, channel, queue, onClose, onError }: CleanModalProps) {
  const [confirm, setConfirm] = useState('')
  const [removed, setRemoved] = useState<number | null>(null)
  const clean = useClean((result) => setRemoved(result.removed), onError)

  if (removed !== null) {
    return (
      <div
        style={{
          position: 'fixed',
          inset: 0,
          background: 'rgba(0,0,0,0.4)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 100,
        }}
      >
        <div style={{ background: '#fff', borderRadius: 8, padding: 24, width: 400, boxShadow: '0 4px 24px rgba(0,0,0,0.2)' }}>
          <h3 style={{ margin: '0 0 12px' }}>Clean complete</h3>
          <p>
            Removed {removed} message{removed !== 1 ? 's' : ''} from <strong>{queue}</strong>.
          </p>
          <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 12 }}>
            <button onClick={onClose}>Close</button>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        background: 'rgba(0,0,0,0.4)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 100,
      }}
    >
      <div style={{ background: '#fff', borderRadius: 8, padding: 24, width: 400, boxShadow: '0 4px 24px rgba(0,0,0,0.2)' }}>
        <h3 style={{ margin: '0 0 8px', color: '#d33f49' }}>Clean Queue</h3>
        <p style={{ margin: '0 0 12px', color: '#555' }}>
          This will remove all messages from <strong>{queue}</strong>. Type the queue name to confirm.
        </p>
        <input
          value={confirm}
          onChange={(e) => setConfirm(e.target.value)}
          placeholder={queue}
          style={{
            width: '100%',
            padding: '6px 8px',
            border: '1px solid #f5c0c0',
            borderRadius: 4,
            boxSizing: 'border-box',
          }}
          autoFocus
        />
        <div style={{ display: 'flex', gap: 8, marginTop: 12, justifyContent: 'flex-end' }}>
          <button onClick={onClose} disabled={clean.isPending}>
            Cancel
          </button>
          <button
            onClick={() => clean.mutate({ queueManager, channel, queue })}
            disabled={confirm !== queue || clean.isPending}
            style={{ background: '#d33f49', color: '#fff', border: 'none', borderRadius: 4, padding: '6px 16px' }}
          >
            {clean.isPending ? 'Cleaning…' : 'Clean'}
          </button>
        </div>
      </div>
    </div>
  )
}
```

- [ ] **Step 5: Create `frontend/src/components/StatusBar.tsx`**

Error toasts auto-dismiss after 4 seconds. The `useEffect` dependency on `error` re-arms the timer each time a new error arrives.

```tsx
import { useEffect, useState } from 'react'

interface StatusBarProps {
  messageCount: number | null
  error: string | null
}

export function StatusBar({ messageCount, error }: StatusBarProps) {
  const [visibleError, setVisibleError] = useState<string | null>(null)

  useEffect(() => {
    if (!error) return
    setVisibleError(error)
    const timer = setTimeout(() => setVisibleError(null), 4000)
    return () => clearTimeout(timer)
  }, [error])

  return (
    <div
      style={{
        padding: '4px 12px',
        borderTop: '1px solid #eee',
        background: '#fff',
        display: 'flex',
        alignItems: 'center',
        gap: 16,
        fontSize: 13,
        color: '#666',
        minHeight: 28,
      }}
    >
      {messageCount !== null && (
        <span>
          {messageCount} message{messageCount !== 1 ? 's' : ''}
        </span>
      )}
      {visibleError && (
        <span style={{ color: '#d33f49', fontWeight: 500 }}>{visibleError}</span>
      )}
    </div>
  )
}
```

- [ ] **Step 6: Verify TypeScript compiles**

```bash
cd frontend && npx tsc --noEmit
```
Expected: no errors.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/components/
git commit -m "feat: add Toolbar, MessageTable, PutModal, CleanModal, StatusBar components"
```

---

## Task 8: App shell and production build

Wires all components into `App.tsx`, verifies the dev server, then confirms the production build lands in the right place.

**Files:**
- Create: `frontend/src/App.tsx`

- [ ] **Step 1: Create `frontend/src/App.tsx`**

`handleQueueManagerChange` resets the channel and rows when the queue manager changes — channel keys are manager-specific.

```tsx
import { useEffect, useState } from 'react'
import { useMe } from './api/useMe'
import { useTopology } from './api/useTopology'
import { useBrowse } from './api/useBrowse'
import { useDelete } from './api/useDelete'
import { Toolbar } from './components/Toolbar'
import { MessageTable } from './components/MessageTable'
import { PutModal } from './components/PutModal'
import { CleanModal } from './components/CleanModal'
import { StatusBar } from './components/StatusBar'
import type { MessageRow } from './types'

export default function App() {
  const me = useMe()
  const topology = useTopology()

  const [queueManager, setQueueManager] = useState('')
  const [channel, setChannel] = useState('')
  const [queue, setQueue] = useState('')
  const [rows, setRows] = useState<MessageRow[]>([])
  const [browseCount, setBrowseCount] = useState<number | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [showPut, setShowPut] = useState(false)
  const [showClean, setShowClean] = useState(false)
  const [deletingId, setDeletingId] = useState<string | null>(null)

  useEffect(() => {
    if (me.error?.message === 'unauthenticated') {
      window.location.href = '/login'
    }
  }, [me.error])

  const browse = useBrowse(
    (result) => {
      setRows(result)
      setBrowseCount(result.length)
      setError(null)
    },
    setError
  )

  const del = useDelete(
    () => {
      setDeletingId(null)
      browse.mutate({ queueManager, channel, queue })
    },
    (msg) => {
      setDeletingId(null)
      setError(msg)
    }
  )

  const handleDelete = (jmsMessageId: string) => {
    setDeletingId(jmsMessageId)
    del.mutate({ queueManager, channel, queue, jmsMessageId })
  }

  const handleQueueManagerChange = (v: string) => {
    setQueueManager(v)
    setChannel('')
    setRows([])
    setBrowseCount(null)
  }

  if (me.isLoading || topology.isLoading) {
    return <div style={{ padding: 24 }}>Loading…</div>
  }

  if (!topology.data) {
    return <div style={{ padding: 24, color: '#d33f49' }}>Failed to load topology.</div>
  }

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        height: '100vh',
        fontFamily: 'system-ui, -apple-system, sans-serif',
      }}
    >
      <div
        style={{
          background: '#255f85',
          color: '#fff',
          padding: '8px 12px',
          fontWeight: 700,
          display: 'flex',
          alignItems: 'center',
          gap: 12,
        }}
      >
        IBM MQ Operation Tool
        {me.data && (
          <span style={{ fontWeight: 400, opacity: 0.7, fontSize: 14 }}>{me.data.user}</span>
        )}
      </div>
      <Toolbar
        topology={topology.data}
        queueManager={queueManager}
        channel={channel}
        queue={queue}
        onQueueManagerChange={handleQueueManagerChange}
        onChannelChange={setChannel}
        onQueueChange={setQueue}
        onBrowse={() => browse.mutate({ queueManager, channel, queue })}
        onPut={() => setShowPut(true)}
        onClean={() => setShowClean(true)}
        browsing={browse.isPending}
      />
      <div style={{ flex: 1, overflow: 'auto' }}>
        <MessageTable rows={rows} onDelete={handleDelete} deleting={deletingId} />
      </div>
      <StatusBar messageCount={browseCount} error={error} />
      {showPut && (
        <PutModal
          queueManager={queueManager}
          channel={channel}
          queue={queue}
          onClose={() => setShowPut(false)}
          onError={setError}
        />
      )}
      {showClean && (
        <CleanModal
          queueManager={queueManager}
          channel={channel}
          queue={queue}
          onClose={() => setShowClean(false)}
          onError={setError}
        />
      )}
    </div>
  )
}
```

- [ ] **Step 2: Verify TypeScript compiles cleanly**

```bash
cd frontend && npx tsc --noEmit
```
Expected: no errors.

- [ ] **Step 3: Start Quarkus dev server in one terminal**

```bash
mvn quarkus:dev
```
Wait until: `Listening on: http://localhost:8080`

- [ ] **Step 4: Start Vite dev server and open browser**

```bash
cd frontend && npm run dev
```
Open `http://localhost:5173` in a browser.

Verify:
- Header shows "IBM MQ Operation Tool" with user `dev` (from `DevHttpAuthMechanism`)
- Queue Manager dropdown shows `QM1`
- Channel dropdown populates after selecting `QM1`
- Queue name input accepts free text
- Browse button is enabled once all three fields are filled

- [ ] **Step 5: Run production build**

```bash
cd frontend && npm run build
```
Expected: files written to `src/main/resources/META-INF/resources/` (includes `index.html`, `assets/`).

```bash
ls src/main/resources/META-INF/resources/
```
Expected: `index.html` and `assets/` directory present.

- [ ] **Step 6: Verify Quarkus serves the built SPA**

With `mvn quarkus:dev` running, open `http://localhost:8080`.
Expected: the SPA loads (same as Vite dev server, but served by Quarkus directly).

- [ ] **Step 7: Run all backend tests**

```bash
mvn test
```
Expected: all tests pass.

- [ ] **Step 8: Commit**

```bash
git add frontend/src/App.tsx src/main/resources/META-INF/resources/
git commit -m "feat: wire App.tsx layout shell; add production SPA build output"
```

---

## Self-Review

**Spec coverage check:**

| Spec requirement | Task |
|-----------------|------|
| Replace `WMQConstants.WMQ_PROVIDER` with `"wmq_provider"` | Task 1 |
| Remove `allowedQueues` from config, service, properties, tests | Task 2 |
| Add `quarkus-rest-jackson` | Task 3 |
| Add `MqApiRequest.kt` request data classes | Task 3 |
| `MqApiResource.kt` — 6 endpoints | Task 3 |
| `MqApiResourceTest.kt` — auth + topology + error cases | Task 3 |
| Remove `QueueOpsResource.kt` and Qute templates | Task 3 |
| Testcontainers `MqContainerResource` | Task 4 |
| `IbmMqGatewayTest` — browse/put/delete/clean | Task 4 |
| Vite scaffold with proxy `/api/*` → `:8080` | Task 5 |
| `types.ts` — `MessageRow`, `Topology`, `MeResponse` | Task 6 |
| API hooks — `useMe`, `useTopology`, `useBrowse`, `useDelete`, `usePut`, `useClean` | Task 6 |
| `Toolbar.tsx` — selects + free text queue input + Browse/Put…/Clean… | Task 7 |
| `MessageTable.tsx` — full-width table with Delete per row | Task 7 |
| `PutModal.tsx` — textarea + submit → close on success | Task 7 |
| `CleanModal.tsx` — confirm queue name + removed count on success | Task 7 |
| `StatusBar.tsx` — message count + 4s error toasts | Task 7 |
| `App.tsx` — layout shell, `useMe` on mount, redirect 401 → `/login` | Task 8 |
| Production build → `META-INF/resources` | Task 8 |
| Dev CORS for Vite | Task 2 (application.properties) |

All spec requirements are covered. No gaps found.
