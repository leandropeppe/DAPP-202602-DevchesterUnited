package com.devchesterunited.devchesterunitedv2.persistence;

import com.devchesterunited.devchesterunitedv2.persistence.dto.EjecucionValorizacionEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EjecucionValorizacionRepository extends JpaRepository<EjecucionValorizacionEntity, UUID> { }
