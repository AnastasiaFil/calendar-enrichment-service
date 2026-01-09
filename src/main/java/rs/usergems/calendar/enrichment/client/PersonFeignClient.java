package rs.usergems.calendar.enrichment.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import rs.usergems.calendar.enrichment.config.FeignConfiguration;
import rs.usergems.calendar.enrichment.dto.PersonDto;

@FeignClient(name = "person-api", url = "${person.api.base-url}", configuration = FeignConfiguration.class)
public interface PersonFeignClient {

    @GetMapping("/person/{email}")
    PersonDto getPersonByEmail(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("email") String email);
}
