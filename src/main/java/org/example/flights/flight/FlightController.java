package org.example.flights.flight;

import lombok.Getter;
import lombok.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Set;

@Controller
@RequestMapping("/flight")
public class FlightController {

    private final FlightRepository flightRepository;

    public FlightController(final FlightRepository flightRepository) {
        this.flightRepository = flightRepository;
    }

    @GetMapping
    public String listAll(Model model) {
        var flights = new ArrayList<Flight>();
        flightRepository.findAll().forEach(flights::add);

        model.addAttribute("flights", flights);

        return "flight/list";
    }

    @GetMapping("/create")
    public String createFlightPage(Model model) {
        addFlightReferenceData(model);
        if (!model.containsAttribute("flightCreateForm")) {
            model.addAttribute("flightCreateForm", FlightCreateForm.empty());
        }

        return "flight/create";
    }

    @PostMapping("/create")
    public String createFlight(@RequestParam(required = false) String no,
                               @RequestParam(required = false) String departureDate,
                               @RequestParam(required = false) String departureTime,
                               @RequestParam(required = false) String checkInTime,
                               @RequestParam(required = false) String arrivalDate,
                               @RequestParam(required = false) String arrivalTime,
                               @RequestParam(required = false) String fromAirportCode,
                               @RequestParam(required = false) String toAirportCode,
                               @RequestParam(required = false) String airlineCode,
                               @RequestParam(required = false) String aircraftNumber,
                               RedirectAttributes redirectAttributes) {
        var form = new FlightCreateForm(
            normalizeText(no),
            normalizeText(departureDate),
            normalizeText(departureTime),
            normalizeText(checkInTime),
            normalizeText(arrivalDate),
            normalizeText(arrivalTime),
            normalizeUpper(fromAirportCode),
            normalizeUpper(toAirportCode),
            normalizeUpper(airlineCode),
            normalizeText(aircraftNumber)
        );

        if (!form.hasRequiredFields()) {
            return redirectToCreateFlightError(redirectAttributes, "All flight fields are required.", form);
        }

        var parsedDepartureDate = parseDate(form.departureDate());
        var parsedDepartureTime = parseTime(form.departureTime());
        var parsedCheckInTime = parseTime(form.getCheckInTime());
        var parsedArrivalDate = parseDate(form.getArrivalDate());
        var parsedArrivalTime = parseTime(form.getArrivalTime());

        if (parsedDepartureDate == null || parsedDepartureTime == null || parsedCheckInTime == null
            || parsedArrivalDate == null || parsedArrivalTime == null) {
            return redirectToCreateFlightError(redirectAttributes,
                "Use valid date and time values for the flight schedule.", form);
        }

        if (form.getFromAirportCode().equals(form.getToAirportCode())) {
            return redirectToCreateFlightError(redirectAttributes,
                "Departure and arrival airports must be different.", form);
        }

        if (parsedArrivalDate.atTime(parsedArrivalTime).isBefore(parsedDepartureDate.atTime(parsedDepartureTime))) {
            return redirectToCreateFlightError(redirectAttributes,
                "Arrival must be later than departure.", form);
        }

        if (!flightRepository.existsAirportCode(form.getFromAirportCode())
            || !flightRepository.existsAirportCode(form.getToAirportCode())) {
            return redirectToCreateFlightError(redirectAttributes,
                "Choose valid airport codes from the reference list.", form);
        }

        if (!flightRepository.existsAirlineCode(form.getAirlineCode())) {
            return redirectToCreateFlightError(redirectAttributes,
                "Choose a valid airline code.", form);
        }

        if (flightRepository.existsByNoAndDepartureDate(form.no(), parsedDepartureDate)) {
            return redirectToCreateFlightError(redirectAttributes,
                "A flight with that number already exists on the selected departure date.", form);
        }

        var flight = createFlight(form, parsedDepartureDate, parsedDepartureTime, parsedCheckInTime,
            parsedArrivalDate, parsedArrivalTime);

        flightRepository.save(flight);
        redirectAttributes.addFlashAttribute("successMessage", "Flight " + form.no() + " created.");

        return "redirect:/flight";
    }

    @GetMapping("/{id}/meals/load")
    public String loadFlightPreview(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        var flight = getFlight(id, redirectAttributes);
        if (flight == null) return "redirect:/flight";

        var mealsToLoad = flightRepository.findMealsToLoadByFlightId(id);
        var beveragesToLoad = flightRepository.findBeveragesToLoadByFlightId(id);

        model.addAttribute("flight", flight);
        model.addAttribute("mealsToLoad", mealsToLoad);
        model.addAttribute("beveragesToLoad", beveragesToLoad);

        return "flight/load";
    }

    @Nullable
    private Flight getFlight(final Long id, final RedirectAttributes redirectAttributes) {
        var flight = flightRepository.findById(id).orElse(null);
        if (flight == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Flight not found.");
            return null;
        }
        return flight;
    }

    @GetMapping("/{id}/meals/load/confirm")
    public String loadFlight(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        var flight = getFlight(id, redirectAttributes);
        if (flight == null) return "redirect:/flight";

        var loadedMeals = flightRepository.findMealsToLoadByFlightId(id);
        var loadedBeverages = flightRepository.findBeveragesToLoadByFlightId(id);

        flight.setLoadedMeals(loadedMeals);
        flight.setLoadedBeverages(loadedBeverages);

        flightRepository.save(flight);
        redirectAttributes.addFlashAttribute("successMessage",
            "Load plan saved for flight " + flight.getNo() + ".");

        return "redirect:/flight";
    }

    private void addFlightReferenceData(Model model) {
        model.addAttribute("airportCodes", flightRepository.findAirportCodes());
        model.addAttribute("airlineCodes", flightRepository.findAirlineCodes());
        model.addAttribute("aircraftAssignments", flightRepository.findAircraftAssignments());
    }

    private String redirectToCreateFlightError(RedirectAttributes redirectAttributes,
                                               String message,
                                               FlightCreateForm form) {
        redirectAttributes.addFlashAttribute("errorMessage", message);
        redirectAttributes.addFlashAttribute("flightCreateForm", form);
        return "redirect:/flight/create";
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.strip();
    }

    private String normalizeUpper(String value) {
        return normalizeText(value).toUpperCase();
    }

    private LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private LocalTime parseTime(String value) {
        try {
            return LocalTime.parse(value);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static @NonNull Flight createFlight(final FlightCreateForm form, final LocalDate parsedDepartureDate,
                                                final LocalTime parsedDepartureTime,
                                                final LocalTime parsedCheckInTime, final LocalDate parsedArrivalDate,
                                                final LocalTime parsedArrivalTime) {
        var flight = new Flight();
        flight.setNo(form.no());
        flight.setDepartureDate(parsedDepartureDate);
        flight.setDepartureTime(parsedDepartureTime);
        flight.setCheckInTime(parsedCheckInTime);
        flight.setArrivalDate(parsedArrivalDate);
        flight.setArrivalTime(parsedArrivalTime);
        flight.setFromAirportCode(form.getFromAirportCode());
        flight.setToAirportCode(form.getToAirportCode());
        flight.setAirlineCode(form.getAirlineCode());
        flight.setAircraftNumber(form.getAircraftNumber());
        flight.setLoadedMeals(Set.of());
        flight.setLoadedBeverages(Set.of());
        return flight;
    }

    @Getter
    public static final class FlightCreateForm {
        private final String no;
        private final String departureDate;
        private final String departureTime;
        private final String checkInTime;
        private final String arrivalDate;
        private final String arrivalTime;
        private final String fromAirportCode;
        private final String toAirportCode;
        private final String airlineCode;
        private final String aircraftNumber;

        private FlightCreateForm(String no,
                                 String departureDate,
                                 String departureTime,
                                 String checkInTime,
                                 String arrivalDate,
                                 String arrivalTime,
                                 String fromAirportCode,
                                 String toAirportCode,
                                 String airlineCode,
                                 String aircraftNumber) {
            this.no = no;
            this.departureDate = departureDate;
            this.departureTime = departureTime;
            this.checkInTime = checkInTime;
            this.arrivalDate = arrivalDate;
            this.arrivalTime = arrivalTime;
            this.fromAirportCode = fromAirportCode;
            this.toAirportCode = toAirportCode;
            this.airlineCode = airlineCode;
            this.aircraftNumber = aircraftNumber;
        }

        public boolean hasRequiredFields() {
            return StringUtils.hasText(no)
                   && StringUtils.hasText(departureDate)
                   && StringUtils.hasText(departureTime)
                   && StringUtils.hasText(checkInTime)
                   && StringUtils.hasText(arrivalDate)
                   && StringUtils.hasText(arrivalTime)
                   && StringUtils.hasText(fromAirportCode)
                   && StringUtils.hasText(toAirportCode)
                   && StringUtils.hasText(airlineCode)
                   && StringUtils.hasText(aircraftNumber);
        }

        public String no() {
            return no;
        }

        public String departureDate() {
            return departureDate;
        }

        public String departureTime() {
            return departureTime;
        }

        public static FlightCreateForm empty() {
            return new FlightCreateForm("", "", "", "", "", "", "", "", "", "");
        }
    }
}
