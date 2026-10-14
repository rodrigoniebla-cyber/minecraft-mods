package net.manaz.vtp.trade;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedSet;
import java.util.Set;
import java.util.function.Supplier;
import net.manaz.vtp.VillagerTradingPredictor;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * Supplies the Minecraft 26.2 villager trade definitions on a 1.21.11 client.
 *
 * <p>The target server runs 26.2, where villager trades are data-driven ({@code trade_set} and
 * {@code villager_trade} registries) and rolled from a per-world named random sequence. A 1.21.11
 * client has none of those registries, so the vanilla 26.2 definitions are bundled under
 * {@code /vtp_data/26.2/} and decoded here. Enchantment pools are resolved against the enchantment
 * registry and tags the server synced to the client, falling back to the bundled 26.2 tags.
 */
public class TradeDataLoader {
   public static final String DATA_VERSION = "26.2";
   private static final String ROOT = "/vtp_data/" + DATA_VERSION + "/";
   private static final Map<Identifier, Optional<TradeSet>> tradeSets = new HashMap<>();
   private static final Map<Identifier, Optional<VillagerTrade>> trades = new HashMap<>();
   private static final Map<String, List<Holder<Enchantment>>> enchantmentSets = new HashMap<>();
   private static Object enchantmentRegistry;
   private static boolean registryLogged = false;
   /** Used when no client world is loaded (unit tests replay recorded 26.2 server trades through this). */
   static Supplier<Registry<Enchantment>> fallbackRegistry = () -> null;

   public static void resetRegistryLog() {
      registryLogged = false;
      clearEnchantmentCache();
   }

   public static void clearEnchantmentCache() {
      enchantmentSets.clear();
      enchantmentRegistry = null;
   }

   public static Optional<TradeSet> getTradeSet(ResourceKey<TradeSet> key) {
      return key == null ? Optional.empty() : tradeSets.computeIfAbsent(key.identifier(), TradeDataLoader::loadTradeSet);
   }

   /** 26.2 professions map level N to the trade set {@code <profession>/level_N}; none and nitwit have no trades. */
   public static ResourceKey<TradeSet> getTradeSetKey(Holder<VillagerProfession> professionHolder, int level) {
      if (professionHolder == null || level < 1 || level > 5) {
         return null;
      } else if (professionHolder.is(VillagerProfession.NONE) || professionHolder.is(VillagerProfession.NITWIT)) {
         return null;
      } else {
         Optional<ResourceKey<VillagerProfession>> key = professionHolder.unwrapKey();
         if (key.isEmpty()) {
            return null;
         } else {
            Identifier profession = key.get().identifier();
            Identifier id = Identifier.fromNamespaceAndPath(profession.getNamespace(), profession.getPath() + "/level_" + level);
            return resourceExists(dataPath(id, "trade_set")) ? ResourceKey.create(TradeSet.REGISTRY, id) : null;
         }
      }
   }

   public static List<VillagerTrade> getTradesFromSet(TradeSet tradeSet) {
      return tradeSet.trades();
   }

   public static List<Holder<Enchantment>> getTradeableEnchantments() {
      return resolveEnchantments(new com.google.gson.JsonPrimitive("#" + EnchantmentTags.TRADEABLE.location()));
   }

   /**
    * Resolves an enchantment holder-set reference ("#tag", "id" or a list of ids) in the order the
    * 26.2 server iterates it. Returns an empty list when the client has no enchantment registry yet.
    */
   public static List<Holder<Enchantment>> resolveEnchantments(JsonElement reference) {
      Registry<Enchantment> registry = enchantmentRegistry();
      if (registry == null || reference == null) {
         return List.of();
      } else {
         if (enchantmentRegistry != registry) {
            enchantmentSets.clear();
            enchantmentRegistry = registry;
         }

         return enchantmentSets.computeIfAbsent(reference.toString(), ignored -> {
            List<Holder<Enchantment>> result = new ArrayList<>();
            if (reference.isJsonPrimitive() && reference.getAsString().startsWith("#")) {
               result.addAll(resolveEnchantmentTag(registry, parseId(reference.getAsString().substring(1))));
            } else {
               for (JsonElement element : VillagerTrade.asArray(reference)) {
                  registry.get(ResourceKey.create(Registries.ENCHANTMENT, parseId(element.getAsString()))).ifPresent(result::add);
               }
            }

            return List.copyOf(result);
         });
      }
   }

   private static List<Holder<Enchantment>> resolveEnchantmentTag(Registry<Enchantment> registry, Identifier tag) {
      Optional<HolderSet.Named<Enchantment>> synced = registry.get(TagKey.create(Registries.ENCHANTMENT, tag));
      if (synced.isPresent() && synced.get().size() > 0) {
         // The server sends its resolved tags in iteration order, so this matches the server exactly
         // (including datapack changes on the server).
         return synced.get().stream().toList();
      } else {
         SequencedSet<Identifier> ids = new LinkedHashSet<>();
         collectBundledTag(tag, "enchantment", ids, new HashSet<>());
         List<Holder<Enchantment>> result = new ArrayList<>();

         for (Identifier id : ids) {
            registry.get(ResourceKey.create(Registries.ENCHANTMENT, id)).ifPresent(result::add);
         }

         VillagerTradingPredictor.LOGGER.info("[VTP] Enchantment tag #{} not synced by server; using bundled {} tag ({} entries)", tag, DATA_VERSION, result.size());
         return result;
      }
   }

   private static Registry<Enchantment> enchantmentRegistry() {
      Minecraft client = Minecraft.getInstance();
      if (client == null || client.level == null) {
         return fallbackRegistry.get();
      } else {
         RegistryAccess access = client.level.registryAccess();
         Optional<Registry<Enchantment>> registry = access.lookup(Registries.ENCHANTMENT);
         if (!registryLogged) {
            registryLogged = true;
            VillagerTradingPredictor.LOGGER.info(
               "[VTP] Using bundled Minecraft {} trade definitions; enchantment registry: {}",
               DATA_VERSION,
               registry.map(r -> r.size() + " entries").orElse("NOT PRESENT")
            );
         }

         return registry.orElse(null);
      }
   }

   private static Optional<TradeSet> loadTradeSet(Identifier id) {
      JsonElement json = loadRawJson(dataPath(id, "trade_set"));
      if (json != null && json.isJsonObject()) {
         try {
            JsonObject object = json.getAsJsonObject();
            SequencedSet<Identifier> tradeIds = new LinkedHashSet<>();
            JsonElement tradesElement = object.get("trades");
            if (tradesElement != null) {
               if (tradesElement.isJsonPrimitive() && tradesElement.getAsString().startsWith("#")) {
                  collectBundledTag(parseId(tradesElement.getAsString().substring(1)), "villager_trade", tradeIds, new HashSet<>());
               } else {
                  for (JsonElement element : VillagerTrade.asArray(tradesElement)) {
                     tradeIds.add(parseId(element.getAsString()));
                  }
               }
            }

            List<VillagerTrade> list = new ArrayList<>();

            for (Identifier tradeId : tradeIds) {
               Optional<VillagerTrade> trade = trades.computeIfAbsent(tradeId, TradeDataLoader::loadTrade);
               if (trade.isEmpty()) {
                  VillagerTradingPredictor.LOGGER.warn("[VTP] Missing villager trade {} referenced by {}", tradeId, id);
                  return Optional.empty();
               }

               list.add(trade.get());
            }

            Optional<Identifier> sequence = object.has("random_sequence") ? Optional.of(parseId(object.get("random_sequence").getAsString())) : Optional.empty();
            boolean duplicates = object.has("allow_duplicates") && object.get("allow_duplicates").getAsBoolean();
            JsonElement amount = object.get("amount");
            return Optional.of(new TradeSet(id, list, amount, duplicates, sequence));
         } catch (Exception var10) {
            VillagerTradingPredictor.LOGGER.error("[VTP] Failed to decode trade set {}", id, var10);
            return Optional.empty();
         }
      } else {
         VillagerTradingPredictor.LOGGER.warn("[VTP] Trade set {} is not bundled", id);
         return Optional.empty();
      }
   }

   private static Optional<VillagerTrade> loadTrade(Identifier id) {
      JsonElement json = loadRawJson(dataPath(id, "villager_trade"));
      if (json != null && json.isJsonObject()) {
         try {
            return Optional.of(VillagerTrade.parse(id, json.getAsJsonObject()));
         } catch (Exception var3) {
            VillagerTradingPredictor.LOGGER.error("[VTP] Failed to decode villager trade {}", id, var3);
            return Optional.empty();
         }
      } else {
         return Optional.empty();
      }
   }

   /** Resolves a bundled tag the same way the server's TagLoader does: in order, nested tags inline, no duplicates. */
   private static void collectBundledTag(Identifier tag, String registry, SequencedSet<Identifier> result, Set<Identifier> visiting) {
      if (visiting.add(tag)) {
         JsonElement json = loadRawJson(ROOT + "tags/" + registry + "/" + tag.getPath() + ".json");
         if (json != null && json.isJsonObject() && json.getAsJsonObject().has("values")) {
            for (JsonElement value : json.getAsJsonObject().getAsJsonArray("values")) {
               String entry = value.isJsonObject() ? value.getAsJsonObject().get("id").getAsString() : value.getAsString();
               if (entry.startsWith("#")) {
                  collectBundledTag(parseId(entry.substring(1)), registry, result, visiting);
               } else {
                  result.add(parseId(entry));
               }
            }
         } else {
            VillagerTradingPredictor.LOGGER.warn("[VTP] Bundled {} tag not found: #{}", registry, tag);
         }

         visiting.remove(tag);
      }
   }

   private static Identifier parseId(String id) {
      return id.contains(":") ? Identifier.parse(id) : Identifier.withDefaultNamespace(id);
   }

   private static String dataPath(Identifier id, String type) {
      return ROOT + type + "/" + id.getPath() + ".json";
   }

   private static boolean resourceExists(String path) {
      return TradeDataLoader.class.getResource(path) != null;
   }

   private static JsonElement loadRawJson(String path) {
      try (InputStream is = TradeDataLoader.class.getResourceAsStream(path)) {
         return is == null ? null : JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8));
      } catch (Exception var6) {
         VillagerTradingPredictor.LOGGER.error("[VTP] Failed to read {}", path, var6);
         return null;
      }
   }
}
