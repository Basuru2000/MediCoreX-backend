package com.medicorex.service.expiry;

import com.medicorex.dto.ExpiryAlertConfigCreateDTO;
import com.medicorex.dto.ExpiryAlertConfigDTO;
import com.medicorex.entity.ExpiryAlert;
import com.medicorex.entity.ExpiryAlertConfig;
import com.medicorex.entity.Product;
import com.medicorex.exception.DuplicateAlertConfigException;
import com.medicorex.exception.ResourceNotFoundException;
import com.medicorex.repository.ExpiryAlertConfigRepository;
import com.medicorex.repository.ExpiryAlertRepository;
import com.medicorex.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ExpiryAlertConfigService {

    private final ExpiryAlertConfigRepository configRepository;
    private final ExpiryAlertRepository alertRepository;
    private final ProductRepository productRepository;

    /**
     * Get all alert configurations
     */
    @Transactional(readOnly = true)
    public List<ExpiryAlertConfigDTO> getAllConfigurations() {
        return configRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get active alert configurations ordered by sort order
     */
    @Transactional(readOnly = true)
    public List<ExpiryAlertConfigDTO> getActiveConfigurations() {
        return configRepository.findByActiveTrueOrderBySortOrderAsc().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get configuration by ID
     */
    @Transactional(readOnly = true)
    public ExpiryAlertConfigDTO getConfigurationById(Long id) {
        ExpiryAlertConfig config = configRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Alert configuration", "id", id));
        return convertToDTO(config);
    }

    /**
     * Get configurations for a specific role
     */
    @Transactional(readOnly = true)
    public List<ExpiryAlertConfigDTO> getConfigurationsForRole(String role) {
        return configRepository.findActiveConfigsForRole(role).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Create new alert configuration
     */
    public ExpiryAlertConfigDTO createConfiguration(ExpiryAlertConfigCreateDTO dto) {
        // Check if configuration with same days already exists
        if (configRepository.existsByDaysBeforeExpiry(dto.getDaysBeforeExpiry())) {
            throw new DuplicateAlertConfigException(
                    "Alert configuration for " + dto.getDaysBeforeExpiry() + " days already exists"
            );
        }

        ExpiryAlertConfig config = new ExpiryAlertConfig();
        updateConfigFromDTO(config, dto);

        // Set sort order if not provided
        if (config.getSortOrder() == null || config.getSortOrder() == 0) {
            Integer maxOrder = configRepository.findMaxSortOrder();
            config.setSortOrder(maxOrder != null ? maxOrder + 1 : 1);
        }

        ExpiryAlertConfig savedConfig = configRepository.save(config);
        log.info("Created new expiry alert configuration: {} for {} days",
                savedConfig.getTierName(), savedConfig.getDaysBeforeExpiry());

        return convertToDTO(savedConfig);
    }

    /**
     * Update existing alert configuration
     */
    public ExpiryAlertConfigDTO updateConfiguration(Long id, ExpiryAlertConfigCreateDTO dto) {
        ExpiryAlertConfig config = configRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Alert configuration", "id", id));

        // Check if changing days conflicts with another config
        if (!config.getDaysBeforeExpiry().equals(dto.getDaysBeforeExpiry())) {
            if (configRepository.existsByDaysBeforeExpiry(dto.getDaysBeforeExpiry())) {
                throw new DuplicateAlertConfigException(
                        "Alert configuration for " + dto.getDaysBeforeExpiry() + " days already exists"
                );
            }
        }

        updateConfigFromDTO(config, dto);
        ExpiryAlertConfig updatedConfig = configRepository.save(config);

        log.info("Updated expiry alert configuration: {} for {} days",
                updatedConfig.getTierName(), updatedConfig.getDaysBeforeExpiry());

        return convertToDTO(updatedConfig);
    }

    /**
     * Delete alert configuration
     */
    public void deleteConfiguration(Long id) {
        ExpiryAlertConfig config = configRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Alert configuration", "id", id));

        // Check if there are any alerts using this configuration
        Long alertCount = alertRepository.countByStatus(null); // We'll need to add a method to count by config
        if (alertCount > 0) {
            throw new IllegalStateException(
                    "Cannot delete configuration with existing alerts. Please resolve all alerts first."
            );
        }

        configRepository.deleteById(id);
        log.info("Deleted expiry alert configuration: {}", config.getTierName());
    }

    /**
     * Toggle configuration active status
     */
    public ExpiryAlertConfigDTO toggleConfigurationStatus(Long id) {
        ExpiryAlertConfig config = configRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Alert configuration", "id", id));

        config.setActive(!config.getActive());
        ExpiryAlertConfig updatedConfig = configRepository.save(config);

        log.info("Toggled expiry alert configuration '{}' to: {}",
                config.getTierName(), config.getActive() ? "ACTIVE" : "INACTIVE");

        return convertToDTO(updatedConfig);
    }

    /**
     * Update configuration sort order (for reordering)
     */
    public void updateSortOrders(List<Long> configIds) {
        int order = 1;
        for (Long configId : configIds) {
            ExpiryAlertConfig config = configRepository.findById(configId)
                    .orElseThrow(() -> new ResourceNotFoundException("Alert configuration", "id", configId));
            config.setSortOrder(order++);
            configRepository.save(config);
        }
        log.info("Updated sort order for {} configurations", configIds.size());
    }

    /**
     * Get affected product count for a configuration
     */
    @Transactional(readOnly = true)
    public Long getAffectedProductCount(Long configId) {
        ExpiryAlertConfig config = configRepository.findById(configId)
                .orElseThrow(() -> new ResourceNotFoundException("Alert configuration", "id", configId));

        LocalDate thresholdDate = LocalDate.now().plusDays(config.getDaysBeforeExpiry());

        return productRepository.findAll().stream()
                .filter(product -> product.getExpiryDate() != null &&
                        product.getExpiryDate().isBefore(thresholdDate) &&
                        product.getExpiryDate().isAfter(LocalDate.now()))
                .count();
    }

    // Helper methods

    private void updateConfigFromDTO(ExpiryAlertConfig config, ExpiryAlertConfigCreateDTO dto) {
        config.setTierName(dto.getTierName());
        config.setDaysBeforeExpiry(dto.getDaysBeforeExpiry());
        config.setSeverity(dto.getSeverity());
        config.setDescription(dto.getDescription());
        config.setActive(dto.getActive() != null ? dto.getActive() : true);
        config.setNotifyRoles(String.join(",", dto.getNotifyRoles()));
        config.setColorCode(dto.getColorCode());

        if (dto.getSortOrder() != null) {
            config.setSortOrder(dto.getSortOrder());
        }
    }

    private ExpiryAlertConfigDTO convertToDTO(ExpiryAlertConfig config) {
        // Get alert counts
        Long activeAlertCount = alertRepository.countByStatus(ExpiryAlert.AlertStatus.PENDING);
        Long affectedProductCount = getAffectedProductCount(config.getId());

        return ExpiryAlertConfigDTO.builder()
                .id(config.getId())
                .tierName(config.getTierName())
                .daysBeforeExpiry(config.getDaysBeforeExpiry())
                .severity(config.getSeverity())
                .description(config.getDescription())
                .active(config.getActive())
                .notifyRoles(Arrays.asList(config.getNotifyRoles().split(",")))
                .colorCode(config.getColorCode())
                .sortOrder(config.getSortOrder())
                .createdAt(config.getCreatedAt())
                .updatedAt(config.getUpdatedAt())
                .activeAlertCount(activeAlertCount)
                .affectedProductCount(affectedProductCount)
                .build();
    }
}