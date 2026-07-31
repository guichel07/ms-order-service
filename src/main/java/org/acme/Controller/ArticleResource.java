package org.acme.Controller;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.acme.DTO.ArticleBundleDTO;
import org.acme.DTO.ArticleDetailDTO;
import org.acme.DTO.ArticleRankingEntryDTO;
import org.acme.DTO.ArticleRequestDTO;
import org.acme.DTO.ArticleResponseDTO;
import org.acme.DTO.BundleContentDTO;
import org.acme.DTO.OccasionBundleRequestDTO;
import org.acme.DTO.QuantityAdjustmentDTO;
import org.acme.DTO.QuantityOrderedDTO;
import org.acme.Service.Article.ArticleBundleService;
import org.acme.Service.Article.ArticleDetailService;
import org.acme.Service.Article.ArticleRankingService;
import org.acme.Service.Article.ArticleService;
import org.acme.Service.BundleContent.BundleContentService;

@Path("/articles")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ArticleResource {

    @Inject
    ArticleService articleService;

    @Inject
    ArticleDetailService articleDetailService;

    @Inject
    ArticleRankingService articleRankingService;

    @Inject
    ArticleBundleService articleBundleService;

    @Inject
    BundleContentService bundleContentService;

    @GET
    @RolesAllowed({ "SELLER", "ADMIN" })
    public List<ArticleResponseDTO> listAll() {
        return articleService.listAll();
    }

    /**
     * Classement catalogue pour une période — best-sellers / flops côté front en triant
     * cette liste. À consommer par la page catalogue de ms-section-admin.
     */
    @GET
    @RolesAllowed({ "SELLER", "ADMIN" })
    @Path("/ranking/{period}")
    public List<ArticleRankingEntryDTO> getRanking(@PathParam("period") String period) {
        return articleRankingService.getRanking(period);
    }

    /**
     * Agrégat performance d'un article (les 4 périodes en un seul appel) — à
     * consommer directement par la fiche article du front.
     */
    @GET
    @RolesAllowed({ "SELLER", "ADMIN" })
    @Path("/{id}/detail")
    public ArticleDetailDTO getDetail(@PathParam("id") String id) {
        return articleDetailService.getDetail(id);
    }

    /**
     * Paires d'articles souvent achetés ensemble sur une tranche horaire ("matin",
     * "midi", "soir") — alimente les PDF marketing par moment de la journée.
     */
    @GET
    @RolesAllowed({ "SELLER", "ADMIN" })
    @Path("/bundles/{periodKey}")
    public ArticleBundleDTO getBundles(@PathParam("periodKey") String periodKey) {
        return articleBundleService.getBundlesForTimeOfDay(periodKey);
    }

    /**
     * Paires d'articles souvent achetés ensemble sur une tendance calendaire ("debut-semaine",
     * "fin-semaine", "debut-mois", "fin-mois") — alimente les PDF marketing par période du mois/semaine.
     */
    @GET
    @RolesAllowed({ "SELLER", "ADMIN" })
    @Path("/bundles/tendance/{trendKey}")
    public ArticleBundleDTO getCalendarTrendBundles(@PathParam("trendKey") String trendKey) {
        return articleBundleService.getBundlesForCalendarTrend(trendKey);
    }

    /**
     * Paires d'articles souvent achetés ensemble au sein d'une même catégorie du catalogue
     * (ex: "Hygiène", "Maison") — alimente les PDF marketing par besoin.
     */
    @GET
    @RolesAllowed({ "SELLER", "ADMIN" })
    @Path("/bundles/categorie/{category}")
    public ArticleBundleDTO getCategoryBundles(@PathParam("category") String category) {
        return articleBundleService.getBundlesForCategory(category);
    }

    /**
     * Thème manuel/occasion défini par l'admin (titre + fenêtre de dates), ex: "Soirée LDC" —
     * relance le même moteur de lift sur la fenêtre choisie.
     */
    @POST
    @Path("/bundles/occasion")
    @RolesAllowed({ "SELLER", "ADMIN" })
    public ArticleBundleDTO getOccasionBundles(@Valid OccasionBundleRequestDTO request) {
        return articleBundleService.getBundlesForOccasion(request);
    }

    /**
     * Paires les plus fortement liées sur tout le catalogue (6 mois, sans filtre) — sert de point
     * de départ pour créer une gamme dans l'admin.
     */
    @GET
    @RolesAllowed({ "SELLER", "ADMIN" })
    @Path("/bundles/suggestions")
    public ArticleBundleDTO getCatalogSuggestions() {
        return articleBundleService.getCatalogSuggestions();
    }

    /**
     * Contenu déjà généré (texte marketing + conseil d'usage) pour une clé de bundle — ce que
     * consomme la page d'impression PDF. 404 si rien n'a encore été généré pour cette clé.
     */
    @GET
    @RolesAllowed({ "SELLER", "ADMIN" })
    @Path("/bundles/contenu/{key}")
    public BundleContentDTO getBundleContent(@PathParam("key") String key) {
        return bundleContentService.getStored(key);
    }

    /** (Re)génère maintenant le contenu PDF pour une tranche horaire, sans attendre le job planifié. */
    @POST
    @Consumes(MediaType.WILDCARD)
    @RolesAllowed({ "SELLER", "ADMIN" })
    @Path("/bundles/{periodKey}/contenu")
    public BundleContentDTO generateTimeOfDayContent(@PathParam("periodKey") String periodKey) {
        return bundleContentService.generateAndStore(articleBundleService.getBundlesForTimeOfDay(periodKey));
    }

    /** (Re)génère maintenant le contenu PDF pour une tendance calendaire, sans attendre le job planifié. */
    @POST
    @Consumes(MediaType.WILDCARD)
    @RolesAllowed({ "SELLER", "ADMIN" })
    @Path("/bundles/tendance/{trendKey}/contenu")
    public BundleContentDTO generateCalendarTrendContent(@PathParam("trendKey") String trendKey) {
        return bundleContentService.generateAndStore(articleBundleService.getBundlesForCalendarTrend(trendKey));
    }

    /** (Re)génère maintenant le contenu PDF pour une catégorie, sans attendre le job planifié. */
    @POST
    @Consumes(MediaType.WILDCARD)
    @RolesAllowed({ "SELLER", "ADMIN" })
    @Path("/bundles/categorie/{category}/contenu")
    public BundleContentDTO generateCategoryContent(@PathParam("category") String category) {
        return bundleContentService.generateAndStore(articleBundleService.getBundlesForCategory(category));
    }

    /** Génère le contenu PDF pour un thème manuel/occasion — toujours à la demande, jamais par le cron. */
    @POST
    @Path("/bundles/occasion/contenu")
    @RolesAllowed({ "SELLER", "ADMIN" })
    public BundleContentDTO generateOccasionContent(@Valid OccasionBundleRequestDTO request) {
        return bundleContentService.generateAndStore(articleBundleService.getBundlesForOccasion(request));
    }

    @GET
    @RolesAllowed({ "SELLER", "ADMIN" })
    @Path("/{id}")
    public ArticleResponseDTO getById(@PathParam("id") String id) {
        return articleService.findById(id);
    }

    @GET
    @RolesAllowed({ "SELLER", "ADMIN" })
    @Path("/by-name/{name}")
    public ArticleResponseDTO getByName(@PathParam("name") String name) {
        return articleService.findByName(name);
    }

    @POST
    @RolesAllowed({ "ADMIN" })
    public Response register(@Valid ArticleRequestDTO articleDTO) {
        ArticleResponseDTO created = articleService.register(articleDTO);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @POST
    @Path("/batch")
    @RolesAllowed({ "ADMIN" })
    public Response registerAll(@Valid List<@Valid ArticleRequestDTO> articles) {
        List<ArticleResponseDTO> created = articleService.registerAll(articles);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @PUT
    @RolesAllowed({ "ADMIN" })
    @Path("/{id}")
    public ArticleResponseDTO update(
        @PathParam("id") String id,
        @Valid ArticleRequestDTO articleDTO
    ) {
        return articleService.update(id, articleDTO);
    }

    /**
     * Retire (ou remet) l'article du catalogue actif — pas de suppression définitive.
     * @Consumes wildcard : pas de corps de requête, ne pas exiger de Content-Type ici
     * malgré le @Consumes(APPLICATION_JSON) hérité de la classe.
     */
    @POST
    @Consumes(MediaType.WILDCARD)
    @Path("/{id}/archive")
    @RolesAllowed({ "SELLER", "ADMIN" })
    public ArticleResponseDTO toggleArchive(@PathParam("id") String id) {
        return articleService.toggleArchive(id);
    }

    @PATCH
    @RolesAllowed({ "SELLER", "ADMIN" })
    @Path("/{id}/quantity")
    public ArticleResponseDTO restock(
        @PathParam("id") String id,
        @Valid QuantityAdjustmentDTO adjustment
    ) {
        return articleService.incrementQuantity(id, adjustment.quantity());
    }

    @PATCH
    @RolesAllowed({ "SELLER", "ADMIN" })
    @Path("/{id}/quantityOrdered")
    public ArticleResponseDTO destock(
        @PathParam("id") String id,
        @Valid QuantityOrderedDTO quantityOrdered
    ) {
        return articleService.decrementQuantity(id, quantityOrdered.quantity());
    }
}
