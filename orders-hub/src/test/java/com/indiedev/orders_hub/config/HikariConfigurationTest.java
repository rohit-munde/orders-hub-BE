package com.indiedev.orders_hub.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HikariConfigurationTest {

    @Test
    void waitsForDatabaseDuringRailwayColdStarts() throws IOException {
        Properties applicationProperties = new Properties();
        applicationProperties.load(getClass().getResourceAsStream("/application.properties"));

        assertEquals(
                "${HIKARI_INITIALIZATION_FAIL_TIMEOUT:60000}",
                applicationProperties.getProperty("spring.datasource.hikari.initialization-fail-timeout")
        );
    }
}
