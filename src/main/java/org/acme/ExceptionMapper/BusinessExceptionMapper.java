package org.acme.ExceptionMapper;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.Map;
import org.acme.Exception.BusinessException;

@Provider
public class BusinessExceptionMapper
    implements ExceptionMapper<BusinessException>
{

    @Override
    public Response toResponse(BusinessException exception) {
        Response.Status status = exception.getErrorCode();

        Map<String, Object> errorPayload = Map.of(
            "status",
            status.getStatusCode(),
            "error",
            status.getReasonPhrase(),
            "message",
            exception.getMessage()
        );

        return Response.status(status).entity(errorPayload).build();
    }
}
