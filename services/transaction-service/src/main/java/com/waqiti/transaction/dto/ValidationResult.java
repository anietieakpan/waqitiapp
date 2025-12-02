package com.waqiti.transaction.dto;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class ValidationResult {
    private boolean valid = true;
    private List<String> errors = new ArrayList<>();
    
    public void addError(String error) {
        this.errors.add(error);
        this.valid = false;
    }
    
    public boolean hasErrors() {
        return !errors.isEmpty();
    }
}