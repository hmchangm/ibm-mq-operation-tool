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
