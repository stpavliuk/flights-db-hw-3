package org.example.flights.passenger;

import org.example.flights.beverage.Beverage;
import org.example.flights.beverage.BeverageTypeRepository;
import org.example.flights.flight.FlightRepository;
import org.example.flights.meal.Meal;
import org.example.flights.meal.MealTypeRepository;
import org.example.flights.passenger.preorder.PassengerBeverage;
import org.example.flights.passenger.preorder.PassengerMealType;
import org.example.flights.passenger.tclass.TravelClass;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.BindParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@Controller
public class PassengerController {

    private static final List<String> SEAT_LETTERS = List.of("A", "B", "C", "D", "E", "F");

    private final PassengerRepository    passengerRepository;
    private final MealTypeRepository     mealTypeRepository;
    private final BeverageTypeRepository beverageTypeRepository;
    private final FlightRepository flightRepository;
    private final JdbcAggregateTemplate jdbcAggregateTemplate;

    public PassengerController(final PassengerRepository passengerRepository,
                               final MealTypeRepository mealTypeRepository,
                               final BeverageTypeRepository beverageTypeRepository,
                               final FlightRepository flightRepository,
                               final JdbcAggregateTemplate jdbcAggregateTemplate) {
        this.passengerRepository = passengerRepository;
        this.mealTypeRepository = mealTypeRepository;
        this.beverageTypeRepository = beverageTypeRepository;
        this.flightRepository = flightRepository;
        this.jdbcAggregateTemplate = jdbcAggregateTemplate;
    }

    @GetMapping("/flight/{flightId}/passenger")
    public String listAll(@PathVariable Long flightId, Model model) {
        var passengers = passengerRepository.findAllByFlightId(flightId);

        model.addAttribute("passengers", passengers);
        model.addAttribute("flightId", flightId);

        return "passenger/list";
    }

    @GetMapping("/passenger/{ticketNo}/edit")
    public String editPassengerMeal(@PathVariable String ticketNo,
                                    Model model) {
        var passenger = passengerRepository.findById(ticketNo)
                .orElseThrow(() -> new IllegalArgumentException("Invalid ticket number:" + ticketNo));

        model.addAttribute("passenger", passenger);
        addPassengerPreferenceOptions(model);

        return "passenger/edit";
    }

    @PostMapping("/passenger/{ticketNo}/update")
    public String updatePassengerPreorder(
            @PathVariable String ticketNo,
            @BindParam("preorderedMealType") String preorderedMealType,
            @BindParam("preorderedBeverageType") String[] preorderedBeverageType
    ) {
        var passenger = passengerRepository.findById(ticketNo).orElseThrow(
                () -> new IllegalArgumentException("Invalid ticket number:" + ticketNo));

        var mealType = mealTypeRepository.findById(preorderedMealType)
                .map(it -> Set.of(new PassengerMealType.Preordered(it.type())))
                .orElseGet(Set::of);

        var beverages = Arrays.stream(preorderedBeverageType)
                .map(beverageTypeRepository::findById)
                .flatMap(Optional::stream)
                .map(it -> new PassengerBeverage.Preordered(it.type()))
                .collect(Collectors.toSet());

        passenger.setPreorderedBeverages(beverages);
        passenger.setPreorderedMeals(mealType);
        passengerRepository.save(passenger);


        return "redirect:/flight/" + passenger.getFlightId() + "/passenger";
    }

    @GetMapping("/flight/{flightId}/passenger/create")
    public String createPassengerPage(@PathVariable Long flightId, Model model) {
        var flight = flightRepository.findById(flightId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Flight not found."));

        model.addAttribute("flight", flight);
        model.addAttribute("seatLetters", SEAT_LETTERS);
        addPassengerPreferenceOptions(model);

        return "passenger/create";
    }

    @PostMapping("/flight/{flightId}/passenger/create")
    public String createPassenger(@PathVariable Long flightId,
                                  @RequestParam String ticketNo,
                                  @RequestParam String fullName,
                                  @RequestParam Integer seatRow,
                                  @RequestParam String seatLetter,
                                  @RequestParam String creditCardNo,
                                  @RequestParam(required = false) String preorderedMealType,
                                  @RequestParam(required = false) String[] preorderedBeverageType) {
        flightRepository.findById(flightId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Flight not found."));

        if (seatRow == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seat row is required.");
        }

        var normalizedTicketNo = ticketNo.strip().toUpperCase();
        var normalizedFullName = fullName.strip();
        var normalizedSeatLetter = seatLetter.strip().toUpperCase();
        var normalizedCreditCardNo = creditCardNo.strip();

        if (!StringUtils.hasText(normalizedTicketNo)
                || !StringUtils.hasText(normalizedFullName)
                || !StringUtils.hasText(normalizedSeatLetter)
                || !StringUtils.hasText(normalizedCreditCardNo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "All passenger fields are required.");
        }

        if (!SEAT_LETTERS.contains(normalizedSeatLetter)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seat letter must be between A and F.");
        }

        var mealType = parseRequiredMealType(preorderedMealType);

        try {
            TravelClass.fromSeatRow(seatRow);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }

        if (passengerRepository.existsById(normalizedTicketNo)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Passenger ticket number already exists.");
        }

        if (passengerRepository.existsByFlightIdAndSeatRowAndSeatLetter(flightId, seatRow, normalizedSeatLetter)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Seat is already occupied on this flight.");
        }

        var passenger = new Passenger();
        passenger.setTicketNo(normalizedTicketNo);
        passenger.setFlightId(flightId);
        passenger.setFullName(normalizedFullName);
        passenger.setSeatRow(seatRow);
        passenger.setSeatLetter(normalizedSeatLetter);
        passenger.setCreditCardNo(normalizedCreditCardNo);
        passenger.setPreorderedMeals(Set.of(new PassengerMealType.Preordered(mealType.type())));
        passenger.setPreorderedBeverages(parsePreorderedBeverages(preorderedBeverageType));
        passenger.setMealsToBeServed(Set.of());
        passenger.setBeveragesToBeServed(Set.of());

        jdbcAggregateTemplate.insert(passenger);

        return "redirect:/flight/" + flightId + "/passenger";
    }

    private void addPassengerPreferenceOptions(Model model) {
        var beverageTypes = StreamSupport.stream(beverageTypeRepository.findAll().spliterator(), false)
                .collect(Collectors.groupingBy(Beverage.Type::hoc));

        model.addAttribute("mealTypes", mealTypeRepository.findAll());
        model.addAttribute("beverageTypesH", beverageTypes.get(Beverage.HoC.H));
        model.addAttribute("beverageTypesC", beverageTypes.get(Beverage.HoC.C));
    }

    private Meal.MealType parseRequiredMealType(String preorderedMealType) {
        if (!StringUtils.hasText(preorderedMealType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Meal selection is required.");
        }

        return mealTypeRepository.findById(preorderedMealType.strip())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Meal selection is required."));
    }

    private Set<PassengerBeverage.Preordered> parsePreorderedBeverages(String[] preorderedBeverageType) {
        return Stream.ofNullable(preorderedBeverageType)
                .flatMap(Arrays::stream)
                .filter(StringUtils::hasText)
                .map(String::strip)
                .map(beverageTypeRepository::findById)
                .flatMap(Optional::stream)
                .map(it -> new PassengerBeverage.Preordered(it.type()))
                .collect(Collectors.toSet());
    }
}
