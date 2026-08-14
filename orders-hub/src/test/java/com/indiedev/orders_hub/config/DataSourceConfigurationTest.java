package com.indiedev.orders_hub.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.env.StandardEnvironment;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DataSourceConfigurationTest {

    @Test
    void railwayMysqlEnvironmentVariablesConfigureDatasource() throws IOException {
        Properties applicationProperties = new Properties();
        applicationProperties.load(getClass().getResourceAsStream("/application.properties"));

        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource(
                "railway",
                Map.of(
                        "MYSQLHOST", "mysql.railway.internal",
                        "MYSQLPORT", "3306",
                        "MYSQLDATABASE", "orders_hub",
                        "MYSQLUSER", "app_user",
                        "MYSQLPASSWORD", "secret"
                )
        ));
        PropertySourcesPropertyResolver resolver = new PropertySourcesPropertyResolver(environment.getPropertySources());

        assertEquals(
                "jdbc:mysql://mysql.railway.internal:3306/orders_hub",
                resolver.resolveRequiredPlaceholders(applicationProperties.getProperty("spring.datasource.url"))
        );
        assertEquals(
                "app_user",
                resolver.resolveRequiredPlaceholders(applicationProperties.getProperty("spring.datasource.username"))
        );
        assertEquals(
                "secret",
                resolver.resolveRequiredPlaceholders(applicationProperties.getProperty("spring.datasource.password"))
        );
    }
}
