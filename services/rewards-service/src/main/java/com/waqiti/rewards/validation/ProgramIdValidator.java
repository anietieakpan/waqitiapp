package com.waqiti.rewards.validation;

import com.waqiti.rewards.domain.ReferralProgram;
import com.waqiti.rewards.repository.ReferralProgramRepository;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Validator for program IDs
 *
 * @author Waqiti Platform Team
 * @version 2.0
 * @since 2025-11-09
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProgramIdValidator implements ConstraintValidator<ValidProgramId, String> {

    private static final Pattern PROGRAM_ID_PATTERN = Pattern.compile("^REF-PROG-[A-Z0-9-]+$");

    private final ReferralProgramRepository programRepository;

    private boolean checkExists;
    private boolean checkActive;

    @Override
    public void initialize(ValidProgramId constraintAnnotation) {
        this.checkExists = constraintAnnotation.checkExists();
        this.checkActive = constraintAnnotation.checkActive();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return false;
        }

        // Check format
        if (!PROGRAM_ID_PATTERN.matcher(value).matches()) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                    "Program ID must follow format: REF-PROG-XXX"
            ).addConstraintViolation();
            return false;
        }

        // Check existence if required
        if (checkExists) {
            var programOpt = programRepository.findByProgramId(value);
            if (programOpt.isEmpty()) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(
                        "Program not found: " + value
                ).addConstraintViolation();
                return false;
            }

            // Check if active
            if (checkActive) {
                ReferralProgram program = programOpt.get();
                if (!program.isCurrentlyActive()) {
                    context.disableDefaultConstraintViolation();
                    context.buildConstraintViolationWithTemplate(
                            "Program is not currently active: " + value
                    ).addConstraintViolation();
                    return false;
                }
            }
        }

        return true;
    }
}
