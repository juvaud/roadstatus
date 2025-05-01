package com.jv.roadstatus.service;

import com.jv.roadstatus.domain.WaterLevel;
import com.jv.roadstatus.repository.WaterLevelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WaterLevelService {

    private final WaterLevelRepository waterLevelRepository;
    private final RestTemplate restTemplate;

    @Value("${water.level.api.url}")
    private String waterLevelApiUrl;

    // Method to retrieve the current water level from the database
    public WaterLevel findCurrentWaterLevel() {
        // Fetch the latest water level entry from the database using the new method
        Optional<WaterLevel> currentWaterLevelOpt = waterLevelRepository.findFirstByOrderByCreatedAtDesc();

        if (currentWaterLevelOpt.isPresent()) {
            WaterLevel currentWaterLevel = currentWaterLevelOpt.get();
            log.info("Fetched current water level: {}", currentWaterLevel.getCurrentWaterLevel());
            return currentWaterLevel;
        } else {
            log.warn("No current water level data found in the database.");
            return null; // or handle accordingly
        }
    }

    // Method to save a water level to the database
    public WaterLevel saveWaterLevel(WaterLevel waterLevel) {
        return waterLevelRepository.save(waterLevel);
    }

    // Method to fetch water level data from the API and save it to the database
    public WaterLevel fetchAndSaveWaterLevel() {
        try {
            log.info("Fetching water level data from API: {}", waterLevelApiUrl);
            ResponseEntity<Map> response = restTemplate.getForEntity(waterLevelApiUrl, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                log.debug("Response body keys: {}", responseBody.keySet());

                // Extract the water level value from the response
                // The water level data is under the "W" key
                if (responseBody.containsKey("W")) {
                    Map<String, Object> wData = (Map<String, Object>) responseBody.get("W");
                    log.debug("W data keys: {}", wData.keySet());

                    // The current water level is in the "yLast" field
                    if (wData.containsKey("yLast")) {
                        Object yLast = wData.get("yLast");
                        log.info("Found current water level (yLast): {}", yLast);

                        if (yLast != null) {
                            try {
                                BigDecimal waterLevelValue = new BigDecimal(yLast.toString());

                                // Convert from centimeters to meters (divide by 100)
                                waterLevelValue = waterLevelValue.divide(new BigDecimal("100"));

                                // Create and save a new WaterLevel entity
                                WaterLevel waterLevel = new WaterLevel(waterLevelValue);
                                WaterLevel savedWaterLevel = saveWaterLevel(waterLevel);

                                log.info("Saved new water level: {}", savedWaterLevel.getCurrentWaterLevel());
                                return savedWaterLevel;
                            } catch (NumberFormatException e) {
                                log.error("Failed to convert water level to BigDecimal: {}", e.getMessage());
                            }
                        }
                    } else {
                        log.warn("No 'yLast' field found in W data");
                    }
                } else {
                    log.warn("No 'W' key found in response body");
                }
                log.warn("Could not extract water level value from API response");
            } else {
                log.error("Failed to fetch water level data: {}", response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Error fetching water level data", e);
        }
        return null;
    }

    // Schedule the fetch operation to run every hour
    @Scheduled(fixedRate = 3600000) // 1 hour in milliseconds
    public void scheduledFetchWaterLevel() {
        log.info("Running scheduled water level fetch");
        fetchAndSaveWaterLevel();
    }

    /**
     * Get all historical water level data ordered by timestamp
     * @return List of water levels
     */
    public List<WaterLevel> findAllWaterLevels() {
        return waterLevelRepository.findAllByOrderByCreatedAtAsc();
    }

    /**
     * Get the most recent water levels (up to 50)
     * @return List of recent water levels
     */
    public List<WaterLevel> findRecentWaterLevels() {
        return waterLevelRepository.findTop50ByOrderByCreatedAtDesc();
    }
}
