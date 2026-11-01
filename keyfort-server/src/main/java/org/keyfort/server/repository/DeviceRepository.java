package org.keyfort.server.repository;

import org.keyfort.server.entity.DeviceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceRepository extends JpaRepository<DeviceEntity, String> {
    Optional<DeviceEntity> findByUserIdAndDeviceId(String userId, String deviceId);
    List<DeviceEntity> findByUserId(String userId);
}
