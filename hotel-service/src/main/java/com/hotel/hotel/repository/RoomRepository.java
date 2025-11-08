package com.hotel.hotel.repository;

import com.hotel.hotel.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {

    List<Room> findByAvailableTrue();

    @Query("SELECT r FROM Room r WHERE r.available = true AND r.id NOT IN " +
            "(SELECT b.roomId FROM Booking b WHERE b.status = 'CONFIRMED' AND " +
            "((b.startDate BETWEEN :startDate AND :endDate) OR " +
            "(b.endDate BETWEEN :startDate AND :endDate) OR " +
            "(:startDate BETWEEN b.startDate AND b.endDate)))")
    List<Room> findAvailableRooms(@Param("startDate") LocalDate startDate,
                                  @Param("endDate") LocalDate endDate);

    @Query("SELECT r FROM Room r WHERE r.available = true AND r.id NOT IN " +
            "(SELECT b.roomId FROM Booking b WHERE b.status = 'CONFIRMED' AND " +
            "((b.startDate BETWEEN :startDate AND :endDate) OR " +
            "(b.endDate BETWEEN :startDate AND :endDate) OR " +
            "(:startDate BETWEEN b.startDate AND b.endDate))) " +
            "ORDER BY r.timesBooked ASC, r.id ASC")
    List<Room> findRecommendedRooms(@Param("startDate") LocalDate startDate,
                                    @Param("endDate") LocalDate endDate);
}