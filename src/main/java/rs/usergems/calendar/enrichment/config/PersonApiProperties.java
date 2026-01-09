package rs.usergems.calendar.enrichment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "person.api")
public class PersonApiProperties {

    private String baseUrl;
    private String apiKey;
}
