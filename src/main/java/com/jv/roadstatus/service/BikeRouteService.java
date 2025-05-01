package com.jv.roadstatus.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Service for monitoring the bike lanes along the Mosel between Trier and Wasserbillig
 * and determining if they are open based on the current water level and user reports
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BikeRouteService {

    /**
     * Check if the bike lanes along the Mosel between Trier and Wasserbillig are open
     * based on the current water level. This is a preliminary check that should be
     * combined with user reports for more accurate status determination.
     * 
     * @param waterLevel Current water level in meters
     * @return true if the bike lanes are likely open, false otherwise
     */
    public boolean isBikeRouteOpen(BigDecimal waterLevel) {
        // This is a preliminary check based on water level only
        // The actual status should be determined by combining this with user reports
        // The "meldehöhe" (reporting height) should be used as a reference point
        // when available, rather than a fixed threshold

        // For backward compatibility, we use a default threshold
        // but this should be refined based on user reports
        BigDecimal preliminaryThreshold = new BigDecimal("5.0");
        boolean isLikelyOpen = waterLevel.compareTo(preliminaryThreshold) <= 0;

        log.info("Mosel bike lanes preliminary status check: {}, water level: {}m", 
                isLikelyOpen ? "LIKELY OPEN" : "LIKELY CLOSED", waterLevel);

        return isLikelyOpen;
    }
}
