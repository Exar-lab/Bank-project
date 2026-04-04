<p align="center">
  <img
    src="https://capsule-render.vercel.app/api?type=waving&color=0:0ea5e9,100:22c55e&height=180&section=header&text=Bank-project%20%E2%80%94%20banco-service&fontSize=38&fontColor=ffffff&animation=fadeIn&desc=Java%2021%2B%20%7C%20Spring%20Boot%20%7C%20MySQL%20%7C%20Kafka%20%7C%20Flyway%20%7C%20JWT&descAlignY=70"
    alt="Bank-project — banco-service banner"
  />
</p>
# Bank-project — banco-service

Proyecto backend de tipo **servicio bancario** construido con **Java + Spring Boot**, orientado a una arquitectura **Hexagonal (Ports & Adapters) + DDD** y organización **feature-first (Screaming Architecture)**.

Incluye persistencia en **MySQL**, mensajería asíncrona con **Kafka**, migraciones con **Flyway**, seguridad con **Spring Security + JWT**, encriptación de propiedades con **Jasypt**, tareas programadas (**Scheduling**) y ejecución asíncrona (**Async**).

---

## Stack / Tecnologías

- **Java**: 21+  
- **Framework**: Spring Boot (parent `4.0.2`)
- **Web**: Spring Web MVC
- **Persistencia**: Spring Data JPA + **MySQL**
- **Migraciones DB**: **Flyway**
- **Mensajería**: **Kafka** (Spring Kafka)
- **Seguridad**: Spring Security + JWT (`com.auth0:java-jwt`)
- **Encriptación**: Jasypt
- **Mapeo DTOs/Modelos**: MapStruct
- **Observabilidad**: Spring Boot Actuator
- **Testing** (dependencias previstas): JUnit 5, Mockito, Testcontainers (según lineamientos del repo)

---

## Estructura del repositorio

- `banco-service/` → servicio principal (Spring Boot + Maven)
- `docker-compose.yml` → infraestructura local (MySQL + Kafka)

---

## Requisitos

- **Docker + Docker Compose** (para levantar MySQL y Kafka localmente)
- **JDK 21+**
- (Recomendado) Maven via wrapper incluido: `banco-service/mvnw`

---

## Configuración (variables de entorno) — Opción C

Se implementó la **Opción C**: un único archivo `.env` compartido entre:

- `docker-compose.yml` (infra local)
- Spring Boot (`application.yml` vía `springboot4-dotenv`)

### Prioridad de valores

Para Spring Boot, los valores se resuelven con esta prioridad:

1. **Variables de entorno reales del sistema/proceso**
2. **Archivo `.env`**
3. **Defaults definidos en `application.yml`** (si existen)

Esto permite sobrescribir una variable puntual sin editar `.env`.

### Archivo `.env` compartido

- Plantilla versionada: `/.env.example`
- Archivo local real: `/.env`
- `.env` está ignorado por Git (no se commitea)

#### Variables mínimas obligatorias para arrancar

- `MYSQL_ROOT_PASSWORD` (Docker Compose / MySQL)
- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `JWT_SECRET_KEY`
- `ISSUER_GENERATOR`
- `JASYPT_ENCRYPTOR_PASSWORD`

`KAFKA_BOOTSTRAP_SERVERS` tiene default (`localhost:9092`), pero se recomienda definirlo igual en `.env`.

> Importante: usar valores locales/no reales y no hardcodear secretos en el repo.

---

## Levantar dependencias (MySQL + Kafka) con Docker

En la **raíz** del repo:

```bash
docker compose up -d
```

Esto levanta:
- **Kafka** en `localhost:9092`
- **MySQL** en `localhost:3306` (DB: `banco_db`)

El `docker-compose.yml` permite configurar el root password vía:
- `MYSQL_ROOT_PASSWORD` (si no se setea, usa `bankpassword` por defecto)

---

## Ejecutar el servicio

### Paso a paso local (recomendado)

1) **Copiar plantilla de entorno**

Linux/macOS:
```bash
cp .env.example .env
```

Windows (PowerShell):
```powershell
Copy-Item .env.example .env
```

2) **Levantar infraestructura**

```bash
docker compose up -d
```

3) **Ejecutar la app Spring Boot**

> Recomendado: ejecutar Maven desde la **raíz** para que Spring tome el mismo `.env` compartido.

Linux/macOS:
```bash
./banco-service/mvnw -f banco-service/pom.xml spring-boot:run
```

Windows (PowerShell o CMD):
```bat
banco-service\mvnw.cmd -f banco-service/pom.xml spring-boot:run
```

### Si ejecutás desde `banco-service/`

`application.yml` define:

```yaml
springdotenv:
  directory: ${SPRINGDOTENV_DIRECTORY:..}
```

Con eso, por defecto busca `.env` en el directorio padre (la raíz del repo).

Si cambiás el directorio de ejecución, podés sobreescribirlo con:

- Linux/macOS:
  ```bash
  export SPRINGDOTENV_DIRECTORY=/ruta/al/directorio/que/contiene/env
  ```
- Windows PowerShell:
  ```powershell
  $env:SPRINGDOTENV_DIRECTORY="C:\ruta\al\directorio\que\contiene\env"
  ```

### Ejecución directa desde `banco-service/` (alternativa)

### Linux/macOS
```bash
./mvnw spring-boot:run
```

### Windows
```bat
mvnw.cmd spring-boot:run
```

---

## Migraciones (Flyway)

Flyway está habilitado y buscará migraciones en:

- `classpath:db/migration`

Actualmente existe al menos esta migración:

- `V1__create_outbox_events.sql` → crea la tabla `outbox_events` para soporte de publicación de eventos.

---

## ¿Cómo funciona? (visión general)

### 1) API + Capas (arquitectura)
El proyecto está planteado para separar responsabilidades por capas, típicamente:

- **Domain**: modelos/entidades del dominio, reglas de negocio, enums, excepciones
- **Application**: casos de uso / orquestación, DTOs (records), mappers (MapStruct)
- **Infrastructure**: repositorios JPA, adaptadores (p.ej. Kafka), configuración técnica (security, etc.)
- **Presentation**: controladores REST, handlers/exception handlers, validaciones de entrada

> Convención de paquetes: `com.banco.co.{feature}.*` (feature-first).

### 2) Persistencia en MySQL
El servicio usa Spring Data JPA para leer/escribir en MySQL. Las migraciones Flyway inicializan estructuras necesarias.

### 3) Mensajería asíncrona con Kafka (event publishing)
El servicio está configurado para conectarse a Kafka y publicar/consumir mensajes.

Además, existe una tabla `outbox_events` (creada por Flyway) con campos como:
- `aggregate_type`, `aggregate_id`, `event_type`, `kafka_topic`, `payload`
- `status` (por defecto `PENDING`)
- timestamps (`created_at`, `published_at`)

Esto sugiere un patrón tipo **Outbox**, donde:
1. Se persiste un evento pendiente en DB dentro del flujo transaccional.
2. Un proceso/tarea (posiblemente programada) publica a Kafka y marca como publicado.

### 4) Seguridad (JWT)
La seguridad está pensada para JWT en el header:
- `Authorization: Bearer <token>`

Y se contempla control de acceso por roles con anotaciones tipo `@PreAuthorize` en endpoints.

### 5) Scheduling + Async
La clase principal habilita:
- scheduling (`@EnableScheduling`)
- ejecución asíncrona (`@EnableAsync`)
- auditoría JPA (`@EnableJpaAuditing`)
- properties para fraude (`@EnableConfigurationProperties`)

---

## Clase principal (entrypoint)

- `banco-service/src/main/java/com/banco/co/BancoServiceApplication.java`

---

## Desarrollo / Convenciones (resumen)

Lineamientos importantes del proyecto:
- DTOs como **`record`** (evitar DTOs mutables)
- Inyección por **constructor** (evitar `@Autowired` en campos)
- No usar `Optional.get()` sin control (usar `orElseThrow`, etc.)
- Excepciones base **abstractas** (jerarquía de `RuntimeException`)
- Evitar N+1 en JPA (`JOIN FETCH`, `@Transactional(readOnly = true)`, etc.)

---

## Error Handling Contract (actualizado)

Para respuestas de error HTTP, el contrato canónico del servicio es:

- `errorCode` (string)
- `message` (string)
- `details` (map)
- `timestamp` (datetime)

Este contrato se construye de forma centralizada para mantener consistencia entre:

- Excepciones manejadas en MVC (`GlobalExceptionHandler`)
- Errores de seguridad (`401/403`) vía handlers dedicados en `SecurityConfig`

Componentes principales:

- `banco-service/src/main/java/com/banco/co/exception/ErrorResponseDto.java`
- `banco-service/src/main/java/com/banco/co/exception/support/ErrorResponseFactory.java`
- `banco-service/src/main/java/com/banco/co/exception/catalog/ErrorCodeCatalog.java`
- `banco-service/src/main/java/com/banco/co/security/config/handler/RestAuthenticationEntryPoint.java`
- `banco-service/src/main/java/com/banco/co/security/config/handler/RestAccessDeniedHandler.java`

Ejemplo de payload:

```json
{
  "errorCode": "VALIDATION_FAILED",
  "message": "Validation failed",
  "details": {
    "field": "must not be blank"
  },
  "timestamp": "2026-03-31T21:00:00"
}
```

> Nota: el contrato vigente usa `errorCode` (no `code`) y `details` como mapa para compatibilidad y consistencia.

---

## Governance de excepciones

Se reforzó la jerarquía de excepciones para cumplir reglas del proyecto:

- `UserException` es abstracta
- `RoleException` es abstracta
- `EnvelopeException` es abstracta

Esto evita instanciación intermedia y fuerza excepciones concretas por caso de negocio.

---

## Verificación focalizada recomendada

Para validar rápidamente el contrato de errores y seguridad sin ejecutar una batería completa:

```bash
./mvnw -Dtest=ExceptionHierarchyGovernanceTest,GlobalExceptionHandlerWebMvcTest,RestAuthenticationEntryPointTest,RestAccessDeniedHandlerTest test
```

Cobertura de esta suite focalizada:

- contrato de error en handlers MVC
- contrato de error en `401/403`
- regresión de consumidor legacy (parser de payload)
- cumplimiento de jerarquía abstracta de excepciones

---

## Notas

- El proyecto incluye `docker-compose.yml` para desarrollo local (Kafka + MySQL).
- Si necesitas que el README también documente **endpoints** (rutas REST) y ejemplos de request/response, hace falta listar/controlar los controllers del servicio para armar una sección de “API Reference”.

---
