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
import org.acme.DTO.GammeRequestDTO;
import org.acme.DTO.GammeResponseDTO;
import org.acme.Service.Gamme.GammeService;

/**
 * Gammes libres : un flyer composé à la main par l'admin (ex: "Femme enceinte", "Petit-déj
 * enfant") — des lignes {article, prix, quantité}, pas une catégorie ni une fenêtre de dates.
 * Souvent créées après avoir vu les suggestions de lift et reconnu un pattern humain dedans.
 */
@Path("/gammes")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class GammeResource {

    @Inject
    GammeService gammeService;

    @GET
    @RolesAllowed({ "SELLER", "ADMIN" })
    public List<GammeResponseDTO> listAll() {
        return gammeService.listAll();
    }

    @GET
    @RolesAllowed({ "SELLER", "ADMIN" })
    @Path("/{id}")
    public GammeResponseDTO getById(@PathParam("id") String id) {
        return gammeService.findById(id);
    }

    @POST
    @RolesAllowed({ "SELLER", "ADMIN" })
    public Response create(@Valid GammeRequestDTO request) {
        GammeResponseDTO created = gammeService.create(request);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @PUT
    @RolesAllowed({ "SELLER", "ADMIN" })
    @Path("/{id}")
    public GammeResponseDTO update(@PathParam("id") String id, @Valid GammeRequestDTO request) {
        return gammeService.update(id, request);
    }

    /** (Re)génère le texte marketing + le pattern reconnu pour cette gamme, sans attendre le job planifié. */
    @POST
    @Consumes(MediaType.WILDCARD)
    @RolesAllowed({ "SELLER", "ADMIN" })
    @Path("/{id}/contenu")
    public GammeResponseDTO generateContent(@PathParam("id") String id) {
        return gammeService.generateContent(id);
    }
}
