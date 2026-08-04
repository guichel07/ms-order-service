/**
 * Migration — refonte "produit multi-modes de vente" (atomicUnit / packagingLevels)
 *
 * Contexte : Article.unit (enum PIECE/KG) est remplacé par :
 *   - atomicUnit (String libre : "pièce", "kg", "verre"...)
 *   - packagingLevels (liste de paliers de vente additionnels, vide par défaut)
 * Les documents Mongo existants ont encore `unit` (stocké "PIECE"/"KG" en majuscules —
 * voir la migration précédente 2026-08-01-fractional-quantity.js) et n'ont ni
 * atomicUnit ni packagingLevels.
 *
 * Ce script :
 *   1. Backfill atomicUnit depuis unit : "PIECE" -> "pièce", "KG" -> "kg", absent -> "pièce".
 *   2. Backfill packagingLevels -> [] sur tous les articles qui ne l'ont pas.
 *   3. NE retire PAS le champ `unit` — laissé en place pour permettre un rollback
 *      instantané (revert du code Java) sans script de migration inverse. À supprimer
 *      dans un futur script une fois la prod vérifiée stable sur atomicUnit/packagingLevels.
 *
 * Usage : identique à la migration précédente (DRY_RUN=true par défaut, mongodump avant
 * exécution réelle, staging avant prod).
 *   mongosh "$MONGO_URL/$MONGO_NAME" --eval "var DRY_RUN=false" scripts/migrations/2026-08-04-packaging-levels.js
 */

const dryRun = typeof DRY_RUN === "undefined" ? true : DRY_RUN;

print(`\n=== Migration packaging-levels — ${dryRun ? "DRY RUN (aucune écriture)" : "EXÉCUTION RÉELLE"} ===\n`);

const missingAtomicUnit = db.Articles.countDocuments({ atomicUnit: { $exists: false } });
const missingPackagingLevels = db.Articles.countDocuments({ packagingLevels: { $exists: false } });
const unitPiece = db.Articles.countDocuments({ unit: "PIECE" });
const unitKg = db.Articles.countDocuments({ unit: "KG" });
const noUnitAtAll = db.Articles.countDocuments({ unit: { $exists: false } });

print(`Articles sans atomicUnit       : ${missingAtomicUnit}`);
print(`Articles sans packagingLevels  : ${missingPackagingLevels}`);
print(`  dont unit=PIECE -> "pièce"   : ${unitPiece}`);
print(`  dont unit=KG    -> "kg"      : ${unitKg}`);
print(`  dont sans unit  -> "pièce"   : ${noUnitAtAll}`);

if (!dryRun) {
  const pieceResult = db.Articles.updateMany(
    { atomicUnit: { $exists: false }, unit: "PIECE" },
    { $set: { atomicUnit: "pièce" } }
  );
  print(`  -> atomicUnit="pièce" backfillé sur ${pieceResult.modifiedCount} article(s) (depuis unit=PIECE)`);

  const kgResult = db.Articles.updateMany(
    { atomicUnit: { $exists: false }, unit: "KG" },
    { $set: { atomicUnit: "kg" } }
  );
  print(`  -> atomicUnit="kg" backfillé sur ${kgResult.modifiedCount} article(s) (depuis unit=KG)`);

  const defaultResult = db.Articles.updateMany(
    { atomicUnit: { $exists: false } },
    { $set: { atomicUnit: "pièce" } }
  );
  print(`  -> atomicUnit="pièce" backfillé sur ${defaultResult.modifiedCount} article(s) restant(s) (sans unit)`);

  const packagingResult = db.Articles.updateMany(
    { packagingLevels: { $exists: false } },
    { $set: { packagingLevels: [] } }
  );
  print(`  -> packagingLevels=[] backfillé sur ${packagingResult.modifiedCount} article(s)`);
}

print(`\n=== Fin migration packaging-levels (${dryRun ? "dry run" : "exécution réelle"}) ===\n`);
