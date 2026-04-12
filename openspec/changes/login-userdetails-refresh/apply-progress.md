# Apply Progress: login-userdetails-refresh (runtime remediation v3)

## Mode
Strict TDD (from `sdd/banco-service-clean/testing-capabilities`).

## Goal of this remediation
Close VERIFY CRITICAL runtime gap for UserDetails/repository scenarios by stabilizing Testcontainers↔Docker handshake on Windows and rerunning the objective suite.

## Implemented remediation (advanced, non-destructive)
- ✅ Kept integration tests intact (`@Testcontainers` + `MySQLContainer` + `@DynamicPropertySource`) without disabling or deleting target suites.
- ✅ Adjusted Testcontainers Windows strategy in `banco-service/src/test/resources/testcontainers.properties`:
  - `docker.client.strategy=org.testcontainers.dockerclient.EnvironmentAndSystemPropertyClientProviderStrategy`
  - `docker.host=npipe:////./pipe/dockerDesktopLinuxEngine`
  - `docker.tls.verify=0`
  - `dockerconfig.source=autoIgnoringUserProperties`
  - `client.ping.timeout=60`
- ✅ Enabled diagnostic traces for effective provider/transport visibility:
  - Added `banco-service/src/test/resources/logback-test.xml` with DEBUG for `org.testcontainers` and `com.github.dockerjava`.
- ✅ Applied one minimal additional compatibility iteration in dependency management:
  - `banco-service/pom.xml`: aligned `docker-java` transport/API from transitive `3.4.2` to managed `3.5.3` (`docker-java-api` + `docker-java-transport-zerodep`).

## TDD Cycle Evidence
| Task | Test File | Layer | Safety Net | RED | GREEN | TRIANGULATE | REFACTOR |
|------|-----------|-------|------------|-----|-------|-------------|----------|
| Runtime remediation baseline | Objective suite (same 5 files) | Integration | ✅ Baseline run: 21 run / 2 infra errors | ➖ Infra/config-only task | ✅ Command executed | ➖ Not applicable (no behavior branch added) | ✅ No production behavior changes |
| Advanced config + diagnostics | `testcontainers.properties`, `logback-test.xml` + objective suite | Integration | ✅ Same baseline preserved | ➖ Infra/config-only task | ✅ Re-run executed with DEBUG traces | ➖ Not applicable (config-only) | ✅ Kept changes bounded to test infra |
| Additional minimal iteration | `pom.xml` docker-java alignment + objective suite | Integration | ✅ Baseline and first remediation results kept | ➖ Infra/dependency-only task | ✅ Single retry executed after version alignment | ➖ Not applicable (dependency-only) | ✅ Limited to test dependency management |

## Runtime execution evidence (objective suite only)
Command:
`./mvnw -B -ntp -Dtest=AuthServiceTest,CustomUserDetailsServiceTest,AuthControllerWebMvcTest,UserCredentialRepositoryIntegrationTest,RefreshTokenRepositoryIntegrationTest test`

### Run 1 (baseline)
- Total: **21 run, 0 failures, 2 errors**
- PASS:
  - `AuthServiceTest` (7/7)
  - `CustomUserDetailsServiceTest` (4/4)
  - `AuthControllerWebMvcTest` (8/8)
- ERROR:
  - `RefreshTokenRepositoryIntegrationTest` → `Could not find a valid Docker environment`
  - `UserCredentialRepositoryIntegrationTest` → `Previous attempts to find a Docker environment failed`

### Run 2 (after advanced remediation + single additional iteration)
- Total: **21 run, 0 failures, 2 errors**
- PASS:
  - `AuthServiceTest` (7/7)
  - `CustomUserDetailsServiceTest` (4/4)
  - `AuthControllerWebMvcTest` (8/8)
- ERROR (unchanged):
  - `RefreshTokenRepositoryIntegrationTest` → `Could not find a valid Docker environment`
  - `UserCredentialRepositoryIntegrationTest` → `Previous attempts to find a Docker environment failed`

## Diagnostic findings (confirmed)
- `docker -H npipe:////./pipe/dockerDesktopLinuxEngine info` and `docker -H npipe:////./pipe/docker_engine info` both return valid daemon metadata (`ServerVersion 29.1.5`, `OSType linux`).
- Testcontainers DEBUG trace confirms strategy resolution still executes `NpipeSocketClientProviderStrategy` and probes `GET /v1.32/info`.
- Probe consistently returns HTTP `400` with payload containing empty engine identity fields (`ID`, `OperatingSystem`, `ServerVersion`, etc.), causing provider invalidation.
- After aligning docker-java to `3.5.3`, failure signature remains identical.

## Interpretation
The remaining blocker is a host-specific Docker Desktop/Testcontainers handshake incompatibility on Windows named pipe negotiation (provider validation receives 400 with empty engine identity), not missing daemon availability and not auth/repository business logic.

## Files changed in this remediation
- `banco-service/src/test/resources/testcontainers.properties`
- `banco-service/src/test/resources/logback-test.xml`
- `banco-service/pom.xml`

## Status
Apply remediation completed with one additional minimal iteration and single retry, per request. Objective suite still blocked by Testcontainers runtime handshake; VERIFY CRITICAL remains open until environment-level compatibility is resolved.
