package com.hotel.hotel.dto;

import lombok.Data;

@Data
public class RoomDTO {
    private Long id;
    private String number;
    private Boolean available;
    private Integer timesBooked;
    private Long hotelId; // Только ID отеля вместо полного объекта
}