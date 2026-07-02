package org.acme.Controller;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.acme.DTO.ClientDTO;
import org.acme.DTO.ClientDetailDTO;
import org.acme.DTO.ClientsOverviewDTO;
import org.acme.Entity.AgeCategory;
import org.acme.Entity.Gender;
import org.acme.Service.Client.ClientDetailService;
import org.acme.Service.Client.ClientService;
import org.acme.Service.Client.ClientsOverviewService;

@Path("/clients")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ClientResource {

    @Inject
    ClientService clientService;

    @Inject
    ClientDetailService clientDetailService;

    @Inject
    ClientsOverviewService clientsOverviewService;

    @GET
    @RolesAllowed({ "SELLER", "ADMIN" })
    public List<ClientDTO> listAll() {
        return clientService.getAll();
    }

    @GET
    @RolesAllowed({ "SELLER", "ADMIN" })
    @Path("/{phone}")
    public ClientDTO getByPhone(@PathParam("phone") String phone) {
        return clientService.getByPhone(phone);
    }

    @GET
    @RolesAllowed({ "SELLER", "ADMIN" })
    @Path("/search")
    public List<ClientDTO> searchByName(@QueryParam("query") String query) {
        return clientService.searchByName(query);
    }

    /**
     * Agrégat complet de l'onglet "Analyse" (vue globale, mois courant) — à
     * consommer directement par ClientsOverview côté front.
     */
    @GET
    @RolesAllowed({ "SELLER", "ADMIN" })
    @Path("/overview")
    public ClientsOverviewDTO getOverview() {
        return clientsOverviewService.getOverview();
    }

    @GET
    @RolesAllowed({ "SELLER", "ADMIN" })
    @Path("/id/{id}")
    public ClientDTO getById(@PathParam("id") String id) {
        return clientService.getById(id);
    }

    /**
     * Agrégat complet de la fiche client (les 4 périodes 7j/4sem/6mois/2ans en un
     * seul appel) — à consommer directement par la sidebar client du front.
     */
    @GET
    @RolesAllowed({ "SELLER", "ADMIN" })
    @Path("/id/{id}/detail")
    public ClientDetailDTO getDetail(@PathParam("id") String id) {
        return clientDetailService.getDetail(id);
    }

    /**
     * Récupère (ou crée si c'est la première fois) la fiche partagée pour les ventes
     * sans téléphone de ce couple (catégorie d'âge, genre) — à appeler avant de créer
     * une commande anonyme, pour obtenir le clientId à y mettre. gender est optionnel
     * (genre inconnu si absent).
     */
    @GET
    @RolesAllowed({ "SELLER", "ADMIN" })
    @Path("/anonymous/{ageCategory}")
    public ClientDTO getOrCreateAnonymous(
            @PathParam("ageCategory") AgeCategory ageCategory,
            @QueryParam("gender") Gender gender
    ) {
        return clientService.getOrCreateAnonymous(ageCategory, gender);
    }

    @POST
    @RolesAllowed({ "SELLER", "ADMIN" })
    public Response saveClient(@Valid ClientDTO clientDTO) {
        ClientDTO created = clientService.saveClient(clientDTO);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @POST
    @Path("/batch")
    @RolesAllowed({ "SELLER", "ADMIN" })
    public Response saveAll(@Valid List<@Valid ClientDTO> clients) {
        List<ClientDTO> created = clientService.saveAll(clients);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @POST
    @RolesAllowed({ "SELLER", "ADMIN" })
    @Path("/{phone}/archive")
    public Response toggleArchiveClient(@PathParam("phone") String phone) {
        ClientDTO updated = clientService.toggleArchiveClient(phone);
        return Response.ok(updated).build();
    }

    @PUT
    @RolesAllowed({ "SELLER", "ADMIN" })
    @Path("/{phone}")
    public ClientDTO updateClient(
            @PathParam("phone") String phone,
            @Valid ClientDTO clientDTO
    ) {
        return clientService.updateClient(phone, clientDTO);
    }

    @DELETE
    @Path("/{phone}")
    @RolesAllowed({ "ADMIN" })
    public Response delete(@PathParam("phone") String phone) {
        clientService.deleteClient(phone);
        return Response.noContent().build();
    }
}