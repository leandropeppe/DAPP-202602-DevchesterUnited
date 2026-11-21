package com.devchesterunited.test.observability;

import java.io.IOException;

import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class RequestServletContextProbeFilter extends OncePerRequestFilter {

    private final RequestServletContextProbe probe;

    public RequestServletContextProbeFilter(RequestServletContextProbe probe) {
        this.probe = probe;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        probe.record(request);
        filterChain.doFilter(request, response);
    }
}
