package com.hotel.booking.service;

import com.hotel.booking.client.HotelServiceClient;
import com.hotel.booking.dto.BookingRequest;
import com.hotel.booking.entity.Booking;
import com.hotel.booking.entity.User;
import com.hotel.booking.repository.BookingRepository;
import com.hotel.booking.repository.UserRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final HotelServiceClient hotelServiceClient;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    @CircuitBreaker(name = "hotelService", fallbackMethod = "fallbackCreateBooking")
    @Retry(name = "hotelService")
    public Booking createBooking(BookingRequest request, Long userId, String username) {
        System.out.println("=".repeat(80));
        System.out.println("🎯 BOOKING SERVICE - createBooking START");
        System.out.println("   📥 Input parameters:");
        System.out.println("   - User ID: " + userId);
        System.out.println("   - Username: " + username);
        System.out.println("   - Room ID: " + request.getRoomId());
        System.out.println("   - Dates: " + request.getStartDate() + " to " + request.getEndDate());
        System.out.println("   - Auto-select: " + request.getAutoSelect());

        String requestId = UUID.randomUUID().toString();
        Long roomId = request.getRoomId();

        try {
            // ✅ ВАЛИДАЦИЯ 1: Проверка обязательных полей
            System.out.println("🔍 Step 1: Validating required fields");
            if (request.getRoomId() == null) {
                throw new RuntimeException("Room ID is required");
            }
            if (request.getStartDate() == null || request.getEndDate() == null) {
                throw new RuntimeException("Start date and end date are required");
            }
            if (userId == null) {
                throw new RuntimeException("User ID is required");
            }

            // ✅ ВАЛИДАЦИЯ 2: Проверка корректности дат
            System.out.println("🔍 Step 2: Validating dates");
            if (request.getStartDate().isAfter(request.getEndDate())) {
                throw new RuntimeException("Start date cannot be after end date");
            }
            if (request.getStartDate().isBefore(LocalDate.now())) {
                throw new RuntimeException("Start date cannot be in the past");
            }
            if (request.getStartDate().equals(request.getEndDate())) {
                throw new RuntimeException("Start date and end date cannot be the same");
            }

            // ✅ ВАЛИДАЦИЯ 3: Проверка максимальной длительности бронирования
            long bookingDays = ChronoUnit.DAYS.between(request.getStartDate(), request.getEndDate());
            if (bookingDays > 30) {
                throw new RuntimeException("Booking cannot exceed 30 days");
            }

            // ✅ ВАЛИДАЦИЯ 4: Проверка существующих бронирований (защита от дубликатов)
            System.out.println("🔍 Step 3: Checking for overlapping bookings");
            List<Booking> existingBookings = bookingRepository.findByRoomIdAndStatusIn(
                    roomId, List.of(Booking.BookingStatus.PENDING, Booking.BookingStatus.CONFIRMED));

            boolean hasOverlap = existingBookings.stream()
                    .anyMatch(booking -> isDateRangeOverlapping(
                            booking.getStartDate(), booking.getEndDate(),
                            request.getStartDate(), request.getEndDate()));

            if (hasOverlap) {
                System.out.println("❌ CONFLICT: Room already booked for these dates");
                existingBookings.forEach(booking ->
                        System.out.println("   - Existing: ID=" + booking.getId() +
                                ", " + booking.getStartDate() + " to " + booking.getEndDate())
                );
                throw new RuntimeException("Room is already booked for the selected dates");
            }

            // ✅ ВАЛИДАЦИЯ 5: Автоподбор комнаты если нужно
            if (Boolean.TRUE.equals(request.getAutoSelect())) {
                System.out.println("🔍 Step 4: Auto-selecting room");
                List<Object> recommendedRooms = hotelServiceClient.getRecommendedRooms(
                        request.getStartDate(), request.getEndDate());

                if (!recommendedRooms.isEmpty()) {
                    roomId = Long.valueOf(recommendedRooms.get(0).toString());
                    System.out.println("✅ Auto-selected room ID: " + roomId);

                    // Повторная проверка для автовыбранной комнаты
                    List<Booking> autoExistingBookings = bookingRepository.findByRoomIdAndStatusIn(
                            roomId, List.of(Booking.BookingStatus.PENDING, Booking.BookingStatus.CONFIRMED));

                    boolean autoHasOverlap = autoExistingBookings.stream()
                            .anyMatch(booking -> isDateRangeOverlapping(
                                    booking.getStartDate(), booking.getEndDate(),
                                    request.getStartDate(), request.getEndDate()));

                    if (autoHasOverlap) {
                        throw new RuntimeException("Auto-selected room is not available for the selected dates");
                    }
                } else {
                    throw new RuntimeException("No available rooms found for auto-selection");
                }
            }

            // ✅ Шаг 6: Создание бронирования в статусе PENDING
            System.out.println("🔍 Step 5: Creating booking in PENDING state");
            Booking booking = new Booking();
            booking.setUserId(userId);
            booking.setRoomId(roomId);
            booking.setStartDate(request.getStartDate());
            booking.setEndDate(request.getEndDate());
            booking.setStatus(Booking.BookingStatus.PENDING);

            booking = bookingRepository.save(booking);
            System.out.println("✅ Booking created with ID: " + booking.getId() + " in PENDING state");

            // ✅ Шаг 7: Подтверждение доступности в Hotel Service
            System.out.println("🔍 Step 6: Confirming availability with hotel service");
            System.out.println("   - Request ID: " + requestId);
            System.out.println("   - Room ID: " + roomId);
            System.out.println("   - Dates: " + request.getStartDate() + " to " + request.getEndDate());

            boolean isAvailable = hotelServiceClient.confirmAvailability(
                    roomId, request.getStartDate(), request.getEndDate(), requestId);

            if (isAvailable) {
                // ✅ Шаг 8: Подтверждение бронирования
                System.out.println("✅ Room is available, confirming booking");
                booking.setStatus(Booking.BookingStatus.CONFIRMED);
                booking = bookingRepository.save(booking);

                // ✅ Шаг 9: Инкремент счетчика бронирований
                try {
                    hotelServiceClient.incrementTimesBooked(roomId);
                    System.out.println("✅ Booking counter incremented for room: " + roomId);
                } catch (Exception e) {
                    System.out.println("⚠️ Failed to increment booking counter: " + e.getMessage());
                    // Не прерываем процесс, т.к. бронирование уже создано
                }

                System.out.println("🎉 SUCCESS: Booking " + booking.getId() + " confirmed successfully");
                return booking;
            } else {
                // ✅ КОМПЕНСАЦИЯ: отмена бронирования если комната недоступна
                System.out.println("❌ Room not available, cancelling booking");
                booking.setStatus(Booking.BookingStatus.CANCELLED);
                bookingRepository.save(booking);
                throw new RuntimeException("Room not available at hotel service");
            }

        } catch (Exception e) {
            System.out.println("💥 ERROR in createBooking:");
            System.out.println("   - Exception: " + e.getClass().getName());
            System.out.println("   - Message: " + e.getMessage());

            // ✅ КОМПЕНСАЦИЯ ПРИ ОШИБКАХ: снятие блокировки в Hotel Service
            try {
                hotelServiceClient.releaseRoom(roomId, requestId);
                System.out.println("🔓 Room lock released for room: " + roomId);
            } catch (Exception ex) {
                System.out.println("⚠️ Error releasing room lock: " + ex.getMessage());
            }

            throw new RuntimeException("Booking failed: " + e.getMessage());
        } finally {
            System.out.println("🎯 BOOKING SERVICE - createBooking END");
            System.out.println("=".repeat(80));
        }
    }

    // ✅ ВСПОМОГАТЕЛЬНЫЙ МЕТОД: Проверка пересечения дат
    private boolean isDateRangeOverlapping(LocalDate start1, LocalDate end1, LocalDate start2, LocalDate end2) {
        return !(end1.isBefore(start2) || start1.isAfter(end2));
    }

    public Booking fallbackCreateBooking(BookingRequest request, Long userId, String username, Exception e) {
        System.out.println("🔄 FALLBACK: createBooking fallback activated");
        System.out.println("   - Error: " + e.getMessage());
        throw new RuntimeException("Service temporarily unavailable");
    }

    public List<Booking> getUserBookings(Long userId) {
        System.out.println("🔍 Getting bookings for user ID: " + userId);
        List<Booking> bookings = bookingRepository.findByUserId(userId);
        System.out.println("✅ Found " + bookings.size() + " bookings for user " + userId);
        return bookings;
    }

    public Booking getBooking(Long id, Long userId) {
        System.out.println("=".repeat(80));
        System.out.println("🔍 BOOKING SERVICE - getBooking START");
        System.out.println("   📥 Input parameters:");
        System.out.println("   - Booking ID: " + id);
        System.out.println("   - User ID: " + userId);

        try {
            // Шаг 1: Ищем бронирование по ID
            System.out.println("🔍 Step 1: Searching for booking with ID: " + id);
            Optional<Booking> bookingOpt = bookingRepository.findById(id);

            if (bookingOpt.isEmpty()) {
                System.out.println("❌ FAIL: No booking found with ID: " + id);
                System.out.println("   📋 All bookings in database:");
                List<Booking> allBookings = bookingRepository.findAll();
                if (allBookings.isEmpty()) {
                    System.out.println("   💡 No bookings found in database at all!");
                } else {
                    allBookings.forEach(b -> System.out.println("      📅 ID: " + b.getId() +
                            " | User: " + b.getUserId() +
                            " | Room: " + b.getRoomId() +
                            " | Status: " + b.getStatus()));
                }
                throw new RuntimeException("Booking not found");
            }

            Booking booking = bookingOpt.get();
            System.out.println("✅ Booking found in database:");
            System.out.println("   - Booking ID: " + booking.getId());
            System.out.println("   - User ID: " + booking.getUserId());
            System.out.println("   - Room ID: " + booking.getRoomId());
            System.out.println("   - Status: " + booking.getStatus());
            System.out.println("   - Dates: " + booking.getStartDate() + " to " + booking.getEndDate());

            // Шаг 2: Проверяем, принадлежит ли бронирование пользователю
            System.out.println("🔍 Step 2: Checking if booking belongs to user");
            System.out.println("   - Booking user ID: " + booking.getUserId());
            System.out.println("   - Requested user ID: " + userId);
            System.out.println("   - Match: " + booking.getUserId().equals(userId));

            if (!booking.getUserId().equals(userId)) {
                System.out.println("❌ FAIL: Booking belongs to different user!");
                System.out.println("   💡 This booking belongs to user ID: " + booking.getUserId());
                System.out.println("   💡 But you are user ID: " + userId);

                // Покажем все бронирования текущего пользователя для помощи
                System.out.println("   📋 Your bookings (user ID: " + userId + "):");
                List<Booking> userBookings = bookingRepository.findByUserId(userId);
                if (userBookings.isEmpty()) {
                    System.out.println("      💡 You have no bookings");
                } else {
                    userBookings.forEach(b -> System.out.println("      📅 ID: " + b.getId() +
                            " | Room: " + b.getRoomId() +
                            " | Status: " + b.getStatus() +
                            " | Dates: " + b.getStartDate() + " to " + b.getEndDate()));
                }
                throw new RuntimeException("Booking not found");
            }

            System.out.println("✅ SUCCESS: Booking belongs to user!");
            return booking;

        } catch (Exception e) {
            System.out.println("💥 ERROR in BookingService.getBooking:");
            System.out.println("   - Exception type: " + e.getClass().getName());
            System.out.println("   - Message: " + e.getMessage());
            throw e;
        } finally {
            System.out.println("🔍 BOOKING SERVICE - getBooking END");
            System.out.println("=".repeat(80));
        }
    }

    @Transactional
    public void cancelBooking(Long id, Long userId) {
        System.out.println("=".repeat(80));
        System.out.println("🗑️ BOOKING SERVICE - cancelBooking START");
        System.out.println("   📥 Input parameters:");
        System.out.println("   - Booking ID: " + id);
        System.out.println("   - User ID: " + userId);

        try {
            // Шаг 1: Находим бронирование
            System.out.println("🔍 Step 1: Finding booking with ID: " + id);
            Optional<Booking> bookingOpt = bookingRepository.findById(id)
                    .filter(b -> b.getUserId().equals(userId));

            if (bookingOpt.isEmpty()) {
                System.out.println("❌ FAIL: Booking not found or doesn't belong to user");
                throw new RuntimeException("Booking not found");
            }

            Booking booking = bookingOpt.get();
            System.out.println("✅ Booking found:");
            System.out.println("   - ID: " + booking.getId());
            System.out.println("   - Status: " + booking.getStatus());
            System.out.println("   - Room: " + booking.getRoomId());

            // Шаг 2: Отменяем если подтверждено
            if (booking.getStatus() == Booking.BookingStatus.CONFIRMED) {
                System.out.println("🔍 Step 2: Cancelling confirmed booking");
                booking.setStatus(Booking.BookingStatus.CANCELLED);
                bookingRepository.save(booking);
                System.out.println("✅ SUCCESS: Booking " + id + " cancelled by user " + userId);
            } else {
                System.out.println("⚠️ Booking is already in status: " + booking.getStatus());
            }

        } catch (Exception e) {
            System.out.println("💥 ERROR in cancelBooking:");
            System.out.println("   - Exception: " + e.getClass().getName());
            System.out.println("   - Message: " + e.getMessage());
            throw e;
        } finally {
            System.out.println("🗑️ BOOKING SERVICE - cancelBooking END");
            System.out.println("=".repeat(80));
        }
    }

    public User authenticate(String username, String password) {
        System.out.println("=".repeat(80));
        System.out.println("🔐 BOOKING SERVICE - authenticate START");
        System.out.println("   📥 Username: " + username);

        try {
            System.out.println("🔍 Step 1: Finding user by username");
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> {
                        System.out.println("❌ User not found: " + username);
                        return new RuntimeException("User not found");
                    });

            System.out.println("✅ User found:");
            System.out.println("   - ID: " + user.getId());
            System.out.println("   - Username: " + user.getUsername());
            System.out.println("   - Role: " + user.getRole());

            System.out.println("🔍 Step 2: Checking password");
            System.out.println("   - Input password: " + password);
            System.out.println("   - Stored password (hashed): " + user.getPassword());

            boolean passwordMatches = passwordEncoder.matches(password, user.getPassword());
            System.out.println("   - Password matches: " + passwordMatches);

            if (!passwordMatches) {
                System.out.println("❌ Password mismatch for user: " + username);
                throw new RuntimeException("Invalid password");
            }

            System.out.println("✅ SUCCESS: Authentication successful for: " + username);
            return user;

        } catch (Exception e) {
            System.out.println("💥 ERROR in authenticate:");
            System.out.println("   - Exception: " + e.getClass().getName());
            System.out.println("   - Message: " + e.getMessage());
            throw e;
        } finally {
            System.out.println("🔐 BOOKING SERVICE - authenticate END");
            System.out.println("=".repeat(80));
        }
    }

    public User registerUser(User user) {
        System.out.println("=".repeat(80));
        System.out.println("👤 BOOKING SERVICE - registerUser START");
        System.out.println("   📥 Username: " + user.getUsername());

        try {
            System.out.println("🔍 Step 1: Checking if username exists");
            if (userRepository.existsByUsername(user.getUsername())) {
                System.out.println("❌ Username already exists: " + user.getUsername());
                throw new RuntimeException("Username already exists");
            }

            System.out.println("🔍 Step 2: Setting up user");
            user.setRole("USER");
            String rawPassword = user.getPassword();
            user.setPassword(passwordEncoder.encode(rawPassword));
            System.out.println("   - Role set to: USER");
            System.out.println("   - Password hashed");

            User savedUser = userRepository.save(user);
            System.out.println("✅ SUCCESS: User registered with ID: " + savedUser.getId());
            return savedUser;

        } catch (Exception e) {
            System.out.println("💥 ERROR in registerUser:");
            System.out.println("   - Exception: " + e.getClass().getName());
            System.out.println("   - Message: " + e.getMessage());
            throw e;
        } finally {
            System.out.println("👤 BOOKING SERVICE - registerUser END");
            System.out.println("=".repeat(80));
        }
    }

    public User createUser(User user, String role) {
        System.out.println("=".repeat(80));
        System.out.println("👤 BOOKING SERVICE - createUser START");
        System.out.println("   📥 Username: " + user.getUsername());
        System.out.println("   📥 Role: " + role);

        try {
            System.out.println("🔍 Step 1: Checking if username exists");
            if (userRepository.existsByUsername(user.getUsername())) {
                System.out.println("❌ Username already exists: " + user.getUsername());
                throw new RuntimeException("Username already exists");
            }

            System.out.println("🔍 Step 2: Setting up user");
            user.setRole(role);
            String rawPassword = user.getPassword();
            user.setPassword(passwordEncoder.encode(rawPassword));
            System.out.println("   - Role set to: " + role);
            System.out.println("   - Password hashed");

            User savedUser = userRepository.save(user);
            System.out.println("✅ SUCCESS: User created with ID: " + savedUser.getId());
            return savedUser;

        } catch (Exception e) {
            System.out.println("💥 ERROR in createUser:");
            System.out.println("   - Exception: " + e.getClass().getName());
            System.out.println("   - Message: " + e.getMessage());
            throw e;
        } finally {
            System.out.println("👤 BOOKING SERVICE - createUser END");
            System.out.println("=".repeat(80));
        }
    }

    public void deleteUser(Long id) {
        System.out.println("=".repeat(80));
        System.out.println("🗑️ BOOKING SERVICE - deleteUser START");
        System.out.println("   📥 User ID: " + id);

        try {
            System.out.println("🔍 Step 1: Checking if user exists");
            if (!userRepository.existsById(id)) {
                System.out.println("❌ User not found with ID: " + id);
                throw new RuntimeException("User not found");
            }

            System.out.println("🔍 Step 2: Deleting user");
            userRepository.deleteById(id);
            System.out.println("✅ SUCCESS: User deleted with ID: " + id);

        } catch (Exception e) {
            System.out.println("💥 ERROR in deleteUser:");
            System.out.println("   - Exception: " + e.getClass().getName());
            System.out.println("   - Message: " + e.getMessage());
            throw e;
        } finally {
            System.out.println("🗑️ BOOKING SERVICE - deleteUser END");
            System.out.println("=".repeat(80));
        }
    }

    public User updateUser(Long id, User userDetails) {
        System.out.println("=".repeat(80));
        System.out.println("✏️ BOOKING SERVICE - updateUser START");
        System.out.println("   📥 User ID: " + id);
        System.out.println("   📥 New username: " + userDetails.getUsername());

        try {
            System.out.println("🔍 Step 1: Finding user by ID");
            User user = userRepository.findById(id)
                    .orElseThrow(() -> {
                        System.out.println("❌ User not found with ID: " + id);
                        return new RuntimeException("User not found");
                    });

            System.out.println("✅ User found:");
            System.out.println("   - Current username: " + user.getUsername());
            System.out.println("   - Current role: " + user.getRole());

            System.out.println("🔍 Step 2: Updating user details");
            user.setUsername(userDetails.getUsername());
            System.out.println("   - Username updated to: " + userDetails.getUsername());

            if (userDetails.getPassword() != null && !userDetails.getPassword().isEmpty()) {
                user.setPassword(passwordEncoder.encode(userDetails.getPassword()));
                System.out.println("   - Password updated and hashed");
            } else {
                System.out.println("   - Password not changed");
            }

            User updatedUser = userRepository.save(user);
            System.out.println("✅ SUCCESS: User updated with ID: " + updatedUser.getId());
            return updatedUser;

        } catch (Exception e) {
            System.out.println("💥 ERROR in updateUser:");
            System.out.println("   - Exception: " + e.getClass().getName());
            System.out.println("   - Message: " + e.getMessage());
            throw e;
        } finally {
            System.out.println("✏️ BOOKING SERVICE - updateUser END");
            System.out.println("=".repeat(80));
        }
    }

    public List<User> getAllUsers() {
        System.out.println("🔍 Getting all users from database");
        List<User> users = userRepository.findAll();
        System.out.println("✅ Found " + users.size() + " users");
        return users;
    }
}