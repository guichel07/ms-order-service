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
import org.acme.DTO.SupplierNoteRequestDTO;
import org.acme.DTO.SupplierRequestDTO;
import org.acme.DTO.SupplierResponseDTO;
import org.acme.Service.Supplier.SupplierService;

/**
 * Annuaire des fournisseurs : qui vend quoi (articles précis, pas une catégorie), avec un
 * historique de notes horodatées. Sert de base à un futur matching automatique
 * article-en-rupture → fournisseur (voir Alertes).
 */
@Path("/suppliers")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SupplierResource {

    @Inject
    SupplierService supplierService;

    @GET
    @RolesAllowed({ "SELLER", "ADMIN" })
    public List<SupplierResponseDTO> listAll() {
        return supplierService.listAll();
    }

    @GET
    @RolesAllowed({ "SELLER", "ADMIN" })
    @Path("/{id}")
    public SupplierResponseDTO getById(@PathParam("id") String id) {
        return supplierService.findById(id);
    }

    @POST
    @RolesAllowed({ "SELLER", "ADMIN" })
    public Response create(@Valid SupplierRequestDTO request) {
        SupplierResponseDTO created = supplierService.create(request);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @PUT
    @RolesAllowed({ "SELLER", "ADMIN" })
    @Path("/{id}")
    public SupplierResponseDTO update(@PathParam("id") String id, @Valid SupplierRequestDTO request) {
        return supplierService.update(id, request);
    }

    @POST
    @RolesAllowed({ "SELLER", "ADMIN" })
    @Path("/{id}/notes")
    public SupplierResponseDTO addNote(@PathParam("id") String id, @Valid SupplierNoteRequestDTO request) {
        return supplierService.addNote(id, request);
    }
}
