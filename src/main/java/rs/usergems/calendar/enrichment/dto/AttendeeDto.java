package rs.usergems.calendar.enrichment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

@Data
public class AttendeeDto {

    @JsonProperty("email")
    private String email;

    @JsonProperty("name")
    private String name;

    @JsonProperty("title")
    private String title;

    @JsonProperty("linkedin")
    private String linkedin;

    @JsonProperty("avatar")
    private String avatar;

    @JsonProperty("status")
    private String status;

    @JsonProperty("meeting_count")
    private Integer meetingCount;

    @JsonProperty("met_with_colleagues")
    private Map<String, Integer> metWithColleagues;
}
