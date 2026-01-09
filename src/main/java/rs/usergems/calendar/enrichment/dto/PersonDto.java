package rs.usergems.calendar.enrichment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class PersonDto {

    @JsonProperty("first_name")
    private String firstName;

    @JsonProperty("last_name")
    private String lastName;

    @JsonProperty("avatar")
    private String avatar;

    @JsonProperty("title")
    private String title;

    @JsonProperty("linkedin_url")
    private String linkedinUrl;

    @JsonProperty("company")
    private CompanyDto company;
}
