package de.kiaim.anon.exception;

public class ColumnNumberMismatchException extends IllegalArgumentException {
    public ColumnNumberMismatchException(String message) {
        super(message);
    }
}