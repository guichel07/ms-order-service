package org.acme.Service.Mission.impl;

import java.math.BigDecimal;
import java.util.List;
import org.acme.DTO.MissionRequestDTO;
import org.acme.DTO.MissionResponseDTO;
import org.acme.DTO.MissionUpdateDTO;
import org.acme.DTO.SupplierNoteRequestDTO;
import org.acme.DTO.SupplierRequestDTO;
import org.acme.DTO.SupplierResponseDTO;
import org.acme.Entity.Article;
import org.acme.Entity.Mission;
import org.acme.Entity.Queste;
import org.acme.Exception.BusinessException;
import org.acme.Repository.ArticleRepository;
import org.acme.Repository.MissionRepository;
import org.acme.Repository.QuesteRepository;
import org.acme.Service.Supplier.SupplierService;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires pour {@link MissionServiceImpl}. Tous les repositories/services dépendants sont mockés.
 */
@ExtendWith(MockitoExtension.class)
class MissionServiceImplTest {

    @Mock
    private MissionRepository missionRepository;

    @Mock
    private QuesteRepository questeRepository;

    @Mock
    private ArticleRepository articleRepository;

    @Mock
    private SupplierService supplierService;

    @InjectMocks
    private MissionServiceImpl missionService;

    private Queste questeEnCours() {
        Queste queste = new Queste();
        queste.id = new ObjectId();
        queste.setNumber(3);
        queste.setBuyerName("Awa D.");
        queste.setStatus("en_cours");
        queste.setPrimePercent(BigDecimal.valueOf(50));
        return queste;
    }

    private Article article() {
        Article article = new Article();
        article.id = new ObjectId();
        article.setName("Riz parfumé 5kg");
        article.setIcon("🍚");
        article.setColor("rgba(1,2,3,0.1)");
        article.setCostPrice(BigDecimal.valueOf(320));
        return article;
    }

    private void stubPersistAssignsId() {
        doAnswer(invocation -> {
            Mission persisted = invocation.getArgument(0);
            persisted.id = new ObjectId();
            return null;
        }).when(missionRepository).persist(any(Mission.class));
    }

    @Test
    void create_snapshotsArticleData_andPersistsMission_withNoSupplier() {
        Queste queste = questeEnCours();
        Article article = article();
        when(questeRepository.findById(queste.id)).thenReturn(queste);
        when(articleRepository.findById(article.id)).thenReturn(article);
        stubPersistAssignsId();

        MissionResponseDTO result = missionService.create(
            queste.id.toHexString(),
            new MissionRequestDTO(article.id.toHexString(), 10, BigDecimal.valueOf(3000), "terminee", null, null, null, null)
        );

        assertEquals("Riz parfumé 5kg", result.name());
        assertEquals("🍚", result.icon());
        assertEquals(0, BigDecimal.valueOf(320).compareTo(result.refUnitCost()));
        assertEquals("terminee", result.status());
        assertNull(result.supplierId());
        verify(supplierService, never()).create(any());
        verify(supplierService, never()).linkArticle(any(), any());
    }

    @Test
    void create_throwsBadRequest_whenQuesteNotEnCours() {
        Queste queste = questeEnCours();
        queste.setStatus("en_attente_validation");
        when(questeRepository.findById(queste.id)).thenReturn(queste);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> missionService.create(
                queste.id.toHexString(),
                new MissionRequestDTO("a1", 1, BigDecimal.ZERO, null, null, null, null, null)
            )
        );

        assertEquals(400, exception.getErrorCode().getStatusCode());
    }

    @Test
    void create_throwsBadRequest_whenArticleUnknown() {
        Queste queste = questeEnCours();
        when(questeRepository.findById(queste.id)).thenReturn(queste);
        String unknownArticleId = new ObjectId().toHexString();
        when(articleRepository.findById(new ObjectId(unknownArticleId))).thenReturn(null);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> missionService.create(
                queste.id.toHexString(),
                new MissionRequestDTO(unknownArticleId, 1, BigDecimal.ZERO, null, null, null, null, null)
            )
        );

        assertEquals(400, exception.getErrorCode().getStatusCode());
    }

    @Test
    void create_linksExistingSupplier_andAddsHistoryNote() {
        Queste queste = questeEnCours();
        Article article = article();
        when(questeRepository.findById(queste.id)).thenReturn(queste);
        when(articleRepository.findById(article.id)).thenReturn(article);
        SupplierResponseDTO linked = new SupplierResponseDTO(
            "s1", "Marché Total", "+242051234567", List.of(), List.of(), Instant.now(), Instant.now()
        );
        when(supplierService.linkArticle("s1", article.id.toHexString())).thenReturn(linked);
        stubPersistAssignsId();

        MissionResponseDTO result = missionService.create(
            queste.id.toHexString(),
            new MissionRequestDTO(article.id.toHexString(), 10, BigDecimal.valueOf(3000), "terminee", "s1", null, null, null)
        );

        assertEquals("s1", result.supplierId());
        assertEquals("Marché Total", result.supplierName());
        verify(supplierService).linkArticle("s1", article.id.toHexString());
        verify(supplierService).addNote(eq("s1"), any(SupplierNoteRequestDTO.class));
    }

    @Test
    void create_createsSupplierOnTheFly_andAddsHistoryNote() {
        Queste queste = questeEnCours();
        Article article = article();
        when(questeRepository.findById(queste.id)).thenReturn(queste);
        when(articleRepository.findById(article.id)).thenReturn(article);
        SupplierResponseDTO created = new SupplierResponseDTO(
            "s2", "Boutique du Marché", "+242061112233", List.of(), List.of(), Instant.now(), Instant.now()
        );
        when(supplierService.create(any(SupplierRequestDTO.class))).thenReturn(created);
        stubPersistAssignsId();

        MissionResponseDTO result = missionService.create(
            queste.id.toHexString(),
            new MissionRequestDTO(article.id.toHexString(), 10, BigDecimal.valueOf(3000), "terminee", null, "Boutique du Marché", "+242061112233", null)
        );

        assertEquals("s2", result.supplierId());
        verify(supplierService).create(new SupplierRequestDTO("Boutique du Marché", "+242061112233", List.of(article.id.toHexString())));
        verify(supplierService).addNote(eq("s2"), any(SupplierNoteRequestDTO.class));
    }

    @Test
    void create_throwsBadRequest_whenNewSupplierPhoneIsMissing() {
        Queste queste = questeEnCours();
        Article article = article();
        when(questeRepository.findById(queste.id)).thenReturn(queste);
        when(articleRepository.findById(article.id)).thenReturn(article);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> missionService.create(
                queste.id.toHexString(),
                new MissionRequestDTO(article.id.toHexString(), 10, BigDecimal.valueOf(3000), "terminee", null, "Boutique du Marché", null, null)
            )
        );

        assertEquals(400, exception.getErrorCode().getStatusCode());
    }

    @Test
    void update_changesQtyPriceStatus_whenQuesteStillEnCours() {
        Queste queste = questeEnCours();
        Mission mission = new Mission();
        mission.id = new ObjectId();
        mission.setQuesteId(queste.id.toHexString());
        mission.setQty(5);
        mission.setPricePaid(BigDecimal.valueOf(1000));
        mission.setStatus("en_attente");
        when(missionRepository.findById(mission.id)).thenReturn(mission);
        when(questeRepository.findById(queste.id)).thenReturn(queste);

        MissionResponseDTO result = missionService.update(
            mission.id.toHexString(),
            new MissionUpdateDTO(8, BigDecimal.valueOf(1600), "terminee", null, null, null, null)
        );

        assertEquals(8, result.qty());
        assertEquals(0, BigDecimal.valueOf(1600).compareTo(result.pricePaid()));
        assertEquals("terminee", result.status());
    }

    @Test
    void update_throwsBadRequest_whenQuesteNoLongerEnCours() {
        Queste queste = questeEnCours();
        queste.setStatus("cloturee");
        Mission mission = new Mission();
        mission.id = new ObjectId();
        mission.setQuesteId(queste.id.toHexString());
        when(missionRepository.findById(mission.id)).thenReturn(mission);
        when(questeRepository.findById(queste.id)).thenReturn(queste);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> missionService.update(mission.id.toHexString(), new MissionUpdateDTO(1, BigDecimal.ZERO, "terminee", null, null, null, null))
        );

        assertEquals(400, exception.getErrorCode().getStatusCode());
    }

    @Test
    void update_throwsNotFound_whenMissionDoesNotExist() {
        String id = new ObjectId().toHexString();
        when(missionRepository.findById(new ObjectId(id))).thenReturn(null);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> missionService.update(id, new MissionUpdateDTO(1, BigDecimal.ZERO, "terminee", null, null, null, null))
        );

        assertEquals(404, exception.getErrorCode().getStatusCode());
    }

    @Test
    void update_changesSupplier_andAddsHistoryNote_whenNoPreviousSupplier() {
        Queste queste = questeEnCours();
        Mission mission = new Mission();
        mission.id = new ObjectId();
        mission.setQuesteId(queste.id.toHexString());
        mission.setArticleId("a1");
        mission.setName("Riz parfumé 5kg");
        mission.setQty(5);
        mission.setPricePaid(BigDecimal.valueOf(1000));
        when(missionRepository.findById(mission.id)).thenReturn(mission);
        when(questeRepository.findById(queste.id)).thenReturn(queste);
        SupplierResponseDTO linked = new SupplierResponseDTO(
            "s1", "Marché Total", "+242051234567", List.of(), List.of(), Instant.now(), Instant.now()
        );
        when(supplierService.linkArticle("s1", "a1")).thenReturn(linked);

        MissionResponseDTO result = missionService.update(
            mission.id.toHexString(),
            new MissionUpdateDTO(5, BigDecimal.valueOf(1000), "terminee", "s1", null, null, null)
        );

        assertEquals("s1", result.supplierId());
        verify(supplierService).addNote(eq("s1"), any(SupplierNoteRequestDTO.class));
    }

    @Test
    void update_doesNotAddHistoryNote_whenSupplierUnchanged() {
        Queste queste = questeEnCours();
        Mission mission = new Mission();
        mission.id = new ObjectId();
        mission.setQuesteId(queste.id.toHexString());
        mission.setArticleId("a1");
        mission.setName("Riz parfumé 5kg");
        mission.setQty(5);
        mission.setPricePaid(BigDecimal.valueOf(1000));
        mission.setSupplierId("s1");
        mission.setSupplierName("Marché Total");
        when(missionRepository.findById(mission.id)).thenReturn(mission);
        when(questeRepository.findById(queste.id)).thenReturn(queste);
        SupplierResponseDTO linked = new SupplierResponseDTO(
            "s1", "Marché Total", "+242051234567", List.of(), List.of(), Instant.now(), Instant.now()
        );
        when(supplierService.linkArticle("s1", "a1")).thenReturn(linked);

        missionService.update(
            mission.id.toHexString(),
            new MissionUpdateDTO(6, BigDecimal.valueOf(1200), "terminee", "s1", null, null, null)
        );

        verify(supplierService, never()).addNote(any(), any());
    }

    @Test
    void update_createsSupplierOnTheFly() {
        Queste queste = questeEnCours();
        Mission mission = new Mission();
        mission.id = new ObjectId();
        mission.setQuesteId(queste.id.toHexString());
        mission.setArticleId("a1");
        mission.setName("Riz parfumé 5kg");
        mission.setQty(5);
        mission.setPricePaid(BigDecimal.valueOf(1000));
        when(missionRepository.findById(mission.id)).thenReturn(mission);
        when(questeRepository.findById(queste.id)).thenReturn(queste);
        SupplierResponseDTO created = new SupplierResponseDTO(
            "s2", "Boutique du Marché", "+242061112233", List.of(), List.of(), Instant.now(), Instant.now()
        );
        when(supplierService.create(any(SupplierRequestDTO.class))).thenReturn(created);

        MissionResponseDTO result = missionService.update(
            mission.id.toHexString(),
            new MissionUpdateDTO(5, BigDecimal.valueOf(1000), "terminee", null, "Boutique du Marché", "+242061112233", null)
        );

        assertEquals("s2", result.supplierId());
        verify(supplierService).addNote(eq("s2"), any(SupplierNoteRequestDTO.class));
    }

    @Test
    void update_throwsBadRequest_whenSupplierChangeAttempted_andQuesteNoLongerEnCours() {
        Queste queste = questeEnCours();
        queste.setStatus("en_attente_validation");
        Mission mission = new Mission();
        mission.id = new ObjectId();
        mission.setQuesteId(queste.id.toHexString());
        when(missionRepository.findById(mission.id)).thenReturn(mission);
        when(questeRepository.findById(queste.id)).thenReturn(queste);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> missionService.update(
                mission.id.toHexString(),
                new MissionUpdateDTO(1, BigDecimal.ZERO, "terminee", "s1", null, null, null)
            )
        );

        assertEquals(400, exception.getErrorCode().getStatusCode());
        verify(supplierService, never()).linkArticle(any(), any());
    }

    @Test
    void create_appliesMarketPrice_toArticle_whenProvided() {
        Queste queste = questeEnCours();
        Article article = article();
        when(questeRepository.findById(queste.id)).thenReturn(queste);
        when(articleRepository.findById(article.id)).thenReturn(article);
        stubPersistAssignsId();

        missionService.create(
            queste.id.toHexString(),
            new MissionRequestDTO(article.id.toHexString(), 10, BigDecimal.valueOf(3000), "terminee", null, null, null, BigDecimal.valueOf(450))
        );

        assertEquals(0, BigDecimal.valueOf(450).compareTo(article.getMarketPrice()));
        verify(articleRepository).update(article);
    }

    @Test
    void create_doesNotTouchArticle_whenMarketPriceAbsent() {
        Queste queste = questeEnCours();
        Article article = article();
        when(questeRepository.findById(queste.id)).thenReturn(queste);
        when(articleRepository.findById(article.id)).thenReturn(article);
        stubPersistAssignsId();

        missionService.create(
            queste.id.toHexString(),
            new MissionRequestDTO(article.id.toHexString(), 10, BigDecimal.valueOf(3000), "terminee", null, null, null, null)
        );

        assertNull(article.getMarketPrice());
        verify(articleRepository, never()).update(article);
    }

    @Test
    void update_appliesMarketPrice_toArticle_whenProvided() {
        Queste queste = questeEnCours();
        Article article = article();
        Mission mission = new Mission();
        mission.id = new ObjectId();
        mission.setQuesteId(queste.id.toHexString());
        mission.setArticleId(article.id.toHexString());
        mission.setQty(5);
        mission.setPricePaid(BigDecimal.valueOf(1000));
        when(missionRepository.findById(mission.id)).thenReturn(mission);
        when(questeRepository.findById(queste.id)).thenReturn(queste);
        when(articleRepository.findById(article.id)).thenReturn(article);

        missionService.update(
            mission.id.toHexString(),
            new MissionUpdateDTO(5, BigDecimal.valueOf(1000), "terminee", null, null, null, BigDecimal.valueOf(470))
        );

        assertEquals(0, BigDecimal.valueOf(470).compareTo(article.getMarketPrice()));
        verify(articleRepository).update(article);
    }
}
