package com.urbangeopulse.exceptions;

public class InitializationException extends  Exception {

    public InitializationException(String message) {
        super(message);
    }
    public InitializationException(String message, Throwable t) {
        super(message, t);
    }
}
