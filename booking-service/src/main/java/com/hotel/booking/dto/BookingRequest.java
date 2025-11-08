package com.hotel.booking.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class BookingRequest {
    private Long roomId;
    private LocalDate startDate;
    private LocalDate endDate;
    private String guestName;
    private String guestEmail;
    private Boolean autoSelect = false;
}