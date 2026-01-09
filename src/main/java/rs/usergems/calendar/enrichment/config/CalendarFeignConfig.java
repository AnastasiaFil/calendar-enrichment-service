package rs.usergems.calendar.enrichment.config;

import feign.Logger;
import feign.Request;
import feign.Retryer;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class CalendarFeignConfig {

    @Value("${calendar.api.feign.logger-level:FULL}")
    private String loggerLevel;

    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.valueOf(loggerLevel);
    }

    @Bean
    public Request.Options requestOptions() {
        return new Request.Options(10000, 30000);
    }

    @Bean
    public Retryer retryer() {
        return new Retryer.Default(100, 1000, 3);
    }
}
