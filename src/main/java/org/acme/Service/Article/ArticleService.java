package org.acme.Service.Article;

import java.util.List;
import org.acme.DTO.ArticleDTO;
import org.acme.Entity.Article;

public interface ArticleService {
    List<Article> listAll();
    Article findByName(String name);
    Article findById(String id);
    Article register(ArticleDTO articleDTO);
    Article update(String id, ArticleDTO articleDTO);
    void delete(String id);
    Article decrementQuantity(String id, int quantityOrdered);
    Article incrementQuantity(String id, int quantityAdded);
}
