package com.hotel.hotel.dto;

import lombok.Data;

@Data
public class RoomRequestDTO {
    private Long hotelId;
    private String number;
    private String type;
    private Double price;
    private String description;
    private Boolean available;
}