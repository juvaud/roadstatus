package com.jv.roadstatus.controller;

import com.jv.roadstatus.domain.RoadStatus;
import com.jv.roadstatus.domain.WaterLevel;
import com.jv.roadstatus.service.BikeRouteService;
import com.jv.roadstatus.service.RoadStatusService;
import com.jv.roadstatus.service.WaterLevelService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequestMapping("/")
@RequiredArgsConstructor
public class WaterLevelController {

    private final WaterLevelService waterLevelService;
    private final RoadStatusService roadStatusService;
    private final BikeRouteService bikeRouteService;

    // Initialize by fetching water level data when the application starts
    @PostConstruct
    public void init() {
        log.info("Initializing WaterLevelController - fetching initial water level data");
        waterLevelService.fetchAndSaveWaterLevel();
    }

    // Main page that displays water level and road status forecast information
    @GetMapping
    public String getIndexPage(Model model) {
        // Get the current water level from the database
        WaterLevel currentWaterLevel = waterLevelService.findCurrentWaterLevel();

        // If no water level data exists, fetch it from the API
        if (currentWaterLevel == null) {
            currentWaterLevel = waterLevelService.fetchAndSaveWaterLevel();
        }

        if (currentWaterLevel != null) {
            model.addAttribute("waterLevel", currentWaterLevel.getCurrentWaterLevel());
            model.addAttribute("timestamp", currentWaterLevel.getCreatedAt());

            // Check if the bike route is open based on the water level
            boolean isBikeRouteOpen = bikeRouteService.isBikeRouteOpen(currentWaterLevel.getCurrentWaterLevel());
            model.addAttribute("isBikeRouteOpen", isBikeRouteOpen);
        } else {
            model.addAttribute("waterLevel", "Keine Daten verfügbar");
        }

        // Get road status forecast information
        RoadStatus forecastStatus = roadStatusService.getLatestRoadStatus();
        model.addAttribute("isOpen", forecastStatus.isOpen());
        model.addAttribute("lastUpdated", forecastStatus.getTimestamp());

        // Get forecast probability
        int probability = roadStatusService.calculateForecastProbability();
        model.addAttribute("probability", probability);

        // Get recent road status reports for statistics
        List<RoadStatus> recentStatuses = roadStatusService.getRecentRoadStatuses();
        model.addAttribute("recentStatuses", recentStatuses);

        return "index";
    }

    /**
     * Handle form submission for road status update
     */
    @PostMapping("/road-status-update")
    public String updateRoadStatus(@RequestParam("isOpen") boolean isOpen, Model model) {
        log.info("Received road status update: isOpen={}", isOpen);

        // Save the new road status
        RoadStatus savedStatus = roadStatusService.saveRoadStatus(isOpen);

        // Add attributes for the response
        model.addAttribute("message", "Vielen Dank für Ihre Eingabe! Der Radwegstatus wurde aktualisiert.");

        // Redirect to the main page to show all information
        return "redirect:/";
    }

    // Endpoint to manually refresh water level data
    @GetMapping("/api/water-level/refresh")
    public String refreshWaterLevel(Model model) {
        WaterLevel waterLevel = waterLevelService.fetchAndSaveWaterLevel();
        if (waterLevel != null) {
            model.addAttribute("message", "Wasserstandsdaten erfolgreich aktualisiert");
        } else {
            model.addAttribute("message", "Aktualisierung der Wasserstandsdaten fehlgeschlagen");
        }
        return "redirect:/";
    }

    /**
     * API endpoint to get the current road status and prediction
     */
    @GetMapping("/api/road-status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getRoadStatusApi() {
        RoadStatus latestStatus = roadStatusService.getLatestRoadStatus();
        boolean prediction = roadStatusService.predictRoadUsability();
        int probability = roadStatusService.calculateForecastProbability();

        return ResponseEntity.ok(Map.of(
            "isOpen", latestStatus.isOpen(),
            "lastUpdated", latestStatus.getTimestamp(),
            "prediction", prediction,
            "probability", probability
        ));
    }

    /**
     * API endpoint to get historical water level and road status data for the graph
     */
    @GetMapping("/api/graph-data")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getGraphData() {
        // Get historical water level data
        List<WaterLevel> waterLevels = waterLevelService.findRecentWaterLevels();

        // Get recent road status reports
        List<RoadStatus> roadStatuses = roadStatusService.getRecentRoadStatuses();

        // Format the data for the graph
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        // Water level data for the graph
        List<Map<String, Object>> waterLevelData = waterLevels.stream()
            .map(wl -> {
                Map<String, Object> data = new HashMap<>();
                data.put("timestamp", wl.getCreatedAt().format(formatter));
                data.put("value", wl.getCurrentWaterLevel());
                return data;
            })
            .collect(Collectors.toList());

        // Road status data for the graph
        List<Map<String, Object>> roadStatusData = roadStatuses.stream()
            .map(rs -> {
                Map<String, Object> data = new HashMap<>();
                data.put("timestamp", rs.getTimestamp().format(formatter));
                data.put("isOpen", rs.isOpen());
                return data;
            })
            .collect(Collectors.toList());

        // Combine the data
        Map<String, Object> result = new HashMap<>();
        result.put("waterLevels", waterLevelData);
        result.put("roadStatuses", roadStatusData);
        result.put("threshold", roadStatusService.getWaterLevelThreshold());

        return ResponseEntity.ok(result);
    }
}
