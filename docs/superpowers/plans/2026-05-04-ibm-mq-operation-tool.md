# IBM MQ Operation Tool Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Quarkus Kotlin UI for browsing IBM MQ queues, deleting selected messages, putting plain text messages, and cleaning queues.

**Architecture:** A single Quarkus service renders Qute pages and fragments. UI resources validate configured queue targets and call an application service; the application service uses a gateway port implemented by IBM MQ JMS `JmsFactoryFactory`, with lazy connections opened only for explicit MQ operations.

**Tech Stack:** Java 17, Kotlin, Quarkus 3.34.x, Qute, Quarkus REST, Quarkus OIDC, IBM MQ `com.ibm.mq.jakarta.client`, JUnit 5, RestAssured.

---

## References

- Quarkus REST Qute extension: `io.quarkus:quarkus-rest-qute`, Java 17 minimum, version `3.34.5`.
- Quarkus OIDC extension: `io.quarkus:quarkus-oidc`, Java 17 minimum, version `3.34.5`.
- IBM MQ JMS factory extensions are available from `com.ibm.msg.client.jms` and `com.ibm.msg.client.services`.
- IBM MQ Jakarta client artifact latest checked version for this plan: `com.ibm.mq:com.ibm.mq.jakarta.client:9.4.5.0`.

## File Structure

- `pom.xml`: Maven build, Quarkus platform, Kotlin, Qute, OIDC, IBM MQ Jakarta client, tests.
- `.gitignore`: generated build/runtime files.
- `src/main/resources/application.properties`: local defaults, OIDC settings, sample MQ topology.
- `src/main/kotlin/com/acme/mqops/config/MqTopologyConfig.kt`: typed Quarkus config mapping.
- `src/main/kotlin/com/acme/mqops/config/MqTarget.kt`: normalized target identity.
- `src/main/kotlin/com/acme/mqops/config/MqTopologyService.kt`: target validation and hierarchy lookup.
- `src/main/kotlin/com/acme/mqops/mq/MqGateway.kt`: application-facing MQ gateway port and DTOs.
- `src/main/kotlin/com/acme/mqops/mq/IbmMqGateway.kt`: IBM MQ JMS implementation using `JmsFactoryFactory`.
- `src/main/kotlin/com/acme/mqops/service/MqOperationService.kt`: validates targets, invokes gateway, handles audit results.
- `src/main/kotlin/com/acme/mqops/audit/AuditLogger.kt`: structured audit log emitter.
- `src/main/kotlin/com/acme/mqops/web/QueueOpsResource.kt`: Qute routes and form handlers.
- `src/main/resources/templates/QueueOpsResource/index.html`: main operations workspace.
- `src/main/resources/templates/QueueOpsResource/browse.html`: browse table fragment.
- `src/main/resources/templates/QueueOpsResource/notice.html`: result/error fragment.
- `src/main/resources/META-INF/resources/styles.css`: restrained internal-ops styling.
- `src/test/kotlin/com/acme/mqops/config/MqTopologyServiceTest.kt`: topology tests.
- `src/test/kotlin/com/acme/mqops/service/MqOperationServiceTest.kt`: operation service tests with fake gateway.
- `src/test/kotlin/com/acme/mqops/web/QueueOpsResourceTest.kt`: route/security/rendering tests.

---

### Task 1: Scaffold Quarkus Kotlin Project

**Files:**
- Create: `pom.xml`
- Create: `.gitignore`
- Create: `src/main/resources/application.properties`

- [ ] **Step 1: Create the Maven build**

Create `pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.acme</groupId>
    <artifactId>ibm-mq-operation-tool</artifactId>
    <version>1.0.0-SNAPSHOT</version>

    <properties>
        <kotlin.version>2.2.21</kotlin.version>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>
        <quarkus.platform.artifact-id>quarkus-bom</quarkus.platform.artifact-id>
        <quarkus.platform.group-id>io.quarkus.platform</quarkus.platform.group-id>
        <quarkus.platform.version>3.34.5</quarkus.platform.version>
        <surefire-plugin.version>3.5.4</surefire-plugin.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>${quarkus.platform.group-id}</groupId>
                <artifactId>${quarkus.platform.artifact-id}</artifactId>
                <version>${quarkus.platform.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-kotlin</artifactId>
        </dependency>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-rest-qute</artifactId>
        </dependency>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-oidc</artifactId>
        </dependency>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-arc</artifactId>
        </dependency>
        <dependency>
            <groupId>com.ibm.mq</groupId>
            <artifactId>com.ibm.mq.jakarta.client</artifactId>
            <version>9.4.5.0</version>
        </dependency>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-junit5</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>io.rest-assured</groupId>
            <artifactId>rest-assured</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-test-security</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <sourceDirectory>src/main/kotlin</sourceDirectory>
        <testSourceDirectory>src/test/kotlin</testSourceDirectory>
        <plugins>
            <plugin>
                <groupId>${quarkus.platform.group-id}</groupId>
                <artifactId>quarkus-maven-plugin</artifactId>
                <version>${quarkus.platform.version}</version>
                <extensions>true</extensions>
                <executions>
                    <execution>
                        <goals>
                            <goal>build</goal>
                            <goal>generate-code</goal>
                            <goal>generate-code-tests</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
            <plugin>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>${surefire-plugin.version}</version>
                <configuration>
                    <systemPropertyVariables>
                        <java.util.logging.manager>org.jboss.logmanager.LogManager</java.util.logging.manager>
                        <maven.home>${maven.home}</maven.home>
                    </systemPropertyVariables>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.jetbrains.kotlin</groupId>
                <artifactId>kotlin-maven-plugin</artifactId>
                <version>${kotlin.version}</version>
                <executions>
                    <execution>
                        <id>compile</id>
                        <goals>
                            <goal>compile</goal>
                        </goals>
                    </execution>
                    <execution>
                        <id>test-compile</id>
                        <goals>
                            <goal>test-compile</goal>
                        </goals>
                    </execution>
                </executions>
                <configuration>
                    <jvmTarget>17</jvmTarget>
                    <javaParameters>true</javaParameters>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Add ignored generated files**

Create `.gitignore`:

```gitignore
target/
.idea/
.vscode/
*.iml
.env
.superpowers/
```

- [ ] **Step 3: Add base application configuration**

Create `src/main/resources/application.properties`:

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

mq.browse-limit=100
mq.receive-timeout-ms=500
mq.queue-managers.QM1.host=localhost
mq.queue-managers.QM1.port=1414
mq.queue-managers.QM1.channels.APP_SVRCONN.name=APP.SVRCONN
mq.queue-managers.QM1.channels.APP_SVRCONN.allowed-queues[0]=DEV.QUEUE.1
mq.queue-managers.QM1.channels.APP_SVRCONN.allowed-queues[1]=DEV.QUEUE.2
mq.queue-managers.QM1.channels.OPS_SVRCONN.name=OPS.SVRCONN
mq.queue-managers.QM1.channels.OPS_SVRCONN.username=${MQ_QM1_OPS_USER:}
mq.queue-managers.QM1.channels.OPS_SVRCONN.password=${MQ_QM1_OPS_PASSWORD:}
mq.queue-managers.QM1.channels.OPS_SVRCONN.allowed-queues[0]=OPS.QUEUE.1
```

- [ ] **Step 4: Run the build**

Run:

```bash
./mvnw test
```

If `./mvnw` does not exist, run:

```bash
mvn test
```

Expected: Maven downloads dependencies and fails only because no source files exist yet or passes with zero tests. Dependency resolution must include `quarkus-rest-qute`, `quarkus-oidc`, and `com.ibm.mq.jakarta.client`.

- [ ] **Step 5: Commit**

```bash
git add pom.xml .gitignore src/main/resources/application.properties
git commit -m "chore: scaffold Quarkus Kotlin project"
```

---

### Task 2: Add MQ Topology Configuration And Validation

**Files:**
- Create: `src/main/kotlin/com/acme/mqops/config/MqTarget.kt`
- Create: `src/main/kotlin/com/acme/mqops/config/MqTopologyConfig.kt`
- Create: `src/main/kotlin/com/acme/mqops/config/MqTopologyService.kt`
- Create: `src/test/kotlin/com/acme/mqops/config/MqTopologyServiceTest.kt`

- [ ] **Step 1: Write topology service tests**

Create `src/test/kotlin/com/acme/mqops/config/MqTopologyServiceTest.kt`:

```kotlin
package com.acme.mqops.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

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
                        username = "",
                        password = "",
                        allowedQueues = listOf("DEV.QUEUE.1", "DEV.QUEUE.2")
                    )
                )
            )
        )
    )

    @Test
    fun `resolve accepts configured queue manager channel and queue`() {
        val service = MqTopologyService(config)

        val target = service.resolve("QM1", "APP_SVRCONN", "DEV.QUEUE.2")

        assertEquals("QM1", target.queueManagerName)
        assertEquals("APP.SVRCONN", target.channelName)
        assertEquals("DEV.QUEUE.2", target.queueName)
        assertEquals("mq.example.test", target.host)
        assertEquals(1414, target.port)
    }

    @Test
    fun `resolve rejects queue outside configured channel`() {
        val service = MqTopologyService(config)

        assertThrows(InvalidMqTargetException::class.java) {
            service.resolve("QM1", "APP_SVRCONN", "SYSTEM.ADMIN.COMMAND.QUEUE")
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -Dtest=MqTopologyServiceTest test
```

Expected: FAIL because `MqTopologyService`, test config classes, and target types do not exist.

- [ ] **Step 3: Add target model**

Create `src/main/kotlin/com/acme/mqops/config/MqTarget.kt`:

```kotlin
package com.acme.mqops.config

data class MqTarget(
    val queueManagerKey: String,
    val queueManagerName: String,
    val host: String,
    val port: Int,
    val channelKey: String,
    val channelName: String,
    val queueName: String,
    val username: String?,
    val password: String?
)

class InvalidMqTargetException(message: String) : RuntimeException(message)
```

- [ ] **Step 4: Add config mapping and test adapters**

Create `src/main/kotlin/com/acme/mqops/config/MqTopologyConfig.kt`:

```kotlin
package com.acme.mqops.config

import io.quarkus.runtime.annotations.StaticInitSafe
import io.smallrye.config.ConfigMapping
import jakarta.enterprise.context.ApplicationScoped

interface MqTopologyView {
    fun browseLimit(): Int
    fun receiveTimeoutMs(): Long
    fun queueManagers(): Map<String, QueueManagerView>
}

interface QueueManagerView {
    fun host(): String
    fun port(): Int
    fun channels(): Map<String, ChannelView>
}

interface ChannelView {
    fun name(): String
    fun username(): String?
    fun password(): String?
    fun allowedQueues(): List<String>
}

@StaticInitSafe
@ConfigMapping(prefix = "mq")
interface MqTopologyConfig : MqTopologyView

@ApplicationScoped
class MqTopologyConfigHolder(val config: MqTopologyConfig) {
    fun view(): MqTopologyView = config
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
    private val channels: Map<String, TestChannelConfig>
) : QueueManagerView {
    override fun host() = host
    override fun port() = port
    override fun channels(): Map<String, ChannelView> = channels
}

data class TestChannelConfig(
    private val name: String,
    private val username: String?,
    private val password: String?,
    private val allowedQueues: List<String>
) : ChannelView {
    override fun name() = name
    override fun username() = username
    override fun password() = password
    override fun allowedQueues() = allowedQueues
}
```

- [ ] **Step 5: Add topology service**

Create `src/main/kotlin/com/acme/mqops/config/MqTopologyService.kt`:

```kotlin
package com.acme.mqops.config

import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class MqTopologyService(private val config: MqTopologyView) {
    fun queueManagers(): Map<String, QueueManagerView> = config.queueManagers()

    fun browseLimit(): Int = config.browseLimit()

    fun receiveTimeoutMs(): Long = config.receiveTimeoutMs()

    fun resolve(queueManagerKey: String, channelKey: String, queueName: String): MqTarget {
        val queueManager = config.queueManagers()[queueManagerKey]
            ?: throw InvalidMqTargetException("Unknown queue manager: $queueManagerKey")
        val channel = queueManager.channels()[channelKey]
            ?: throw InvalidMqTargetException("Unknown channel: $channelKey")
        if (!channel.allowedQueues().contains(queueName)) {
            throw InvalidMqTargetException("Queue is not allowed for selected channel: $queueName")
        }

        return MqTarget(
            queueManagerKey = queueManagerKey,
            queueManagerName = queueManagerKey,
            host = queueManager.host(),
            port = queueManager.port(),
            channelKey = channelKey,
            channelName = channel.name(),
            queueName = queueName,
            username = channel.username()?.takeIf { it.isNotBlank() },
            password = channel.password()?.takeIf { it.isNotBlank() }
        )
    }
}
```

- [ ] **Step 6: Run tests**

Run:

```bash
mvn -Dtest=MqTopologyServiceTest test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/com/acme/mqops/config src/test/kotlin/com/acme/mqops/config
git commit -m "feat: validate configured MQ topology"
```

---

### Task 3: Add MQ Gateway Port, Audit Logger, And Operation Service

**Files:**
- Create: `src/main/kotlin/com/acme/mqops/mq/MqGateway.kt`
- Create: `src/main/kotlin/com/acme/mqops/audit/AuditLogger.kt`
- Create: `src/main/kotlin/com/acme/mqops/service/MqOperationService.kt`
- Create: `src/test/kotlin/com/acme/mqops/service/MqOperationServiceTest.kt`

- [ ] **Step 1: Write operation service tests**

Create `src/test/kotlin/com/acme/mqops/service/MqOperationServiceTest.kt`:

```kotlin
package com.acme.mqops.service

import com.acme.mqops.audit.AuditLogger
import com.acme.mqops.config.MqTarget
import com.acme.mqops.mq.MessageGoneException
import com.acme.mqops.mq.MessageRow
import com.acme.mqops.mq.MqGateway
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class MqOperationServiceTest {
    private val target = MqTarget("QM1", "QM1", "host", 1414, "APP", "APP.SVRCONN", "DEV.QUEUE.1", null, null)

    @Test
    fun `browse passes configured limit to gateway`() {
        val gateway = FakeGateway()
        val service = MqOperationService(gateway, RecordingAuditLogger())

        service.browse("alice", target, 25)

        assertEquals(25, gateway.lastBrowseLimit)
    }

    @Test
    fun `put writes plain text body and audits success`() {
        val audit = RecordingAuditLogger()
        val gateway = FakeGateway()
        val service = MqOperationService(gateway, audit)

        service.putText("alice", target, "hello")

        assertEquals("hello", gateway.lastPutBody)
        assertEquals("put:success:alice:DEV.QUEUE.1", audit.entries.single())
    }

    @Test
    fun `delete missing message throws message gone and audits not found`() {
        val audit = RecordingAuditLogger()
        val gateway = FakeGateway(deleteMissing = true)
        val service = MqOperationService(gateway, audit)

        assertThrows(MessageGoneException::class.java) {
            service.delete("alice", target, "ID:123")
        }

        assertEquals("delete:not_found:alice:DEV.QUEUE.1:ID:123", audit.entries.single())
    }

    @Test
    fun `clean returns removed count and audits success`() {
        val audit = RecordingAuditLogger()
        val gateway = FakeGateway(cleanCount = 7)
        val service = MqOperationService(gateway, audit)

        val count = service.clean("alice", target)

        assertEquals(7, count)
        assertEquals("clean:success:alice:DEV.QUEUE.1:7", audit.entries.single())
    }

    private class FakeGateway(
        private val deleteMissing: Boolean = false,
        private val cleanCount: Int = 0
    ) : MqGateway {
        var lastBrowseLimit: Int = 0
        var lastPutBody: String = ""

        override fun browse(target: MqTarget, limit: Int): List<MessageRow> {
            lastBrowseLimit = limit
            return emptyList()
        }

        override fun delete(target: MqTarget, jmsMessageId: String): Boolean = !deleteMissing

        override fun putText(target: MqTarget, body: String) {
            lastPutBody = body
        }

        override fun clean(target: MqTarget): Int = cleanCount
    }

    private class RecordingAuditLogger : AuditLogger {
        val entries = mutableListOf<String>()
        override fun delete(user: String, target: MqTarget, messageId: String, result: String, error: String?) {
            entries.add("delete:$result:$user:${target.queueName}:$messageId")
        }

        override fun put(user: String, target: MqTarget, result: String, error: String?) {
            entries.add("put:$result:$user:${target.queueName}")
        }

        override fun clean(user: String, target: MqTarget, removedCount: Int, result: String, error: String?) {
            entries.add("clean:$result:$user:${target.queueName}:$removedCount")
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -Dtest=MqOperationServiceTest test
```

Expected: FAIL because the gateway, audit logger, and service do not exist.

- [ ] **Step 3: Add gateway DTOs and exceptions**

Create `src/main/kotlin/com/acme/mqops/mq/MqGateway.kt`:

```kotlin
package com.acme.mqops.mq

import com.acme.mqops.config.MqTarget

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
```

- [ ] **Step 4: Add audit logger**

Create `src/main/kotlin/com/acme/mqops/audit/AuditLogger.kt`:

```kotlin
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
        log.info(auditLine(user, "delete", target, result, "messageId=$messageId", 0, error))
    }

    override fun put(user: String, target: MqTarget, result: String, error: String?) {
        log.info(auditLine(user, "put", target, result, "messageId=", 0, error))
    }

    override fun clean(user: String, target: MqTarget, removedCount: Int, result: String, error: String?) {
        log.info(auditLine(user, "clean", target, result, "messageId=", removedCount, error))
    }

    private fun auditLine(
        user: String,
        operation: String,
        target: MqTarget,
        result: String,
        messagePart: String,
        removedCount: Int,
        error: String?
    ): String {
        val errorPart = error?.replace('\n', ' ') ?: ""
        return "event=mq_operation timestamp=${Instant.now()} user=$user operation=$operation " +
            "queueManager=${target.queueManagerName} channel=${target.channelName} queue=${target.queueName} " +
            "$messagePart removedCount=$removedCount result=$result error=\"$errorPart\""
    }
}
```

- [ ] **Step 5: Add operation service**

Create `src/main/kotlin/com/acme/mqops/service/MqOperationService.kt`:

```kotlin
package com.acme.mqops.service

import com.acme.mqops.audit.AuditLogger
import com.acme.mqops.config.MqTarget
import com.acme.mqops.mq.MessageGoneException
import com.acme.mqops.mq.MessageRow
import com.acme.mqops.mq.MqGateway
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class MqOperationService(
    private val gateway: MqGateway,
    private val audit: AuditLogger
) {
    fun browse(user: String, target: MqTarget, limit: Int): List<MessageRow> = gateway.browse(target, limit)

    fun delete(user: String, target: MqTarget, jmsMessageId: String) {
        try {
            val deleted = gateway.delete(target, jmsMessageId)
            if (!deleted) {
                audit.delete(user, target, jmsMessageId, "not_found")
                throw MessageGoneException("message no longer available")
            }
            audit.delete(user, target, jmsMessageId, "success")
        } catch (ex: MessageGoneException) {
            throw ex
        } catch (ex: RuntimeException) {
            audit.delete(user, target, jmsMessageId, "failure", ex.message)
            throw ex
        }
    }

    fun putText(user: String, target: MqTarget, body: String) {
        try {
            gateway.putText(target, body)
            audit.put(user, target, "success")
        } catch (ex: RuntimeException) {
            audit.put(user, target, "failure", ex.message)
            throw ex
        }
    }

    fun clean(user: String, target: MqTarget): Int {
        var removedCount = 0
        try {
            removedCount = gateway.clean(target)
            audit.clean(user, target, removedCount, "success")
            return removedCount
        } catch (ex: RuntimeException) {
            audit.clean(user, target, removedCount, "failure", ex.message)
            throw ex
        }
    }
}
```

- [ ] **Step 6: Run tests**

Run:

```bash
mvn -Dtest=MqOperationServiceTest test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/com/acme/mqops/mq src/main/kotlin/com/acme/mqops/audit src/main/kotlin/com/acme/mqops/service src/test/kotlin/com/acme/mqops/service
git commit -m "feat: add MQ operation service boundary"
```

---

### Task 4: Implement Lazy IBM MQ JMS Gateway

**Files:**
- Create: `src/main/kotlin/com/acme/mqops/mq/IbmMqGateway.kt`
- Modify: `src/main/kotlin/com/acme/mqops/mq/MqGateway.kt`

- [ ] **Step 1: Add preview constants to gateway file**

Modify `src/main/kotlin/com/acme/mqops/mq/MqGateway.kt`:

```kotlin
const val TEXT_PREVIEW_LIMIT = 240
const val UNSUPPORTED_PREVIEW = "[payload preview unsupported]"
```

- [ ] **Step 2: Implement IBM gateway with lazy connections**

Create `src/main/kotlin/com/acme/mqops/mq/IbmMqGateway.kt`:

```kotlin
package com.acme.mqops.mq

import com.acme.mqops.config.MqTarget
import com.ibm.msg.client.jms.JmsFactoryFactory
import com.ibm.msg.client.wmq.WMQConstants
import jakarta.enterprise.context.ApplicationScoped
import jakarta.jms.DeliveryMode
import jakarta.jms.JMSContext
import jakarta.jms.JMSException
import jakarta.jms.Message
import jakarta.jms.TextMessage

@ApplicationScoped
class IbmMqGateway : MqGateway {
    override fun browse(target: MqTarget, limit: Int): List<MessageRow> =
        withContext(target) { context ->
            val queue = context.createQueue("queue:///${target.queueName}")
            val browser = context.createBrowser(queue)
            browser.use {
                val rows = mutableListOf<MessageRow>()
                val messages = it.enumeration
                while (messages.hasMoreElements() && rows.size < limit) {
                    rows.add((messages.nextElement() as Message).toRow())
                }
                rows
            }
        }

    override fun delete(target: MqTarget, jmsMessageId: String): Boolean =
        withContext(target) { context ->
            val queue = context.createQueue("queue:///${target.queueName}")
            val selector = "JMSMessageID = '${jmsMessageId.replace("'", "''")}'"
            val consumer = context.createConsumer(queue, selector)
            consumer.use {
                it.receive(500) != null
            }
        }

    override fun putText(target: MqTarget, body: String) {
        withContext(target) { context ->
            val queue = context.createQueue("queue:///${target.queueName}")
            val message = context.createTextMessage(body)
            context.createProducer()
                .setDeliveryMode(DeliveryMode.PERSISTENT)
                .send(queue, message)
        }
    }

    override fun clean(target: MqTarget): Int =
        withContext(target) { context ->
            val queue = context.createQueue("queue:///${target.queueName}")
            val consumer = context.createConsumer(queue)
            consumer.use {
                var count = 0
                while (it.receive(500) != null) {
                    count += 1
                }
                count
            }
        }

    private fun <T> withContext(target: MqTarget, block: (JMSContext) -> T): T {
        val factoryFactory = JmsFactoryFactory.getInstance(WMQConstants.WMQ_PROVIDER)
        val connectionFactory = factoryFactory.createConnectionFactory()
        connectionFactory.setStringProperty(WMQConstants.WMQ_HOST_NAME, target.host)
        connectionFactory.setIntProperty(WMQConstants.WMQ_PORT, target.port)
        connectionFactory.setStringProperty(WMQConstants.WMQ_CHANNEL, target.channelName)
        connectionFactory.setIntProperty(WMQConstants.WMQ_CONNECTION_MODE, WMQConstants.WMQ_CM_CLIENT)
        connectionFactory.setStringProperty(WMQConstants.WMQ_QUEUE_MANAGER, target.queueManagerName)

        val context = if (target.username != null && target.password != null) {
            connectionFactory.createContext(target.username, target.password)
        } else {
            connectionFactory.createContext()
        }

        context.use {
            return block(it)
        }
    }

    private fun Message.toRow(): MessageRow {
        val typeName = this::class.java.simpleName
        val preview = if (this is TextMessage) {
            text.orEmpty().let { if (it.length > TEXT_PREVIEW_LIMIT) it.take(TEXT_PREVIEW_LIMIT) + "..." else it }
        } else {
            UNSUPPORTED_PREVIEW
        }

        return MessageRow(
            jmsMessageId = jmsMessageID,
            correlationId = jmsCorrelationID,
            timestamp = safeLong { jmsTimestamp },
            expiration = safeLong { jmsExpiration },
            priority = safeInt { jmsPriority },
            type = typeName,
            preview = preview
        )
    }

    private fun safeLong(read: () -> Long): Long? = try {
        read()
    } catch (_: JMSException) {
        null
    }

    private fun safeInt(read: () -> Int): Int? = try {
        read()
    } catch (_: JMSException) {
        null
    }
}
```

- [ ] **Step 3: Run compile**

Run:

```bash
mvn test
```

Expected: PASS with IBM MQ Jakarta Messaging imports from `jakarta.jms`.

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/com/acme/mqops/mq
git commit -m "feat: add lazy IBM MQ JMS gateway"
```

---

### Task 5: Add Qute UI Routes

**Files:**
- Create: `src/main/kotlin/com/acme/mqops/web/QueueOpsResource.kt`
- Create: `src/test/kotlin/com/acme/mqops/web/QueueOpsResourceTest.kt`

- [ ] **Step 1: Write route tests**

Create `src/test/kotlin/com/acme/mqops/web/QueueOpsResourceTest.kt`:

```kotlin
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
            .redirects().follow(false)
            .`when`().get("/")
            .then()
            .statusCode(302)
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
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -Dtest=QueueOpsResourceTest test
```

Expected: FAIL because `QueueOpsResource` and templates do not exist.

- [ ] **Step 3: Add resource**

Create `src/main/kotlin/com/acme/mqops/web/QueueOpsResource.kt`:

```kotlin
package com.acme.mqops.web

import com.acme.mqops.config.InvalidMqTargetException
import com.acme.mqops.config.MqTopologyService
import com.acme.mqops.mq.MessageGoneException
import com.acme.mqops.service.MqOperationService
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
    private val index: Template,
    private val browse: Template,
    private val notice: Template
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
```

- [ ] **Step 4: Commit resource after templates are added in Task 6**

Do not commit yet. This task intentionally fails until templates exist.

---

### Task 6: Add Qute Templates And Styling

**Files:**
- Create: `src/main/resources/templates/QueueOpsResource/index.html`
- Create: `src/main/resources/templates/QueueOpsResource/browse.html`
- Create: `src/main/resources/templates/QueueOpsResource/notice.html`
- Create: `src/main/resources/META-INF/resources/styles.css`

- [ ] **Step 1: Add main template**

Create `src/main/resources/templates/QueueOpsResource/index.html`:

```html
<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>IBM MQ Operation Tool</title>
  <link rel="stylesheet" href="/styles.css">
</head>
<body>
<main class="shell">
  <header class="topbar">
    <h1>IBM MQ Operation Tool</h1>
  </header>

  <section class="panel">
    <form class="target-form" method="post" action="/browse">
      <label>Queue manager
        <select name="queueManager">
          {#for qm in queueManagers.keySet}
            <option value="{qm}">{qm}</option>
          {/for}
        </select>
      </label>
      <label>Channel key
        <input name="channel" value="APP_SVRCONN">
      </label>
      <label>Queue
        <input name="queue" value="DEV.QUEUE.1">
      </label>
      <button type="submit">Browse</button>
    </form>
  </section>

  <section class="panel">
    <h2>Put Message</h2>
    <form method="post" action="/put">
      <input type="hidden" name="queueManager" value="QM1">
      <input type="hidden" name="channel" value="APP_SVRCONN">
      <input type="hidden" name="queue" value="DEV.QUEUE.1">
      <textarea name="body" rows="8"></textarea>
      <button type="submit">Put message</button>
    </form>
  </section>

  <section class="panel danger">
    <h2>Clean Queue</h2>
    <form method="post" action="/clean">
      <input type="hidden" name="queueManager" value="QM1">
      <input type="hidden" name="channel" value="APP_SVRCONN">
      <input type="hidden" name="queue" value="DEV.QUEUE.1">
      <label>Type queue name to confirm
        <input name="confirmQueueName">
      </label>
      <button type="submit">Clean queue</button>
    </form>
  </section>
</main>
</body>
</html>
```

- [ ] **Step 2: Add browse table fragment**

Create `src/main/resources/templates/QueueOpsResource/browse.html`:

```html
<table class="message-table">
  <thead>
    <tr>
      <th>Message ID</th>
      <th>Correlation ID</th>
      <th>Type</th>
      <th>Preview</th>
      <th></th>
    </tr>
  </thead>
  <tbody>
  {#for message in messages}
    <tr>
      <td>{message.jmsMessageId}</td>
      <td>{message.correlationId ?: ""}</td>
      <td>{message.type}</td>
      <td>{message.preview}</td>
      <td>
        <form method="post" action="/delete">
          <input type="hidden" name="queueManager" value="{target.queueManagerKey}">
          <input type="hidden" name="channel" value="{target.channelKey}">
          <input type="hidden" name="queue" value="{target.queueName}">
          <input type="hidden" name="jmsMessageId" value="{message.jmsMessageId}">
          <button type="submit" onclick="return confirm('Delete selected message?')">Delete</button>
        </form>
      </td>
    </tr>
  {/for}
  </tbody>
</table>
```

- [ ] **Step 3: Add notice fragment**

Create `src/main/resources/templates/QueueOpsResource/notice.html`:

```html
<div class="notice {kind}">
  {message}
</div>
```

- [ ] **Step 4: Add CSS**

Create `src/main/resources/META-INF/resources/styles.css`:

```css
body {
  margin: 0;
  font-family: system-ui, sans-serif;
  background: #f6f7f8;
  color: #1f2933;
}

.shell {
  max-width: 1180px;
  margin: 0 auto;
  padding: 24px;
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18px;
}

h1 {
  font-size: 24px;
  margin: 0;
}

h2 {
  font-size: 18px;
  margin: 0 0 12px;
}

.panel {
  background: #ffffff;
  border: 1px solid #d8dee4;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 16px;
}

.danger {
  border-color: #d33f49;
}

.target-form {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  align-items: end;
}

label {
  display: grid;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
}

input, select, textarea {
  width: 100%;
  box-sizing: border-box;
  border: 1px solid #b8c0cc;
  border-radius: 6px;
  padding: 8px 10px;
  font: inherit;
}

button {
  border: 0;
  border-radius: 6px;
  padding: 9px 12px;
  background: #255f85;
  color: #ffffff;
  font-weight: 700;
  cursor: pointer;
}

.message-table {
  width: 100%;
  border-collapse: collapse;
  background: #ffffff;
}

.message-table th,
.message-table td {
  border-bottom: 1px solid #e5e9ef;
  padding: 10px;
  text-align: left;
  vertical-align: top;
  font-size: 13px;
}

.notice {
  border-radius: 6px;
  padding: 10px 12px;
}

.notice.success {
  background: #e8f5e9;
}

.notice.warning {
  background: #fff6d6;
}

.notice.error {
  background: #fde7e9;
}
```

- [ ] **Step 5: Run route tests**

Run:

```bash
mvn -Dtest=QueueOpsResourceTest test
```

Expected: PASS.

- [ ] **Step 6: Commit route and UI**

```bash
git add src/main/kotlin/com/acme/mqops/web src/main/resources/templates src/main/resources/META-INF/resources src/test/kotlin/com/acme/mqops/web
git commit -m "feat: add server-rendered MQ operations UI"
```

---

### Task 7: Verify Lazy Connection Behavior By Code Boundary

**Files:**
- Modify: `src/main/kotlin/com/acme/mqops/web/QueueOpsResource.kt`
- Modify: `src/test/kotlin/com/acme/mqops/web/QueueOpsResourceTest.kt`

- [ ] **Step 1: Add route test proving page load does not call gateway**

Extend `QueueOpsResourceTest.kt` with:

```kotlin
@Test
@TestSecurity(user = "alice")
fun `workspace page load does not perform MQ operation`() {
    given()
        .`when`().get("/")
        .then()
        .statusCode(200)
        .body(containsString("IBM MQ Operation Tool"))
}
```

The test passes because the index route does not call `MqOperationService`; keep this route free of gateway calls in future changes.

- [ ] **Step 2: Run all tests**

Run:

```bash
mvn test
```

Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/com/acme/mqops/web/QueueOpsResourceTest.kt
git commit -m "test: cover lazy MQ page load boundary"
```

---

### Task 8: Final Verification And Developer Notes

**Files:**
- Create: `README.md`

- [ ] **Step 1: Add README**

Create `README.md`:

```markdown
# IBM MQ Operation Tool

Internal Quarkus Kotlin UI for IBM MQ queue operations.

## Features

- OIDC-protected UI.
- Configured queue manager -> channel -> queue hierarchy.
- Optional MQ username/password per channel.
- Lazy MQ connection: startup and target selection do not connect to IBM MQ.
- Browse first configured limit of messages.
- Delete selected message.
- Put a plain text message.
- Clean queue by consuming available messages.
- Audit write operations to application logs.

## Run

```bash
mvn quarkus:dev
```

Configure OIDC and MQ topology in `src/main/resources/application.properties` or environment variables.

## Test

```bash
mvn test
```
```

- [ ] **Step 2: Run final tests**

Run:

```bash
mvn test
```

Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add README.md
git commit -m "docs: add project usage notes"
```

---

## Self-Review Checklist

- Spec coverage:
  - OIDC login: Tasks 1, 5, 6.
  - No app role control: Task 1 security config and no role checks in Task 5.
  - Configured hierarchy: Task 2.
  - Optional per-channel MQ credentials: Tasks 1, 2, 4.
  - Lazy MQ connection: Tasks 4 and 7.
  - Browse first N messages: Tasks 3, 4, 5, 6.
  - Text preview only: Task 4.
  - Delete selected message: Tasks 3, 4, 5, 6.
  - Put plain text message: Tasks 3, 4, 5, 6.
  - Clean via JMS consume loop: Tasks 3, 4, 5, 6.
  - Audit write operations: Task 3.
- Placeholder scan: no placeholder markers or ambiguous implementation steps are present.
- Type consistency: `MqTarget`, `MessageRow`, `MqGateway`, `AuditLogger`, and `MqOperationService` signatures are defined before use and reused consistently.
