package com.hotel.hotel.service;

import com.hotel.hotel.dto.RoomRequestDTO;
import com.hotel.hotel.entity.Hotel;
import com.hotel.hotel.entity.Room;
import com.hotel.hotel.repository.HotelRepository;
import com.hotel.hotel.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HotelService {

    private final HotelRepository hotelRepository;
    private final RoomRepository roomRepository;

    public Hotel createHotel(Hotel hotel) {
        return hotelRepository.save(hotel);
    }

    public List<Hotel> getAllHotels() {
        return hotelRepository.findAll();
    }

    @Transactional
    public Room createRoomFromDTO(RoomRequestDTO roomRequest) {
        System.out.println("🔍 Creating room from DTO:");
        System.out.println("   - Hotel ID: " + roomRequest.getHotelId());
        System.out.println("   - Room number: " + roomRequest.getNumber());
        System.out.println("   - Type: " + roomRequest.getType());
        System.out.println("   - Price: " + roomRequest.getPrice());

        // Находим отель
        Hotel hotel = hotelRepository.findById(roomRequest.getHotelId())
                .orElseThrow(() -> {
                    System.out.println("❌ Hotel not found with ID: " + roomRequest.getHotelId());
                    return new RuntimeException("Hotel not found with id: " + roomRequest.getHotelId());
                });

        System.out.println("✅ Hotel found: " + hotel.getName() + " (ID: " + hotel.getId() + ")");

        // Создаем комнату
        Room room = new Room();
        room.setNumber(roomRequest.getNumber());
        room.setType(roomRequest.getType());
        room.setPrice(roomRequest.getPrice());
        room.setDescription(roomRequest.getDescription());
        room.setAvailable(roomRequest.getAvailable() != null ? roomRequest.getAvailable() : true);
        room.setTimesBooked(0); // По умолчанию 0
        room.setHotel(hotel); // Устанавливаем связь с отелем

        Room savedRoom = roomRepository.save(room);

        System.out.println("✅ Room created successfully:");
        System.out.println("   - Room ID: " + savedRoom.getId());
        System.out.println("   - Number: " + savedRoom.getNumber());
        System.out.println("   - Hotel: " + savedRoom.getHotel().getName());

        return savedRoom;
    }

    public Room createRoom(Room room, Long hotelId) {
        // Находим отель
        Hotel hotel = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new RuntimeException("Hotel not found with id: " + hotelId));

        // Устанавливаем связь
        room.setHotel(hotel);

        // Сохраняем комнату
        Room savedRoom = roomRepository.save(room);

        // Добавляем комнату в список отеля (для корректной работы при получении)
        hotel.getRooms().add(savedRoom);

        System.out.println("✅ Room created: " + savedRoom.getNumber() +
                " for hotel: " + hotel.getName() +
                " (ID: " + hotel.getId() + ")");

        return savedRoom;
    }

    public List<Room> getAllRooms() {
        return roomRepository.findByAvailableTrue();
    }

    public List<Room> getAvailableRooms(LocalDate startDate, LocalDate endDate) {
        return roomRepository.findAvailableRooms(startDate, endDate);
    }

    public List<Room> getRecommendedRooms(LocalDate startDate, LocalDate endDate) {
        return roomRepository.findRecommendedRooms(startDate, endDate);
    }

    @Transactional
    public synchronized boolean confirmAvailability(Long roomId, LocalDate startDate, LocalDate endDate, String requestId) {
        // Проверка идемпотентности
        if (isRequestProcessed(requestId)) {
            return true;
        }

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        if (!room.getAvailable()) {
            return false;
        }

        // Проверка доступности на даты
        List<Room> availableRooms = roomRepository.findAvailableRooms(startDate, endDate);
        boolean isAvailable = availableRooms.stream()
                .anyMatch(r -> r.getId().equals(roomId));

        if (isAvailable) {
            markRequestProcessed(requestId);
        }

        return isAvailable;
    }

    @Transactional
    public void releaseRoom(Long roomId, String requestId) {
        // Компенсирующее действие - снятие блокировки
        if (!isRequestProcessed(requestId)) {
            return;
        }

        removeProcessedRequest(requestId);
    }

    @Transactional
    public void incrementTimesBooked(Long roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));
        room.setTimesBooked(room.getTimesBooked() + 1);
        roomRepository.save(room);
    }

    private boolean isRequestProcessed(String requestId) {
        // В реальной системе это была бы отдельная таблица для отслеживания обработанных запросов
        // Здесь для простоты используем in-memory решение
        return ProcessedRequests.contains(requestId);
    }

    private void markRequestProcessed(String requestId) {
        ProcessedRequests.add(requestId);
    }

    private void removeProcessedRequest(String requestId) {
        ProcessedRequests.remove(requestId);
    }

    private static class ProcessedRequests {
        private static final java.util.Set<String> requests = java.util.Collections.synchronizedSet(new java.util.HashSet<>());

        public static boolean contains(String requestId) {
            return requests.contains(requestId);
        }

        public static void add(String requestId) {
            requests.add(requestId);
        }

        public static void remove(String requestId) {
            requests.remove(requestId);
        }
    }
}