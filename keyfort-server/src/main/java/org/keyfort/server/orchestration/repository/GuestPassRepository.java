package org.keyfort.server.orchestration.repository;

import org.keyfort.core.guestpass.GuestPassState;
import org.keyfort.server.orchestration.entity.GuestPassEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface GuestPassRepository extends JpaRepository<GuestPassEntity, String> {
    Optional<GuestPassEntity> findByCredentialRef(String credentialRef);
    List<GuestPassEntity> findByState(GuestPassState state);
    List<GuestPassEntity> findByExpiresAtBeforeAndStateNotIn(Instant now, Collection<GuestPassState> states);
    List<GuestPassEntity> findByOwnerUserId(String ownerUserId);
}
