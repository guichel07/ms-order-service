package org.acme.Exception;

/**
 * BusinessException
 */
import jakarta.ws.rs.core.Response;

public class BusinessException extends RuntimeException {

    private final Response.Status errorCode;

    public BusinessException(Response.Status errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public Response.Status getErrorCode() {
        return errorCode;
    }
}
