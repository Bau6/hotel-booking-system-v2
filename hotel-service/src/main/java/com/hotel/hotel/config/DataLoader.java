package com.hotel.hotel.config;

import com.hotel.hotel.entity.Hotel;
import com.hotel.hotel.entity.Room;
import com.hotel.hotel.repository.HotelRepository;
import com.hotel.hotel.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataLoader implements CommandLineRunner {

    private final HotelRepository hotelRepository;
    private final RoomRepository roomRepository;

    @Override
    public void run(String... args) throws Exception {
        // Очищаем базу
        roomRepository.deleteAll();
        hotelRepository.deleteAll();

        // Создаем отели
        Hotel grandHotel = new Hotel();
        grandHotel.setName("Grand Hotel");
        grandHotel.setAddress("Moscow, Red Square 1");
        grandHotel = hotelRepository.save(grandHotel);

        Hotel luxuryResort = new Hotel();
        luxuryResort.setName("Luxury Resort");
        luxuryResort.setAddress("Sochi, Beach Avenue 25");
        luxuryResort = hotelRepository.save(luxuryResort);

        Hotel businessHotel = new Hotel();
        businessHotel.setName("Business Hotel");
        businessHotel.setAddress("St. Petersburg, Nevsky Prospect 50");
        businessHotel = hotelRepository.save(businessHotel);

        // Создаем номера для Grand Hotel
        createRoom(grandHotel, "101");
        createRoom(grandHotel, "102");
        createRoom(grandHotel, "201");
        createRoom(grandHotel, "202");
        createRoom(grandHotel, "301");

        // Создаем номера для Luxury Resort
        createRoom(luxuryResort, "Suite-1");
        createRoom(luxuryResort, "Suite-2");
        createRoom(luxuryResort, "Standard-1");

        // Создаем номера для Business Hotel
        createRoom(businessHotel, "Executive-101");
        createRoom(businessHotel, "Executive-102");
        createRoom(businessHotel, "Conference-201");

        System.out.println("✅ Initial data loaded successfully!");
    }

    private void createRoom(Hotel hotel, String number) {
        Room room = new Room();
        room.setNumber(number);
        room.setAvailable(true);
        room.setTimesBooked(0);
        room.setHotel(hotel);
        roomRepository.save(room);
    }
}