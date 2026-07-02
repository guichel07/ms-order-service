package org.acme.Service.Article.impl;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.acme.DTO.ArticleDTO;
import org.acme.Entity.Article;
import org.acme.Exception.BusinessException;
import org.acme.Repository.ArticleRepository;
import org.acme.Service.Article.ArticleService;
import org.bson.types.ObjectId;

@ApplicationScoped
public class ArticleServiceImpl implements ArticleService {

    private ArticleRepository articleRepository;

    public ArticleServiceImpl(ArticleRepository _articleRepository) {
        this.articleRepository = _articleRepository;
    }

    public List<Article> listAll() {
        return articleRepository.listAll();
    }

    public Article findByName(String name) {
        Article articleFound = articleRepository.findByName(name);

        if (articleFound == null) {
            throw new BusinessException(
                Response.Status.NOT_FOUND,
                "Article not found"
            );
        }

        return articleFound;
    }

    public Article findById(String id) {
        Article articleFound = articleRepository.findById(new ObjectId(id));

        if (articleFound == null) {
            throw new BusinessException(
                Response.Status.NOT_FOUND,
                "Article not found" + id
            );
        }

        return articleFound;
    }

    public Article register(ArticleDTO articleDTO) {
        if (articleRepository.count("name", articleDTO.name()) != 0) {
            throw new BusinessException(
                Response.Status.CONFLICT,
                "Un article avec ce nom existe déjà"
            );
        }

        Article article = new Article();
        article.setName(articleDTO.name());
        article.setIcon(articleDTO.icon());
        article.setColor(articleDTO.color());
        article.setCategory(articleDTO.category());
        article.setPrice(articleDTO.price());
        article.setQuantity(0);

        articleRepository.persist(article);

        return article;
    }

    public Article update(String id, ArticleDTO articleDTO) {
        Article articleFound = articleRepository.findById(new ObjectId(id));

        if (articleFound == null) {
            throw new BusinessException(
                Response.Status.NOT_FOUND,
                "Article not found" + id
            );
        }

        if (
            !articleFound.getName().equals(articleDTO.name()) &&
            articleRepository.count("name", articleDTO.name()) != 0
        ) {
            throw new BusinessException(
                Response.Status.CONFLICT,
                "Un article avec ce nom existe déjà"
            );
        }

        articleFound.setName(articleDTO.name());
        articleFound.setIcon(articleDTO.icon());
        articleFound.setColor(articleDTO.color());
        articleFound.setCategory(articleDTO.category());
        articleFound.setPrice(articleDTO.price());

        articleRepository.update(articleFound);

        return articleFound;
    }

    public void delete(String id) {
        Article articleFound = articleRepository.findById(new ObjectId(id));

        if (articleFound == null) {
            throw new BusinessException(
                Response.Status.NOT_FOUND,
                "Article not found " + id
            );
        }

        articleRepository.delete(articleFound);
    }

    public Article decrementQuantity(String id, int quantityOrdered) {
        Article articleFound = articleRepository.findById(new ObjectId(id));

        if (articleFound == null) {
            throw new BusinessException(
                Response.Status.NOT_FOUND,
                "Article not found " + id
            );
        }

        articleFound.setQuantity(articleFound.getQuantity() - quantityOrdered);
        articleRepository.update(articleFound);

        return articleFound;
    }

    public Article incrementQuantity(String id, int quantityAdded) {
        Article articleFound = articleRepository.findById(new ObjectId(id));

        if (articleFound == null) {
            throw new BusinessException(
                Response.Status.NOT_FOUND,
                "Article not found " + id
            );
        }

        if (quantityAdded <= 0) {
            throw new BusinessException(
                Response.Status.BAD_REQUEST,
                "La quantité à ajouter doit être positive"
            );
        }

        articleFound.setQuantity(articleFound.getQuantity() + quantityAdded);
        articleRepository.update(articleFound);

        return articleFound;
    }
}
