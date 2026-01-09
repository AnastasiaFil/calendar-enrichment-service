package rs.usergems.calendar.enrichment.client.calendar;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import rs.usergems.calendar.enrichment.config.CalendarFeignConfig;
import rs.usergems.calendar.enrichment.dto.CalendarApiResponse;

@FeignClient(name = "calendar-api", url = "${calendar.api.base-url}", configuration = CalendarFeignConfig.class)
public interface CalendarFeignClient {

    @GetMapping("/events")
    CalendarApiResponse getEvents(
            @RequestHeader("Authorization") String authorization, 
            @RequestParam("page") int page,
            @RequestParam("rep_email") String repEmail);
}
