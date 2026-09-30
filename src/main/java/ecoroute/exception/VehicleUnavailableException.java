package ecoroute.exception;

public class VehicleUnavailableException extends RuntimeException {
    public VehicleUnavailableException(String message) { super(message); }
    public VehicleUnavailableException(String message, Throwable cause) { super(message, cause); }
}
