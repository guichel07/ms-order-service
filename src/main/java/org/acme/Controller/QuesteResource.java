package org.acme.Controller;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.acme.DTO.QuesteClosureRequestDTO;
import org.acme.DTO.QuesteRequestDTO;
import org.acme.DTO.QuesteResponseDTO;
import org.acme.Service.Queste.QuesteService;

/**
 * Une quête est la campagne d'achat en cours d'un acheteur — voir /questes/{id}/missions
 * (MissionResource) pour la saisie unitaire des achats qu'elle regroupe.
 */
@Path("/questes")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class QuesteResource {

    @Inject
    QuesteService questeService;

    @GET
    @RolesAllowed({ "SELLER", "ADMIN" })
    public List<QuesteResponseDTO> listAll() {
        return questeService.listAll();
    }

    @GET
    @RolesAllowed({ "SELLER", "ADMIN" })
    @Path("/{id}")
    public QuesteResponseDTO getById(@PathParam("id") String id) {
        return questeService.findById(id);
    }

    @POST
    @RolesAllowed({ "SELLER", "ADMIN" })
    public Response create(@Valid QuesteRequestDTO request) {
        QuesteResponseDTO created = questeService.create(request);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @POST
    @RolesAllowed({ "SELLER", "ADMIN" })
    @Path("/{id}/cloturer")
    public QuesteResponseDTO close(@PathParam("id") String id, @Valid QuesteClosureRequestDTO request) {
        return questeService.close(id, request);
    }

    /**
     * Validation admin : injecte les missions terminées en stock et clôture définitivement.
     * @Consumes wildcard : pas de corps de requête, ne pas exiger de Content-Type ici
     * malgré le @Consumes(APPLICATION_JSON) hérité de la classe.
     */
    @POST
    @Consumes(MediaType.WILDCARD)
    @RolesAllowed({ "ADMIN" })
    @Path("/{id}/valider")
    public QuesteResponseDTO validate(@PathParam("id") String id) {
        return questeService.validate(id);
    }
}
