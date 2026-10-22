package com.aerosentinel.assistant.tools;

import com.aerosentinel.assistant.dto.LocationDto;
import com.aerosentinel.assistant.warning.OfficialWarningClient;
import org.springframework.stereotype.Component;

@Component
public class OfficialWarningTool {

    private final OfficialWarningClient warningClient;

    public OfficialWarningTool(OfficialWarningClient warningClient) {
        this.warningClient = warningClient;
    }

    /**
     * Tool 3: getOfficialWarnings(location)
     * Fetches warnings exclusively from verified official government sources (IMD / NDMA).
     * Never merges AeroSentinel model risk into an official warning.
     */
    public OfficialWarningToolResult getOfficialWarnings(LocationDto location) {
        String locName = (location != null && location.cityName() != null)
                ? location.cityName().trim() : "Requested Area";
        Double lat = (location != null) ? location.latitude() : null;
        Double lon = (location != null) ? location.longitude() : null;

        return warningClient.fetchWarningsForLocation(locName, lat, lon);
    }
}
