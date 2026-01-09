package rs.usergems.calendar.enrichment.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MeetingHistory {

    private Integer totalCount;
    private Map<String, Integer> colleagueCounts;
}
