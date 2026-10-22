package com.aerosentinel.config;

import com.aerosentinel.forecast.ForecastAiClient;
import com.aerosentinel.hotspot.AiServiceHotspotClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AI Service Binding Configuration Tests")
class AiServiceBindingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner();

    @Test
    @DisplayName("Default AI service URL resolves to http://localhost:8000 when unconfigured")
    void testDefaultAiServiceUrl() {
        contextRunner
                .withUserConfiguration(AiServiceConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(AiServiceConfig.class);
                    AiServiceConfig config = context.getBean(AiServiceConfig.class);
                    assertThat(config.getAiServiceUrl()).isEqualTo("http://localhost:8000");
                });
    }

    @Test
    @DisplayName("Vercel AI_SERVICE_URL binding is consumed correctly by AiServiceConfig")
    void testVercelAiServiceUrlBindingInConfig() {
        String mockVercelInternalBindingUrl = "https://ai-service-internal-pr123.vercel.app";

        contextRunner
                .withUserConfiguration(AiServiceConfig.class)
                .withPropertyValues("app.ai-service.url=" + mockVercelInternalBindingUrl)
                .run(context -> {
                    assertThat(context).hasSingleBean(AiServiceConfig.class);
                    AiServiceConfig config = context.getBean(AiServiceConfig.class);
                    assertThat(config.getAiServiceUrl()).isEqualTo(mockVercelInternalBindingUrl);
                });
    }

    @Test
    @DisplayName("ForecastAiClient receives the Vercel internal AI service URL binding")
    void testForecastAiClientReceivesBinding() {
        String mockVercelInternalBindingUrl = "https://ai-service-internal-pr123.vercel.app";

        ForecastAiClient forecastClient = new ForecastAiClient(mockVercelInternalBindingUrl, 15000);
        assertThat(forecastClient.getServiceUrl()).isEqualTo(mockVercelInternalBindingUrl);
    }

    @Test
    @DisplayName("AiServiceHotspotClient receives the Vercel internal AI service URL binding")
    void testAiServiceHotspotClientReceivesBinding() {
        String mockVercelInternalBindingUrl = "https://ai-service-internal-pr123.vercel.app";

        AiServiceHotspotClient hotspotClient = new AiServiceHotspotClient(mockVercelInternalBindingUrl, 10000);
        assertThat(hotspotClient.getServiceUrl()).isEqualTo(mockVercelInternalBindingUrl);
    }
}
