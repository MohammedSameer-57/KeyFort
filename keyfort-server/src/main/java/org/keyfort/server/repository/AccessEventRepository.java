package org.keyfort.server.repository;

import org.keyfort.server.entity.AccessEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface AccessEventRepository extends JpaRepository<AccessEventEntity, Long> {
    List<AccessEventEntity> findByUserIdAndAtAfterOrderByAtAsc(String userId, Instant cutoff);
}
