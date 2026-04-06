# SDD Scope Manifest — complete-remaining-entity-controllers-scopes

## Purpose
Definir alcance explícito para que `verify` evalúe SOLO los cambios del SDD y excluya ruido del working tree.

## In Scope (cambio SDD + mitigación)

### Controllers / security alignment
- `banco-service/src/main/java/com/banco/co/account/controller/AccountController.java`
- `banco-service/src/main/java/com/banco/co/account/controller/AccountAdminController.java`
- `banco-service/src/main/java/com/banco/co/envelope/controller/EnvelopeController.java`
- `banco-service/src/main/java/com/banco/co/user/controller/PublicUserController.java`
- `banco-service/src/main/java/com/banco/co/user/controller/UserController.java`
- `banco-service/src/main/java/com/banco/co/user/controller/UserAdminController.java`
- `banco-service/src/main/java/com/banco/co/card/controller/CardController.java`
- `banco-service/src/main/java/com/banco/co/card/controller/CardAdminController.java`

### Permission matrix / enums
- `banco-service/src/main/java/com/banco/co/permission/enums/SystemPermission.java`
- `banco-service/src/main/java/com/banco/co/role/configuration/RolePermissionMatrix.java`

### Error contract
- `banco-service/src/main/java/com/banco/co/exception/GlobalExceptionHandler.java`

### Tests
- `banco-service/src/test/java/com/banco/co/account/controller/AccountControllerTest.java`
- `banco-service/src/test/java/com/banco/co/account/controller/AccountAdminControllerTest.java`
- `banco-service/src/test/java/com/banco/co/envelope/controller/EnvelopeControllerTest.java`
- `banco-service/src/test/java/com/banco/co/user/controller/PublicUserControllerTest.java`
- `banco-service/src/test/java/com/banco/co/user/controller/UserControllerTest.java`
- `banco-service/src/test/java/com/banco/co/user/controller/UserAdminControllerTest.java`
- `banco-service/src/test/java/com/banco/co/card/controller/CardControllerTest.java`
- `banco-service/src/test/java/com/banco/co/card/controller/CardAdminControllerTest.java`
- `banco-service/src/test/java/com/banco/co/security/controller/ControllerSecurityAnnotationsTest.java`
- `banco-service/src/test/java/com/banco/co/exception/GlobalExceptionHandlerWebMvcTest.java`
- `banco-service/src/test/java/com/banco/co/account/controller/AccountControllerSecuritySliceWebMvcTest.java` (mitigación MVC security slice)
- `banco-service/src/test/java/com/banco/co/card/controller/CardControllerSecuritySliceWebMvcTest.java` (mitigación MVC security slice)
- `banco-service/src/test/java/com/banco/co/envelope/controller/EnvelopeControllerSecuritySliceWebMvcTest.java` (mitigación MVC security slice)

### Scope governance artifacts
- `docs/sdd/complete-remaining-entity-controllers-scopes/scope-manifest.md`
- `docs/sdd/complete-remaining-entity-controllers-scopes/mitigation-evidence.md`

## Explicitly Out of Scope
- Cambios preexistentes en IDE/config local (`.idea/**`, `.claude/**`, etc.).
- Features no relacionadas (`auditLog`, `transaction`, `utils/FlywayConfig`, `utils/JacksonConfig`, etc.) salvo listado explícito arriba.
- Cualquier archivo fuera de la lista In Scope.

## Verify Filter Rules
1. Validar diffs únicamente sobre archivos `In Scope`.
2. Ignorar todo archivo no listado.
3. Ejecutar sólo pruebas focalizadas de seguridad/controller/error contract (sin build global).

## Focused Verification Commands
```bash
./mvnw -B -ntp -Dtest=AccountControllerSecuritySliceWebMvcTest,CardControllerSecuritySliceWebMvcTest,EnvelopeControllerSecuritySliceWebMvcTest test
./mvnw -B -ntp -Dtest=ControllerSecurityAnnotationsTest,AccountControllerTest,AccountAdminControllerTest,EnvelopeControllerTest,PublicUserControllerTest,UserControllerTest,UserAdminControllerTest,CardControllerTest,CardAdminControllerTest,GlobalExceptionHandlerWebMvcTest test
```
