package be.kuleuven.suitsrestservice.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

@ControllerAdvice
class SuitNotFoundAdvice {

    @ResponseBody
    @ExceptionHandler(SuitNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    String suitNotFoundHandler(SuitNotFoundException ex) {
        return ex.getMessage();
    }
}
