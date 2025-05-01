package com.jv.roadstatus.service;

import com.jv.roadstatus.domain.RoadStatus;
import com.jv.roadstatus.domain.WaterLevel;
import com.jv.roadstatus.repository.RoadStatusRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoadStatusService {

    private final RoadStatusRepository roadStatusRepository;
    private final WaterLevelService waterLevelService;
    private final BikeRouteService bikeRouteService;

    // Default water level threshold (in meters) above which the road is considered unusable
    private static final BigDecimal DEFAULT_WATER_LEVEL_THRESHOLD = new BigDecimal("5.0");

    /**
     * Get the default water level threshold
     * @return The threshold value in meters
     */
    public BigDecimal getWaterLevelThreshold() {
        return DEFAULT_WATER_LEVEL_THRESHOLD;
    }

    /**
     * Get the latest road status based on forecast
     * 
     * According to the requirements, we prioritize the forecast over the current status
     */
    public RoadStatus getLatestRoadStatus() {
        // Get the forecast
        boolean isOpen = predictRoadUsability();

        // Get the latest timestamp from the database or use current time
        LocalDateTime timestamp = roadStatusRepository.findFirstByOrderByTimestampDesc()
                .map(RoadStatus::getTimestamp)
                .orElse(LocalDateTime.now());

        // Create a status object with the forecast result
        RoadStatus status = new RoadStatus();
        status.setOpen(isOpen);
        status.setTimestamp(timestamp);

        return status;
    }

    /**
     * Save a new road status report from a cyclist
     */
    public RoadStatus saveRoadStatus(boolean isOpen) {
        RoadStatus roadStatus = new RoadStatus();
        roadStatus.setOpen(isOpen);
        roadStatus.setTimestamp(LocalDateTime.now());
        return roadStatusRepository.save(roadStatus);
    }

    /**
     * Predict if the road will be usable based primarily on user reports,
     * with water level data as a secondary factor
     */
    public boolean predictRoadUsability() {
        // Get recent road status reports - these are our primary source of truth
        List<RoadStatus> recentStatuses = roadStatusRepository.findTop10ByOrderByTimestampDesc();

        // If we have recent reports, prioritize them
        if (!recentStatuses.isEmpty()) {
            // Count how many recent reports indicate the road is open
            long openCount = recentStatuses.stream().filter(RoadStatus::isOpen).count();
            boolean isOpenBasedOnReports = openCount > recentStatuses.size() / 2;

            log.info("Road status prediction based on {} user reports: {}", 
                    recentStatuses.size(), isOpenBasedOnReports ? "OPEN" : "CLOSED");

            // If we have a good number of recent reports, trust them completely
            if (recentStatuses.size() >= 3) {
                return isOpenBasedOnReports;
            }

            // Otherwise, combine with water level data
            WaterLevel currentWaterLevel = waterLevelService.findCurrentWaterLevel();
            if (currentWaterLevel != null) {
                // Get preliminary status based on water level
                boolean isOpenBasedOnWaterLevel = bikeRouteService.isBikeRouteOpen(currentWaterLevel.getCurrentWaterLevel());

                // If user reports and water level agree, use that result
                if (isOpenBasedOnReports == isOpenBasedOnWaterLevel) {
                    return isOpenBasedOnReports;
                } else {
                    // When they disagree, still prioritize user reports but with less confidence
                    // The more recent reports we have, the more we trust them
                    return isOpenBasedOnReports;
                }
            }

            // If no water level data, trust the user reports
            return isOpenBasedOnReports;
        }

        // If no recent reports, fall back to water level data
        WaterLevel currentWaterLevel = waterLevelService.findCurrentWaterLevel();
        if (currentWaterLevel == null) {
            // If no water level data either, assume road is usable
            log.info("No water level data or user reports available. Assuming road is usable.");
            return true;
        }

        // Use bike route preliminary check based on water level
        boolean isOpenBasedOnWaterLevel = bikeRouteService.isBikeRouteOpen(currentWaterLevel.getCurrentWaterLevel());
        log.info("Road status prediction based on water level only: {}", 
                isOpenBasedOnWaterLevel ? "LIKELY OPEN" : "LIKELY CLOSED");

        return isOpenBasedOnWaterLevel;
    }

    /**
     * Calculate the probability of the forecast being accurate
     * @return Probability as a percentage (0-100)
     */
    public int calculateForecastProbability() {
        // Get current water level
        WaterLevel currentWaterLevel = waterLevelService.findCurrentWaterLevel();
        if (currentWaterLevel == null) {
            // If no water level data, low confidence
            return 50;
        }

        // Get recent road status reports
        List<RoadStatus> recentStatuses = roadStatusRepository.findTop10ByOrderByTimestampDesc();

        // If we have no recent reports, base probability on water level only
        if (recentStatuses.isEmpty()) {
            // If water level is far from threshold, higher confidence
            BigDecimal difference = currentWaterLevel.getCurrentWaterLevel().subtract(DEFAULT_WATER_LEVEL_THRESHOLD).abs();
            if (difference.compareTo(new BigDecimal("1.0")) > 0) {
                return 80; // Water level is significantly different from threshold
            } else {
                return 60; // Water level is close to threshold
            }
        }

        // Count how many recent reports are consistent with each other
        boolean majorityIsOpen = recentStatuses.stream().filter(RoadStatus::isOpen).count() > recentStatuses.size() / 2;
        long consistentCount = majorityIsOpen ? 
                recentStatuses.stream().filter(RoadStatus::isOpen).count() : 
                recentStatuses.stream().filter(status -> !status.isOpen()).count();

        // Calculate consistency percentage
        BigDecimal consistencyPercentage = new BigDecimal(consistentCount)
                .divide(new BigDecimal(recentStatuses.size()), 2, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));

        // Adjust based on water level
        boolean waterLevelPrediction = currentWaterLevel.getCurrentWaterLevel().compareTo(DEFAULT_WATER_LEVEL_THRESHOLD) <= 0;
        if (waterLevelPrediction == majorityIsOpen) {
            // Water level prediction agrees with majority of reports, higher confidence
            return Math.min(consistencyPercentage.add(new BigDecimal("10")).intValue(), 100);
        } else {
            // Water level prediction disagrees with majority of reports, lower confidence
            return Math.max(consistencyPercentage.subtract(new BigDecimal("10")).intValue(), 50);
        }
    }

    /**
     * Get recent road status reports
     * @return List of recent road status reports
     */
    public List<RoadStatus> getRecentRoadStatuses() {
        return roadStatusRepository.findTop10ByOrderByTimestampDesc();
    }
}
