package org.acme.Service.BundleContent;

import org.acme.DTO.ArticleBundleDTO;
import org.acme.DTO.BundleContentDTO;

public interface BundleContentService {
    /** Calcule le texte IA à partir du bundle donné, et (ré)écrit le contenu stocké pour sa clé. */
    BundleContentDTO generateAndStore(ArticleBundleDTO bundle);

    /** Lève une BusinessException NOT_FOUND si aucun contenu n'a encore été généré pour cette clé. */
    BundleContentDTO getStored(String key);
}
