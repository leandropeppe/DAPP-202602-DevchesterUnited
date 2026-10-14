package com.devchesterunited.devchesterunitedv2.adapter;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.openqa.selenium.WebDriver;

/** Una unica sesion WebDriver reutilizable durante una sincronizacion completa. */
public final class WebDriverSession implements AutoCloseable {
    private final WebDriver driver;
    private final AtomicBoolean closed = new AtomicBoolean();

    private WebDriverSession(WebDriver driver) {
        this.driver = Objects.requireNonNull(driver, "El WebDriver es obligatorio.");
    }

    /** Crea y configura una sesion, limpiando el driver si la configuracion falla. */
    public static WebDriverSession open(WebDriverFactory factory) {
        Objects.requireNonNull(factory, "La fabrica WebDriver es obligatoria.");
        WebDriver driver = null;
        boolean failureHandled = false;
        boolean sessionCreated = false;
        try {
            driver = Objects.requireNonNull(factory.create(), "La fabrica no creo un WebDriver.");
            factory.configure(driver);
            sessionCreated = true;
            return new WebDriverSession(driver);
        } catch (RuntimeException failure) {
            failureHandled = true;
            cerrarTrasFallo(driver, failure);
            throw failure;
        } finally {
            if (!sessionCreated && !failureHandled && driver != null) {
                try {
                    driver.quit();
                } catch (RuntimeException ignored) {
                    // Mantener el Error original como causa de la salida del metodo.
                }
            }
        }
    }

    /** Devuelve siempre la misma instancia durante el ciclo de vida de la sesion. */
    public WebDriver driver() {
        if (closed.get()) {
            throw new IllegalStateException("La sesion WebDriver ya fue cerrada.");
        }
        return driver;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            driver.quit();
        }
    }

    private static void cerrarTrasFallo(WebDriver driver, Throwable failure) {
        if (driver == null) {
            return;
        }
        try {
            driver.quit();
        } catch (RuntimeException closeFailure) {
            failure.addSuppressed(closeFailure);
        }
    }
}
