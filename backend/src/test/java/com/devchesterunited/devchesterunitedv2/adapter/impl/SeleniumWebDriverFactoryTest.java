package com.devchesterunited.devchesterunitedv2.adapter.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SeleniumWebDriverFactoryTest {
    @Test
    void usaChromeHeadedConservandoElViewportProductivo() {
        var chromeOptions = (Map<?, ?>) SeleniumWebDriverFactory.productiveOptions()
                .asMap().get("goog:chromeOptions");
        var arguments = ((List<?>) chromeOptions.get("args")).stream()
                .map(String::valueOf).toList();

        assertThat(arguments).containsExactly("--window-size=1920,1080");
        assertThat(arguments).doesNotContain("--headless", "--headless=new", "--headless=old");
    }
}
