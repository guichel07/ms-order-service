package org.acme.Controller;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.util.List;
import org.acme.DTO.OrderRequestDTO;
import org.acme.DTO.OrderResponseDTO;
import org.acme.Service.Order.OrderService;

@Path("/orders")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OrderResource {

    @Inject
    OrderService orderService;

    @GET
    @RolesAllowed({ "SELLER", "ADMIN" })
    public List<OrderResponseDTO> listAll() {
        return orderService.listAll();
    }

    @GET
    @RolesAllowed({ "SELLER", "ADMIN" })
    @Path("/{id}")
    public OrderResponseDTO getById(@PathParam("id") String id) {
        return orderService.findById(id);
    }

    @POST
    @RolesAllowed({ "SELLER", "ADMIN" })
    public Response register(@Valid OrderRequestDTO orderDTO) {
        OrderResponseDTO created = orderService.register(orderDTO);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @POST
    @Path("/batch")
    @RolesAllowed({ "SELLER", "ADMIN" })
    public Response registerAll(@Valid List<@Valid OrderRequestDTO> orders) {
        List<OrderResponseDTO> created = orderService.registerAll(orders);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed({ "ADMIN" })
    public Response delete(@PathParam("id") String id) {
        orderService.delete(id);
        return Response.noContent().build();
    }

    @GET
    @RolesAllowed({ "SELLER", "ADMIN" })
    @Path("/total-today")
    public BigDecimal getTotalSoldToday(@QueryParam("email") String email) {
        return orderService.getTotalSoldTodayByEmail(email);
    }
}
