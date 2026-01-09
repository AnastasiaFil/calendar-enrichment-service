package rs.usergems.calendar.enrichment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class MeetingDto {

    @JsonProperty("title")
    private String title;

    @JsonProperty("start")
    private String start;

    @JsonProperty("end")
    private String end;

    @JsonProperty("duration_min")
    private Integer durationMin;

    @JsonProperty("internal_attendees")
    private List<String> internalAttendees;

    @JsonProperty("external_attendees")
    private List<AttendeeDto> externalAttendees;

    @JsonProperty("company")
    private CompanyDto company;
}
