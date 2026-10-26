package com.devchesterunited.devchesterunitedv2.scraping.estadisticas;

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
        DriverResource resource = null;
        try (DriverResource candidate = new DriverResource(
                Objects.requireNonNull(factory.create(), "La fabrica no creo un WebDriver."))) {
            resource = candidate;
            WebDriver driver = candidate.driver();
            factory.configure(driver);
            return candidate.transferToSession();
        } catch (RuntimeException failure) {
            if (resource != null) {
                resource.agregarFalloDeCierre(failure);
            }
            throw failure;
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

    private static final class DriverResource implements AutoCloseable {
        private final WebDriver driver;
        private RuntimeException closeFailure;
        private boolean transferred;

        private DriverResource(WebDriver driver) {
            this.driver = driver;
        }

        private WebDriver driver() {
            return driver;
        }

        private WebDriverSession transferToSession() {
            WebDriverSession session = new WebDriverSession(driver);
            transferred = true;
            return session;
        }

        @Override
        public void close() {
            if (!transferred) {
                try {
                    driver.quit();
                } catch (RuntimeException failure) {
                    closeFailure = failure;
                }
            }
        }

        private void agregarFalloDeCierre(RuntimeException failure) {
            if (closeFailure != null) {
                failure.addSuppressed(closeFailure);
            }
        }
    }
}
