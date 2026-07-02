package org.acme.Service.Flyer.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.acme.DTO.FlyerRequestDTO;
import org.acme.DTO.FlyerResponseDTO;
import org.acme.DTO.FlyerSectionRequestDTO;
import org.acme.DTO.GammeResponseDTO;
import org.acme.Entity.Flyer;
import org.acme.Entity.FlyerSection;
import org.acme.Exception.BusinessException;
import org.acme.Repository.FlyerRepository;
import org.acme.Service.Gamme.GammeService;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires pour {@link FlyerServiceImpl}.
 * FlyerRepository et GammeService sont mockés — aucune base de données réelle nécessaire.
 */
@ExtendWith(MockitoExtension.class)
class FlyerServiceImplTest {

    @Mock
    private FlyerRepository flyerRepository;

    @Mock
    private GammeService gammeService;

    @InjectMocks
    private FlyerServiceImpl flyerService;

    private GammeResponseDTO gamme(String id, String title) {
        return new GammeResponseDTO(
            id,
            title,
            List.of(),
            new BigDecimal("1000.00"),
            new BigDecimal("400.00"),
            null,
            null,
            null,
            Instant.now(),
            Instant.now()
        );
    }

    @Test
    void create_persistsFlyer_andReturnsEnrichedSections_withGammeFromGammeService() {
        String gammeAId = new ObjectId().toHexString();
        String gammeBId = new ObjectId().toHexString();
        when(gammeService.findById(gammeAId)).thenReturn(gamme(gammeAId, "Petit-déjeuner"), gamme(gammeAId, "Petit-déjeuner"));
        when(gammeService.findById(gammeBId)).thenReturn(gamme(gammeBId, "Midi"), gamme(gammeBId, "Midi"));
        doAnswer(invocation -> {
            Flyer persisted = invocation.getArgument(0);
            persisted.id = new ObjectId();
            return null;
        }).when(flyerRepository).persist(any(Flyer.class));

        FlyerRequestDTO request = new FlyerRequestDTO(
            "Carte du jour",
            List.of(
                new FlyerSectionRequestDTO("Petit-déjeuner", gammeAId),
                new FlyerSectionRequestDTO("Midi", gammeBId)
            )
        );

        FlyerResponseDTO result = flyerService.create(request);

        assertEquals("Carte du jour", result.title());
        assertEquals(2, result.sections().size());
        assertEquals("Petit-déjeuner", result.sections().get(0).title());
        assertEquals("Petit-déjeuner", result.sections().get(0).gamme().title());
        assertEquals("Midi", result.sections().get(1).title());
        assertEquals("Midi", result.sections().get(1).gamme().title());
    }

    @Test
    void create_throwsBadRequest_whenAGammeDoesNotExist() {
        String unknownId = new ObjectId().toHexString();
        when(gammeService.findById(unknownId)).thenThrow(
            new BusinessException(jakarta.ws.rs.core.Response.Status.NOT_FOUND, "Gamme not found " + unknownId)
        );

        FlyerRequestDTO request = new FlyerRequestDTO(
            "Carte du jour",
            List.of(new FlyerSectionRequestDTO("Petit-déjeuner", unknownId))
        );

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> flyerService.create(request)
        );

        assertEquals(400, exception.getErrorCode().getStatusCode());
    }

    @Test
    void findById_throwsNotFound_whenFlyerDoesNotExist() {
        String id = new ObjectId().toHexString();
        when(flyerRepository.findById(new ObjectId(id))).thenReturn(null);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> flyerService.findById(id)
        );

        assertEquals(404, exception.getErrorCode().getStatusCode());
    }

    @Test
    void update_replacesSections_whenFlyerExists() {
        Flyer existing = new Flyer();
        existing.id = new ObjectId();
        existing.setTitle("Ancien titre");
        existing.setSections(List.of(new FlyerSection("Ancien", "old-gamme-id")));
        when(flyerRepository.findById(existing.id)).thenReturn(existing);

        String gammeId = new ObjectId().toHexString();
        when(gammeService.findById(gammeId)).thenReturn(gamme(gammeId, "Soir"), gamme(gammeId, "Soir"));

        FlyerRequestDTO request = new FlyerRequestDTO(
            "Nouveau titre",
            List.of(new FlyerSectionRequestDTO("Soir", gammeId))
        );

        FlyerResponseDTO result = flyerService.update(existing.id.toHexString(), request);

        assertEquals("Nouveau titre", result.title());
        assertEquals(1, result.sections().size());
        verify(flyerRepository).update(existing);
    }

    @Test
    void listAll_returnsAllStoredFlyers_enrichedWithGammeData() {
        String gammeId = new ObjectId().toHexString();
        when(gammeService.findById(gammeId)).thenReturn(
            gamme(gammeId, "Petit-déjeuner"), gamme(gammeId, "Petit-déjeuner")
        );

        Flyer f1 = new Flyer();
        f1.id = new ObjectId();
        f1.setTitle("Carte A");
        f1.setSections(List.of(new FlyerSection("Petit-déjeuner", gammeId)));
        Flyer f2 = new Flyer();
        f2.id = new ObjectId();
        f2.setTitle("Carte B");
        f2.setSections(List.of(new FlyerSection("Petit-déjeuner", gammeId)));
        when(flyerRepository.listAll()).thenReturn(List.of(f1, f2));

        List<FlyerResponseDTO> result = flyerService.listAll();

        assertEquals(2, result.size());
        assertEquals("Carte A", result.get(0).title());
        assertEquals("Carte B", result.get(1).title());
    }

    @Test
    void toResponseDTO_skipsSection_whenGammeWasDeletedSinceCreation() {
        Flyer flyer = new Flyer();
        flyer.id = new ObjectId();
        flyer.setTitle("Carte orpheline");
        String deletedGammeId = new ObjectId().toHexString();
        flyer.setSections(List.of(new FlyerSection("Section fantôme", deletedGammeId)));
        when(flyerRepository.findById(flyer.id)).thenReturn(flyer);
        when(gammeService.findById(deletedGammeId)).thenThrow(
            new BusinessException(jakarta.ws.rs.core.Response.Status.NOT_FOUND, "Gamme not found " + deletedGammeId)
        );

        FlyerResponseDTO result = flyerService.findById(flyer.id.toHexString());

        assertEquals(0, result.sections().size());
    }
}
