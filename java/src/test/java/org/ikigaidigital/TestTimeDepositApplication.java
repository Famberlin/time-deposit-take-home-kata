package org.ikigaidigital;

import org.springframework.boot.SpringApplication;

/**
 * Runs the service against PostgreSQL in Docker (Testcontainers) with the local profile and its
 * demo data, without docker compose. Start it from the IDE or with {@code mvn spring-boot:test-run}.
 */
public class TestTimeDepositApplication {

    public static void main(String[] args) {
        SpringApplication.from(TimeDepositApplication::main)
                .with(PostgresContainerConfiguration.class)
                .withAdditionalProfiles("local")
                .run(args);
    }
}
