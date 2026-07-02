package org.acme.Service.Alert;

import java.math.BigDecimal;
import java.util.List;
import org.acme.DTO.AlertResponseDTO;

public interface AlertService {
    List<AlertResponseDTO> listAll();
    AlertResponseDTO toggleResolved(String id);

    /**
     * Enregistre une vente à bénéfice négatif détectée par OrderServiceImpl.register().
     * `margin` est le bénéfice unitaire (attemptedPrice - costPrice), toujours négatif ou nul ici.
     */
    AlertResponseDTO recordNegativeMarginSale(
        String orderId,
        String articleId,
        String vendor,
        String productName,
        BigDecimal attemptedPrice,
        BigDecimal costPrice,
        BigDecimal margin,
        BigDecimal quantity
    );
}
