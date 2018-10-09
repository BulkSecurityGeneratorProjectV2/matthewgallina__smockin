package com.smockin.admin.controller;

import com.smockin.admin.dto.response.SimpleMessageResponseDTO;
import com.smockin.admin.exception.ApiImportException;
import com.smockin.admin.exception.AuthException;
import com.smockin.admin.exception.RecordNotFoundException;
import com.smockin.admin.exception.ValidationException;
import com.smockin.mockserver.exception.MockServerException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.io.IOException;

/**
 * Created by mgallina.
 */
@ControllerAdvice
public class ExceptionHandlerController {

    // NOTE Removed the use of the @ResponseStatus annotation and explicitly returning a ResponseEntity, as a workaround
    // to a problem with the the Jetty container, which seems to automatically wrap exceptions where no response is present.

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<String> handleDataIntegrityViolationException() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_JSON)
                .build();
    }

    @ExceptionHandler(RecordNotFoundException.class)
    public ResponseEntity<String> handleRecordNotFoundException() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .build();
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<SimpleMessageResponseDTO<String>> handleValidationBadRequest(ValidationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new SimpleMessageResponseDTO(ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<SimpleMessageResponseDTO<String>> handleIllegalArgumentException(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new SimpleMessageResponseDTO(ex.getMessage()));
    }

    @ExceptionHandler(MockServerException.class)
    public ResponseEntity<String> handleMockServerException(MockServerException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .build();
    }

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<String> handleAuthException(AuthException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_JSON)
                .build();
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<String> handleIOException(IOException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .build();
    }

    @ExceptionHandler(ApiImportException.class)
    public ResponseEntity<SimpleMessageResponseDTO<String>> handleApiImportException(ApiImportException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new SimpleMessageResponseDTO(ex.getMessage()));
    }

}
