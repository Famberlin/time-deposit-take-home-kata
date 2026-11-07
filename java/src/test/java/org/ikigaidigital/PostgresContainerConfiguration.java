package org.ikigaidigital;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * PostgreSQL in Docker for integration tests and {@code TestTimeDepositApplication}. {@code
 * @ServiceConnection} supplies the datasource URL, user and password (Testcontainers' throwaway
 * defaults), so the main configuration needs no credentials.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresContainerConfiguration {

    /** Same major version as compose.yaml. */
    public static final DockerImageName POSTGRES = DockerImageName.parse("postgres:18");

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(POSTGRES);
    }
}
