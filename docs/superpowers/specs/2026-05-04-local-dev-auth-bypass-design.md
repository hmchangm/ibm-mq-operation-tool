# Local Dev Auth Bypass Design

**Date:** 2026-05-04  
**Goal:** Allow `mvn quarkus:dev` to run without a live OIDC server by automatically authenticating every request as a hardcoded `"dev"` user.

---

## Problem

Running the app locally requires a Keycloak instance. Without one, every request fails with `OIDC Server is not available`. Developers need a zero-friction local mode.

## Approach

Implement a custom `HttpAuthenticationMechanism` that is compiled into the app only in the `dev` build profile (`@IfBuildProfile("dev")`). It returns a non-anonymous `SecurityIdentity` (principal `"dev"`) for every request, satisfying `@Authenticated` and `identity.principal.name` without any OIDC interaction.

OIDC is disabled for the `dev` profile via a single property so no OIDC bean competes with the dev mechanism.

---

## Changes

### New file: `src/main/kotlin/com/acme/mqops/dev/DevHttpAuthMechanism.kt`

- `@ApplicationScoped`
- `@IfBuildProfile("dev")` — absent from `test` and production builds
- Implements `io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism`
- `authenticate()` returns `Uni.createFrom().item(QuarkusSecurityIdentity.builder().setPrincipal { "dev" }.setAnonymous(false).build())`
- `getChallenge()` returns `Uni.createFrom().nullItem()`
- `getCredentialTypes()` returns `setOf(TrustedAuthenticationRequest::class.java)`

### Modified: `src/main/resources/application.properties`

Add one line:

```properties
%dev.quarkus.oidc.tenant-enabled=false
```

---

## Constraints

- Dev user name is fixed as `"dev"`. It appears in audit log lines for any write operation performed locally.
- No roles are assigned to the dev identity. The existing routes use only `@Authenticated` (no role checks), so this is sufficient.
- The mechanism is invisible in `test` and production profiles — no risk of accidentally bypassing auth in deployed environments.

---

## Testing

No automated tests are added. The dev mechanism is exercised manually via `mvn quarkus:dev`. Existing `QueueOpsResourceTest` runs under `%test` profile and is unaffected.
