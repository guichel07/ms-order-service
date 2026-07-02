package org.acme.Controller;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import org.acme.DTO.AlertResponseDTO;
import org.acme.Service.Alert.AlertService;

@Path("/alerts")
@Produces(MediaType.APPLICATION_JSON)
public class AlertResource {

    @Inject
    AlertService alertService;

    @GET
    @RolesAllowed({ "SELLER", "ADMIN" })
    public List<AlertResponseDTO> listAll() {
        return alertService.listAll();
    }

    @POST
    @Path("/{id}/toggle-resolved")
    @RolesAllowed({ "ADMIN" })
    public AlertResponseDTO toggleResolved(@PathParam("id") String id) {
        return alertService.toggleResolved(id);
    }
}
