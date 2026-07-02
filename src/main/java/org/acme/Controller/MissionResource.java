package org.acme.Controller;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.acme.DTO.MissionResponseDTO;
import org.acme.DTO.MissionUpdateDTO;
import org.acme.Service.Mission.MissionService;

/** Correction d'une mission déjà saisie (qty/prix/statut) — création : voir QuesteMissionResource. */
@Path("/missions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MissionResource {

    @Inject
    MissionService missionService;

    @PUT
    @RolesAllowed({ "SELLER", "ADMIN" })
    @Path("/{id}")
    public MissionResponseDTO update(@PathParam("id") String id, @Valid MissionUpdateDTO request) {
        return missionService.update(id, request);
    }
}
