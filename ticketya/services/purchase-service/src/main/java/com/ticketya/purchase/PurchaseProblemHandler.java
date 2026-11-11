package com.ticketya.purchase;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;

@RestControllerAdvice
public class PurchaseProblemHandler {
    private static final Logger log=LoggerFactory.getLogger(PurchaseProblemHandler.class);
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Problem> handle(ResponseStatusException ex,HttpServletRequest request){
        HttpStatus status=HttpStatus.valueOf(ex.getStatusCode().value());
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(new Problem(status.value(),status.getReasonPhrase(),ex.getReason(),request.getRequestURI(),(String)request.getAttribute(PurchaseTracingFilter.ATTRIBUTE),OffsetDateTime.now()));
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<Problem> unexpected(Exception ex,HttpServletRequest request){
        log.error("Purchase request failed; correlationId={}",request.getAttribute(PurchaseTracingFilter.ATTRIBUTE),ex);
        return ResponseEntity.status(500).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(new Problem(500,"Internal server error","The request could not be completed",request.getRequestURI(),(String)request.getAttribute(PurchaseTracingFilter.ATTRIBUTE),OffsetDateTime.now()));
    }
    public record Problem(int status,String title,String detail,String instance,String correlationId,OffsetDateTime timestamp){}
}
