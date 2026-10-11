package com.devchesterunited.devchesterunitedv2.adapter;

import org.openqa.selenium.WebDriver;

/** Fabrica de sesiones WebDriver confinada a la infraestructura del Adapter. */
public interface WebDriverFactory {
    WebDriver create();

    /** Configura una instancia ya creada antes de entregarla a la sesion. */
    default void configure(WebDriver driver) { }
}
