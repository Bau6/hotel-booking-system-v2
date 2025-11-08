package com.hotel.hotel.controller;

import com.hotel.hotel.dto.HotelDTO;
import com.hotel.hotel.dto.RoomDTO;
import com.hotel.hotel.dto.RoomRequestDTO;
import com.hotel.hotel.entity.Hotel;
import com.hotel.hotel.entity.Room;
import com.hotel.hotel.mapper.HotelMapper;
import com.hotel.hotel.service.HotelService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HotelController {

    private final HotelService hotelService;
    private final HotelMapper hotelMapper;

    @PostMapping("/hotels")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Hotel> createHotel(@RequestBody Hotel hotel) {
        System.out.println("🏨 POST /hotels - Creating hotel: " + hotel.getName());
        return ResponseEntity.ok(hotelService.createHotel(hotel));
    }

    @GetMapping("/hotels")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<List<HotelDTO>> getAllHotels() {
        System.out.println("🏨 GET /hotels - Getting all hotels");

        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            System.out.println("🏨 Authenticated user: " + auth.getName());
        }

        List<Hotel> hotels = hotelService.getAllHotels();
        System.out.println("🏨 Found " + hotels.size() + " hotels");

        // Преобразуем в DTO
        List<HotelDTO> hotelDTOs = hotelMapper.toDTOList(hotels);

        return ResponseEntity.ok(hotelDTOs);
    }

    @PostMapping("/rooms")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RoomDTO> createRoom(@RequestBody RoomRequestDTO roomRequest) {
        System.out.println("🏨 POST /rooms - Creating room: " + roomRequest.getNumber() + " for hotel: " + roomRequest.getHotelId());

        Room createdRoom = hotelService.createRoomFromDTO(roomRequest);
        RoomDTO roomDTO = hotelMapper.toRoomDTO(createdRoom);
        return ResponseEntity.ok(roomDTO);
    }

    @GetMapping("/rooms")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<List<RoomDTO>> getAllRooms() { // Измените на RoomDTO
        System.out.println("🏨 GET /rooms - Getting all rooms");
        List<Room> rooms = hotelService.getAllRooms();
        List<RoomDTO> roomDTOs = rooms.stream()
                .map(hotelMapper::toRoomDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(roomDTOs);
    }

    @GetMapping("/rooms/available")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<List<RoomDTO>> getAvailableRooms( // Измените на RoomDTO
                                                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        System.out.println("🏨 GET /rooms/available - From " + startDate + " to " + endDate);
        List<Room> rooms = hotelService.getAvailableRooms(startDate, endDate);
        List<RoomDTO> roomDTOs = rooms.stream()
                .map(hotelMapper::toRoomDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(roomDTOs);
    }

    @GetMapping("/rooms/recommend")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<List<RoomDTO>> getRecommendedRooms( // Измените на RoomDTO
                                                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        System.out.println("🏨 GET /rooms/recommend - From " + startDate + " to " + endDate);
        List<Room> rooms = hotelService.getRecommendedRooms(startDate, endDate);
        List<RoomDTO> roomDTOs = rooms.stream()
                .map(hotelMapper::toRoomDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(roomDTOs);
    }

    @PostMapping("/rooms/{id}/confirm-availability")
    public ResponseEntity<Boolean> confirmAvailability(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestHeader("X-Request-Id") String requestId) {
        System.out.println("🏨 POST /rooms/" + id + "/confirm-availability - Request: " + requestId);
        boolean available = hotelService.confirmAvailability(id, startDate, endDate, requestId);
        return ResponseEntity.ok(available);
    }

    @PostMapping("/rooms/{id}/release")
    public ResponseEntity<Void> releaseRoom(
            @PathVariable Long id,
            @RequestHeader("X-Request-Id") String requestId) {
        System.out.println("🏨 POST /rooms/" + id + "/release - Request: " + requestId);
        hotelService.releaseRoom(id, requestId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/rooms/{id}/increment-bookings")
    public ResponseEntity<Void> incrementTimesBooked(@PathVariable Long id) {
        System.out.println("🏨 POST /rooms/" + id + "/increment-bookings");
        hotelService.incrementTimesBooked(id);
        return ResponseEntity.ok().build();
    }
}