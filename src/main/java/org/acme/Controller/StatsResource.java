package org.acme.Controller;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.acme.DTO.SellerDetailDTO;
import org.acme.DTO.StatsResponseDTO;
import org.acme.Service.Stats.SellerDetailService;
import org.acme.Service.Stats.StatsService;

@Path("/stats")
@Produces(MediaType.APPLICATION_JSON)
public class StatsResource {

    @Inject
    StatsService statsService;

    @Inject
    SellerDetailService sellerDetailService;

    @GET
    @RolesAllowed({ "SELLER", "ADMIN" })
    @Path("/{period}")
    public StatsResponseDTO getStats(@PathParam("period") String period) {
        return statsService.getStats(period);
    }

    /**
     * Agrégat comportement + performance d'un vendeur (les 4 périodes en un seul
     * appel) — à consommer directement par le side panel vendeur du front.
     */
    @GET
    @RolesAllowed({ "SELLER", "ADMIN" })
    @Path("/seller/{email}/detail")
    public SellerDetailDTO getSellerDetail(@PathParam("email") String email) {
        return sellerDetailService.getDetail(email);
    }
}
