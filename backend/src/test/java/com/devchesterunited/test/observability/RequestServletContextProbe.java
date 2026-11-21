package com.devchesterunited.test.observability;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;

public class RequestServletContextProbe {

    private final Map<Integer, Observation> observations = new ConcurrentHashMap<>();

    public void record(HttpServletRequest request) {
        ServletContext servletContext = request.getServletContext();
        WebApplicationContext webApplicationContext = WebApplicationContextUtils
                .getWebApplicationContext(servletContext);
        observations.put(request.getLocalPort(), new Observation(servletContext, webApplicationContext));
    }

    public Observation observationFor(int port) {
        Observation observation = observations.get(port);
        if (observation == null) {
            throw new AssertionError("No servlet-context observation for port " + port);
        }
        return observation;
    }

    public record Observation(ServletContext servletContext, WebApplicationContext webApplicationContext) {
    }
}
