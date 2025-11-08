package com.hotel.booking.config;

import com.hotel.booking.entity.Booking;
import com.hotel.booking.entity.User;
import com.hotel.booking.repository.BookingRepository;
import com.hotel.booking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class DataLoader implements CommandLineRunner {

    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        // Очищаем базу
        bookingRepository.deleteAll();
        userRepository.deleteAll();

        // Создаем пользователей
        User user1 = new User();
        user1.setUsername("user1");
        user1.setPassword(passwordEncoder.encode("password123"));
        user1.setRole("USER");
        user1 = userRepository.save(user1);

        User user2 = new User();
        user2.setUsername("user2");
        user2.setPassword(passwordEncoder.encode("password123"));
        user2.setRole("USER");
        user2 = userRepository.save(user2);

        User admin = new User();
        admin.setUsername("admin");
        admin.setPassword(passwordEncoder.encode("admin123"));
        admin.setRole("ADMIN");
        admin = userRepository.save(admin);

        // Создаем тестовые бронирования
        createBooking(user1, 1L, "2024-01-10", "2024-01-15", Booking.BookingStatus.CONFIRMED);
        createBooking(user1, 2L, "2024-02-01", "2024-02-05", Booking.BookingStatus.CONFIRMED);
        createBooking(user2, 3L, "2024-01-20", "2024-01-25", Booking.BookingStatus.CONFIRMED);

        System.out.println("✅ Initial data loaded successfully!");
        System.out.println("👤 Users created: user1/password123, user2/password123, admin/admin123");
    }

    private void createBooking(User user, Long roomId, String startDate, String endDate, Booking.BookingStatus status) {
        Booking booking = new Booking();
        booking.setUserId(user.getId());
        booking.setRoomId(roomId);
        booking.setStartDate(LocalDate.parse(startDate));
        booking.setEndDate(LocalDate.parse(endDate));
        booking.setStatus(status);
        bookingRepository.save(booking);
    }
}