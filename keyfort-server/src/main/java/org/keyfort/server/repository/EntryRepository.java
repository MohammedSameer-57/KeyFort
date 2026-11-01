package org.keyfort.server.repository;

import org.keyfort.server.entity.EntryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EntryRepository extends JpaRepository<EntryEntity, String> {
    List<EntryEntity> findByUserId(String userId);
    void deleteByUserId(String userId);
}
