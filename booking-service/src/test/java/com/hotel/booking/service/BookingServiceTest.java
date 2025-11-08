package com.hotel.booking.service;

import com.hotel.booking.client.HotelServiceClient;
import com.hotel.booking.dto.BookingRequest;
import com.hotel.booking.entity.Booking;
import com.hotel.booking.repository.BookingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private HotelServiceClient hotelServiceClient;

    @InjectMocks
    private BookingService bookingService;

    @Test
    void testCreateBooking_Success() {
        BookingRequest request = new BookingRequest();
        request.setRoomId(1L);
        request.setStartDate(LocalDate.now().plusDays(1));
        request.setEndDate(LocalDate.now().plusDays(3));

        when(hotelServiceClient.confirmAvailability(anyLong(), any(), any(), anyString()))
                .thenReturn(true);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking booking = inv.getArgument(0);
            booking.setId(1L);
            return booking;
        });

        Booking result = bookingService.createBooking(request, 1L, "testuser");

        assertNotNull(result);
        assertEquals(Booking.BookingStatus.CONFIRMED, result.getStatus());
        verify(hotelServiceClient).incrementTimesBooked(1L);
    }

    @Test
    void testCreateBooking_RoomNotAvailable() {
        BookingRequest request = new BookingRequest();
        request.setRoomId(1L);
        request.setStartDate(LocalDate.now().plusDays(1));
        request.setEndDate(LocalDate.now().plusDays(3));

        when(hotelServiceClient.confirmAvailability(anyLong(), any(), any(), anyString()))
                .thenReturn(false);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking booking = inv.getArgument(0);
            booking.setId(1L);
            return booking;
        });

        assertThrows(RuntimeException.class, () -> {
            bookingService.createBooking(request, 1L, "testuser");
        });
    }
}