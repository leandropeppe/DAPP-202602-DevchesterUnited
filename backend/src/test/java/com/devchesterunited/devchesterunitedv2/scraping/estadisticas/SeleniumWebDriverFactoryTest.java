package com.devchesterunited.devchesterunitedv2.scraping.estadisticas;

import com.devchesterunited.devchesterunitedv2.scraping.estadisticas.SeleniumWebDriverFactory;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SeleniumWebDriverFactoryTest {
    @Test
    void usaChromeHeadedConservandoElViewportProductivo() {
        var chromeOptions = (Map<?, ?>) SeleniumWebDriverFactory.productiveOptions()
                .asMap().get("goog:chromeOptions");
        var arguments = ((List<?>) chromeOptions.get("args")).stream()
                .map(String::valueOf).toList();

        assertThat(arguments)
                .containsExactly("--window-size=1920,1080")
                .doesNotContain("--headless", "--headless=new", "--headless=old");
    }
}
