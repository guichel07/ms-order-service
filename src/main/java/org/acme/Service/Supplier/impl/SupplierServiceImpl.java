package org.acme.Service.Supplier.impl;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.acme.DTO.SupplierArticleDTO;
import org.acme.DTO.SupplierNoteRequestDTO;
import org.acme.DTO.SupplierNoteResponseDTO;
import org.acme.DTO.SupplierRequestDTO;
import org.acme.DTO.SupplierResponseDTO;
import org.acme.Entity.Article;
import org.acme.Entity.Supplier;
import org.acme.Entity.SupplierNote;
import org.acme.Exception.BusinessException;
import org.acme.Repository.ArticleRepository;
import org.acme.Repository.SupplierRepository;
import org.acme.Service.Supplier.SupplierService;
import org.bson.types.ObjectId;

@ApplicationScoped
public class SupplierServiceImpl implements SupplierService {

    private final SupplierRepository supplierRepository;
    private final ArticleRepository articleRepository;

    @Inject
    public SupplierServiceImpl(SupplierRepository _supplierRepository, ArticleRepository _articleRepository) {
        this.supplierRepository = _supplierRepository;
        this.articleRepository = _articleRepository;
    }

    @Override
    public SupplierResponseDTO create(SupplierRequestDTO request) {
        List<String> articleIds = validateArticleIds(request.articleIds());

        Supplier supplier = new Supplier();
        supplier.setName(request.name());
        supplier.setPhone(request.phone());
        supplier.setArticleIds(articleIds);
        supplier.setNotes(List.of());
        supplier.setCreatedAt(Instant.now());
        supplier.setUpdatedAt(supplier.getCreatedAt());
        supplierRepository.persist(supplier);

        return toResponseDTO(supplier);
    }

    @Override
    public SupplierResponseDTO update(String id, SupplierRequestDTO request) {
        Supplier supplier = findEntityById(id);
        List<String> articleIds = validateArticleIds(request.articleIds());

        supplier.setName(request.name());
        supplier.setPhone(request.phone());
        supplier.setArticleIds(articleIds);
        supplier.setUpdatedAt(Instant.now());
        supplierRepository.update(supplier);

        return toResponseDTO(supplier);
    }

    @Override
    public List<SupplierResponseDTO> listAll() {
        return supplierRepository.listAll().stream().map(this::toResponseDTO).toList();
    }

    @Override
    public SupplierResponseDTO findById(String id) {
        return toResponseDTO(findEntityById(id));
    }

    @Override
    public SupplierResponseDTO addNote(String id, SupplierNoteRequestDTO request) {
        Supplier supplier = findEntityById(id);
        List<SupplierNote> notes = new java.util.ArrayList<>(
            supplier.getNotes() == null ? List.of() : supplier.getNotes()
        );
        notes.add(new SupplierNote(request.text(), Instant.now()));
        supplier.setNotes(notes);
        supplier.setUpdatedAt(Instant.now());
        supplierRepository.update(supplier);

        return toResponseDTO(supplier);
    }

    @Override
    public SupplierResponseDTO linkArticle(String id, String articleId) {
        Supplier supplier = findEntityById(id);
        List<String> articleIds = supplier.getArticleIds() == null
            ? new java.util.ArrayList<>()
            : new java.util.ArrayList<>(supplier.getArticleIds());

        if (!articleIds.contains(articleId)) {
            articleIds.add(articleId);
            supplier.setArticleIds(articleIds);
            supplier.setUpdatedAt(Instant.now());
            supplierRepository.update(supplier);
        }

        return toResponseDTO(supplier);
    }

    private Supplier findEntityById(String id) {
        Supplier supplier = supplierRepository.findById(new ObjectId(id));
        if (supplier == null) {
            throw new BusinessException(Response.Status.NOT_FOUND, "Supplier not found " + id);
        }
        return supplier;
    }

    /** Valide que chaque article référencé existe encore avant de le rattacher au fournisseur. */
    private List<String> validateArticleIds(List<String> articleIds) {
        if (articleIds == null) {
            return List.of();
        }
        return articleIds.stream()
            .map(articleId -> {
                if (articleRepository.findById(new ObjectId(articleId)) == null) {
                    throw new BusinessException(
                        Response.Status.BAD_REQUEST,
                        "Article inconnu : " + articleId
                    );
                }
                return articleId;
            })
            .toList();
    }

    private SupplierResponseDTO toResponseDTO(Supplier supplier) {
        List<SupplierArticleDTO> articles = supplier.getArticleIds() == null
            ? List.of()
            : supplier.getArticleIds().stream()
                .map(this::toArticleDTO)
                .filter(article -> article != null)
                .toList();

        List<SupplierNoteResponseDTO> notes = supplier.getNotes() == null
            ? List.of()
            : supplier.getNotes().stream()
                .map(note -> new SupplierNoteResponseDTO(note.getText(), note.getCreatedAt()))
                .sorted(Comparator.comparing(SupplierNoteResponseDTO::createdAt).reversed())
                .toList();

        return new SupplierResponseDTO(
            supplier.id.toHexString(),
            supplier.getName(),
            supplier.getPhone(),
            articles,
            notes,
            supplier.getCreatedAt(),
            supplier.getUpdatedAt()
        );
    }

    private SupplierArticleDTO toArticleDTO(String articleId) {
        Article article = articleRepository.findById(new ObjectId(articleId));
        if (article == null) {
            // L'article a été supprimé du catalogue depuis — on ignore cette ligne plutôt que de planter.
            return null;
        }
        return new SupplierArticleDTO(articleId, article.getName(), article.getIcon());
    }
}
