## spring-boot-patterns

Core Spring Boot 4.x conventions for banco-service: constructor injection (never @Autowired on fields), Records for all DTOs (never @Data classes), @ConfigurationProperties for typed config, MapStruct mappers with componentModel="spring", SecurityFilterChain bean for security configuration, and Kafka producer/consumer setup.

Use this skill before writing any @Service, @Configuration, DTO, mapper, or security config class.
