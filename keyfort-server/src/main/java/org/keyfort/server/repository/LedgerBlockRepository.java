package org.keyfort.server.repository;

import org.keyfort.server.entity.LedgerBlockEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LedgerBlockRepository extends JpaRepository<LedgerBlockEntity, Long> {
    List<LedgerBlockEntity> findAllByOrderBySeqAsc();
    Optional<LedgerBlockEntity> findTopByOrderBySeqDesc();
}
