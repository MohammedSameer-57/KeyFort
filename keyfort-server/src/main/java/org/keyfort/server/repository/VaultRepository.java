package org.keyfort.server.repository;

import org.keyfort.server.entity.VaultEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VaultRepository extends JpaRepository<VaultEntity, String> {
}
