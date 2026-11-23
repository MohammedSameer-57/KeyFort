package org.keyfort.demosite.repository;

import org.keyfort.demosite.entity.GuestSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GuestSessionRepository extends JpaRepository<GuestSessionEntity, String> {
    List<GuestSessionEntity> findByTicketId(String ticketId);
}
