package org.acme.Controller;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.acme.DTO.ArticleDTO;
import org.acme.DTO.QuantityAdjustmentDTO;
import org.acme.DTO.QuantityOrdered;
import org.acme.Entity.Article;
import org.acme.Service.Article.ArticleService;

@Path("/articles")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ArticleResource {

    @Inject
    ArticleService articleService;

    @GET
    @RolesAllowed({ "SELLER", "ADMIN" })
    public List<Article> listAll() {
        return articleService.listAll();
    }

    @GET
    @RolesAllowed({ "SELLER", "ADMIN" })
    @Path("/{id}")
    public Article getById(@PathParam("id") String id) {
        return articleService.findById(id);
    }

    @GET
    @RolesAllowed({ "SELLER", "ADMIN" })
    @Path("/by-name/{name}")
    public Article getByName(@PathParam("name") String name) {
        return articleService.findByName(name);
    }

    @POST
    @RolesAllowed({ "SELLER", "ADMIN" })
    public Response register(@Valid ArticleDTO articleDTO) {
        Article created = articleService.register(articleDTO);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @PUT
    @RolesAllowed({ "SELLER", "ADMIN" })
    @Path("/{id}")
    public Article update(
        @PathParam("id") String id,
        @Valid ArticleDTO articleDTO
    ) {
        return articleService.update(id, articleDTO);
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed({ "SELLER", "ADMIN" })
    public Response delete(@PathParam("id") String id) {
        articleService.delete(id);
        return Response.noContent().build();
    }

    @PATCH
    @RolesAllowed({ "SELLER", "ADMIN" })
    @Path("/{id}/quantity")
    public Article restock(
        @PathParam("id") String id,
        @Valid QuantityAdjustmentDTO adjustment
    ) {
        return articleService.incrementQuantity(id, adjustment.quantity());
    }

    @PATCH
    @RolesAllowed({ "SELLER", "ADMIN" })
    @Path("/{id}/quantityOrdered")
    public Article destock(
        @PathParam("id") String id,
        @Valid QuantityOrdered quantityOrdered
    ) {
        return articleService.decrementQuantity(id, quantityOrdered.quantity());
    }
}
