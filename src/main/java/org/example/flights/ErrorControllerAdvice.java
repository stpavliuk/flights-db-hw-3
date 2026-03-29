package org.example.flights;

import lombok.extern.java.Log;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletResponse;
import java.util.logging.Level;

@ControllerAdvice
@Log
public class ErrorControllerAdvice {

    @ExceptionHandler(ResponseStatusException.class)
    public String handleError(ResponseStatusException e, Model model, HttpServletResponse response) {
        log.log(Level.SEVERE, "Http error occured: ", e);

        HttpStatusCode status = e.getStatusCode();
        String errorMessage = e.getMessage();
        response.setStatus(status.value());
        model.addAttribute("statusCode", status.value());
        model.addAttribute("errorMessage", errorMessage);
        model.addAttribute("exception", e);

        return "common/error";
    }

    @ExceptionHandler(Exception.class)
    public String handleGeneralError(Exception e, Model model, HttpServletResponse response) {
        log.log(Level.SEVERE, "Http error occured: ", e);

        HttpStatusCode status = HttpStatus.INTERNAL_SERVER_ERROR;
        String errorMessage = "An unexpected error occurred.";
        response.setStatus(status.value());
        model.addAttribute("statusCode", status.value());
        model.addAttribute("errorMessage", errorMessage);
        model.addAttribute("exception", e);

        return "common/error";
    }


}
