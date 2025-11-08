package com.hotel.booking.controller;

import com.hotel.booking.dto.AuthRequest;
import com.hotel.booking.dto.BookingRequest;
import com.hotel.booking.entity.Booking;
import com.hotel.booking.entity.User;
import com.hotel.booking.service.BookingService;
import com.hotel.booking.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;
    private final JwtUtil jwtUtil;

    @PostMapping("/user/register")
    public ResponseEntity<?> register(@RequestBody User user) {
        System.out.println("=== REGISTER ENDPOINT CALLED ===");
        System.out.println("Username: " + user.getUsername());

        try {
            User savedUser = bookingService.registerUser(user);
            String token = jwtUtil.generateToken(savedUser.getUsername(), savedUser.getRole());

            System.out.println("User registered successfully: " + savedUser.getUsername());
            System.out.println("Token generated");

            return ResponseEntity.ok(Map.of("token", token, "user", savedUser));
        } catch (RuntimeException e) {
            System.out.println("Registration error: " + e.getMessage());
            Map<String, String> errorResponse = Map.of("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            System.out.println("Unexpected registration error: " + e.getMessage());
            Map<String, String> errorResponse = Map.of("error", "Registration failed");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @PostMapping("/user/auth")
    public ResponseEntity<?> authenticate(@RequestBody AuthRequest authRequest) {
        System.out.println("=== AUTHENTICATE ENDPOINT CALLED ===");
        System.out.println("Username: " + authRequest.getUsername());

        try {
            User user = bookingService.authenticate(authRequest.getUsername(), authRequest.getPassword());
            String token = jwtUtil.generateToken(user.getUsername(), user.getRole());

            System.out.println("User authenticated successfully: " + user.getUsername());

            Map<String, Object> userResponse = new HashMap<>();
            userResponse.put("id", user.getId());
            userResponse.put("username", user.getUsername());
            userResponse.put("role", user.getRole());
            userResponse.put("createdAt", user.getCreatedAt());

            return ResponseEntity.ok(Map.of("token", token, "user", userResponse));
        } catch (RuntimeException e) {
            System.out.println("Authentication error: " + e.getMessage());
            Map<String, String> errorResponse = Map.of("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
        } catch (Exception e) {
            System.out.println("Unexpected authentication error: " + e.getMessage());
            Map<String, String> errorResponse = Map.of("error", "Authentication failed");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @PostMapping("/booking")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> createBooking(@RequestBody Map<String, Object> requestMap,
                                           @AuthenticationPrincipal String username) {

        System.out.println("=== CONTROLLER - MAP DATA ===");
        System.out.println("Raw map: " + requestMap);

        try {
            // ✅ Валидация обязательных полей
            if (!requestMap.containsKey("roomId") || !requestMap.containsKey("startDate") ||
                    !requestMap.containsKey("endDate")) {
                Map<String, String> errorResponse = Map.of("error", "Missing required fields: roomId, startDate, endDate");
                return ResponseEntity.badRequest().body(errorResponse);
            }

            // ✅ Создаем BookingRequest вручную с обработкой ошибок парсинга
            BookingRequest request = new BookingRequest();

            try {
                request.setRoomId(Long.valueOf(requestMap.get("roomId").toString()));
            } catch (NumberFormatException e) {
                Map<String, String> errorResponse = Map.of("error", "Invalid roomId format");
                return ResponseEntity.badRequest().body(errorResponse);
            }

            try {
                request.setStartDate(LocalDate.parse(requestMap.get("startDate").toString()));
            } catch (DateTimeParseException e) {
                Map<String, String> errorResponse = Map.of("error", "Invalid startDate format. Use YYYY-MM-DD");
                return ResponseEntity.badRequest().body(errorResponse);
            }

            try {
                request.setEndDate(LocalDate.parse(requestMap.get("endDate").toString()));
            } catch (DateTimeParseException e) {
                Map<String, String> errorResponse = Map.of("error", "Invalid endDate format. Use YYYY-MM-DD");
                return ResponseEntity.badRequest().body(errorResponse);
            }

            request.setGuestName((String) requestMap.get("guestName"));
            request.setGuestEmail((String) requestMap.get("guestEmail"));

            try {
                if (requestMap.containsKey("autoSelect")) {
                    request.setAutoSelect(Boolean.valueOf(requestMap.get("autoSelect").toString()));
                }
            } catch (Exception e) {
                Map<String, String> errorResponse = Map.of("error", "Invalid autoSelect format");
                return ResponseEntity.badRequest().body(errorResponse);
            }

            System.out.println("Manual BookingRequest:");
            System.out.println("roomId: " + request.getRoomId());
            System.out.println("startDate: " + request.getStartDate());
            System.out.println("endDate: " + request.getEndDate());
            System.out.println("autoSelect: " + request.getAutoSelect());
            System.out.println("guestName: " + request.getGuestName());
            System.out.println("guestEmail: " + request.getGuestEmail());

            Long userId = getCurrentUserId(username);
            Booking booking = bookingService.createBooking(request, userId, username);
            return ResponseEntity.ok(booking);

        } catch (RuntimeException e) {
            System.out.println("Business error in createBooking: " + e.getMessage());
            String errorMessage = e.getMessage();
            Map<String, String> errorResponse = Map.of("error", errorMessage);

            // ✅ Детальная обработка бизнес-ошибок
            if (errorMessage.contains("Room ID is required") ||
                    errorMessage.contains("Start date and end date are required") ||
                    errorMessage.contains("cannot be after") ||
                    errorMessage.contains("cannot be in the past") ||
                    errorMessage.contains("cannot exceed") ||
                    errorMessage.contains("cannot be the same") ||
                    errorMessage.contains("Invalid")) {
                return ResponseEntity.badRequest().body(errorResponse); // 400
            }
            else if (errorMessage.contains("already booked") ||
                    errorMessage.contains("not available")) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse); // 409 Conflict
            }
            else if (errorMessage.contains("No available rooms")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse); // 404
            }
            else if (errorMessage.contains("Service temporarily unavailable")) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResponse); // 503
            }
            else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse); // 500
            }
        } catch (Exception e) {
            System.out.println("Unexpected error in createBooking: " + e.getMessage());
            Map<String, String> errorResponse = Map.of("error", "Booking creation failed");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @GetMapping("/bookings")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> getUserBookings(@AuthenticationPrincipal String username) {
        System.out.println("📅 GET /bookings - User: " + username);

        try {
            Long userId = getCurrentUserId(username);
            System.out.println("📅 Found user ID: " + userId);

            List<Booking> bookings = bookingService.getUserBookings(userId);
            System.out.println("📅 Found " + bookings.size() + " bookings");

            return ResponseEntity.ok(bookings);
        } catch (RuntimeException e) {
            System.out.println("❌ Error getting bookings: " + e.getMessage());
            Map<String, String> errorResponse = Map.of("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
        } catch (Exception e) {
            System.out.println("❌ Unexpected error getting bookings: " + e.getMessage());
            Map<String, String> errorResponse = Map.of("error", "Failed to retrieve bookings");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @GetMapping("/booking/{id}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> getBooking(@PathVariable Long id,
                                        @AuthenticationPrincipal String username) {
        System.out.println("=".repeat(80));
        System.out.println("🎯 GET /booking/" + id + " - START");
        System.out.println("🔐 Authenticated user: " + username);
        System.out.println("📋 Requested booking ID: " + id);

        try {
            // Шаг 1: Получаем ID пользователя
            System.out.println("🔍 Step 1: Getting user ID for: " + username);
            Long userId = getCurrentUserId(username);
            System.out.println("✅ User ID found: " + userId);

            // Шаг 2: Получаем бронирование
            System.out.println("🔍 Step 2: Getting booking with ID: " + id + " for user ID: " + userId);
            Booking booking = bookingService.getBooking(id, userId);

            if (booking != null) {
                System.out.println("✅ SUCCESS: Booking found");
                System.out.println("   📊 Booking details:");
                System.out.println("   - Booking ID: " + booking.getId());
                System.out.println("   - User ID: " + booking.getUserId());
                System.out.println("   - Room ID: " + booking.getRoomId());
                System.out.println("   - Status: " + booking.getStatus());
                System.out.println("   - Dates: " + booking.getStartDate() + " to " + booking.getEndDate());
                return ResponseEntity.ok(booking);
            } else {
                System.out.println("❌ FAIL: Booking is null");
                Map<String, String> errorResponse = Map.of("error", "Booking not found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
            }

        } catch (RuntimeException e) {
            System.out.println("💥 ERROR in getBooking: " + e.getMessage());
            Map<String, String> errorResponse = Map.of("error", e.getMessage());

            if (e.getMessage().contains("not found") || e.getMessage().contains("not belong")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
            } else {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
            }
        } catch (Exception e) {
            System.out.println("💥 UNEXPECTED ERROR in getBooking: " + e.getMessage());
            Map<String, String> errorResponse = Map.of("error", "Failed to retrieve booking");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        } finally {
            System.out.println("🎯 GET /booking/" + id + " - END");
            System.out.println("=".repeat(80));
        }
    }

    @DeleteMapping("/booking/{id}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> cancelBooking(@PathVariable Long id,
                                           @AuthenticationPrincipal String username) {
        System.out.println("=".repeat(80));
        System.out.println("🗑️ DELETE /booking/" + id + " - START");
        System.out.println("🔐 Authenticated user: " + username);

        try {
            Long userId = getCurrentUserId(username);
            System.out.println("✅ User ID found: " + userId);

            bookingService.cancelBooking(id, userId);
            System.out.println("✅ SUCCESS: Booking cancelled");

            Map<String, String> successResponse = Map.of("message", "Booking cancelled successfully");
            return ResponseEntity.ok(successResponse);

        } catch (RuntimeException e) {
            System.out.println("💥 ERROR in cancelBooking: " + e.getMessage());
            Map<String, String> errorResponse = Map.of("error", e.getMessage());

            if (e.getMessage().contains("not found") || e.getMessage().contains("doesn't belong")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
            } else if (e.getMessage().contains("past booking")) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
            } else {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
            }
        } catch (Exception e) {
            System.out.println("💥 UNEXPECTED ERROR in cancelBooking: " + e.getMessage());
            Map<String, String> errorResponse = Map.of("error", "Failed to cancel booking");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        } finally {
            System.out.println("🗑️ DELETE /booking/" + id + " - END");
            System.out.println("=".repeat(80));
        }
    }

    // Административные endpoints
    @PostMapping("/user")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createUser(@RequestBody User user) {
        System.out.println("=== USER ENDPOINT CALLED ===");
        try {
            User savedUser = bookingService.createUser(user, user.getRole());
            return ResponseEntity.ok(savedUser);
        } catch (RuntimeException e) {
            Map<String, String> errorResponse = Map.of("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            Map<String, String> errorResponse = Map.of("error", "Failed to create user");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @DeleteMapping("/user/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        try {
            bookingService.deleteUser(id);
            Map<String, String> successResponse = Map.of("message", "User deleted successfully");
            return ResponseEntity.ok(successResponse);
        } catch (RuntimeException e) {
            Map<String, String> errorResponse = Map.of("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
        } catch (Exception e) {
            Map<String, String> errorResponse = Map.of("error", "Failed to delete user");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @PatchMapping("/user/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateUser(@PathVariable Long id, @RequestBody User user) {
        try {
            User updatedUser = bookingService.updateUser(id, user);
            return ResponseEntity.ok(updatedUser);
        } catch (RuntimeException e) {
            Map<String, String> errorResponse = Map.of("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
        } catch (Exception e) {
            Map<String, String> errorResponse = Map.of("error", "Failed to update user");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getAllUsers() {
        System.out.println("👥 GET /users - Getting all users");

        try {
            List<User> users = bookingService.getAllUsers();
            System.out.println("👥 Found " + users.size() + " users");
            return ResponseEntity.ok(users);
        } catch (Exception e) {
            System.out.println("❌ Error getting users: " + e.getMessage());
            Map<String, String> errorResponse = Map.of("error", "Failed to retrieve users");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    private Long getCurrentUserId(String username) {
        System.out.println("🔍 Getting user ID for: " + username);

        try {
            List<User> allUsers = bookingService.getAllUsers();
            System.out.println("📋 All users in DB (" + allUsers.size() + " users):");
            allUsers.forEach(u -> System.out.println("   👤 ID: " + u.getId() + " | Username: " + u.getUsername() + " | Role: " + u.getRole()));

            Long userId = allUsers.stream()
                    .filter(u -> u.getUsername().equals(username))
                    .findFirst()
                    .map(User::getId)
                    .orElseThrow(() -> {
                        System.out.println("❌ User not found: " + username);
                        return new RuntimeException("User not found: " + username);
                    });

            System.out.println("✅ User found - ID: " + userId);
            return userId;

        } catch (Exception e) {
            System.out.println("💥 ERROR in getCurrentUserId: " + e.getMessage());
            throw new RuntimeException("Failed to get user ID: " + e.getMessage());
        }
    }
}