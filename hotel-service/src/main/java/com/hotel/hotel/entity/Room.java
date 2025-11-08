package com.hotel.hotel.entity;

import lombok.Data;
import jakarta.persistence.*;
import java.time.LocalDate;

@Data
@Entity
@Table(name = "rooms")
public class Room {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String number;
    private Boolean available = true;
    private Integer timesBooked = 0;
    private String type; // Добавьте это поле
    private Double price; // Добавьте это поле
    private String description; // Добавьте это поле
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hotel_id")
    private Hotel hotel;

    @Transient
    private LocalDate startDate;

    @Transient
    private LocalDate endDate;
}