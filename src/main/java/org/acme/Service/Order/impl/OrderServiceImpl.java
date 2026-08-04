package org.acme.Service.Order.impl;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.acme.DTO.ArticleResponseDTO;
import org.acme.DTO.OrderRequestDTO;
import org.acme.DTO.OrderResponseDTO;
import org.acme.Entity.Client;
import org.acme.Entity.Order;
import org.acme.Entity.OrderItem;
import org.acme.Exception.BusinessException;
import org.acme.Repository.ClientRepository;
import org.acme.Repository.OrderRepository;
import org.acme.Service.Alert.AlertService;
import org.acme.Service.Article.ArticleService;
import org.acme.Service.Order.OrderService;
import org.bson.types.ObjectId;

@ApplicationScoped
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final ArticleService articleService;
    private final ClientRepository clientRepository;
    private final AlertService alertService;

    public OrderServiceImpl(
        OrderRepository _orderRepository,
        ArticleService _articleService,
        ClientRepository _clientRepository,
        AlertService _alertService
    ) {
        this.orderRepository = _orderRepository;
        this.articleService = _articleService;
        this.clientRepository = _clientRepository;
        this.alertService = _alertService;
    }

    public List<OrderResponseDTO> listAll() {
        return orderRepository
            .listAll()
            .stream()
            .map(OrderResponseDTO::fromEntity)
            .toList();
    }

    public OrderResponseDTO findById(String id) {
        Order orderFound = orderRepository.findById(new ObjectId(id));

        if (orderFound == null) {
            throw new BusinessException(
                Response.Status.NOT_FOUND,
                "Order not found " + id
            );
        }

        return OrderResponseDTO.fromEntity(orderFound);
    }

    @Transactional
    public void delete(String id) {
        Order orderFound = orderRepository.findById(new ObjectId(id));

        if (orderFound == null) {
            throw new BusinessException(
                Response.Status.NOT_FOUND,
                "Order not found " + id
            );
        }

        orderRepository.delete(orderFound);
    }

    @Transactional
    public OrderResponseDTO register(OrderRequestDTO orderDTO) {
        Client client = resolveClient(orderDTO.clientId());

        ArrayList<OrderItem> items = new ArrayList<>();
        ArrayList<OrderItem> negativeMarginItems = new ArrayList<>();
        BigDecimal delta = BigDecimal.ZERO;

        for (OrderRequestDTO.OrderLineRequestDTO line : orderDTO.items()) {
            ArticleResponseDTO article = articleService.findById(line.articleId());

            BigDecimal ratio = resolveSoldAsRatio(article, line.soldAsLabel());
            // Coût de revient DU PALIER VENDU (ex: coût des 12 unités atomiques d'un
            // carton), pas le coût de l'unité atomique seule — indispensable pour que
            // la marge (price - unitCost) reste juste quand price est le prix du palier
            // entier, pas un prix à l'unité atomique.
            BigDecimal unitCost = article.costPrice().multiply(ratio);

            OrderItem item = new OrderItem(
                article.id(),
                article.name(),
                line.price(),
                line.quantity()
            );
            item.setUnitCost(unitCost);
            item.setSoldAsLabel(line.soldAsLabel() != null ? line.soldAsLabel() : article.atomicUnit());
            item.setSoldAsRatio(ratio);
            items.add(item);

            delta = delta.add(
                line
                    .price()
                    .subtract(unitCost)
                    .multiply(line.quantity())
            );

            if (line.price().compareTo(unitCost) < 0) {
                negativeMarginItems.add(item);
            }

            articleService.decrementQuantity(line.articleId(), line.quantity().multiply(ratio));
        }

        Order order = new Order();
        order.setReceiptNumber(generateReceiptNumber());
        order.setReceiptDate(new Date());
        order.setDailySummary(orderDTO.dailySummary());
        order.setSellerName(orderDTO.sellerName());
        order.setSaleDate(orderDTO.saleDate());
        order.setEmail(orderDTO.email());
        order.setClientPhone(client.getPhone());
        order.setClientId(orderDTO.clientId());
        order.setArticles(items);
        order.setDelta(delta);

        orderRepository.persist(order);

        // Le Gendarme : une vente en dessous du coût de revient réel procède quand
        // même (jamais bloquée), mais lève une alerte pour revue admin.
        for (OrderItem negativeMarginItem : negativeMarginItems) {
            alertService.recordNegativeMarginSale(
                order.id.toHexString(),
                negativeMarginItem.getArticleId(),
                order.getSellerName(),
                negativeMarginItem.getName(),
                negativeMarginItem.getPrice(),
                negativeMarginItem.getUnitCost(),
                negativeMarginItem.getPrice().subtract(negativeMarginItem.getUnitCost()),
                negativeMarginItem.getQuantityOrdered()
            );
        }

        return OrderResponseDTO.fromEntity(order);
    }

    /**
     * Résout le ratio (nb d'unités atomiques) du palier de vente choisi. soldAsLabel
     * null/vide ou ne correspondant à aucun palier connu = vente à l'unité atomique
     * (ratio 1) — matché par libellé plutôt que par index pour rester valide même si
     * l'admin réordonne/édite packagingLevels entre deux ventes.
     */
    private BigDecimal resolveSoldAsRatio(ArticleResponseDTO article, String soldAsLabel) {
        if (soldAsLabel == null || soldAsLabel.isBlank() || article.packagingLevels() == null) {
            return BigDecimal.ONE;
        }
        return article
            .packagingLevels()
            .stream()
            .filter(level -> soldAsLabel.equals(level.label()))
            .map(level -> level.ratio())
            .findFirst()
            .orElse(BigDecimal.ONE);
    }

    /**
     * Le front a déjà résolu (ou créé) le client avant de construire la commande — il
     * envoie directement son id. On vérifie juste qu'il existe bien.
     */
    private Client resolveClient(String clientId) {
        Client client;
        try {
            client = clientRepository.findById(new ObjectId(clientId));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(Response.Status.BAD_REQUEST, "Identifiant client invalide");
        }

        if (client == null) {
            throw new BusinessException(Response.Status.NOT_FOUND, "Client not found");
        }

        return client;
    }

    @Transactional
    public List<OrderResponseDTO> registerAll(List<OrderRequestDTO> orders) {
        return orders.stream().map(this::register).toList();
    }

    private String generateReceiptNumber() {
        return (
            "CMD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase()
        );
    }

    public BigDecimal getTotalSoldTodayByEmail(String email) {
        // Bornes du jour courant : minuit aujourd'hui -> minuit demain
        ZoneId zone = ZoneId.systemDefault();
        Date startOfDay = Date.from(
            LocalDate.now().atStartOfDay(zone).toInstant()
        );
        Date endOfDay = Date.from(
            LocalDate.now().plusDays(1).atStartOfDay(zone).toInstant()
        );

        List<Order> ordersToday = orderRepository.list(
            "email = ?1 and receiptDate >= ?2 and receiptDate < ?3",
            email,
            startOfDay,
            endOfDay
        );

        BigDecimal total = BigDecimal.ZERO;

        for (Order order : ordersToday) {
            for (OrderItem item : order.getArticles()) {
                BigDecimal lineTotal = item
                    .getPrice()
                    .multiply(item.getQuantityOrdered());
                total = total.add(lineTotal);
            }
        }

        return total;
    }
}
