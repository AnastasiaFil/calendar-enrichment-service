package rs.usergems.calendar.enrichment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "calendar.api")
public class CalendarApiProperties {

    private String baseUrl;
    private Map<String, String> apiKeys;

    public String getApiKeyForEmail(String email) {
        return apiKeys != null ? apiKeys.get(email) : null;
    }
}
