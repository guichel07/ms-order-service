/**
 * Migration — vente au kilo / demi / quart (ms-market-front)
 *
 * Contexte : Article.quantity/purchasedQuantity/criticalStock, OrderItem.quantityOrdered
 * et Alert.quantity passent de int (BSON int32) à BigDecimal (BSON Decimal128) côté code
 * Java, et Article gagne un nouveau champ `unit` (enum PIECE/KG). Les documents Mongo
 * existants ont ces champs en int32 et n'ont pas `unit` du tout.
 *
 * Ce script :
 *   1. Backfill `unit` sur tous les articles qui ne l'ont pas -> "PIECE" (aucun article
 *      existant n'est vendu au kilo aujourd'hui).
 *      /!\ IMPORTANT : "PIECE" en majuscules, pas "piece". Le codec BSON par défaut de
 *      Quarkus MongoDB Panache (PojoCodecProvider) sérialise un enum Java via son
 *      .name() — c'est un format DIFFÉRENT du JSON exposé par l'API REST (qui, lui, est
 *      passé par Jackson et les annotations @JsonProperty("piece")/@JsonProperty("kg")
 *      sur Unit.java, donc "piece"/"kg" en minuscule). Les deux couches (stockage Mongo
 *      vs. JSON API) ont des conventions de casse différentes pour le même enum — NE PAS
 *      confondre les deux. Avant de lancer ce script en prod, vérifier empiriquement le
 *      format réel en persistant un Article de test via le code Java (pas ce script) et
 *      en inspectant le document brut avec `db.Articles.findOne()` — si le format observé
 *      diffère de "PIECE", corriger la constante UNIT_DEFAULT ci-dessous en conséquence.
 *   2. Convertit quantity/purchasedQuantity/criticalStock (Articles), quantity (Alerts) et
 *      articles.$[].quantityOrdered (Orders, sous-documents imbriqués) de int32 vers
 *      Decimal128 — un $toDecimal suffit, Mongo gère la conversion numérique proprement.
 *
 * Usage :
 *   1. DRY_RUN=true d'abord (défaut) : affiche le nombre de documents qui seraient
 *      modifiés dans chaque collection, ne modifie rien.
 *   2. Faire un dump de sauvegarde de la base avant toute exécution réelle :
 *        mongodump --uri="$MONGO_URL" --db="$MONGO_NAME" --out=./backup-pre-migration
 *   3. Lancer d'abord sur un dump/replica de staging, jamais directement en prod.
 *   4. Une fois validé sur staging : mongosh "$MONGO_URL/$MONGO_NAME" --eval "var DRY_RUN=false" scripts/migrations/2026-08-01-fractional-quantity.js
 *
 * Rollback : restaurer le dump pris à l'étape 2 (mongorestore) si un problème est détecté
 * après exécution en prod.
 */

const UNIT_DEFAULT = "PIECE"; // À corriger si la vérification empirique (voir ci-dessus) montre un format différent.
const dryRun = typeof DRY_RUN === "undefined" ? true : DRY_RUN;

print(`\n=== Migration fractional-quantity — ${dryRun ? "DRY RUN (aucune écriture)" : "EXÉCUTION RÉELLE"} ===\n`);

// --- 1. Articles : backfill unit + conversion Decimal128 ---
const articlesMissingUnit = db.Articles.countDocuments({ unit: { $exists: false } });
const articlesIntQuantity = db.Articles.countDocuments({ quantity: { $type: "int" } });
print(`Articles sans champ 'unit'            : ${articlesMissingUnit}`);
print(`Articles avec 'quantity' encore int32 : ${articlesIntQuantity}`);

if (!dryRun) {
  const unitResult = db.Articles.updateMany(
    { unit: { $exists: false } },
    { $set: { unit: UNIT_DEFAULT } }
  );
  print(`  -> unit backfillé sur ${unitResult.modifiedCount} article(s)`);

  const qtyResult = db.Articles.aggregate([
    { $match: {
        $or: [
          { quantity: { $type: "int" } },
          { purchasedQuantity: { $type: "int" } },
          { criticalStock: { $type: "int" } }
        ]
    } },
    { $set: {
        quantity: { $toDecimal: "$quantity" },
        purchasedQuantity: { $toDecimal: "$purchasedQuantity" },
        criticalStock: { $toDecimal: "$criticalStock" }
    } },
    { $merge: { into: "Articles", whenMatched: "merge", whenNotMatched: "discard" } }
  ]).toArray();
  print(`  -> quantity/purchasedQuantity/criticalStock convertis en Decimal128`);
}

// --- 2. Alerts : quantity -> Decimal128 ---
const alertsIntQuantity = db.Alerts.countDocuments({ quantity: { $type: "int" } });
print(`\nAlerts avec 'quantity' encore int32    : ${alertsIntQuantity}`);

if (!dryRun) {
  db.Alerts.aggregate([
    { $match: { quantity: { $type: "int" } } },
    { $set: { quantity: { $toDecimal: "$quantity" } } },
    { $merge: { into: "Alerts", whenMatched: "merge", whenNotMatched: "discard" } }
  ]).toArray();
  print(`  -> quantity converti en Decimal128`);
}

// --- 3. Orders : articles[].quantityOrdered -> Decimal128 (sous-documents imbriqués) ---
const ordersWithIntLines = db.Orders.countDocuments({ "articles.quantityOrdered": { $type: "int" } });
print(`\nOrders avec au moins une ligne quantityOrdered encore int32 : ${ordersWithIntLines}`);

if (!dryRun) {
  const cursor = db.Orders.find({ "articles.quantityOrdered": { $type: "int" } });
  let updated = 0;
  cursor.forEach((order) => {
    const articles = (order.articles || []).map((item) => {
      if (typeof item.quantityOrdered === "number") {
        item.quantityOrdered = NumberDecimal(item.quantityOrdered.toString());
      }
      return item;
    });
    db.Orders.updateOne({ _id: order._id }, { $set: { articles: articles } });
    updated++;
  });
  print(`  -> ${updated} commande(s) mise(s) à jour`);
}

print(`\n=== ${dryRun ? "Dry run terminé — relancer avec DRY_RUN=false pour appliquer" : "Migration appliquée"} ===\n`);
