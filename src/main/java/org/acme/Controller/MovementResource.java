package org.acme.Controller;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.acme.DTO.MovementRequestDTO;
import org.acme.DTO.MovementResponseDTO;
import org.acme.Service.Movement.MovementService;

@Path("/movements")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MovementResource {

    @Inject
    MovementService movementService;

    @GET
    @RolesAllowed({ "ADMIN" })
    public List<MovementResponseDTO> listAll() {
        return movementService.listAll();
    }

    @POST
    @RolesAllowed({ "ADMIN" })
    public Response create(@Valid MovementRequestDTO request) {
        MovementResponseDTO created = movementService.create(request);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }
}
