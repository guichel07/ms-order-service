package org.acme.Service.Article;

import java.math.BigDecimal;
import java.util.List;
import org.acme.DTO.ArticleRequestDTO;
import org.acme.DTO.ArticleResponseDTO;

public interface ArticleService {
    List<ArticleResponseDTO> listAll();
    ArticleResponseDTO findByName(String name);
    ArticleResponseDTO findById(String id);
    ArticleResponseDTO register(ArticleRequestDTO articleDTO);
    List<ArticleResponseDTO> registerAll(List<ArticleRequestDTO> articles);
    ArticleResponseDTO update(String id, ArticleRequestDTO articleDTO);
    /** Retire (ou remet) un article du catalogue actif — pas de suppression définitive. */
    ArticleResponseDTO toggleArchive(String id);
    ArticleResponseDTO decrementQuantity(String id, BigDecimal quantityOrdered);
    ArticleResponseDTO incrementQuantity(String id, BigDecimal quantityAdded);

    /**
     * Injecte un lot réellement acheté (via une Mission validée) : ajoute la quantité au stock et
     * remplace le coût de revient par ce dernier lot (prix payé + part de transport ventilée).
     */
    ArticleResponseDTO receiveStock(String id, BigDecimal qty, BigDecimal purchasePrice, BigDecimal transportShare);
}
