package rs.usergems.calendar.enrichment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class CalendarEventDto {

    @JsonProperty("id")
    private Long id;

    @JsonProperty("changed")
    private String changed;

    @JsonProperty("start")
    private String start;

    @JsonProperty("end")
    private String end;

    @JsonProperty("title")
    private String title;

    @JsonProperty("accepted")
    private List<String> accepted;

    @JsonProperty("rejected")
    private List<String> rejected;
}