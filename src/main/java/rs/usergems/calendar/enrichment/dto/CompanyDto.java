package rs.usergems.calendar.enrichment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class CompanyDto {

    @JsonProperty("name")
    private String name;

    @JsonProperty("linkedin_url")
    private String linkedinUrl;

    @JsonProperty("employees")
    private Integer employees;
}
