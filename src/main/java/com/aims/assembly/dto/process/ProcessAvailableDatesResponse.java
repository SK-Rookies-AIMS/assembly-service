package com.aims.assembly.dto.process;

import java.time.LocalDate;
import java.util.List;

public record ProcessAvailableDatesResponse(
        List<LocalDate> dates,
        LocalDate latestDate
) {
    public static ProcessAvailableDatesResponse of(List<LocalDate> dates) {
        LocalDate latestDate = dates.isEmpty() ? null : dates.get(dates.size() - 1);
        return new ProcessAvailableDatesResponse(dates, latestDate);
    }
}
