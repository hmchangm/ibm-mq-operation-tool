# Local Dev Auth Bypass Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow `mvn quarkus:dev` to run without a live OIDC server by automatically authenticating every request as a hardcoded `"dev"` user.

**Architecture:** A `DevHttpAuthMechanism` bean is annotated `@IfBuildProfile("dev")` so it only exists in the dev build. It implements `HttpAuthenticationMechanism` and returns a non-anonymous `SecurityIdentity` (principal `"dev"`) for every request. `%dev.quarkus.oidc.tenant-enabled=false` prevents the OIDC mechanism from registering in dev, leaving the dev mechanism as the sole auth provider.

**Tech Stack:** Java 17, Kotlin, Quarkus 3.34.x, `quarkus-oidc`, `quarkus-arc` (`@IfBuildProfile`), Smallrye Mutiny (`Uni`).

---

## File Structure

- Create: `src/main/kotlin/com/acme/mqops/dev/DevHttpAuthMechanism.kt`
- Modify: `src/main/resources/application.properties`

---

### Task 1: Add Dev Auth Bypass Mechanism

**Files:**
- Create: `src/main/kotlin/com/acme/mqops/dev/DevHttpAuthMechanism.kt`
- Modify: `src/main/resources/application.properties`

- [ ] **Step 1: Disable OIDC in the dev profile**

In `src/main/resources/application.properties`, add one line after the existing OIDC credentials block:

```properties
%dev.quarkus.oidc.tenant-enabled=false
```

The full OIDC block should now read:

```properties
# Replace these in deployment. Tests override OIDC with @TestSecurity.
quarkus.oidc.application-type=web-app
quarkus.oidc.auth-server-url=${OIDC_AUTH_SERVER_URL:http://localhost:8180/realms/mqops}
quarkus.oidc.client-id=${OIDC_CLIENT_ID:mqops-ui}
quarkus.oidc.credentials.secret=${OIDC_CLIENT_SECRET:dev-secret}
%dev.quarkus.oidc.tenant-enabled=false

# Disable OIDC in tests — @TestSecurity covers authenticated scenarios, HTTP policy covers the 401.
%test.quarkus.oidc.tenant-enabled=false
```

- [ ] **Step 2: Create the dev auth mechanism**

Create `src/main/kotlin/com/acme/mqops/dev/DevHttpAuthMechanism.kt`:

```kotlin
package com.acme.mqops.dev

import io.quarkus.arc.profile.IfBuildProfile
import io.quarkus.security.identity.IdentityProviderManager
import io.quarkus.security.identity.SecurityIdentity
import io.quarkus.security.identity.request.AuthenticationRequest
import io.quarkus.security.identity.request.TrustedAuthenticationRequest
import io.quarkus.security.runtime.QuarkusSecurityIdentity
import io.quarkus.vertx.http.runtime.security.ChallengeData
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism
import io.smallrye.mutiny.Uni
import io.vertx.ext.web.RoutingContext
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
@IfBuildProfile("dev")
class DevHttpAuthMechanism : HttpAuthenticationMechanism {
    override fun authenticate(
        context: RoutingContext,
        identityProviderManager: IdentityProviderManager
    ): Uni<SecurityIdentity> =
        Uni.createFrom().item(
            QuarkusSecurityIdentity.builder()
                .setPrincipal(java.security.Principal { "dev" })
                .build()
        )

    override fun getChallenge(context: RoutingContext): Uni<ChallengeData> =
        Uni.createFrom().nullItem()

    override fun getCredentialTypes(): Set<Class<out AuthenticationRequest>> =
        setOf(TrustedAuthenticationRequest::class.java)
}
```

- [ ] **Step 3: Run existing tests**

Run:

```bash
mvn test
```

Expected: `Tests run: 21, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`. The `%dev` profile is not active during `mvn test`, so `DevHttpAuthMechanism` is not registered and the test behaviour is unchanged.

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/com/acme/mqops/dev/DevHttpAuthMechanism.kt src/main/resources/application.properties
git commit -m "feat: bypass OIDC in dev mode with hardcoded dev user"
```

---

## Self-Review Checklist

- Spec coverage: OIDC disabled in dev ✓, dev identity provided ✓, no change to existing code ✓, absent from non-dev builds ✓.
- Placeholder scan: none present.
- Type consistency: `HttpAuthenticationMechanism`, `IdentityProviderManager`, `SecurityIdentity`, `TrustedAuthenticationRequest`, `ChallengeData`, `QuarkusSecurityIdentity` all from Quarkus 3.x APIs used consistently.
