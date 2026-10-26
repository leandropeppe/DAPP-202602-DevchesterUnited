package com.devchesterunited.devchesterunitedv2.unit.scraping;

import com.devchesterunited.devchesterunitedv2.scraping.estadisticas.WebDriverFactory;
import com.devchesterunited.devchesterunitedv2.scraping.estadisticas.WebDriverSession;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebDriverSessionTest {
    @Test
    void creaUnaInstanciaLaReutilizaYCierraUnaSolaVez() {
        WebDriver driver = mock(WebDriver.class);
        var factory = new RecordingFactory(driver);

        try (var session = WebDriverSession.open(factory)) {
            assertThat(session.driver()).isSameAs(driver);
            assertThat(session.driver()).isSameAs(driver);
        }
        verify(driver).quit();
        verifyNoMoreInteractions(driver);
        assertThat(factory.creations).isEqualTo(1);
    }

    @Test
    void cierraElDriverSiFallaLaConfiguracionDespuesDeCrearlo() {
        WebDriver driver = mock(WebDriver.class);
        var factory = new RecordingFactory(driver);
        factory.failure = new IllegalStateException("fallo de configuracion");

        assertThatThrownBy(() -> WebDriverSession.open(factory))
                .isSameAs(factory.failure);

        verify(driver).quit();
        verifyNoMoreInteractions(driver);
        assertThat(factory.creations).isEqualTo(1);
    }

    @Test
    void noPermiteAccederAlDriverLuegoDelCierre() {
        WebDriver driver = mock(WebDriver.class);
        var session = WebDriverSession.open(new RecordingFactory(driver));
        session.close();
        session.close();

        assertThatThrownBy(session::driver)
                .isInstanceOf(IllegalStateException.class);
        verify(driver).quit();
        verifyNoMoreInteractions(driver);
    }

    private static final class RecordingFactory implements WebDriverFactory {
        private final WebDriver driver;
        private int creations;
        private RuntimeException failure;

        private RecordingFactory(WebDriver driver) {
            this.driver = driver;
        }

        @Override
        public WebDriver create() {
            creations++;
            return driver;
        }

        @Override
        public void configure(WebDriver driver) {
            if (failure != null) {
                throw failure;
            }
        }
    }
}
