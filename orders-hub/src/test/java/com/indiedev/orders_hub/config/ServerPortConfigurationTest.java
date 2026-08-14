package com.indiedev.orders_hub.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.env.StandardEnvironment;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServerPortConfigurationTest {

    @Test
    void railwayPortEnvironmentVariableTakesPrecedence() throws IOException {
        Properties applicationProperties = new Properties();
        applicationProperties.load(getClass().getResourceAsStream("/application.properties"));
        String configuredPort = applicationProperties.getProperty("server.port");

        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource(
                "railway",
                Map.of("PORT", "10000", "SERVER_PORT", "8081")
        ));
        PropertySourcesPropertyResolver resolver = new PropertySourcesPropertyResolver(environment.getPropertySources());

        assertEquals("10000", resolver.resolveRequiredPlaceholders(configuredPort));
    }
}
