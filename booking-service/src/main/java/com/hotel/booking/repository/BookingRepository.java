package com.hotel.booking.repository;

import com.hotel.booking.entity.Booking;
import feign.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByUserId(Long userId);
    List<Booking> findByStatus(Booking.BookingStatus status);

    // ✅ Метод для проверки дублирующих бронирований
    List<Booking> findByRoomIdAndStatusIn(Long roomId, List<Booking.BookingStatus> statuses);

    // ✅ Метод для поиска бронирований пользователя на конкретные даты
    @Query("SELECT b FROM Booking b WHERE b.userId = :userId AND b.status IN ('PENDING', 'CONFIRMED') " +
            "AND ((b.startDate BETWEEN :startDate AND :endDate) OR " +
            "(b.endDate BETWEEN :startDate AND :endDate))")
    List<Booking> findUserBookingsForDates(@Param("userId") Long userId,
                                           @Param("startDate") LocalDate startDate,
                                           @Param("endDate") LocalDate endDate);
}