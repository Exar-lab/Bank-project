# Changelog

## [0.9.1](https://github.com/Exar-lab/Bank-project/compare/v0.9.0...v0.9.1) (2026-04-25)


### Bug Fixes

* **infrastructure:** expand email_outbox_events.event_id to VARCHAR(100) ([4564728](https://github.com/Exar-lab/Bank-project/commit/4564728cbdb697be34ac9bd3191c5a097e4a6073))

## [0.9.0](https://github.com/Exar-lab/Bank-project/compare/v0.8.1...v0.9.0) (2026-04-24)


### Features

* **notification:** add TransactionEventsEmailHandler, email context DTOs, and Thymeleaf templates ([6450e50](https://github.com/Exar-lab/Bank-project/commit/6450e503913470e8363247928ab70e44598e237c))
* **notification:** transaction email notifications on completed transactions ([6cba4c7](https://github.com/Exar-lab/Bank-project/commit/6cba4c7c981a0d53eeccf0bf7a84a84ba3eb25d3))
* **outbox:** add TRANSACTION_NOTIFICATION_EVENTS to KafkaTopic ([6c425ad](https://github.com/Exar-lab/Bank-project/commit/6c425ad1be3af70f1f27bb802535572087210c52))
* **transaction:** emit notification OutboxEvent on COMPLETED transactions ([637354b](https://github.com/Exar-lab/Bank-project/commit/637354b7afe41911e5661e0e6609460e080c5301))

## [0.8.1](https://github.com/Exar-lab/Bank-project/compare/v0.8.0...v0.8.1) (2026-04-21)


### Bug Fixes

* **application:** inject PasswordEncoder interface instead of BCryptPasswordEncoder in UserService ([9c30fcf](https://github.com/Exar-lab/Bank-project/commit/9c30fcfb98933391b78847dac833f1935ebd401f))
* **infrastructure:** add pessimistic write lock to dispatchSafely to prevent duplicate email sends under contention ([6873c4c](https://github.com/Exar-lab/Bank-project/commit/6873c4c3bcb2bd3e5ef274665bef2997f1ccb288))
* **infrastructure:** replace @Lock PESSIMISTIC_WRITE with native FOR UPDATE in findByIdForUpdate ([3c205b6](https://github.com/Exar-lab/Bank-project/commit/3c205b615cd5b6bf8c3e1807e9898f0d428c7e4b))
* **infrastructure:** replace pessimistic lock with atomic UPDATE guard in dispatchSafely ([8b387cf](https://github.com/Exar-lab/Bank-project/commit/8b387cf4cf063e558e519508d9e74c08307b4990))
* **infrastructure:** write EMAIL_SENT audit synchronously to fix zero-audit race in contention test ([8435680](https://github.com/Exar-lab/Bank-project/commit/843568096d6c7eb47f1a737bba573a3f0e143a78))
* **test:** add @EnableJpaRepositories and @EntityScan to relay integration TestConfig ([0a56b21](https://github.com/Exar-lab/Bank-project/commit/0a56b2156cb1fadf96d2250df76917b3030e513c))
* **test:** configure SpringBeanContainer so Hibernate delegates JasyptEncryptor creation to Spring ([35c03d8](https://github.com/Exar-lab/Bank-project/commit/35c03d8ef4939cc39320485920c5f99f090cd450))
* **test:** deduplicate securityFilterChain bean names in WebMvcTest security configs ([e8a400d](https://github.com/Exar-lab/Bank-project/commit/e8a400d7d89447cf378c5254b245e2e6bc01a9a8))
* **test:** pin BancoServiceApplication class in EmailRelayIntegrationCorrectiveTest ([68c859b](https://github.com/Exar-lab/Bank-project/commit/68c859b7c798bd78e688a6ff37bb11c50a208515))
* **test:** provide explicit entityManagerFactory in relay integration TestConfig ([153a885](https://github.com/Exar-lab/Bank-project/commit/153a88586d91ce84f90c0f54252e2c000c14cebe))
* **test:** provide no-op StringEncryptor so Spring can wire JasyptEncryptor ([db0b548](https://github.com/Exar-lab/Bank-project/commit/db0b548345f315ec3a6654c387e2bf1858069b9a))
* **test:** provide required env-var-backed properties for full app context ([19e38c2](https://github.com/Exar-lab/Bank-project/commit/19e38c2ded92a18e40665e62feae5edbe38dfe11))
* **test:** re-enable DataSource/JPA/Flyway autoconfiguration for relay integration test ([f68627e](https://github.com/Exar-lab/Bank-project/commit/f68627ea4f7f55b69fa22c2d620c09a4e54baeb5))
* **test:** register com.banco.co as autoconfiguration package in relay test ([9e845c8](https://github.com/Exar-lab/Bank-project/commit/9e845c8d23405b963edaf2db6b1403c22adcd7ee))
* **test:** remove @EntityScan — package removed in Spring Boot 4.0.2 ([217e81f](https://github.com/Exar-lab/Bank-project/commit/217e81f457128f216c5841949e556b852f028749))
* **test:** replace BancoServiceApplication with focused classes list in EmailRelayIntegrationCorrectiveTest ([6fb431d](https://github.com/Exar-lab/Bank-project/commit/6fb431d23e4d29fadd8e8062f9a8fcdd0532a3b5))
* **test:** replace custom @ComponentScan config with @SpringBootTest auto-detection to avoid test bean conflicts ([2546bab](https://github.com/Exar-lab/Bank-project/commit/2546bab65f180bf9fe709ef8a76887cfa7c4e7a2))
* **test:** replace MailConfig with GreenMail-compatible JavaMailSender and fix LazyInit on AuditLog details ([96d8bc0](https://github.com/Exar-lab/Bank-project/commit/96d8bc0545b1aa7e7906137df61fc9f387dd1254))
* **test:** scan entity model packages explicitly to avoid JasyptEncryptor ([ba6f5c4](https://github.com/Exar-lab/Bank-project/commit/ba6f5c4199cb7fb0813cb3136363bb11a571d7d7))
* **test:** set username and availableAt in EmailRelayIntegrationCorrectiveTest helpers ([efef1d3](https://github.com/Exar-lab/Bank-project/commit/efef1d3534ee8ae3fec20a276334bced85136a1e))
* **test:** use create-drop instead of validate in EmailRelayIntegrationCorrectiveTest ([b418ac8](https://github.com/Exar-lab/Bank-project/commit/b418ac83c13e41171aaf63bc6b2f51c86ed6fc86))
* **test:** use findAllWithDetails in testAuditContract to avoid LazyInitializationException ([5d9b676](https://github.com/Exar-lab/Bank-project/commit/5d9b676ef854f4099df75be552e0edd78fec1815))
* **test:** use org.springframework.orm.jpa.hibernate.SpringBeanContainer (moved from spring-boot in SB4) ([97eb0a6](https://github.com/Exar-lab/Bank-project/commit/97eb0a6c55ba751111a292aaf1cc6bf03d6f8a42))

## [0.8.0](https://github.com/Exar-lab/Bank-project/compare/v0.7.0...v0.8.0) (2026-04-16)


### Features

* **notification:** durable email notification system via outbox pattern ([ff57577](https://github.com/Exar-lab/Bank-project/commit/ff57577dc55375258228300a5b8434c63b8901f1))
* **notification:** implement durable email notification system via outbox pattern ([f9f88f5](https://github.com/Exar-lab/Bank-project/commit/f9f88f5093e7ec174820cded0cabbc7a9531ddc9))


### Bug Fixes

* **test:** skip Testcontainers tests gracefully when Docker is unavailable ([7fa198d](https://github.com/Exar-lab/Bank-project/commit/7fa198da1841b77092847a9bba2d8df888012f06))

## [0.7.0](https://github.com/Exar-lab/Bank-project/compare/v0.6.1...v0.7.0) (2026-04-12)


### Features

* **auth:** implement login and refresh token hardening ([799ebb9](https://github.com/Exar-lab/Bank-project/commit/799ebb9acab3ce8fe4c992457f9683be79f023b7))
* **auth:** implement login userdetails refresh flow ([2f72a4f](https://github.com/Exar-lab/Bank-project/commit/2f72a4f726dd4a8cfaa19abe843f01fd728e1a85))


### Bug Fixes

* **domain:** remove scale from floating point columns ([8b02473](https://github.com/Exar-lab/Bank-project/commit/8b0247389e3909c8618c08ab83baf2c123387a5d))
* **infrastructure:** address copilot review on testcontainers and migration ([6cb8da9](https://github.com/Exar-lab/Bank-project/commit/6cb8da97020594f09203dae9ca6dc63427fa97c6))
* **test:** clear persistence context after bulk token revoke ([c14ea47](https://github.com/Exar-lab/Bank-project/commit/c14ea474f0b2dab6e8339d2fb3952268edb2d4bc))
* **test:** isolate query count in user credential integration test ([63ff6bc](https://github.com/Exar-lab/Bank-project/commit/63ff6bc347f13af0d37a7c9e16d3eca527da900d))
* **test:** provide jasypt encryptor bean in jpa integration tests ([a01f221](https://github.com/Exar-lab/Bank-project/commit/a01f221e46da1873b34f4cb04b8531bd902c77f8))
* **test:** set explicit username in refresh token integration test ([a447529](https://github.com/Exar-lab/Bank-project/commit/a447529094146f4bffe697129004481ee27a364f))

## [0.6.1](https://github.com/Exar-lab/Bank-project/compare/v0.6.0...v0.6.1) (2026-04-07)


### Bug Fixes

* **infrastructure:** address Copilot review on flyway jpa and jackson ([65051c6](https://github.com/Exar-lab/Bank-project/commit/65051c6505aaf0e533268c7ae694cf98a5c4d2db))
* **infrastructure:** align persistence mappings and startup configs ([96537b4](https://github.com/Exar-lab/Bank-project/commit/96537b4d4158ef8f9b98621bd724531037489f5a))
* **infrastructure:** align persistence mappings and startup configs ([176d354](https://github.com/Exar-lab/Bank-project/commit/176d354ecff2247318a42482f263cf3d8b1ba4b8))
* **infrastructure:** remove incompatible Boot 4 customizers ([d17c2ac](https://github.com/Exar-lab/Bank-project/commit/d17c2ac529dcd388ae5332972dd2a92f68ad05df))

## [0.6.0](https://github.com/Exar-lab/Bank-project/compare/v0.5.0...v0.6.0) (2026-04-06)


### Features

* **security:** complete remaining controllers and scope alignment ([d15ef8e](https://github.com/Exar-lab/Bank-project/commit/d15ef8e974f34e09a5e2233ba458b5ff0459ccf2))
* **security:** complete remaining controllers and scope alignment ([b4cf47d](https://github.com/Exar-lab/Bank-project/commit/b4cf47d847c2f981ff1467f1920af2e7276a3b13))


### Bug Fixes

* **security:** resolve Copilot PR review findings ([30b88b6](https://github.com/Exar-lab/Bank-project/commit/30b88b62f8e43e63b52c1c9a5558a1a4186f03e4))

## [0.5.0](https://github.com/Exar-lab/Bank-project/compare/v0.4.2...v0.5.0) (2026-04-05)


### Features

* **card:** complete card feature ([7bd50ed](https://github.com/Exar-lab/Bank-project/commit/7bd50ed245f7f8afee152ccdc7c56256341d74be))
* **card:** complete card feature with DTOs, service, mapper, controllers and tests ([fe1a2be](https://github.com/Exar-lab/Bank-project/commit/fe1a2be32f3137696abd9f0a7eefa96b4b848395))


### Bug Fixes

* **card:** resolve Copilot review findings ([166ef04](https://github.com/Exar-lab/Bank-project/commit/166ef04bfd97ab34de9aa04f4b831c1ce079cb26))

## [0.4.2](https://github.com/Exar-lab/Bank-project/compare/v0.4.1...v0.4.2) (2026-04-04)


### Refactoring

* **riskprofile:** depend on service interfaces across fraud flow ([cc50405](https://github.com/Exar-lab/Bank-project/commit/cc50405cbc3cb689511edbf5aeb4f269e9798901))
* **riskprofile:** depend on service interfaces across fraud flow ([0b68a4c](https://github.com/Exar-lab/Bank-project/commit/0b68a4cd836089e12a2ed43a79c1bc990e647c11))

## [0.4.1](https://github.com/Exar-lab/Bank-project/compare/v0.4.0...v0.4.1) (2026-04-04)


### Bug Fixes

* **structure:** restore spring boot main class package path ([68c3ba1](https://github.com/Exar-lab/Bank-project/commit/68c3ba14f8013bec0557f75f51352a0143f48b7c))

## [0.4.0](https://github.com/Exar-lab/Bank-project/compare/v0.3.10...v0.4.0) (2026-04-04)


### Features

* **security:** adopt hybrid scope authorization in transaction controllers ([199e642](https://github.com/Exar-lab/Bank-project/commit/199e64282140a016eccd840f143cefc5c9402288))
* **security:** adopt hybrid scope authorization in transaction controllers ([adca23a](https://github.com/Exar-lab/Bank-project/commit/adca23aef439c0b089a922c2727906ae6f2de07b))


### Bug Fixes

* **security:** enforce scope and role on teller and admin actions ([edd51db](https://github.com/Exar-lab/Bank-project/commit/edd51dbb4c9c42b9f56f39b6089e374efac0f050))

## [0.3.10](https://github.com/Exar-lab/Bank-project/compare/v0.3.9...v0.3.10) (2026-04-03)


### Miscellaneous

* **master:** release 0.3.10-SNAPSHOT ([85afb48](https://github.com/Exar-lab/Bank-project/commit/85afb48c7264a0ca31ecbe2fbac9a881a9f2ea8c))
* **master:** release 0.3.10-SNAPSHOT ([c30e97b](https://github.com/Exar-lab/Bank-project/commit/c30e97bf1c621242caf0a26655c22706a5a0497b))

## [0.3.9](https://github.com/Exar-lab/Bank-project/compare/v0.3.8...v0.3.9) (2026-04-03)


### Miscellaneous

* **master:** release 0.3.9-SNAPSHOT ([00c1328](https://github.com/Exar-lab/Bank-project/commit/00c13287ffbb005c441928ac941cb77f73d269bc))
* **master:** release 0.3.9-SNAPSHOT ([4aec835](https://github.com/Exar-lab/Bank-project/commit/4aec835226d4801cbcd6e0aed3f58310cbd3c848))

## [0.3.8](https://github.com/Exar-lab/Bank-project/compare/v0.3.7...v0.3.8) (2026-04-03)


### Miscellaneous

* **master:** release 0.3.8-SNAPSHOT ([e34c915](https://github.com/Exar-lab/Bank-project/commit/e34c915e1a42620880bb4038778cffcc26cc4e15))
* **master:** release 0.3.8-SNAPSHOT ([f90f066](https://github.com/Exar-lab/Bank-project/commit/f90f066b3ffbca09da3bd9f9fedf2a9142034581))

## [0.3.7](https://github.com/Exar-lab/Bank-project/compare/v0.3.6...v0.3.7) (2026-04-03)


### Bug Fixes

* **security:** enforce jwt subject and authorities mapping ([0c94377](https://github.com/Exar-lab/Bank-project/commit/0c94377fe651ba67eb9e66921eaedcf49d8a2884))


### Miscellaneous

* **master:** release 0.3.7-SNAPSHOT ([9aa113b](https://github.com/Exar-lab/Bank-project/commit/9aa113b447714bd5721410af799d0ff9420eecb1))
* **master:** release 0.3.7-SNAPSHOT ([c3b4c1c](https://github.com/Exar-lab/Bank-project/commit/c3b4c1c0432cc8d2ea2f8cd4f58ca492d7ade403))

## [0.3.6](https://github.com/Exar-lab/Bank-project/compare/v0.3.5...v0.3.6) (2026-04-03)


### Miscellaneous

* **master:** release 0.3.6-SNAPSHOT ([497cc53](https://github.com/Exar-lab/Bank-project/commit/497cc539facb7a61195c06e500b2c3de382e42aa))
* **master:** release 0.3.6-SNAPSHOT ([0eac1a4](https://github.com/Exar-lab/Bank-project/commit/0eac1a47aad851e57b8f44f95b80b88a7f61c853))

## [0.3.5](https://github.com/Exar-lab/Bank-project/compare/v0.3.4...v0.3.5) (2026-04-03)


### Miscellaneous

* **master:** release 0.3.5-SNAPSHOT ([afb34a0](https://github.com/Exar-lab/Bank-project/commit/afb34a0f7f13ffeacf11f9bd0ca2d8ba1effddd9))
* **master:** release 0.3.5-SNAPSHOT ([d83d819](https://github.com/Exar-lab/Bank-project/commit/d83d8191f4a9750ddd5f43873c293b2ea735a811))

## [0.3.4](https://github.com/Exar-lab/Bank-project/compare/v0.3.3...v0.3.4) (2026-04-03)


### Miscellaneous

* **master:** release 0.3.4-SNAPSHOT ([0da207a](https://github.com/Exar-lab/Bank-project/commit/0da207a0ddc63466f59cc5a2abc5d4531657d544))
* **master:** release 0.3.4-SNAPSHOT ([1994c6c](https://github.com/Exar-lab/Bank-project/commit/1994c6cd5bbb09b4025616a42770746eeaefe766))

## [0.3.3](https://github.com/Exar-lab/Bank-project/compare/v0.3.2...v0.3.3) (2026-04-03)


### Miscellaneous

* **master:** release 0.3.3-SNAPSHOT ([ce335de](https://github.com/Exar-lab/Bank-project/commit/ce335def9246dac097c45ab7f30618b3555760e1))
* **master:** release 0.3.3-SNAPSHOT ([04b60e6](https://github.com/Exar-lab/Bank-project/commit/04b60e685ed0b748686e49249602c4c25241d080))

## [0.3.2](https://github.com/Exar-lab/Bank-project/compare/v0.3.1...v0.3.2) (2026-04-03)


### Miscellaneous

* **master:** release 0.3.2-SNAPSHOT ([361195a](https://github.com/Exar-lab/Bank-project/commit/361195a54be9afe47c68af42389e361b029853b3))
* **master:** release 0.3.2-SNAPSHOT ([2d7472b](https://github.com/Exar-lab/Bank-project/commit/2d7472bc62244bca10389dee138ccffae83b260b))

## [0.3.1](https://github.com/Exar-lab/Bank-project/compare/v0.3.0...v0.3.1) (2026-04-03)


### Miscellaneous

* **master:** release 0.3.1-SNAPSHOT ([794d424](https://github.com/Exar-lab/Bank-project/commit/794d42457c275c45d4a6f12eabc9f3f98b8d389f))
* **master:** release 0.3.1-SNAPSHOT ([eeae483](https://github.com/Exar-lab/Bank-project/commit/eeae483d96f6bd80de400514b668fd94efdbaee2))

## [0.3.0](https://github.com/Exar-lab/Bank-project/compare/v0.2.6...v0.3.0) (2026-04-03)


### Features

* **fraud:** add risk profile domain model and schema ([2d59ab5](https://github.com/Exar-lab/Bank-project/commit/2d59ab5422ad614af8ce6b4f0098575b8e61b6bd))
* **fraud:** implement risk profile antifraud gate with kafka dlt ([aea0d2a](https://github.com/Exar-lab/Bank-project/commit/aea0d2a673db516acf942638f9dd7c80536f03de))
* **fraud:** wire synchronous risk profile gate with fallback ([4ec4ebd](https://github.com/Exar-lab/Bank-project/commit/4ec4ebd337daed01299c4daee27e6c190bcbfdcd))


### Bug Fixes

* **test:** stabilize fraud gate and kafka integration tests ([50091c7](https://github.com/Exar-lab/Bank-project/commit/50091c78ab9fd29bf42e2758702ef68b220f3ad9))


### Miscellaneous

* **master:** release 0.2.7-SNAPSHOT ([1209aa0](https://github.com/Exar-lab/Bank-project/commit/1209aa0834ef103218543e68bbe237ee5d0439f8))
* **master:** release 0.2.7-SNAPSHOT ([040734f](https://github.com/Exar-lab/Bank-project/commit/040734f3b95350f6df9a2f3850b8d45481d37e2f))

## [0.2.6](https://github.com/Exar-lab/Bank-project/compare/v0.2.5...v0.2.6) (2026-04-02)


### Miscellaneous

* **master:** release 0.2.6-SNAPSHOT ([cc536eb](https://github.com/Exar-lab/Bank-project/commit/cc536eb341065c5bd51d593e853cbc36a1424f75))
* **master:** release 0.2.6-SNAPSHOT ([39f1cec](https://github.com/Exar-lab/Bank-project/commit/39f1cec5a94757a31bb98ff9b520aabd4a383015))

## [0.2.5](https://github.com/Exar-lab/Bank-project/compare/v0.2.4...v0.2.5) (2026-04-01)


### Bug Fixes

* **build:** align compiler to Java 21 and fix null id in transaction test mocks ([00049be](https://github.com/Exar-lab/Bank-project/commit/00049bef44a46869c30616e6278f5fa579fcb806))
* **build:** restore Java 24 preview and Kafka producer config ([127f1d7](https://github.com/Exar-lab/Bank-project/commit/127f1d76fe3c9546c183656e4bfd2bd277b45580))


### Miscellaneous

* **master:** release 0.2.5-SNAPSHOT ([d839a15](https://github.com/Exar-lab/Bank-project/commit/d839a158a1a14f7086201b7ea3b3103456c2333a))
* **master:** release 0.2.5-SNAPSHOT ([0f38c68](https://github.com/Exar-lab/Bank-project/commit/0f38c68c3d57f423becf5a6f03b3e03fa57b2b92))

## [0.2.4](https://github.com/Exar-lab/Bank-project/compare/v0.2.3...v0.2.4) (2026-04-01)


### Miscellaneous

* **master:** release 0.2.4-SNAPSHOT ([09f0761](https://github.com/Exar-lab/Bank-project/commit/09f07614ebae0537cad032c391bfc7f14263e526))
* **master:** release 0.2.4-SNAPSHOT ([e972e8e](https://github.com/Exar-lab/Bank-project/commit/e972e8e7f48a244aa4eb36cceb5c5727eb78b51e))

## [0.2.3](https://github.com/Exar-lab/Bank-project/compare/v0.2.2...v0.2.3) (2026-04-01)


### Miscellaneous

* **master:** release 0.2.2 ([7a79ff5](https://github.com/Exar-lab/Bank-project/commit/7a79ff5ca878039eecfdf7d06510fb2441c86cbe))
* **master:** release 0.2.3-SNAPSHOT ([8c42d0a](https://github.com/Exar-lab/Bank-project/commit/8c42d0a8501651a646ebb7e7c63a26b0406ea10d))
* **master:** release 0.2.3-SNAPSHOT ([68edb17](https://github.com/Exar-lab/Bank-project/commit/68edb1767b7c05d9415e59a83c4a5d3149d32c5b))
* **master:** release 0.2.3-SNAPSHOT ([a82c833](https://github.com/Exar-lab/Bank-project/commit/a82c8338f658975635920a23e81a41bc46959969))

## [0.2.2](https://github.com/Exar-lab/Bank-project/compare/v0.2.1...v0.2.2) (2026-03-31)


### Miscellaneous

* **master:** release 0.2.2-SNAPSHOT ([e8c58f9](https://github.com/Exar-lab/Bank-project/commit/e8c58f948adea3f943245b673fe8223e2405315d))
* **master:** release 0.2.2-SNAPSHOT ([1b98107](https://github.com/Exar-lab/Bank-project/commit/1b9810761b64b70f13acbf64d22913aee6062cf3))

## [0.2.1](https://github.com/Exar-lab/Bank-project/compare/v0.2.0...v0.2.1) (2026-03-31)


### Miscellaneous

* **master:** release 0.2.1-SNAPSHOT ([6626049](https://github.com/Exar-lab/Bank-project/commit/66260492cb0c56e4b138f38ce576171b2f266095))
* **master:** release 0.2.1-SNAPSHOT ([e71cde7](https://github.com/Exar-lab/Bank-project/commit/e71cde7695271d45e8177f8485c51a4b0e3ee908))

## [0.2.0](https://github.com/Exar-lab/Bank-project/compare/v0.1.1...v0.2.0) (2026-03-31)


### Features

* **fraud:** real-time fraud gate in TransactionService ([b1f66eb](https://github.com/Exar-lab/Bank-project/commit/b1f66ebb3039580efff22616fe10ccce41a8abc4))


### Bug Fixes

* **fraud:** address copilot review feedback in fraud gate ([04e1ed2](https://github.com/Exar-lab/Bank-project/commit/04e1ed255e37c745f9840252ebf4fd99834c7f93))


### Miscellaneous

* **master:** release 0.1.2-SNAPSHOT ([fc961a2](https://github.com/Exar-lab/Bank-project/commit/fc961a2be81c605d1def171211579332b3880a17))
* **master:** release 0.1.2-SNAPSHOT ([ada9531](https://github.com/Exar-lab/Bank-project/commit/ada95312541b3dcd540a36e528e23df7e41d6c97))

## [0.1.1](https://github.com/Exar-lab/Bank-project/compare/v0.1.0...v0.1.1) (2026-03-29)


### Miscellaneous

* **master:** release 0.1.1-SNAPSHOT ([#22](https://github.com/Exar-lab/Bank-project/issues/22)) ([a62a33e](https://github.com/Exar-lab/Bank-project/commit/a62a33eb9335f15d3dacf2c0d9e435e5433fa759))

## [0.1.0](https://github.com/Exar-lab/Bank-project/compare/v0.0.1...v0.1.0) (2026-03-29)


### Features

* Add account exception classes and transaction management ([6f2271d](https://github.com/Exar-lab/Bank-project/commit/6f2271d97029fcf12059f9d5b45d0666875eacf0))
* **application:** add FraudFlagRequestDto record ([02bb3a2](https://github.com/Exar-lab/Bank-project/commit/02bb3a2e233364ad8b36710c6329e2ddc059ac79))
* **application:** complete outbox wiring in UserService and EnvelopeScheduleService ([dad65ef](https://github.com/Exar-lab/Bank-project/commit/dad65ef005fceb46dc28a28b05ea0023e0e0a39a))
* **application:** implement digital and admin transaction service methods ([29b2571](https://github.com/Exar-lab/Bank-project/commit/29b2571fc279db103ce295f67b5fa5c880e870cf))
* **application:** implement presential transaction operations (cash deposit/withdrawal/check) ([661a11f](https://github.com/Exar-lab/Bank-project/commit/661a11fc1b1d3dd5124ace7cdc0cb99c49219015))
* **application:** implement read-only transaction service methods ([6eb3cb8](https://github.com/Exar-lab/Bank-project/commit/6eb3cb88e596c732b54dee94110c64d8f722b7d2))
* Implement transaction service and DTOs for various transaction types ([f54a410](https://github.com/Exar-lab/Bank-project/commit/f54a410cab19e41f69d58d14cde5f4c3aa5bf60e))
* Implement user authentication and management features ([e4257cf](https://github.com/Exar-lab/Bank-project/commit/e4257cf4e36177f47a4f15f72a6f511b31f0f7d6))
* **infrastructure:** implement Kafka consumers phase 2 — fraud detection and notification ([503841d](https://github.com/Exar-lab/Bank-project/commit/503841d8be1a819cc8bc6366d8b5d360e8cdc537))
* **infrastructure:** implement Transactional Outbox pattern with Kafka ([9058516](https://github.com/Exar-lab/Bank-project/commit/9058516189767cd82962687dda160bbab57f097e))
* **infrastructure:** Kafka Consumers Phase 2 — fraud detection and notification ([4edcf13](https://github.com/Exar-lab/Bank-project/commit/4edcf13ff1f1012556427294d00699015ca63377))
* **infrastructure:** Transactional Outbox Pattern with Kafka — complete implementation ([e95fe55](https://github.com/Exar-lab/Bank-project/commit/e95fe551a7940fa6ad8b094d32cec55c17b7bb4e))
* **presentation:** add GlobalExceptionHandler for transaction error mapping ([4e7dfe9](https://github.com/Exar-lab/Bank-project/commit/4e7dfe986e742082555b8dfc15955369c3a817c5))
* **presentation:** add TransactionController, TransactionEmployeeController, TransactionAdminController ([b147d80](https://github.com/Exar-lab/Bank-project/commit/b147d80465dc690b1f47521590903dc82f52965c))
* **skills:** complete registry with 5 Spring Boot technical skills and navigation index ([b7bb17f](https://github.com/Exar-lab/Bank-project/commit/b7bb17f84ef2abaa7a4bb3c8973726a01129c3fd))
* **transaction:** complete Transaction endpoints, service layer and two-phase transfer ([9db6ced](https://github.com/Exar-lab/Bank-project/commit/9db6cedc86da98da18813ae3f017a126efa6617c))


### Bug Fixes

* address all Copilot review comments on PR [#17](https://github.com/Exar-lab/Bank-project/issues/17) ([07c98c9](https://github.com/Exar-lab/Bank-project/commit/07c98c92bf97fd69d1118affd1a76cb5eca1be96))
* address Guardian Angel observations on Account domain and controllers ([030ce68](https://github.com/Exar-lab/Bank-project/commit/030ce68aa59d38349b536a8518b530c2fb7ffca0))
* **application:** implement two-phase blockFunds pattern in transfer and cashWithdrawal ([f0a2387](https://github.com/Exar-lab/Bank-project/commit/f0a23870452ca6f4832026d6f2829df7760c92e5))
* **application:** resolve null accountCode in TransactionMapper ([337b3d5](https://github.com/Exar-lab/Bank-project/commit/337b3d546983358af0e1bd3607c1767cbce5ee3c))
* **infrastructure:** address 10 Copilot review comments on PR [#14](https://github.com/Exar-lab/Bank-project/issues/14) ([bb80f1a](https://github.com/Exar-lab/Bank-project/commit/bb80f1a061a807575d3d66b200e0399990e1d773))
* **infrastructure:** address all Copilot review comments on PR [#16](https://github.com/Exar-lab/Bank-project/issues/16) ([e6cc644](https://github.com/Exar-lab/Bank-project/commit/e6cc64496fc0d5b26dd59b3d75f14273007dc95d))
* **infrastructure:** outbox review fixes — claim filter, missing event, rename, payload helper ([b49b3e6](https://github.com/Exar-lab/Bank-project/commit/b49b3e6fa4bd5e973f6463e6d4065bef9e4e9907))


### Refactoring

* Remove unused bank and branch related classes and enums and account service creation ([fe83398](https://github.com/Exar-lab/Bank-project/commit/fe83398ac97ec69def6246df7fee13df9aaa6b57))


### Miscellaneous

* **master:** release 0.0.2-SNAPSHOT ([#20](https://github.com/Exar-lab/Bank-project/issues/20)) ([7c84ba0](https://github.com/Exar-lab/Bank-project/commit/7c84ba0ca01d82dd4fbf6de2862a59ed1cca2149))
* merge phase 1 and phase 2 changes in TransactionService ([04bcc69](https://github.com/Exar-lab/Bank-project/commit/04bcc69b461d643b49201c4c5fbcf6ed344c65de))
* merge phase 3 - complete all transaction service methods ([54e5099](https://github.com/Exar-lab/Bank-project/commit/54e50999ae5c6b913066948b2977a74479d1b3cf))
* **merge:** resolve conflicts with origin/master ([23761d2](https://github.com/Exar-lab/Bank-project/commit/23761d2e38714f20bd903b2f9ae6fcf524110855))
* **merge:** resolve conflicts with origin/master — keep Copilot fix corrections ([3d6074a](https://github.com/Exar-lab/Bank-project/commit/3d6074a7a90c8dbd0aa636a909458e9d93ea6d72))
