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

## Configuración (variables de entorno)

El servicio usa `application.yml` con variables de entorno obligatorias/esperadas:

### Base de datos (MySQL)
- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`

### Kafka
- `KAFKA_BOOTSTRAP_SERVERS` (por defecto: `localhost:9092`)

### Seguridad (JWT)
- `JWT_SECRET_KEY`
- `ISSUER_GENERATOR`
- Config de expiración:
  - Access token: 15 min
  - Refresh token: 30 días

### Jasypt
- `JASYPT_ENCRYPTOR_PASSWORD`

> Importante: no hardcodear secretos en el repo.

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

Desde `banco-service/`:

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

## Notas

- El proyecto incluye `docker-compose.yml` para desarrollo local (Kafka + MySQL).
- Si necesitas que el README también documente **endpoints** (rutas REST) y ejemplos de request/response, hace falta listar/controlar los controllers del servicio para armar una sección de “API Reference”.

---
