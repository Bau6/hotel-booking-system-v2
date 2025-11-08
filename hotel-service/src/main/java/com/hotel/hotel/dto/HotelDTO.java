package com.hotel.hotel.dto;

import lombok.Data;
import java.util.List;

@Data
public class HotelDTO {
    private Long id;
    private String name;
    private String address;
    private List<RoomDTO> rooms;
}