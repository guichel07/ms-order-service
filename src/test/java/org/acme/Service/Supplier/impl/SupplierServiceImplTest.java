package org.acme.Service.Supplier.impl;

import java.math.BigDecimal;
import java.util.List;
import org.acme.DTO.SupplierNoteRequestDTO;
import org.acme.DTO.SupplierRequestDTO;
import org.acme.DTO.SupplierResponseDTO;
import org.acme.Entity.Article;
import org.acme.Entity.Supplier;
import org.acme.Entity.SupplierNote;
import org.acme.Exception.BusinessException;
import org.acme.Repository.ArticleRepository;
import org.acme.Repository.SupplierRepository;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires pour {@link SupplierServiceImpl}.
 * SupplierRepository et ArticleRepository sont mockés — aucune base de données réelle nécessaire.
 */
@ExtendWith(MockitoExtension.class)
class SupplierServiceImplTest {

    @Mock
    private SupplierRepository supplierRepository;

    @Mock
    private ArticleRepository articleRepository;

    @InjectMocks
    private SupplierServiceImpl supplierService;

    private Article article(String name, String icon) {
        Article a = new Article();
        a.id = new ObjectId();
        a.setName(name);
        a.setIcon(icon);
        a.setPrice(BigDecimal.valueOf(500));
        a.setCostPrice(BigDecimal.valueOf(200));
        a.setQuantity(BigDecimal.valueOf(10));
        a.setArchived(false);
        return a;
    }

    @Test
    void create_persistsSupplier_andReturnsEnrichedArticles() {
        Article riz = article("Riz parfumé 5kg", "🍚");
        Article huile = article("Huile de palme 1L", "🛢️");
        when(articleRepository.findById(riz.id)).thenReturn(riz, riz);
        when(articleRepository.findById(huile.id)).thenReturn(huile, huile);
        doAnswer(invocation -> {
            Supplier persisted = invocation.getArgument(0);
            persisted.id = new ObjectId();
            return null;
        }).when(supplierRepository).persist(any(Supplier.class));

        SupplierRequestDTO request = new SupplierRequestDTO(
            "Fournisseur Marché Total",
            "+242051234567",
            List.of(riz.id.toHexString(), huile.id.toHexString())
        );

        SupplierResponseDTO result = supplierService.create(request);

        assertEquals("Fournisseur Marché Total", result.name());
        assertEquals("+242051234567", result.phone());
        assertEquals(2, result.articles().size());
        assertEquals("Riz parfumé 5kg", result.articles().get(0).name());
        assertEquals(0, result.notes().size());
    }

    @Test
    void create_throwsBadRequest_whenAnArticleDoesNotExist() {
        String unknownId = new ObjectId().toHexString();
        when(articleRepository.findById(new ObjectId(unknownId))).thenReturn(null);

        SupplierRequestDTO request = new SupplierRequestDTO(
            "Fournisseur X", "+242051234567", List.of(unknownId)
        );

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> supplierService.create(request)
        );

        assertEquals(400, exception.getErrorCode().getStatusCode());
    }

    @Test
    void findById_throwsNotFound_whenSupplierDoesNotExist() {
        String id = new ObjectId().toHexString();
        when(supplierRepository.findById(new ObjectId(id))).thenReturn(null);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> supplierService.findById(id)
        );

        assertEquals(404, exception.getErrorCode().getStatusCode());
    }

    @Test
    void update_replacesFieldsAndArticles_whenSupplierExists() {
        Supplier existing = new Supplier();
        existing.id = new ObjectId();
        existing.setName("Ancien nom");
        existing.setPhone("+242000000000");
        existing.setArticleIds(List.of());
        existing.setNotes(List.of());
        when(supplierRepository.findById(existing.id)).thenReturn(existing);

        Article sucre = article("Sucre en poudre 1kg", "🧂");
        when(articleRepository.findById(sucre.id)).thenReturn(sucre, sucre);

        SupplierRequestDTO request = new SupplierRequestDTO(
            "Nouveau nom", "+242051234567", List.of(sucre.id.toHexString())
        );

        SupplierResponseDTO result = supplierService.update(existing.id.toHexString(), request);

        assertEquals("Nouveau nom", result.name());
        assertEquals(1, result.articles().size());
        verify(supplierRepository).update(existing);
    }

    @Test
    void listAll_returnsAllStoredSuppliers_enrichedWithCurrentArticleData() {
        Article riz = article("Riz parfumé 5kg", "🍚");
        when(articleRepository.findById(riz.id)).thenReturn(riz, riz);

        Supplier s1 = new Supplier();
        s1.id = new ObjectId();
        s1.setName("Fournisseur A");
        s1.setArticleIds(List.of(riz.id.toHexString()));
        s1.setNotes(List.of());
        Supplier s2 = new Supplier();
        s2.id = new ObjectId();
        s2.setName("Fournisseur B");
        s2.setArticleIds(List.of());
        s2.setNotes(List.of());
        when(supplierRepository.listAll()).thenReturn(List.of(s1, s2));

        List<SupplierResponseDTO> result = supplierService.listAll();

        assertEquals(2, result.size());
        assertEquals("Fournisseur A", result.get(0).name());
        assertEquals("Fournisseur B", result.get(1).name());
    }

    @Test
    void toResponseDTO_skipsArticle_whenItWasDeletedSinceCreation() {
        Supplier supplier = new Supplier();
        supplier.id = new ObjectId();
        supplier.setName("Fournisseur orphelin");
        String deletedArticleId = new ObjectId().toHexString();
        supplier.setArticleIds(List.of(deletedArticleId));
        supplier.setNotes(List.of());
        when(supplierRepository.findById(supplier.id)).thenReturn(supplier);
        when(articleRepository.findById(new ObjectId(deletedArticleId))).thenReturn(null);

        SupplierResponseDTO result = supplierService.findById(supplier.id.toHexString());

        assertEquals(0, result.articles().size());
    }

    @Test
    void linkArticle_addsArticleId_whenNotAlreadyLinked() {
        Article riz = article("Riz parfumé 5kg", "🍚");
        when(articleRepository.findById(riz.id)).thenReturn(riz, riz);
        Supplier supplier = new Supplier();
        supplier.id = new ObjectId();
        supplier.setName("Fournisseur X");
        supplier.setArticleIds(List.of());
        supplier.setNotes(List.of());
        when(supplierRepository.findById(supplier.id)).thenReturn(supplier);

        SupplierResponseDTO result = supplierService.linkArticle(supplier.id.toHexString(), riz.id.toHexString());

        assertEquals(1, result.articles().size());
        assertEquals("Riz parfumé 5kg", result.articles().get(0).name());
        verify(supplierRepository).update(supplier);
    }

    @Test
    void linkArticle_isIdempotent_whenArticleAlreadyLinked() {
        Article riz = article("Riz parfumé 5kg", "🍚");
        when(articleRepository.findById(riz.id)).thenReturn(riz, riz);
        Supplier supplier = new Supplier();
        supplier.id = new ObjectId();
        supplier.setName("Fournisseur X");
        supplier.setArticleIds(List.of(riz.id.toHexString()));
        supplier.setNotes(List.of());
        when(supplierRepository.findById(supplier.id)).thenReturn(supplier);

        SupplierResponseDTO result = supplierService.linkArticle(supplier.id.toHexString(), riz.id.toHexString());

        assertEquals(1, result.articles().size());
        verify(supplierRepository, org.mockito.Mockito.never()).update(any(Supplier.class));
    }

    @Test
    void addNote_appendsNote_andReturnsMostRecentFirst() {
        Supplier supplier = new Supplier();
        supplier.id = new ObjectId();
        supplier.setName("Fournisseur X");
        supplier.setArticleIds(List.of());
        supplier.setNotes(List.of(new SupplierNote("Première note", Instant.parse("2026-01-01T00:00:00Z"))));
        when(supplierRepository.findById(supplier.id)).thenReturn(supplier);

        SupplierResponseDTO result = supplierService.addNote(
            supplier.id.toHexString(), new SupplierNoteRequestDTO("Deuxième note")
        );

        assertEquals(2, result.notes().size());
        assertEquals("Deuxième note", result.notes().get(0).text());
        assertEquals("Première note", result.notes().get(1).text());
        verify(supplierRepository).update(supplier);
    }
}
