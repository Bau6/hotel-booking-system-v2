package com.hotel.hotel.mapper;

import com.hotel.hotel.dto.HotelDTO;
import com.hotel.hotel.dto.RoomDTO;
import com.hotel.hotel.entity.Hotel;
import com.hotel.hotel.entity.Room;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class HotelMapper {

    public HotelDTO toDTO(Hotel hotel) {
        HotelDTO dto = new HotelDTO();
        dto.setId(hotel.getId());
        dto.setName(hotel.getName());
        dto.setAddress(hotel.getAddress());

        if (hotel.getRooms() != null) {
            List<RoomDTO> roomDTOs = hotel.getRooms().stream()
                    .map(this::toRoomDTO)
                    .collect(Collectors.toList());
            dto.setRooms(roomDTOs);
        }

        return dto;
    }

    public RoomDTO toRoomDTO(Room room) {
        RoomDTO dto = new RoomDTO();
        dto.setId(room.getId());
        dto.setNumber(room.getNumber());
        dto.setAvailable(room.getAvailable());
        dto.setTimesBooked(room.getTimesBooked());
        dto.setHotelId(room.getHotel() != null ? room.getHotel().getId() : null);
        return dto;
    }

    public List<HotelDTO> toDTOList(List<Hotel> hotels) {
        return hotels.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
}