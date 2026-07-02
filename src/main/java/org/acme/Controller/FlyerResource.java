package org.acme.Controller;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.acme.DTO.FlyerRequestDTO;
import org.acme.DTO.FlyerResponseDTO;
import org.acme.Service.Flyer.FlyerService;

/**
 * Flyer complet façon carte de resto : plusieurs sections ordonnées (ex: "Petit-déjeuner",
 * "Midi", "Soir"), chacune reprenant une gamme déjà composée (voir GammeResource).
 */
@Path("/flyers")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class FlyerResource {

    @Inject
    FlyerService flyerService;

    @GET
    @RolesAllowed({ "SELLER", "ADMIN" })
    public List<FlyerResponseDTO> listAll() {
        return flyerService.listAll();
    }

    @GET
    @RolesAllowed({ "SELLER", "ADMIN" })
    @Path("/{id}")
    public FlyerResponseDTO getById(@PathParam("id") String id) {
        return flyerService.findById(id);
    }

    @POST
    @RolesAllowed({ "SELLER", "ADMIN" })
    public Response create(@Valid FlyerRequestDTO request) {
        FlyerResponseDTO created = flyerService.create(request);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @PUT
    @RolesAllowed({ "SELLER", "ADMIN" })
    @Path("/{id}")
    public FlyerResponseDTO update(@PathParam("id") String id, @Valid FlyerRequestDTO request) {
        return flyerService.update(id, request);
    }
}
