package org.acme;

import io.quarkus.test.common.DevServicesContext;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.commons.lang3.StringUtils;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.mysql.MySQLContainer;

import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class MySqlContainerConfig
        implements QuarkusTestResourceLifecycleManager,
        DevServicesContext.ContextAware
{

    private static final Integer MYSQL_PORT = 63053;

    private Optional<String> containerNetworkId;
    private JdbcDatabaseContainer container;

    @Override
    public void setIntegrationTestContext(DevServicesContext context) {
        containerNetworkId = context.containerNetworkId();
    }

    @Override
    public Map<String, String> start() {

       container = new MySQLContainer("mysql:8.0.36")
               .withUsername("root")
               .withPassword("root")
               .withReuse(false);

        // start container before retrieving its URL or other properties
        container.start();

       String driver = container.getDriverClassName();
        // apply the network to the container
        containerNetworkId.ifPresent(container::withNetworkMode);

        String jdbcUrl = container.getJdbcUrl();
        if (containerNetworkId.isPresent()) {
            // Replace hostname + port in the provided JDBC URL with the hostname of the Docker container
            jdbcUrl = fixJdbcUrl(jdbcUrl);
        }

        // return a map containing the configuration the application needs to use the service
        Map<String,String> map =  Map.of(
                "quarkus.datasource.username", container.getUsername(),
                "quarkus.datasource.password", container.getPassword(),
                "quarkus.datasource.jdbc.url", jdbcUrl,
                "quarkus.datasource.reactive.url", jdbcUrl.replace("jdbc:", StringUtils.EMPTY
                )
        );


        return map;
    }
    private String fixJdbcUrl(String jdbcUrl) {
        // Part of the JDBC URL to replace
        String hostPort =
                container.getHost()
                + ':' +
                container.getMappedPort(MySQLContainer.MYSQL_PORT);

        // Host/IP on the container network plus the unmapped port
        String networkHostPort =
                container.getCurrentContainerInfo().getConfig().getHostName()
                + ':'
                + MySQLContainer.MYSQL_PORT;

        return jdbcUrl.replace(hostPort, networkHostPort);
    }
    @Override
    public void stop() {

    }
}
