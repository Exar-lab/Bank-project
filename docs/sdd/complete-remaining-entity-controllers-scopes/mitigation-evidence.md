# Mitigation Evidence — complete-remaining-entity-controllers-scopes

## 1) Riesgo testing (@WebMvcTest/@MockBean)

### Hallazgo
En Spring Boot 4 de este repo, `@WebMvcTest` no está en el paquete histórico `org.springframework.boot.test.autoconfigure.web.servlet`, sino en:

- `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`

Además, para mocks de contexto se usa:

- `org.springframework.test.context.bean.override.mockito.MockitoBean`

### Mitigación aplicada
Se agregó un test slice real MVC + method security:

- `banco-service/src/test/java/com/banco/co/account/controller/AccountControllerSecuritySliceWebMvcTest.java`

Este test valida objetivamente:
- 200 con scope correcto (`SCOPE_account:read`)
- 403 con scope incorrecto
- 401 sin autenticación

Se configuró `SecurityFilterChain` de test con `authenticationEntryPoint` y `accessDeniedHandler` explícitos para evidenciar códigos HTTP esperados.

### Evidencia ejecutada
```bash
./mvnw -B -ntp -Dtest=AccountControllerSecuritySliceWebMvcTest test
```

Resultado:
- BUILD SUCCESS
- Tests run: 3
- Failures: 0
- Errors: 0

---

## 2) Riesgo cambios no relacionados (scope isolation)

### Mitigación aplicada
Se definió manifiesto de alcance explícito para verify:

- `docs/sdd/complete-remaining-entity-controllers-scopes/scope-manifest.md`

Incluye:
- listado In Scope (fuente + tests + artefactos)
- exclusiones explícitas
- reglas de filtro para verify
- comandos de pruebas focalizadas

---

## 3) Ajuste de pruebas/config mínima para evidencia trazable

### Ajustes mínimos realizados
- Nuevo test slice: `AccountControllerSecuritySliceWebMvcTest`
- Mantenido y reutilizado test focused suite existente para contrato de errores y gobernanza de seguridad por anotación.

### Evidencia ejecutada
```bash
./mvnw -B -ntp -Dtest=RolePermissionMatrixCardTest,ControllerSecurityAnnotationsTest,AccountControllerTest,AccountAdminControllerTest,EnvelopeControllerTest,PublicUserControllerTest,UserControllerTest,UserAdminControllerTest,CardControllerTest,CardAdminControllerTest,GlobalExceptionHandlerWebMvcTest test
```

Resultado:
- BUILD SUCCESS
- Tests run: 57
- Failures: 0
- Errors: 0

---

## 4) Trazabilidad para verify

Verify debe usar conjuntamente:

1. `docs/sdd/complete-remaining-entity-controllers-scopes/scope-manifest.md`
2. `docs/sdd/complete-remaining-entity-controllers-scopes/mitigation-evidence.md`
3. Artefacto Engram `sdd/complete-remaining-entity-controllers-scopes/apply-progress`

Con esto se reduce ruido del working tree y se aporta evidencia objetiva de seguridad en MVC slices + contratos 400 sin stacktrace.
