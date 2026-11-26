package com.devchesterunited.devchesterunitedv2.unit.auditoria;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.devchesterunited.devchesterunitedv2.configuration.auditoria.AuditoriaEventWriter;
import com.devchesterunited.devchesterunitedv2.configuration.auditoria.AuditoriaEvento;
import com.devchesterunited.devchesterunitedv2.configuration.auditoria.CorrelacionContext;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

class AuditoriaEventWriterSecurityTest {
    private final Logger logger = (Logger) LoggerFactory.getLogger(AuditoriaEventWriter.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @AfterEach
    void cleanup() {
        appender.stop();
        logger.detachAppender(appender);
        MDC.clear();
        CorrelacionContext.clear();
    }

    @Test
    void emitsOnlyAllowlistedStructuredFieldsAndNoSensitiveMdc() {
        appender.start();
        logger.addAppender(appender);
        MDC.put("Authorization", "Bearer synthetic.jwt.secret");
        MDC.put("X-API-KEY", "synthetic-api-key");
        MDC.put("password", "synthetic-password");
        MDC.put("business.object", "personal-data-secret");
        MDC.put("exception.message", "message contains secret");
        MDC.put("stacktrace", "java.lang.RuntimeException: stack-secret");

        AuditoriaEventWriter writer = new AuditoriaEventWriter();
        writer.write(new AuditoriaEvento(AuditoriaEvento.EVENT_NAME, "market.purchase",
                AuditoriaEvento.Stage.END, AuditoriaEvento.Result.ERROR, 7L,
                Instant.parse("2026-01-01T00:00:00Z"), "request-1", null,
                "client-1", AuditoriaEvento.ContextType.HTTP, "UNEXPECTED_ERROR"));

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getFormattedMessage()).isEqualTo(
                "audit_event eventName=AUDIT_OPERATION operation=market.purchase stage=END result=ERROR "
                        + "durationMs=7 timestamp=2026-01-01T00:00:00Z contextType=HTTP requestCorrelationId=request-1 "
                        + "externalCorrelationId=client-1 errorCode=UNEXPECTED_ERROR");
        assertThat(event.getMDCPropertyMap()).containsOnlyKeys(
                "audit.eventName", "audit.operation", "audit.stage", "audit.result",
                "audit.durationMs", "audit.timestamp", "audit.contextType",
                "audit.requestCorrelationId", "audit.externalCorrelationId", "audit.errorCode");
        String rendered = event.getMDCPropertyMap().toString();
        assertThat(rendered).doesNotContain("synthetic.jwt.secret", "synthetic-api-key",
                "synthetic-password", "personal-data-secret", "message contains secret",
                "stack-secret");
        assertThat(MDC.get("Authorization")).isEqualTo("Bearer synthetic.jwt.secret");
    }

    @Test
    void doesNotEmitNullOptionalFieldsOrArbitraryEventValues() {
        appender.start();
        logger.addAppender(appender);
        new AuditoriaEventWriter().write(new AuditoriaEvento(AuditoriaEvento.EVENT_NAME,
                "market.initialize", AuditoriaEvento.Stage.START, AuditoriaEvento.Result.STARTED,
                null, Instant.parse("2026-01-01T00:00:00Z"), null, "execution-1", null,
                AuditoriaEvento.ContextType.BACKGROUND, null));

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getMDCPropertyMap().keySet())
                .containsExactlyInAnyOrder("audit.eventName", "audit.operation", "audit.stage",
                        "audit.result", "audit.timestamp", "audit.contextType",
                        "audit.executionCorrelationId");
    }
}
