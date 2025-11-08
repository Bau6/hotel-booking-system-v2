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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HotelController {

    private final HotelService hotelService;
    private final HotelMapper hotelMapper;

    @PostMapping("/hotels")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createHotel(@RequestBody Hotel hotel) {
        System.out.println("🏨 POST /hotels - Creating hotel: " + hotel.getName());

        try {
            Hotel createdHotel = hotelService.createHotel(hotel);
            return ResponseEntity.ok(createdHotel);
        } catch (RuntimeException e) {
            System.out.println("❌ Error creating hotel: " + e.getMessage());
            Map<String, String> errorResponse = Map.of("error", e.getMessage());

            if (e.getMessage().contains("already exists") ||
                    e.getMessage().contains("duplicate") ||
                    e.getMessage().contains("уже существует")) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
            } else if (e.getMessage().contains("not found") ||
                    e.getMessage().contains("не найден")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
            } else if (e.getMessage().contains("invalid") ||
                    e.getMessage().contains("Invalid") ||
                    e.getMessage().contains("неверный")) {
                return ResponseEntity.badRequest().body(errorResponse);
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
            }
        } catch (Exception e) {
            System.out.println("❌ Unexpected error creating hotel: " + e.getMessage());
            Map<String, String> errorResponse = Map.of("error", "Failed to create hotel");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @GetMapping("/hotels")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<?> getAllHotels() {
        System.out.println("🏨 GET /hotels - Getting all hotels");

        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null) {
                System.out.println("🏨 Authenticated user: " + auth.getName());
            }

            List<Hotel> hotels = hotelService.getAllHotels();
            System.out.println("🏨 Found " + hotels.size() + " hotels");

            List<HotelDTO> hotelDTOs = hotelMapper.toDTOList(hotels);
            return ResponseEntity.ok(hotelDTOs);
        } catch (AuthenticationException e) {
            System.out.println("❌ Authentication error: " + e.getMessage());
            Map<String, String> errorResponse = Map.of("error", "Authentication failed");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
        } catch (AccessDeniedException e) {
            System.out.println("❌ Access denied: " + e.getMessage());
            Map<String, String> errorResponse = Map.of("error", "Access denied");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
        } catch (Exception e) {
            System.out.println("❌ Unexpected error getting hotels: " + e.getMessage());
            Map<String, String> errorResponse = Map.of("error", "Failed to retrieve hotels");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @PostMapping("/rooms")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createRoom(@RequestBody RoomRequestDTO roomRequest) {
        System.out.println("🏨 POST /rooms - Creating room: " + roomRequest.getNumber() + " for hotel: " + roomRequest.getHotelId());

        try {
            // Валидация обязательных полей
            if (roomRequest.getHotelId() == null) {
                Map<String, String> errorResponse = Map.of("error", "Hotel ID is required");
                return ResponseEntity.badRequest().body(errorResponse);
            }
            if (roomRequest.getNumber() == null || roomRequest.getNumber().trim().isEmpty()) {
                Map<String, String> errorResponse = Map.of("error", "Room number is required");
                return ResponseEntity.badRequest().body(errorResponse);
            }

            Room createdRoom = hotelService.createRoomFromDTO(roomRequest);
            RoomDTO roomDTO = hotelMapper.toRoomDTO(createdRoom);
            return ResponseEntity.ok(roomDTO);
        } catch (RuntimeException e) {
            System.out.println("❌ Error creating room: " + e.getMessage());
            Map<String, String> errorResponse = Map.of("error", e.getMessage());

            if (e.getMessage().contains("already exists") ||
                    e.getMessage().contains("duplicate") ||
                    e.getMessage().contains("уже существует")) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
            } else if (e.getMessage().contains("not found") ||
                    e.getMessage().contains("не найден")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
            } else if (e.getMessage().contains("invalid") ||
                    e.getMessage().contains("Invalid") ||
                    e.getMessage().contains("неверный")) {
                return ResponseEntity.badRequest().body(errorResponse);
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
            }
        } catch (Exception e) {
            System.out.println("❌ Unexpected error creating room: " + e.getMessage());
            Map<String, String> errorResponse = Map.of("error", "Failed to create room");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @GetMapping("/rooms")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<?> getAllRooms() {
        System.out.println("🏨 GET /rooms - Getting all rooms");

        try {
            List<Room> rooms = hotelService.getAllRooms();
            List<RoomDTO> roomDTOs = rooms.stream()
                    .map(hotelMapper::toRoomDTO)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(roomDTOs);
        } catch (AuthenticationException e) {
            System.out.println("❌ Authentication error: " + e.getMessage());
            Map<String, String> errorResponse = Map.of("error", "Authentication failed");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
        } catch (AccessDeniedException e) {
            System.out.println("❌ Access denied: " + e.getMessage());
            Map<String, String> errorResponse = Map.of("error", "Access denied");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
        } catch (Exception e) {
            System.out.println("❌ Unexpected error getting rooms: " + e.getMessage());
            Map<String, String> errorResponse = Map.of("error", "Failed to retrieve rooms");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @GetMapping("/rooms/available")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<?> getAvailableRooms(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        System.out.println("🏨 GET /rooms/available - From " + startDate + " to " + endDate);

        try {
            // Валидация дат
            if (startDate == null || endDate == null) {
                Map<String, String> errorResponse = Map.of("error", "Start date and end date are required");
                return ResponseEntity.badRequest().body(errorResponse);
            }
            if (startDate.isBefore(LocalDate.now())) {
                Map<String, String> errorResponse = Map.of("error", "Start date cannot be in the past");
                return ResponseEntity.badRequest().body(errorResponse);
            }
            if (endDate.isBefore(startDate)) {
                Map<String, String> errorResponse = Map.of("error", "End date cannot be before start date");
                return ResponseEntity.badRequest().body(errorResponse);
            }

            List<Room> rooms = hotelService.getAvailableRooms(startDate, endDate);
            List<RoomDTO> roomDTOs = rooms.stream()
                    .map(hotelMapper::toRoomDTO)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(roomDTOs);
        } catch (DateTimeParseException e) {
            System.out.println("❌ Date format error: " + e.getMessage());
            Map<String, String> errorResponse = Map.of("error", "Invalid date format. Use YYYY-MM-DD");
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (RuntimeException e) {
            System.out.println("❌ Error getting available rooms: " + e.getMessage());
            Map<String, String> errorResponse = Map.of("error", e.getMessage());

            if (e.getMessage().contains("invalid date") ||
                    e.getMessage().contains("Invalid date") ||
                    e.getMessage().contains("неверная дата")) {
                return ResponseEntity.badRequest().body(errorResponse);
            } else if (e.getMessage().contains("not found") ||
                    e.getMessage().contains("не найден")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
            }
        } catch (Exception e) {
            System.out.println("❌ Unexpected error getting available rooms: " + e.getMessage());
            Map<String, String> errorResponse = Map.of("error", "Failed to retrieve available rooms");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @GetMapping("/rooms/recommend")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<?> getRecommendedRooms(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        System.out.println("🏨 GET /rooms/recommend - From " + startDate + " to " + endDate);

        try {
            // Валидация дат
            if (startDate == null || endDate == null) {
                Map<String, String> errorResponse = Map.of("error", "Start date and end date are required");
                return ResponseEntity.badRequest().body(errorResponse);
            }
            if (startDate.isBefore(LocalDate.now())) {
                Map<String, String> errorResponse = Map.of("error", "Start date cannot be in the past");
                return ResponseEntity.badRequest().body(errorResponse);
            }
            if (endDate.isBefore(startDate)) {
                Map<String, String> errorResponse = Map.of("error", "End date cannot be before start date");
                return ResponseEntity.badRequest().body(errorResponse);
            }

            List<Room> rooms = hotelService.getRecommendedRooms(startDate, endDate);
            List<RoomDTO> roomDTOs = rooms.stream()
                    .map(hotelMapper::toRoomDTO)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(roomDTOs);
        } catch (DateTimeParseException e) {
            System.out.println("❌ Date format error: " + e.getMessage());
            Map<String, String> errorResponse = Map.of("error", "Invalid date format. Use YYYY-MM-DD");
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (RuntimeException e) {
            System.out.println("❌ Error getting recommended rooms: " + e.getMessage());
            Map<String, String> errorResponse = Map.of("error", e.getMessage());

            if (e.getMessage().contains("invalid date") ||
                    e.getMessage().contains("Invalid date") ||
                    e.getMessage().contains("неверная дата")) {
                return ResponseEntity.badRequest().body(errorResponse);
            } else if (e.getMessage().contains("not found") ||
                    e.getMessage().contains("не найден")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
            }
        } catch (Exception e) {
            System.out.println("❌ Unexpected error getting recommended rooms: " + e.getMessage());
            Map<String, String> errorResponse = Map.of("error", "Failed to retrieve recommended rooms");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @PostMapping("/rooms/{id}/confirm-availability")
    public ResponseEntity<?> confirmAvailability(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestHeader("X-Request-Id") String requestId) {
        System.out.println("🏨 POST /rooms/" + id + "/confirm-availability - Request: " + requestId);

        try {
            // Валидация входных данных
            if (id == null) {
                Map<String, String> errorResponse = Map.of("error", "Room ID is required");
                return ResponseEntity.badRequest().body(errorResponse);
            }
            if (startDate == null || endDate == null) {
                Map<String, String> errorResponse = Map.of("error", "Start date and end date are required");
                return ResponseEntity.badRequest().body(errorResponse);
            }
            if (requestId == null || requestId.trim().isEmpty()) {
                Map<String, String> errorResponse = Map.of("error", "Request ID is required");
                return ResponseEntity.badRequest().body(errorResponse);
            }
            if (startDate.isBefore(LocalDate.now())) {
                Map<String, String> errorResponse = Map.of("error", "Start date cannot be in the past");
                return ResponseEntity.badRequest().body(errorResponse);
            }
            if (endDate.isBefore(startDate)) {
                Map<String, String> errorResponse = Map.of("error", "End date cannot be before start date");
                return ResponseEntity.badRequest().body(errorResponse);
            }

            boolean available = hotelService.confirmAvailability(id, startDate, endDate, requestId);
            return ResponseEntity.ok(available);
        } catch (DateTimeParseException e) {
            System.out.println("❌ Date format error: " + e.getMessage());
            Map<String, String> errorResponse = Map.of("error", "Invalid date format. Use YYYY-MM-DD");
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (RuntimeException e) {
            System.out.println("❌ Error confirming availability: " + e.getMessage());
            Map<String, String> errorResponse = Map.of("error", e.getMessage());

            if (e.getMessage().contains("not available") ||
                    e.getMessage().contains("already booked") ||
                    e.getMessage().contains("недоступна")) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
            } else if (e.getMessage().contains("not found") ||
                    e.getMessage().contains("не найден")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
            } else if (e.getMessage().contains("invalid") ||
                    e.getMessage().contains("Invalid") ||
                    e.getMessage().contains("неверный")) {
                return ResponseEntity.badRequest().body(errorResponse);
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
            }
        } catch (Exception e) {
            System.out.println("❌ Unexpected error confirming availability: " + e.getMessage());
            Map<String, String> errorResponse = Map.of("error", "Failed to confirm room availability");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @PostMapping("/rooms/{id}/release")
    public ResponseEntity<?> releaseRoom(
            @PathVariable Long id,
            @RequestHeader("X-Request-Id") String requestId) {
        System.out.println("🏨 POST /rooms/" + id + "/release - Request: " + requestId);

        try {
            // Валидация входных данных
            if (id == null) {
                Map<String, String> errorResponse = Map.of("error", "Room ID is required");
                return ResponseEntity.badRequest().body(errorResponse);
            }
            if (requestId == null || requestId.trim().isEmpty()) {
                Map<String, String> errorResponse = Map.of("error", "Request ID is required");
                return ResponseEntity.badRequest().body(errorResponse);
            }

            hotelService.releaseRoom(id, requestId);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            System.out.println("❌ Error releasing room: " + e.getMessage());
            Map<String, String> errorResponse = Map.of("error", e.getMessage());

            if (e.getMessage().contains("not reserved") ||
                    e.getMessage().contains("not booked") ||
                    e.getMessage().contains("не забронирована")) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
            } else if (e.getMessage().contains("not found") ||
                    e.getMessage().contains("не найден")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
            } else if (e.getMessage().contains("invalid") ||
                    e.getMessage().contains("Invalid") ||
                    e.getMessage().contains("неверный")) {
                return ResponseEntity.badRequest().body(errorResponse);
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
            }
        } catch (Exception e) {
            System.out.println("❌ Unexpected error releasing room: " + e.getMessage());
            Map<String, String> errorResponse = Map.of("error", "Failed to release room");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @PostMapping("/rooms/{id}/increment-bookings")
    public ResponseEntity<?> incrementTimesBooked(@PathVariable Long id) {
        System.out.println("🏨 POST /rooms/" + id + "/increment-bookings");

        try {
            // Валидация входных данных
            if (id == null) {
                Map<String, String> errorResponse = Map.of("error", "Room ID is required");
                return ResponseEntity.badRequest().body(errorResponse);
            }

            hotelService.incrementTimesBooked(id);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            System.out.println("❌ Error incrementing bookings: " + e.getMessage());
            Map<String, String> errorResponse = Map.of("error", e.getMessage());

            if (e.getMessage().contains("not found") ||
                    e.getMessage().contains("не найден")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
            }
        } catch (Exception e) {
            System.out.println("❌ Unexpected error incrementing bookings: " + e.getMessage());
            Map<String, String> errorResponse = Map.of("error", "Failed to increment bookings counter");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
}