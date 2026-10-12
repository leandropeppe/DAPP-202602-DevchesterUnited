package com.devchesterunited.devchesterunitedv2.diagnostico;

import com.devchesterunited.devchesterunitedv2.adapter.WhoScoredStatisticsConfiguration;
import com.devchesterunited.devchesterunitedv2.adapter.impl.WhoScoredStatisticsAdapter;
import com.devchesterunited.devchesterunitedv2.configuration.ApiKeyAuthenticationFilter;
import com.devchesterunited.devchesterunitedv2.persistence.UsuarioRepository;
import com.devchesterunited.devchesterunitedv2.service.impl.AutenticacionServiceImpl;
import com.devchesterunited.devchesterunitedv2.service.diagnostico.WhoscoredPlayerIdentityDiagnosticService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifica el contexto mínimo sin invocar al proveedor ni generar el diagnóstico. */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = WhoScoredPlayerIdentityDiagnosticManualConfiguration.class)
@TestPropertySource(locations = "classpath:application.properties")
class WhoScoredPlayerIdentityDiagnosticContextTest {
    @Autowired private ApplicationContext context;

    @Test
    void cargaSoloLasDependenciasDelDiagnostico() {
        assertThat(context.getBean(WhoscoredPlayerIdentityDiagnosticService.class)).isNotNull();
        assertThat(context.getBean(WhoScoredStatisticsAdapter.class)).isNotNull();
        assertThat(context.getBeansOfType(WhoScoredStatisticsConfiguration.class)).hasSize(1);
        assertThat(context.getBeansOfType(ApiKeyAuthenticationFilter.class)).isEmpty();
        assertThat(context.getBeansOfType(AutenticacionServiceImpl.class)).isEmpty();
        assertThat(context.getBeansOfType(UsuarioRepository.class)).isEmpty();
    }
}
