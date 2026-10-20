package com.devchesterunited.devchesterunitedv2.service.config;

import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.EstrategiaGeneralV1;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.EstrategiaPosicionalV1;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.EstrategiaValorizacion;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ValorizacionInicialConfig {
    public EstrategiasIniciales estrategiasIniciales(
            java.math.BigDecimal generalValorBase, java.math.BigDecimal generalFactorEscala,
            java.math.BigDecimal positionalValorBase, java.math.BigDecimal positionalFactorEscala) {
        return estrategiasIniciales(generalValorBase, generalFactorEscala, positionalValorBase,
                positionalFactorEscala, new java.math.BigDecimal("1.15"), new java.math.BigDecimal("1.05"),
                new java.math.BigDecimal("0.95"), new java.math.BigDecimal("0.90"));
    }

    @Bean
    public EstrategiasIniciales estrategiasIniciales(
            @Value("${valorizacion.general.valor-base}") java.math.BigDecimal generalValorBase,
            @Value("${valorizacion.general.factor-escala}") java.math.BigDecimal generalFactorEscala,
            @Value("${valorizacion.positional.valor-base}") java.math.BigDecimal positionalValorBase,
            @Value("${valorizacion.positional.factor-escala}") java.math.BigDecimal positionalFactorEscala,
            @Value("${valorizacion.positional.factor-mercado.dl:1.15}") java.math.BigDecimal factorDl,
            @Value("${valorizacion.positional.factor-mercado.mc:1.05}") java.math.BigDecimal factorMc,
            @Value("${valorizacion.positional.factor-mercado.df:0.95}") java.math.BigDecimal factorDf,
            @Value("${valorizacion.positional.factor-mercado.po:0.90}") java.math.BigDecimal factorPo) {
        List<EstrategiaValorizacion> habilitadas = List.of(
                new EstrategiaGeneralV1(generalValorBase, generalFactorEscala),
                new EstrategiaPosicionalV1(positionalValorBase, positionalFactorEscala,
                        Map.of("DL", factorDl, "MC", factorMc, "DF", factorDf, "PO", factorPo)));
        return new EstrategiasIniciales(habilitadas, "POSITIONAL", 1);
    }
}
