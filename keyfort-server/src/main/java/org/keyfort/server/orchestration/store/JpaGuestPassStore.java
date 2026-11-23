package org.keyfort.server.orchestration.store;

import org.keyfort.core.guestpass.GuestPass;
import org.keyfort.core.guestpass.GuestPassStore;
import org.keyfort.server.orchestration.entity.GuestPassEntity;
import org.keyfort.server.orchestration.repository.GuestPassRepository;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
public class JpaGuestPassStore implements GuestPassStore {

    private final GuestPassRepository repository;

    public JpaGuestPassStore(GuestPassRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<GuestPass> findById(String id) {
        return repository.findById(id).map(GuestPassEntity::toDomain);
    }

    @Override
    @Transactional
    public void save(GuestPass pass) {
        String owner = repository.findById(pass.id()).map(GuestPassEntity::getOwnerUserId).orElse(null);
        GuestPassEntity entity = GuestPassEntity.fromDomain(pass, owner);
        repository.save(entity);
    }

    @Override
    @Transactional
    public boolean compareAndSet(String id, long expectedVersion, GuestPass newPass) {
        Optional<GuestPassEntity> existingOpt = repository.findById(id);
        if (existingOpt.isEmpty()) {
            return false;
        }

        GuestPassEntity entity = existingOpt.get();
        if (entity.getVersion() != expectedVersion) {
            return false;
        }

        try {
            entity.setState(newPass.state());
            entity.setCredentialRef(newPass.credentialRef());
            repository.saveAndFlush(entity);
            return true;
        } catch (OptimisticLockingFailureException e) {
            return false;
        }
    }
}
