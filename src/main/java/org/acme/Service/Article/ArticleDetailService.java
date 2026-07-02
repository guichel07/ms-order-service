package org.acme.Service.Article;

import org.acme.DTO.ArticleDetailDTO;

public interface ArticleDetailService {
    ArticleDetailDTO getDetail(String articleId);
}
