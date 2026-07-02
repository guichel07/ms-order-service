package org.acme.Service.Article;

import java.util.List;
import org.acme.DTO.ArticleRankingEntryDTO;

public interface ArticleRankingService {
    List<ArticleRankingEntryDTO> getRanking(String period);
}
