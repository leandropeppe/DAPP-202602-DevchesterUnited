package com.devchesterunited.test.observability;

import org.springframework.boot.actuate.autoconfigure.web.ManagementContextConfiguration;
import org.springframework.boot.actuate.autoconfigure.web.ManagementContextType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

@ManagementContextConfiguration(value = ManagementContextType.CHILD, proxyBeanMethods = false)
@ConditionalOnBean(RequestServletContextProbe.class)
public class ManagementServletContextProbeConfiguration {

    @Bean
    FilterRegistrationBean<RequestServletContextProbeFilter> managementRequestServletContextProbeFilter(
            RequestServletContextProbe probe) {
        FilterRegistrationBean<RequestServletContextProbeFilter> registration = new FilterRegistrationBean<>(
                new RequestServletContextProbeFilter(probe));
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
