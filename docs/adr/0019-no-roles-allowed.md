# ADR 19: No `@RolesAllowed` — API Authorization Is Written in Code

## Status
Accepted

## Context
Gauge has two auth channels (ADR 11): session cookies for the browser UI, Bearer tokens for API clients. Both are supposed to answer the same question — "is this caller an admin?" — and until now they did not.

`quarkus.http.auth.permission.api.paths=/api/*` is configured with `policy=permit`, because `SecurityFilter` does its own Bearer authentication. A permit policy means Quarkus builds **no `SecurityIdentity`** for `/api/*` requests. `SecurityFilter` authenticates the token and installs a **JAX-RS** `SecurityContext` via `requestContext.setSecurityContext(...)`. Those are two different objects.

`@RolesAllowed` is enforced by Quarkus's own interceptor against its `SecurityIdentity`. For a Bearer caller that identity is anonymous, so the annotation denies unconditionally. It is not a permission check that sometimes fails — it is a check that can never pass for the API.

The asymmetry is what makes this dangerous rather than merely broken:

- **Bearer callers:** `@RolesAllowed` always denies. Endpoint is dead.
- **Session callers:** Quarkus builds a real `SecurityIdentity` for form login, so `@RolesAllowed` genuinely enforces. Endpoint is protected.

So simply deleting the annotations would silently **open** those endpoints to any logged-in session user. The annotation cannot be trusted for one channel and cannot be removed without a replacement for the other. In-code checks against the JAX-RS `SecurityContext` work correctly for **both**, because that is the context `SecurityFilter` populates for Bearer and the context Quarkus populates for session.

## Decision
**No `@RolesAllowed` (or any `jakarta.annotation.security` constraint annotation) under `pl.perigee.gauge.resource`.**

API authorization is written explicitly in the method body against the JAX-RS `SecurityContext`:

```java
private boolean isAdmin() {
    return securityContext != null && securityContext.isUserInRole("admin");
}
```

with `@Context SecurityContext securityContext` injected on the resource. This is the pattern `ProjectResource` and `UserResource.changePassword` already used; the decision makes it the only one.

The ban covers future resources. A new admin endpoint must not reach for `@RolesAllowed`.

It also covers **UI** resources (`GaugePageResource`), where the annotation does work — `/ui/*` is `policy=authenticated`, so Quarkus builds a real identity for session callers and `/ui/users` was genuinely protected. It is converted anyway, for one rule instead of two. The conversion is free: `GaugePageResource` already had an `isAdmin()` helper reading the same `SecurityContext` for every other page's `isAdmin` template variable. The HTTP layer still rejects anonymous callers before the resource runs, so the in-code check only adds the role test.

## Consequences
- **The check is visible in the method body.** No annotation to mistake for enforcement.
- **The guard goes first — before validation.** `@RolesAllowed` ran before the method body, so callers with no admin rights never saw validation outcomes. An in-code guard placed after the null checks would answer `400` to a non-admin where it used to answer `403`, leaking which inputs are malformed. Authorization is the first statement in every guarded method.
- **This fails OPEN, not closed.** A forgotten `isAdmin()` grants access instead of denying it — the exact opposite of `@RolesAllowed`. That trade is accepted because the failure mode is a code review miss rather than a silent dead endpoint, and because the alternative (hand-built `SecurityIdentity` association) moves the check behind an annotation again. Each admin endpoint therefore needs a test that proves both outcomes: authorized passes, unauthorized gets `403`.
- **The tests must discriminate.** Asserting `403` under the `gauge.auth.enabled=false` test profile proves nothing — there is no `SecurityContext`, so everything is `403` for the wrong reason. Real coverage needs a `@TestProfile` with auth enabled and a real non-admin key.
- **Session callers are unaffected.** Quarkus populates the same `SecurityContext` for form login, so `isUserInRole` keeps working for the browser.
- **ADR 11 is unchanged** and remains authoritative for the two channels. This ADR only decides *where* the role check lives.
- Consequence for tests written against the old behaviour: any `403` assertion in a security-disabled profile is a statement about a missing identity, not about roles. Where those exist they should say so in a comment.
