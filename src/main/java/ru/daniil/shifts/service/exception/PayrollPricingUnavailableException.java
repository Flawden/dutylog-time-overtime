package ru.daniil.shifts.service.exception;

import org.springframework.http.HttpStatus;

/** Expected missing/incompatible pricing inputs from pure read services. Strict callers still receive HTTP 409. */
public final class PayrollPricingUnavailableException extends ApiException {
    private PayrollPricingUnavailableException(String code, String message) {
        super(HttpStatus.CONFLICT, code, message);
    }

    public static PayrollPricingUnavailableException unavailable(String code, String message) {
        return new PayrollPricingUnavailableException(code, message);
    }
}
