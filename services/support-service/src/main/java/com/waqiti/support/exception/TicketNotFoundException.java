package com.waqiti.support.exception;

public class TicketNotFoundException extends RuntimeException {
    
    public TicketNotFoundException(String ticketId) {
        super("Ticket not found with ID: " + ticketId);
    }
    
    public TicketNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}