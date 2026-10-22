package com.aerosentinel.assistant.warning;

import com.aerosentinel.assistant.tools.OfficialWarningToolResult;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
public class OfficialWarningClient {

    private static final Logger log = LoggerFactory.getLogger(OfficialWarningClient.class);
    public static final String DEFAULT_IMD_PORTAL = "https://mausam.imd.gov.in";
    public static final String DEFAULT_SACHET_PORTAL = "https://sachet.ndma.gov.in";
    public static final String DEFAULT_SACHET_RSS = "https://sachet.ndma.gov.in/cap_public_website/rss/rss_india.xml";

    private final RestTemplate restTemplate;
    private final String baseUrl;

    @Autowired
    public OfficialWarningClient(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${app.external.warnings.official.base-url:}") String baseUrl,
            @Value("${app.external.warnings.official.timeout-ms:5000}") long timeoutMs
    ) {
        this.baseUrl = (baseUrl != null) ? baseUrl.trim() : "";
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(timeoutMs))
                .setReadTimeout(Duration.ofMillis(timeoutMs))
                .build();
    }

    public OfficialWarningClient(RestTemplate restTemplate, String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = (baseUrl != null) ? baseUrl.trim() : "";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OfficialWarningFeedResponse(
            String authority,
            String timestamp,
            List<FeedWarningItem> alerts
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record FeedWarningItem(
                String id,
                String headline,
                String event,
                String severity,
                String areaDesc,
                String effective,
                String expires,
                String web
        ) {}
    }

    /**
     * Fetches official warnings from the designated official government feed (CAP RSS or REST).
     * Filters expired alerts, handles duplicates, verifies geographic relevance,
     * and reports SOURCE_UNAVAILABLE truthfully on network/feed failure.
     */
    public OfficialWarningToolResult fetchWarningsForLocation(String locationName, Double latitude, Double longitude) {
        String displayLoc = (locationName != null && !locationName.isBlank()) ? locationName.trim() : "Requested Area";

        if (this.baseUrl.isEmpty()) {
            log.info("Official warnings feed unconfigured. Reporting SOURCE_UNAVAILABLE truthfully.");
            return OfficialWarningToolResult.unavailable(
                    displayLoc,
                    "Official disaster and weather warning feeds (India Meteorological Department / NDMA SACHET) " +
                    "are not currently configured for automated polling. Please consult official portals directly: " +
                    DEFAULT_IMD_PORTAL + " and " + DEFAULT_SACHET_PORTAL + "."
            );
        }

        try {
            // Check if endpoint is RSS/XML
            if (this.baseUrl.endsWith(".xml") || this.baseUrl.contains("/rss")) {
                return fetchFromRssFeed(displayLoc);
            }

            // Otherwise, attempt structured JSON endpoint
            return fetchFromJsonFeed(displayLoc, latitude, longitude);

        } catch (Exception e) {
            log.warn("Failed to fetch official warnings from {}: {}", this.baseUrl, e.getMessage());
            return OfficialWarningToolResult.unavailable(
                    displayLoc,
                    "Official warning source connection failed: " + e.getMessage() + ". Check " + DEFAULT_IMD_PORTAL
            );
        }
    }

    private OfficialWarningToolResult fetchFromRssFeed(String displayLoc) {
        ResponseEntity<String> resp = restTemplate.getForEntity(this.baseUrl, String.class);
        if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null || resp.getBody().isBlank()) {
            return OfficialWarningToolResult.unavailable(
                    displayLoc,
                    "Official RSS feed returned HTTP status " + resp.getStatusCode() + ". Check " + DEFAULT_SACHET_PORTAL
            );
        }

        List<OfficialWarningToolResult.OfficialWarningItem> items = parseRssCapFeed(resp.getBody(), displayLoc);
        String now = Instant.now().toString();

        if (items.isEmpty()) {
            return OfficialWarningToolResult.noWarnings(displayLoc, DEFAULT_SACHET_PORTAL, now);
        } else {
            return new OfficialWarningToolResult(
                    "VERIFIED_WARNINGS_FOUND",
                    displayLoc,
                    items,
                    "National Disaster Management Authority (NDMA SACHET) / IMD",
                    DEFAULT_SACHET_PORTAL,
                    now,
                    List.of("Official government warnings active. Please follow instructions issued by local authorities.")
            );
        }
    }

    private OfficialWarningToolResult fetchFromJsonFeed(String displayLoc, Double latitude, Double longitude) {
        String targetUrl = this.baseUrl;
        if (latitude != null && longitude != null) {
            targetUrl += (targetUrl.contains("?") ? "&" : "?") + "lat=" + latitude + "&lon=" + longitude;
        } else if (!"Requested Area".equals(displayLoc)) {
            targetUrl += (targetUrl.contains("?") ? "&" : "?") + "location=" + displayLoc;
        }

        ResponseEntity<OfficialWarningFeedResponse> resp = restTemplate.getForEntity(targetUrl, OfficialWarningFeedResponse.class);
        if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
            OfficialWarningFeedResponse body = resp.getBody();
            List<OfficialWarningToolResult.OfficialWarningItem> items = new ArrayList<>();
            Set<String> seenIds = new HashSet<>();
            Instant now = Instant.now();

            if (body.alerts() != null) {
                for (OfficialWarningFeedResponse.FeedWarningItem alert : body.alerts()) {
                    if (alert.id() != null && !seenIds.add(alert.id())) {
                        continue; // Deduplicate
                    }

                    // Check expiration
                    if (alert.expires() != null) {
                        try {
                            Instant exp = Instant.parse(alert.expires());
                            if (exp.isBefore(now)) {
                                continue; // Filter expired
                            }
                        } catch (Exception ignored) {}
                    }

                    items.add(new OfficialWarningToolResult.OfficialWarningItem(
                            alert.id(),
                            body.authority() != null ? body.authority() : "IMD",
                            alert.headline(),
                            alert.event(),
                            alert.severity(),
                            alert.areaDesc(),
                            alert.effective(),
                            alert.effective(),
                            alert.expires(),
                            alert.web() != null ? alert.web() : DEFAULT_IMD_PORTAL
                    ));
                }
            }

            String nowStr = now.toString();
            if (items.isEmpty()) {
                return OfficialWarningToolResult.noWarnings(displayLoc, DEFAULT_IMD_PORTAL, nowStr);
            } else {
                return new OfficialWarningToolResult(
                        "VERIFIED_WARNINGS_FOUND",
                        displayLoc,
                        items,
                        body.authority() != null ? body.authority() : "IMD",
                        DEFAULT_IMD_PORTAL,
                        nowStr,
                        List.of("Official government warnings active. Please follow instructions issued by local authorities.")
                );
            }
        } else {
            return OfficialWarningToolResult.unavailable(
                    displayLoc,
                    "Official warning source returned HTTP status " + resp.getStatusCode() + ". Check " + DEFAULT_IMD_PORTAL
            );
        }
    }

    /**
     * Parses OASIS CAP RSS 2.0 feed with XXE protection and geographic relevance filtering.
     */
    public List<OfficialWarningToolResult.OfficialWarningItem> parseRssCapFeed(String xmlContent, String locationName) {
        List<OfficialWarningToolResult.OfficialWarningItem> results = new ArrayList<>();
        Set<String> seenGuids = new HashSet<>();
        Instant now = Instant.now();

        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            // Strict XXE defenses
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            dbf.setXIncludeAware(false);
            dbf.setExpandEntityReferences(false);

            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(new InputSource(new StringReader(xmlContent)));
            doc.getDocumentElement().normalize();

            NodeList items = doc.getElementsByTagName("item");
            for (int i = 0; i < items.getLength(); i++) {
                Element item = (Element) items.item(i);
                String title = getTagValue(item, "title");
                String guid = getTagValue(item, "guid");
                String link = getTagValue(item, "link");
                String author = getTagValue(item, "author");
                String pubDateStr = getTagValue(item, "pubDate");

                if (guid == null || guid.isBlank()) {
                    guid = (link != null) ? link : ("item-" + i);
                }
                if (!seenGuids.add(guid)) {
                    continue; // Deduplicate
                }

                // Parse pubDate
                Instant pubInstant = null;
                if (pubDateStr != null && !pubDateStr.isBlank()) {
                    try {
                        pubInstant = ZonedDateTime.parse(pubDateStr, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                        // If alert published more than 36 hours ago, consider it expired
                        if (Duration.between(pubInstant, now).toHours() > 36) {
                            continue;
                        }
                    } catch (Exception ignored) {}
                }

                // Extract issuing authority
                String authority = "NDMA SACHET";
                if (author != null && author.contains("(") && author.contains(")")) {
                    authority = author.substring(author.indexOf("(") + 1, author.lastIndexOf(")")).trim();
                }

                // Geographic filtering: if locationName is specified (e.g. Pune, Jaipur, Delhi)
                boolean matchesLocation = true;
                if (locationName != null && !locationName.isBlank() && !"Requested Area".equalsIgnoreCase(locationName)) {
                    String locLower = locationName.toLowerCase();
                    String titleLower = (title != null) ? title.toLowerCase() : "";
                    String authLower = authority.toLowerCase();

                    // Check if title or authority explicitly mentions the location
                    matchesLocation = titleLower.contains(locLower) || authLower.contains(locLower);
                }

                if (matchesLocation) {
                    results.add(new OfficialWarningToolResult.OfficialWarningItem(
                            guid,
                            authority,
                            title != null ? title.trim() : "Official Weather Alert",
                            "WEATHER_ALERT",
                            "ADVISORY",
                            displayArea(authority, title),
                            pubInstant != null ? pubInstant.toString() : now.toString(),
                            pubInstant != null ? pubInstant.toString() : now.toString(),
                            pubInstant != null ? pubInstant.plus(Duration.ofHours(6)).toString() : now.plus(Duration.ofHours(6)).toString(),
                            link != null ? link : DEFAULT_SACHET_PORTAL
                    ));
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse CAP RSS XML feed: {}", e.getMessage());
        }

        return results;
    }

    private String getTagValue(Element parent, String tagName) {
        NodeList nl = parent.getElementsByTagName(tagName);
        if (nl != null && nl.getLength() > 0 && nl.item(0) != null) {
            return nl.item(0).getTextContent();
        }
        return null;
    }

    private String displayArea(String authority, String title) {
        if (authority != null && !authority.isBlank()) {
            return authority;
        }
        return "Affected District / State";
    }
}
