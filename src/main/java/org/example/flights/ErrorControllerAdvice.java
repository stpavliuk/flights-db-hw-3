package org.example.flights;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.java.Log;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.RequestContextUtils;

import java.util.logging.Level;

@ControllerAdvice
@Log
public class ErrorControllerAdvice {

    @ExceptionHandler(ResponseStatusException.class)
    public String handleError(ResponseStatusException e, HttpServletRequest request) {
        log.log(Level.SEVERE, "Http error occured: ", e);

        RequestContextUtils.getOutputFlashMap(request)
                .put("errorMessage", e.getReason() != null ? e.getReason() : "Request could not be completed.");
        return "redirect:/flight";
    }

    @ExceptionHandler(Exception.class)
    public String handleGeneralError(Exception e, HttpServletRequest request) {
        log.log(Level.SEVERE, "Http error occured: ", e);

        RequestContextUtils.getOutputFlashMap(request)
                .put("errorMessage", "An unexpected error occurred.");
        return "redirect:/flight";
    }
}
