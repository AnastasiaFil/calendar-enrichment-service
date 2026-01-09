package rs.usergems.calendar.enrichment.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ColleagueMeetingCount {

    private String colleagueEmail;
    private Integer meetingCount;
}
