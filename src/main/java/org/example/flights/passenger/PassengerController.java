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
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.BindParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@Controller
public class PassengerController {

    private static final String DEFAULT_SEAT_LETTER = "A";
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
                                    Model model,
                                    RedirectAttributes redirectAttributes) {
        var passenger = passengerRepository.findById(ticketNo).orElse(null);
        if (passenger == null) {
            return redirectToFlightsWithError(redirectAttributes, "Passenger not found.");
        }

        model.addAttribute("passenger", passenger);
        addPassengerPreferenceOptions(model);
        if (!model.containsAttribute("selectedMealType")) {
            model.addAttribute("selectedMealType", currentMealType(passenger));
        }
        if (!model.containsAttribute("selectedBeverageTypes")) {
            model.addAttribute("selectedBeverageTypes", currentBeverageTypes(passenger));
        }

        return "passenger/edit";
    }

    @PostMapping("/passenger/{ticketNo}/update")
    public String updatePassengerPreorder(
            @PathVariable String ticketNo,
            @BindParam("preorderedMealType") String preorderedMealType,
            @BindParam("preorderedBeverageType") String[] preorderedBeverageType,
            RedirectAttributes redirectAttributes
    ) {
        var passenger = passengerRepository.findById(ticketNo).orElse(null);
        if (passenger == null) {
            return redirectToFlightsWithError(redirectAttributes, "Passenger not found.");
        }

        var mealType = findMealType(preorderedMealType);
        if (mealType.isEmpty()) {
            return redirectToPassengerEditError(ticketNo, redirectAttributes,
                    "Meal selection is required.", preorderedMealType, normalizeSelectedBeverages(preorderedBeverageType));
        }

        var beverages = parsePreorderedBeverages(preorderedBeverageType);

        passenger.setPreorderedBeverages(beverages);
        passenger.setPreorderedMeals(Set.of(new PassengerMealType.Preordered(mealType.get().type())));
        passengerRepository.save(passenger);
        redirectAttributes.addFlashAttribute("successMessage", "Passenger preferences updated.");

        return "redirect:/flight/" + passenger.getFlightId() + "/passenger";
    }

    @GetMapping("/flight/{flightId}/passenger/create")
    public String createPassengerPage(@PathVariable Long flightId, Model model, RedirectAttributes redirectAttributes) {
        var flight = flightRepository.findById(flightId).orElse(null);
        if (flight == null) {
            return redirectToFlightsWithError(redirectAttributes, "Flight not found.");
        }

        model.addAttribute("flight", flight);
        model.addAttribute("seatLetters", SEAT_LETTERS);
        addPassengerPreferenceOptions(model);
        if (!model.containsAttribute("createPassengerForm")) {
            model.addAttribute("createPassengerForm", PassengerCreateForm.empty());
        }

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
                                  @RequestParam(required = false) String[] preorderedBeverageType,
                                  RedirectAttributes redirectAttributes) {
        var flight = flightRepository.findById(flightId).orElse(null);
        if (flight == null) {
            return redirectToFlightsWithError(redirectAttributes, "Flight not found.");
        }

        var form = buildCreatePassengerForm(ticketNo, fullName, seatRow, seatLetter, preorderedMealType, preorderedBeverageType);

        if (seatRow == null) {
            return redirectToCreatePassengerError(flightId, redirectAttributes, "Seat row is required.", form);
        }

        if (!StringUtils.hasText(form.getTicketNo())
                || !StringUtils.hasText(form.getFullName())
                || !StringUtils.hasText(form.getSeatLetter())
                || !StringUtils.hasText(creditCardNo)) {
            return redirectToCreatePassengerError(flightId, redirectAttributes,
                    "All passenger fields are required.", form);
        }

        if (!SEAT_LETTERS.contains(form.getSeatLetter())) {
            return redirectToCreatePassengerError(flightId, redirectAttributes,
                    "Seat letter must be between A and F.", form);
        }

        var mealType = findMealType(form.getPreorderedMealType());
        if (mealType.isEmpty()) {
            return redirectToCreatePassengerError(flightId, redirectAttributes,
                    "Meal selection is required.", form);
        }

        try {
            TravelClass.fromSeatRow(seatRow);
        } catch (IllegalArgumentException ex) {
            return redirectToCreatePassengerError(flightId, redirectAttributes, ex.getMessage(), form);
        }

        if (passengerRepository.existsById(form.getTicketNo())) {
            return redirectToCreatePassengerError(flightId, redirectAttributes,
                    "Passenger ticket number already exists.", form);
        }

        if (passengerRepository.existsByFlightIdAndSeatRowAndSeatLetter(flightId, seatRow, form.getSeatLetter())) {
            return redirectToCreatePassengerError(flightId, redirectAttributes,
                    "Seat is already occupied on this flight.", form);
        }

        var passenger = new Passenger();
        passenger.setTicketNo(form.getTicketNo());
        passenger.setFlightId(flightId);
        passenger.setFullName(form.getFullName());
        passenger.setSeatRow(seatRow);
        passenger.setSeatLetter(form.getSeatLetter());
        passenger.setCreditCardNo(creditCardNo.strip());
        passenger.setPreorderedMeals(Set.of(new PassengerMealType.Preordered(mealType.get().type())));
        passenger.setPreorderedBeverages(parsePreorderedBeverages(preorderedBeverageType));
        passenger.setMealsToBeServed(Set.of());
        passenger.setBeveragesToBeServed(Set.of());

        jdbcAggregateTemplate.insert(passenger);
        redirectAttributes.addFlashAttribute("successMessage", "Passenger " + form.getTicketNo() + " created.");

        return "redirect:/flight/" + flightId + "/passenger";
    }

    private void addPassengerPreferenceOptions(Model model) {
        var beverageTypes = StreamSupport.stream(beverageTypeRepository.findAll().spliterator(), false)
                .collect(Collectors.groupingBy(Beverage.Type::hoc));

        model.addAttribute("mealTypes", mealTypeRepository.findAll());
        model.addAttribute("beverageTypesH", beverageTypes.getOrDefault(Beverage.HoC.H, List.of()));
        model.addAttribute("beverageTypesC", beverageTypes.getOrDefault(Beverage.HoC.C, List.of()));
    }

    private Optional<Meal.MealType> findMealType(String preorderedMealType) {
        if (!StringUtils.hasText(preorderedMealType)) {
            return Optional.empty();
        }

        return mealTypeRepository.findById(preorderedMealType.strip());
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

    private Set<String> normalizeSelectedBeverages(String[] preorderedBeverageType) {
        return Stream.ofNullable(preorderedBeverageType)
                .flatMap(Arrays::stream)
                .filter(StringUtils::hasText)
                .map(String::strip)
                .collect(Collectors.toSet());
    }

    private String currentMealType(Passenger passenger) {
        return Stream.ofNullable(passenger.getPreorderedMeals())
                .flatMap(Set::stream)
                .map(PassengerMealType::getMealType)
                .findFirst()
                .orElse("");
    }

    private Set<String> currentBeverageTypes(Passenger passenger) {
        return Stream.ofNullable(passenger.getPreorderedBeverages())
                .flatMap(Set::stream)
                .map(PassengerBeverage::getBeverageType)
                .collect(Collectors.toSet());
    }

    private PassengerCreateForm buildCreatePassengerForm(String ticketNo,
                                                         String fullName,
                                                         Integer seatRow,
                                                         String seatLetter,
                                                         String preorderedMealType,
                                                         String[] preorderedBeverageType) {
        return new PassengerCreateForm(
                normalizeUpper(ticketNo),
                normalizeText(fullName),
                seatRow,
                normalizeUpper(seatLetter),
                normalizeText(preorderedMealType),
                normalizeSelectedBeverages(preorderedBeverageType)
        );
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.strip();
    }

    private String normalizeUpper(String value) {
        return normalizeText(value).toUpperCase();
    }

    private String redirectToCreatePassengerError(Long flightId,
                                                  RedirectAttributes redirectAttributes,
                                                  String message,
                                                  PassengerCreateForm form) {
        redirectAttributes.addFlashAttribute("errorMessage", message);
        redirectAttributes.addFlashAttribute("createPassengerForm", form);
        return "redirect:/flight/" + flightId + "/passenger/create";
    }

    private String redirectToPassengerEditError(String ticketNo,
                                                RedirectAttributes redirectAttributes,
                                                String message,
                                                String selectedMealType,
                                                Set<String> selectedBeverageTypes) {
        redirectAttributes.addFlashAttribute("errorMessage", message);
        redirectAttributes.addFlashAttribute("selectedMealType", normalizeText(selectedMealType));
        redirectAttributes.addFlashAttribute("selectedBeverageTypes", selectedBeverageTypes);
        return "redirect:/passenger/" + ticketNo + "/edit";
    }

    private String redirectToFlightsWithError(RedirectAttributes redirectAttributes, String message) {
        redirectAttributes.addFlashAttribute("errorMessage", message);
        return "redirect:/flight";
    }

    public static final class PassengerCreateForm {
        private final String ticketNo;
        private final String fullName;
        private final Integer seatRow;
        private final String seatLetter;
        private final String preorderedMealType;
        private final Set<String> preorderedBeverageTypes;

        private PassengerCreateForm(String ticketNo,
                                    String fullName,
                                    Integer seatRow,
                                    String seatLetter,
                                    String preorderedMealType,
                                    Set<String> preorderedBeverageTypes) {
            this.ticketNo = ticketNo;
            this.fullName = fullName;
            this.seatRow = seatRow;
            this.seatLetter = StringUtils.hasText(seatLetter) ? seatLetter : DEFAULT_SEAT_LETTER;
            this.preorderedMealType = preorderedMealType;
            this.preorderedBeverageTypes = Set.copyOf(preorderedBeverageTypes);
        }

        public static PassengerCreateForm empty() {
            return new PassengerCreateForm("", "", null, DEFAULT_SEAT_LETTER, "", Set.of());
        }

        public String getTicketNo() {
            return ticketNo;
        }

        public String getFullName() {
            return fullName;
        }

        public Integer getSeatRow() {
            return seatRow;
        }

        public String getSeatLetter() {
            return seatLetter;
        }

        public String getPreorderedMealType() {
            return preorderedMealType;
        }

        public Set<String> getPreorderedBeverageTypes() {
            return preorderedBeverageTypes;
        }
    }
}
