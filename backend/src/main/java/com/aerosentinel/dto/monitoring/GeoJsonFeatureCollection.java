package com.aerosentinel.dto.monitoring;

import java.util.List;
import java.util.Map;

public record GeoJsonFeatureCollection(
        String type,
        List<Feature> features
) {
    public GeoJsonFeatureCollection(List<Feature> features) {
        this("FeatureCollection", features);
    }

    public record Feature(
            String type,
            Geometry geometry,
            Map<String, Object> properties
    ) {
        public Feature(Geometry geometry, Map<String, Object> properties) {
            this("Feature", geometry, properties);
        }
    }

    public record Geometry(
            String type,
            List<List<List<Double>>> coordinates // Polygon: array of linear ring coordinate arrays [lon, lat]
    ) {
        public Geometry(List<List<List<Double>>> coordinates) {
            this("Polygon", coordinates);
        }
    }
}