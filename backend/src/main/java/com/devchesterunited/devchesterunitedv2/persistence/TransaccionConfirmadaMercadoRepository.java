package com.devchesterunited.devchesterunitedv2.persistence;

import com.devchesterunited.devchesterunitedv2.persistence.dto.TransaccionConfirmadaMercadoEntity;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransaccionConfirmadaMercadoRepository
        extends JpaRepository<TransaccionConfirmadaMercadoEntity, UUID>,
        JpaSpecificationExecutor<TransaccionConfirmadaMercadoEntity> {
    Optional<TransaccionConfirmadaMercadoEntity> findBySolicitudIdempotenciaId(
            UUID solicitudIdempotenciaId);

    Optional<TransaccionConfirmadaMercadoEntity> findByIdAndUsuarioId(UUID id, UUID usuarioId);

    @Query("select transaccion from TransaccionConfirmadaMercadoEntity transaccion "
            + "where transaccion.usuarioId = :usuarioId "
            + "order by transaccion.confirmadoEn desc")
    List<TransaccionConfirmadaMercadoEntity> findAllByUsuarioIdOrderByConfirmadoEnDesc(
            @Param("usuarioId") UUID usuarioId);

    @Query("select transaccion from TransaccionConfirmadaMercadoEntity transaccion "
            + "where transaccion.usuarioId = :usuarioId "
            + "and transaccion.jugadorId = :jugadorId "
            + "order by transaccion.confirmadoEn desc")
    List<TransaccionConfirmadaMercadoEntity> findAllByUsuarioIdAndJugadorIdOrderByConfirmadoEnDesc(
            @Param("usuarioId") UUID usuarioId, @Param("jugadorId") UUID jugadorId);

    default Page<TransaccionConfirmadaMercadoEntity> buscar(UUID usuarioId, UUID jugadorId,
            String tipoOperacion, Instant desde, Instant hasta, Pageable pageable) {
        Specification<TransaccionConfirmadaMercadoEntity> specification = (root, query, criteria) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteria.equal(root.get("usuarioId"), usuarioId));
            if (jugadorId != null) {
                predicates.add(criteria.equal(root.get("jugadorId"), jugadorId));
            }
            if (tipoOperacion != null) {
                predicates.add(criteria.equal(root.get("tipoOperacion"), tipoOperacion));
            }
            if (desde != null) {
                predicates.add(criteria.greaterThanOrEqualTo(root.<Instant>get("confirmadoEn"), desde));
            }
            if (hasta != null) {
                predicates.add(criteria.lessThan(root.<Instant>get("confirmadoEn"), hasta));
            }
            if (query != null && !Long.class.equals(query.getResultType())
                    && !long.class.equals(query.getResultType())) {
                query.orderBy(criteria.desc(root.get("confirmadoEn")), criteria.desc(root.get("id")));
            }
            return criteria.and(predicates.toArray(Predicate[]::new));
        };
        return findAll(specification, pageable);
    }
}
