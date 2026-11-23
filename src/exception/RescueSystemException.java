package exception;

public class RescueSystemException extends Exception {
    public RescueSystemException(String message) {
        super(message);
    }

    public RescueSystemException(String message, Throwable cause) {
        super(message, cause);
    }
}