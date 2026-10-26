package com.devchesterunited.devchesterunitedv2.persistence.valorizacion.repository;

import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.entity.EjecucionValorizacionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EjecucionValorizacionRepository extends JpaRepository<EjecucionValorizacionEntity, UUID> { }
