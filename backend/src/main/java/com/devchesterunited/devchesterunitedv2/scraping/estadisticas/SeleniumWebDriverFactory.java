package com.devchesterunited.devchesterunitedv2.scraping.estadisticas;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Crea la sesion Chrome de infraestructura sin exponer Selenium al dominio. */
@Component
public class SeleniumWebDriverFactory implements WebDriverFactory {
    @Override
    public WebDriver create() {
        return new ChromeDriver(productiveOptions());
    }

    static ChromeOptions productiveOptions() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--window-size=1920,1080");
        return options;
    }

    @Override
    public void configure(WebDriver driver) {
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
        driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(30));
    }
}
