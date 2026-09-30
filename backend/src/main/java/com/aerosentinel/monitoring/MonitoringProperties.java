package com.aerosentinel.monitoring;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.monitoring")
public class MonitoringProperties {

    private Double highRiskThreshold = 70.0;
    private Double mediumRiskThreshold = 40.0;
    private Double highUncertaintyThreshold = 0.25;
    private Double farStationThresholdKm = 5.0;
    private Double peripheralStationThresholdKm = 8.0;

    public Double getHighRiskThreshold() {
        return highRiskThreshold;
    }

    public void setHighRiskThreshold(Double highRiskThreshold) {
        this.highRiskThreshold = highRiskThreshold;
    }

    public Double getMediumRiskThreshold() {
        return mediumRiskThreshold;
    }

    public void setMediumRiskThreshold(Double mediumRiskThreshold) {
        this.mediumRiskThreshold = mediumRiskThreshold;
    }

    public Double getHighUncertaintyThreshold() {
        return highUncertaintyThreshold;
    }

    public void setHighUncertaintyThreshold(Double highUncertaintyThreshold) {
        this.highUncertaintyThreshold = highUncertaintyThreshold;
    }

    public Double getFarStationThresholdKm() {
        return farStationThresholdKm;
    }

    public void setFarStationThresholdKm(Double farStationThresholdKm) {
        this.farStationThresholdKm = farStationThresholdKm;
    }

    public Double getPeripheralStationThresholdKm() {
        return peripheralStationThresholdKm;
    }

    public void setPeripheralStationThresholdKm(Double peripheralStationThresholdKm) {
        this.peripheralStationThresholdKm = peripheralStationThresholdKm;
    }
}