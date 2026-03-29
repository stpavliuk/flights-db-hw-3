package org.example.flights.passenger;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PassengerRepository extends CrudRepository<Passenger, String> {

    @Query("""
    SELECT * FROM passenger p
    WHERE flight_id = :flightId
    """)
    List<Passenger> findAllByFlightId(Long flightId);

    @Query("""
    SELECT COUNT(*) > 0
    FROM passenger
    WHERE flight_id = :flightId
      AND seat_row = :seatRow
      AND seat_letter = :seatLetter
    """)
    boolean existsByFlightIdAndSeatRowAndSeatLetter(Long flightId, Integer seatRow, String seatLetter);
}
