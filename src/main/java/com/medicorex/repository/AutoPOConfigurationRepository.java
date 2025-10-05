package com.medicorex.repository;

import com.medicorex.entity.AutoPOConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AutoPOConfigurationRepository extends JpaRepository<AutoPOConfiguration, Long> {

    @Query("SELECT c FROM AutoPOConfiguration c ORDER BY c.id DESC LIMIT 1")
    Optional<AutoPOConfiguration> findLatestConfiguration();
}