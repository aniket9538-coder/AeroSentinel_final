package com.aerosentinel.assistant;

import com.aerosentinel.assistant.dto.LocationDto;
import com.aerosentinel.assistant.tools.OfficialWarningTool;
import com.aerosentinel.assistant.tools.OfficialWarningToolResult;
import com.aerosentinel.assistant.warning.OfficialWarningClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class OfficialWarningToolTest {

    @Test
    @DisplayName("Tool 3: Unconfigured warning feed returns SOURCE_UNAVAILABLE truthfully without fabricating alerts")
    void testOfficialWarnings_Unconfigured() {
        RestTemplate mockTemplate = Mockito.mock(RestTemplate.class);
        OfficialWarningClient client = new OfficialWarningClient(mockTemplate, "");
        OfficialWarningTool tool = new OfficialWarningTool(client);

        LocationDto loc = new LocationDto("Pune", 18.53, 73.84, null);
        OfficialWarningToolResult result = tool.getOfficialWarnings(loc);

        assertNotNull(result);
        assertEquals("SOURCE_UNAVAILABLE", result.status());
        assertTrue(result.warnings().isEmpty());
        assertTrue(result.limitations().get(0).contains("mausam.imd.gov.in"));
    }

    @Test
    @DisplayName("Tool 3: Verified feed returns government warnings without modification")
    void testOfficialWarnings_VerifiedWarningsFound() {
        RestTemplate mockTemplate = Mockito.mock(RestTemplate.class);
        OfficialWarningClient client = new OfficialWarningClient(mockTemplate, "https://mock.imd.gov.in/warnings");
        OfficialWarningTool tool = new OfficialWarningTool(client);

        OfficialWarningClient.OfficialWarningFeedResponse feedResponse =
                new OfficialWarningClient.OfficialWarningFeedResponse(
                        "India Meteorological Department (IMD)",
                        "2026-10-18T12:00:00Z",
                        List.of(new OfficialWarningClient.OfficialWarningFeedResponse.FeedWarningItem(
                                "IMD-PUN-001",
                                "Orange Alert: Heavy to Very Heavy Rainfall expected in Pune Ghat areas",
                                "HEAVY_RAIN",
                                "ORANGE",
                                "Pune District and Ghats",
                                Instant.now().minus(Duration.ofHours(2)).toString(),
                                Instant.now().plus(Duration.ofHours(24)).toString(),
                                "https://mausam.imd.gov.in/pune"
                        ))
                );

        when(mockTemplate.getForEntity(anyString(), eq(OfficialWarningClient.OfficialWarningFeedResponse.class)))
                .thenReturn(new ResponseEntity<>(feedResponse, HttpStatus.OK));

        LocationDto loc = new LocationDto("Pune", 18.53, 73.84, null);
        OfficialWarningToolResult result = tool.getOfficialWarnings(loc);

        assertNotNull(result);
        assertEquals("VERIFIED_WARNINGS_FOUND", result.status());
        assertEquals(1, result.warnings().size());

        OfficialWarningToolResult.OfficialWarningItem item = result.warnings().get(0);
        assertEquals("IMD-PUN-001", item.warningId());
        assertEquals("ORANGE", item.severity());
        assertEquals("HEAVY_RAIN", item.warningType());
        assertEquals("Pune District and Ghats", item.affectedArea());
        assertEquals("https://mausam.imd.gov.in/pune", item.officialReferenceUrl());
    }

    @Test
    @DisplayName("Tool 3: Network or HTTP failure reports SOURCE_UNAVAILABLE, never falsely claiming 'no warnings'")
    void testOfficialWarnings_NetworkFailure() {
        RestTemplate mockTemplate = Mockito.mock(RestTemplate.class);
        OfficialWarningClient client = new OfficialWarningClient(mockTemplate, "https://mock.imd.gov.in/warnings");
        OfficialWarningTool tool = new OfficialWarningTool(client);

        when(mockTemplate.getForEntity(anyString(), eq(OfficialWarningClient.OfficialWarningFeedResponse.class)))
                .thenThrow(new ResourceAccessException("Connection timed out to official portal"));

        LocationDto loc = new LocationDto("Pune", 18.53, 73.84, null);
        OfficialWarningToolResult result = tool.getOfficialWarnings(loc);

        assertNotNull(result);
        assertEquals("SOURCE_UNAVAILABLE", result.status());
        assertTrue(result.warnings().isEmpty());
        assertTrue(result.limitations().get(0).contains("failed"));
    }

    @Test
    @DisplayName("Tool 3: Parses OASIS CAP RSS 2.0 XML with XXE protection and geographic relevance")
    void testOfficialWarnings_RssXmlParsing() {
        RestTemplate mockTemplate = Mockito.mock(RestTemplate.class);
        OfficialWarningClient client = new OfficialWarningClient(mockTemplate, "https://sachet.ndma.gov.in/cap_public_website/rss/rss_india.xml");
        OfficialWarningTool tool = new OfficialWarningTool(client);

        String freshPubDate = DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC));
        String sampleRssXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0">
                  <channel>
                    <title>All India: CAP Disaster Alert Feeds</title>
                    <item>
                      <title>Heavy rainfall and lightning alert over Pune and Satara districts in next 3 hours.</title>
                      <author>controlroom@ndma.gov.in (IMD Mumbai)</author>
                      <guid>ALERT-PUN-9921</guid>
                      <link>https://sachet.ndma.gov.in/alert?id=9921</link>
                      <pubDate>%s</pubDate>
                    </item>
                    <item>
                      <title>Moderate Thunderstorm alert over Jaipur, Rajasthan.</title>
                      <author>controlroom@ndma.gov.in (IMD Jaipur)</author>
                      <guid>ALERT-JAI-1011</guid>
                      <link>https://sachet.ndma.gov.in/alert?id=1011</link>
                      <pubDate>%s</pubDate>
                    </item>
                  </channel>
                </rss>
                """.formatted(freshPubDate, freshPubDate);

        when(mockTemplate.getForEntity(eq("https://sachet.ndma.gov.in/cap_public_website/rss/rss_india.xml"), eq(String.class)))
                .thenReturn(new ResponseEntity<>(sampleRssXml, HttpStatus.OK));

        LocationDto locPune = new LocationDto("Pune", 18.53, 73.84, null);
        OfficialWarningToolResult resultPune = tool.getOfficialWarnings(locPune);

        assertNotNull(resultPune);
        assertEquals("VERIFIED_WARNINGS_FOUND", resultPune.status());
        assertEquals(1, resultPune.warnings().size());
        assertEquals("ALERT-PUN-9921", resultPune.warnings().get(0).warningId());
        assertTrue(resultPune.warnings().get(0).title().contains("Pune"));

        // Query for Delhi - no items in RSS feed mention Delhi
        LocationDto locDelhi = new LocationDto("Delhi", 28.61, 77.20, null);
        OfficialWarningToolResult resultDelhi = tool.getOfficialWarnings(locDelhi);
        assertNotNull(resultDelhi);
        assertEquals("NO_ACTIVE_WARNINGS", resultDelhi.status());
        assertTrue(resultDelhi.warnings().isEmpty());
    }
}
