package rs.usergems.calendar.enrichment;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@OpenAPIDefinition(info = @Info(title = "Calendar Enrichment Service", version = "1.0"))
public class CalendarEnrichmentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CalendarEnrichmentServiceApplication.class, args);
    }
}
