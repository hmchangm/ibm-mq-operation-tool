package com.acme.mqops.mq

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.containers.wait.strategy.WaitAllStrategy
import java.time.Duration

class MqContainerResource : QuarkusTestResourceLifecycleManager {
    private lateinit var container: GenericContainer<*>

    override fun start(): Map<String, String> {
        container = GenericContainer<Nothing>("icr.io/ibm-messaging/mq:latest")
            .withEnv("LICENSE", "accept")
            .withEnv("MQ_QMGR_NAME", "QM1")
            .withExposedPorts(1414)
            .withStartupTimeout(Duration.ofMinutes(3))
            .waitingFor(
                WaitAllStrategy()
                    .withStrategy(Wait.forListeningPort())
                    .withStrategy(
                        Wait.forSuccessfulCommand(
                            "echo 'DISPLAY QLOCAL(DEV.QUEUE.1)' | runmqsc QM1 | grep -q 'QUEUE(DEV.QUEUE.1)'"
                        )
                    )
                    .withStartupTimeout(Duration.ofMinutes(3))
            )
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
