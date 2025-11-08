package com.hotel.booking.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@FeignClient(name = "hotel-service", configuration = FeignConfig.class)
public interface HotelServiceClient {

    @GetMapping("/api/rooms/recommend")
    List<Object> getRecommendedRooms(@RequestParam LocalDate startDate,
                                     @RequestParam LocalDate endDate);

    @PostMapping("/api/rooms/{roomId}/confirm-availability")
    Boolean confirmAvailability(@PathVariable("roomId") Long roomId,
                                @RequestParam("startDate") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,  // ← ДОБАВЬТЕ АННОТАЦИЮ
                                @RequestParam("endDate") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,      // ← ДОБАВЬТЕ АННОТАЦИЮ
                                @RequestHeader("X-Request-Id") String requestId);

    @PostMapping("/api/rooms/{roomId}/release")
    void releaseRoom(@PathVariable("roomId") Long roomId,  // ← Исправлено
                     @RequestHeader("X-Request-Id") String requestId);

    @PostMapping("/api/rooms/{roomId}/increment-bookings")
    void incrementTimesBooked(@PathVariable("roomId") Long roomId);  // ← Исправлено
}