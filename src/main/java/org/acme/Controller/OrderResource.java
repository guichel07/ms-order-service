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
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.util.List;
import org.acme.DTO.OrderDTO;
import org.acme.Entity.Order;
import org.acme.Service.Order.OrderService;

@Path("/orders")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OrderResource {

    @Inject
    OrderService orderService;

    @GET
    @RolesAllowed({ "SELLER", "ADMIN" })
    public List<Order> listAll() {
        return orderService.listAll();
    }

    @GET
    @RolesAllowed({ "SELLER", "ADMIN" })
    @Path("/{id}")
    public Order getById(@PathParam("id") String id) {
        return orderService.findById(id);
    }

    @POST
    @RolesAllowed({ "SELLER", "ADMIN" })
    public Response register(@Valid OrderDTO orderDTO) {
        Order created = orderService.register(orderDTO);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @GET
    @RolesAllowed({ "SELLER", "ADMIN" })
    @Path("/total-today")
    public BigDecimal getTotalSoldToday(@QueryParam("email") String email) {
        return orderService.getTotalSoldTodayByEmail(email);
    }
}
