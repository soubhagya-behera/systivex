package com.soubhagya.systivex.twin.repository;

import com.soubhagya.systivex.twin.model.SystemEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemEntityRepository extends JpaRepository<SystemEntity, UUID> {

    Optional<SystemEntity> findByExternalRef(String externalRef);
}
