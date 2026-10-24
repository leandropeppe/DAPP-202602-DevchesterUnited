package com.devchesterunited.devchesterunitedv2.persistence.valorizacion.repository;

import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.entity.EjecucionValorizacionEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EjecucionValorizacionRepository extends JpaRepository<EjecucionValorizacionEntity, UUID> { }
