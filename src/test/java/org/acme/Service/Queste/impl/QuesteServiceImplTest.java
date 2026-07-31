package org.acme.Service.Queste.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.acme.DTO.QuesteClosureRequestDTO;
import org.acme.DTO.QuesteRequestDTO;
import org.acme.DTO.QuesteResponseDTO;
import org.acme.Entity.Mission;
import org.acme.Entity.Queste;
import org.acme.Exception.BusinessException;
import org.acme.Repository.MissionRepository;
import org.acme.Repository.QuesteRepository;
import org.acme.Service.Article.ArticleService;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires pour {@link QuesteServiceImpl}. QuesteRepository et MissionRepository mockés.
 */
@ExtendWith(MockitoExtension.class)
class QuesteServiceImplTest {

    @Mock
    private QuesteRepository questeRepository;

    @Mock
    private MissionRepository missionRepository;

    @Mock
    private ArticleService articleService;

    @InjectMocks
    private QuesteServiceImpl questeService;

    private Mission mission(String articleId, int qty, BigDecimal refUnitCost, BigDecimal pricePaid, String status) {
        Mission mission = new Mission();
        mission.id = new ObjectId();
        mission.setArticleId(articleId);
        mission.setName("Article " + articleId);
        mission.setQty(qty);
        mission.setRefUnitCost(refUnitCost);
        mission.setPricePaid(pricePaid);
        mission.setStatus(status);
        mission.setCreatedAt(Instant.now());
        return mission;
    }

    @Test
    void create_persistsQueste_withEnCoursStatus() {
        doAnswer(invocation -> {
            Queste persisted = invocation.getArgument(0);
            persisted.id = new ObjectId();
            return null;
        }).when(questeRepository).persist(any(Queste.class));
        when(questeRepository.listAll()).thenReturn(List.of());

        QuesteResponseDTO result = questeService.create(new QuesteRequestDTO("Awa D.", "Produits laitiers", BigDecimal.valueOf(50)));

        assertEquals(1, result.number());
        assertEquals("Awa D.", result.buyerName());
        assertEquals("Produits laitiers", result.objectif());
        assertEquals("en_cours", result.status());
        assertEquals(0, result.missions().size());
    }

    @Test
    void create_assignsIncrementingNumber_basedOnExistingQuestes() {
        Queste existing = new Queste();
        existing.setNumber(7);
        when(questeRepository.listAll()).thenReturn(List.of(existing));
        doAnswer(invocation -> {
            Queste persisted = invocation.getArgument(0);
            persisted.id = new ObjectId();
            return null;
        }).when(questeRepository).persist(any(Queste.class));

        QuesteResponseDTO result = questeService.create(new QuesteRequestDTO("Awa D.", null, BigDecimal.ZERO));

        assertEquals(8, result.number());
    }

    @Test
    void findById_throwsNotFound_whenQuesteDoesNotExist() {
        String id = new ObjectId().toHexString();
        when(questeRepository.findById(new ObjectId(id))).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class, () -> questeService.findById(id));

        assertEquals(404, exception.getErrorCode().getStatusCode());
    }

    @Test
    void close_computesEconomieAndPrime_fromTermineeMissionsOnly() {
        Queste queste = new Queste();
        queste.id = new ObjectId();
        queste.setNumber(1);
        queste.setBuyerName("Awa D.");
        queste.setStatus("en_cours");
        queste.setPrimePercent(BigDecimal.valueOf(50));
        when(questeRepository.findById(queste.id)).thenReturn(queste);

        // terminee : refUnitCost 300 x 10 = 3000 de référence, payé 2700 -> économie 300
        Mission terminee = mission("a1", 10, BigDecimal.valueOf(300), BigDecimal.valueOf(2700), "terminee");
        // en_attente : ne doit pas compter dans l'économie, mais déclenche le report vers une nouvelle quête
        Mission enAttente = mission("a2", 5, BigDecimal.valueOf(100), BigDecimal.ZERO, "en_attente");
        when(missionRepository.listByQueste(queste.id.toHexString())).thenReturn(List.of(terminee, enAttente));
        when(questeRepository.listAll()).thenReturn(new ArrayList<>(List.of(queste)));
        doAnswer(invocation -> {
            Queste persisted = invocation.getArgument(0);
            persisted.id = new ObjectId();
            return null;
        }).when(questeRepository).persist(any(Queste.class));

        QuesteResponseDTO result = questeService.close(queste.id.toHexString(), new QuesteClosureRequestDTO(BigDecimal.valueOf(500)));

        assertEquals("en_attente_validation", result.status());
        assertEquals(0, new BigDecimal("500.00").compareTo(result.transportReel()));
        assertEquals(0, new BigDecimal("300.00").compareTo(result.economieAchats()));
        // prime = 300 * 50% = 150
        assertEquals(0, new BigDecimal("150.00").compareTo(result.prime()));
    }

    @Test
    void close_givesZeroPrime_whenEconomieIsNegative() {
        Queste queste = new Queste();
        queste.id = new ObjectId();
        queste.setNumber(1);
        queste.setBuyerName("Awa D.");
        queste.setStatus("en_cours");
        queste.setPrimePercent(BigDecimal.valueOf(50));
        when(questeRepository.findById(queste.id)).thenReturn(queste);

        // payé plus cher que la référence (400 > 300 x 10 = 3000 -> payé 3500)
        Mission terminee = mission("a1", 10, BigDecimal.valueOf(300), BigDecimal.valueOf(3500), "terminee");
        when(missionRepository.listByQueste(queste.id.toHexString())).thenReturn(List.of(terminee));

        QuesteResponseDTO result = questeService.close(queste.id.toHexString(), new QuesteClosureRequestDTO(BigDecimal.ZERO));

        assertEquals(0, BigDecimal.ZERO.setScale(2).compareTo(result.prime()));
    }

    @Test
    void close_throwsBadRequest_whenAlreadyClosed() {
        Queste queste = new Queste();
        queste.id = new ObjectId();
        queste.setStatus("en_attente_validation");
        when(questeRepository.findById(queste.id)).thenReturn(queste);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> questeService.close(queste.id.toHexString(), new QuesteClosureRequestDTO(BigDecimal.ZERO))
        );

        assertEquals(400, exception.getErrorCode().getStatusCode());
    }

    @Test
    void close_reportsUnfinishedMissions_toExistingActiveQueste_ofSameBuyer() {
        Queste queste = new Queste();
        queste.id = new ObjectId();
        queste.setNumber(1);
        queste.setBuyerName("Awa D.");
        queste.setStatus("en_cours");
        queste.setPrimePercent(BigDecimal.valueOf(50));
        when(questeRepository.findById(queste.id)).thenReturn(queste);

        Queste autreQueteActive = new Queste();
        autreQueteActive.id = new ObjectId();
        autreQueteActive.setNumber(2);
        autreQueteActive.setBuyerName("Awa D.");
        autreQueteActive.setStatus("en_cours");

        Mission enAttente = mission("a2", 5, BigDecimal.valueOf(100), BigDecimal.ZERO, "en_attente");
        when(missionRepository.listByQueste(queste.id.toHexString())).thenReturn(List.of(enAttente));
        when(questeRepository.listAll()).thenReturn(new ArrayList<>(List.of(queste, autreQueteActive)));

        questeService.close(queste.id.toHexString(), new QuesteClosureRequestDTO(BigDecimal.ZERO));

        ArgumentCaptor<Mission> captor = ArgumentCaptor.forClass(Mission.class);
        verify(missionRepository).update(captor.capture());
        assertEquals(autreQueteActive.id.toHexString(), captor.getValue().getQuesteId());
    }

    @Test
    void close_reportsUnfinishedMissions_createsNewQueste_whenNoneActive() {
        Queste queste = new Queste();
        queste.id = new ObjectId();
        queste.setNumber(3);
        queste.setBuyerName("Awa D.");
        queste.setStatus("en_cours");
        queste.setPrimePercent(BigDecimal.valueOf(50));
        when(questeRepository.findById(queste.id)).thenReturn(queste);

        Mission ecartee = mission("a3", 2, BigDecimal.valueOf(50), BigDecimal.ZERO, "ecartee");
        when(missionRepository.listByQueste(queste.id.toHexString())).thenReturn(List.of(ecartee));
        // Après la mise à jour de statut de `queste`, plus aucune quête en_cours pour ce même acheteur.
        when(questeRepository.listAll()).thenReturn(new ArrayList<>(List.of(queste)));
        doAnswer(invocation -> {
            Queste persisted = invocation.getArgument(0);
            persisted.id = new ObjectId();
            return null;
        }).when(questeRepository).persist(any(Queste.class));

        questeService.close(queste.id.toHexString(), new QuesteClosureRequestDTO(BigDecimal.ZERO));

        ArgumentCaptor<Queste> questeCaptor = ArgumentCaptor.forClass(Queste.class);
        verify(questeRepository).persist(questeCaptor.capture());
        Queste nouvelle = questeCaptor.getValue();
        assertEquals("Awa D.", nouvelle.getBuyerName());
        assertEquals("en_cours", nouvelle.getStatus());
        assertEquals(4, nouvelle.getNumber());

        ArgumentCaptor<Mission> missionCaptor = ArgumentCaptor.forClass(Mission.class);
        verify(missionRepository).update(missionCaptor.capture());
        assertEquals(nouvelle.id.toHexString(), missionCaptor.getValue().getQuesteId());
    }

    @Test
    void validate_ventilateLeTransportAuProrataDeLaValeur_etReceptionneChaqueArticle() {
        Queste queste = new Queste();
        queste.id = new ObjectId();
        queste.setStatus("en_attente_validation");
        queste.setTransportReel(BigDecimal.valueOf(1000));
        when(questeRepository.findById(queste.id)).thenReturn(queste);

        // a1 pèse 75% du montant réel (3000/4000), a2 pèse 25% (1000/4000)
        Mission a1 = mission("a1", 10, BigDecimal.valueOf(300), BigDecimal.valueOf(3000), "terminee");
        Mission a2 = mission("a2", 5, BigDecimal.valueOf(200), BigDecimal.valueOf(1000), "terminee");
        Mission ecartee = mission("a3", 1, BigDecimal.valueOf(50), BigDecimal.ZERO, "ecartee");
        when(missionRepository.listByQueste(queste.id.toHexString())).thenReturn(List.of(a1, a2, ecartee));

        questeService.validate(queste.id.toHexString());

        verify(articleService).receiveStock(eq("a1"), eq(10), eq(BigDecimal.valueOf(3000)), eq(new BigDecimal("750.00")));
        verify(articleService).receiveStock(eq("a2"), eq(5), eq(BigDecimal.valueOf(1000)), eq(new BigDecimal("250.00")));
        verify(articleService, never()).receiveStock(eq("a3"), org.mockito.ArgumentMatchers.anyInt(), any(), any());
    }

    @Test
    void validate_agregeLesMissionsDuMemeArticle_enUnSeulAppelDeReception() {
        // Trois missions terminées sur le même article dans la même quête : sans agrégation,
        // receiveStock serait appelé 3 fois pour "a1", et chaque appel écraserait le précédent
        // (mauvais total, mauvaise comparaison de revue de prix). Un seul appel agrégé attendu.
        Queste queste = new Queste();
        queste.id = new ObjectId();
        queste.setStatus("en_attente_validation");
        queste.setTransportReel(BigDecimal.valueOf(300));
        when(questeRepository.findById(queste.id)).thenReturn(queste);

        Mission m1 = mission("a1", 15, BigDecimal.valueOf(100), BigDecimal.valueOf(47000), "terminee");
        Mission m2 = mission("a1", 15, BigDecimal.valueOf(100), BigDecimal.valueOf(48500), "terminee");
        Mission m3 = mission("a1", 15, BigDecimal.valueOf(100), BigDecimal.valueOf(47800), "terminee");
        when(missionRepository.listByQueste(queste.id.toHexString())).thenReturn(List.of(m1, m2, m3));

        questeService.validate(queste.id.toHexString());

        verify(articleService, org.mockito.Mockito.times(1))
            .receiveStock(eq("a1"), eq(45), eq(BigDecimal.valueOf(143300)), eq(BigDecimal.valueOf(300).setScale(2)));
    }

    @Test
    void validate_setsStatusClotureeAndValidatedAt() {
        Queste queste = new Queste();
        queste.id = new ObjectId();
        queste.setStatus("en_attente_validation");
        queste.setTransportReel(BigDecimal.ZERO);
        when(questeRepository.findById(queste.id)).thenReturn(queste);
        when(missionRepository.listByQueste(queste.id.toHexString())).thenReturn(List.of());

        QuesteResponseDTO result = questeService.validate(queste.id.toHexString());

        assertEquals("cloturee", result.status());
        verify(questeRepository).update(queste);
    }

    @Test
    void validate_throwsBadRequest_whenNotAwaitingValidation() {
        Queste queste = new Queste();
        queste.id = new ObjectId();
        queste.setStatus("en_cours");
        when(questeRepository.findById(queste.id)).thenReturn(queste);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> questeService.validate(queste.id.toHexString())
        );

        assertEquals(400, exception.getErrorCode().getStatusCode());
    }
}
