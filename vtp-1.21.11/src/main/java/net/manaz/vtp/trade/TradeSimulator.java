package net.manaz.vtp.trade;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.manaz.vtp.rng.SimulatedRandomSource;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * Reproduces Minecraft 26.2 {@code AbstractVillager.addOffersFromTradeSet} and
 * {@code VillagerTrade.getOffer} for the trade shapes it can predict exactly.
 */
public final class TradeSimulator {
   private static final JsonObject BOOK_FILTER_26_2 = JsonParser.parseString(
         "{\"function\":\"minecraft:filtered\",\"item_filter\":{\"items\":\"minecraft:enchanted_book\",\"predicates\":{\"minecraft:stored_enchantments\":[{}]}},\"on_fail\":{\"function\":\"minecraft:discard\"}}"
      )
      .getAsJsonObject();
   private static final JsonObject BOOK_FILTER_26_3 = JsonParser.parseString(
         "{\"type\":\"minecraft:filtered\",\"item_filter\":{\"items\":\"minecraft:enchanted_book\",\"predicates\":{\"minecraft:stored_enchantments\":[{}]}},\"on_fail\":{\"type\":\"minecraft:discard\"}}"
      )
      .getAsJsonObject();

   private TradeSimulator() {
   }

   public static String compatibilityProblem(TradeSet set) {
      if (set.randomSequence().isEmpty()) {
         return "trade set has no named random sequence";
      } else {
         try {
            if (!set.hasConstantAmount()) {
               return "random trade amount";
            } else {
               for (VillagerTrade holder : TradeDataLoader.getTradesFromSet(set)) {
                  JsonObject trade = holder.json();
                  if (trade.has("merchant_predicate")) {
                     return "conditional trades (including Trade Rebalance)";
                  }

                  for (String field : List.of("max_uses", "xp", "reputation_discount")) {
                     if (trade.has(field) && !Numbers.isConstant(trade.get(field))) {
                        return "random trade values";
                     }
                  }

                  for (String fieldx : List.of("wants", "additional_wants")) {
                     if (trade.has(fieldx)) {
                        VillagerTrade.TradeCost cost = VillagerTrade.TradeCost.parse(trade.getAsJsonObject(fieldx));
                        if (!cost.hasConstantCount()) {
                           return "random trade costs";
                        }

                        if (cost.components().size() > 0) {
                           return "custom trade cost components";
                        }

                        if (cost.baseCount() < 0) {
                           return "invalid trade cost";
                        }
                     }
                  }

                  if (holder.gives().components().size() > 0) {
                     return "custom output components";
                  }

                  int randomFunctions = 0;

                  for (JsonElement element : holder.itemModifiers()) {
                     if (!element.isJsonObject()) {
                        return "referenced item modifiers";
                     }

                     JsonObject function = element.getAsJsonObject();
                     String id = VillagerTrade.functionType(function);
                     if (VillagerTrade.hasConditions(function)) {
                        return "conditional item modifiers";
                     }

                     if (id.equals("minecraft:enchant_randomly")) {
                        if (++randomFunctions > 1 || !function.has("options")) {
                           return "unsupported enchantment options or conditions";
                        }

                        String gives = holder.gives().itemId();
                        if (!gives.equals("minecraft:enchanted_book") && !gives.equals("minecraft:book")) {
                           return "non-book enchantments";
                        }

                        // 26.2 only skips the compatibility filter when the target item is a plain book.
                        if (holder.enchantRandomly().orElseThrow().onlyCompatible() && !gives.equals("minecraft:book")) {
                           return "compatibility-filtered enchantments";
                        }
                     } else {
                        if (!id.equals("minecraft:filtered") || randomFunctions != 1) {
                           return "unsupported item modifier: " + id;
                        }

                        if (!function.equals(BOOK_FILTER_26_2) && !function.equals(BOOK_FILTER_26_3)) {
                           return "custom item filter";
                        }
                     }
                  }

                  if (randomFunctions > 0) {
                     List<Holder<Enchantment>> options = enchantmentOptions(holder);
                     if (options.isEmpty()) {
                        return "empty enchantment pool";
                     }
                  }
               }

               return "";
            }
         } catch (Exception var11) {
            return "trade definitions unavailable";
         }
      }
   }

   public static List<Holder<Enchantment>> enchantmentOptions(VillagerTrade trade) {
      return trade.enchantRandomly().flatMap(VillagerTrade.EnchantRandomly::options).map(TradeDataLoader::resolveEnchantments).orElse(List.of());
   }

   public static boolean doublesPrice(VillagerTrade trade, Holder<Enchantment> enchantment) {
      return trade.doubleTradePriceEnchantments()
         .map(TradeDataLoader::resolveEnchantments)
         .filter(set -> set.stream().anyMatch(option -> option.unwrapKey().equals(enchantment.unwrapKey())))
         .isPresent();
   }

   /** One call of 26.2 {@code addOffersFromTradeSet}, consuming the trade set's random sequence. */
   public static List<TradeSimulator.SimulatedOffer> simulateTradeRound(TradeSet set, SimulatedRandomSource rng, List<Holder<Enchantment>> unused) {
      List<VillagerTrade> pool = new ArrayList<>(TradeDataLoader.getTradesFromSet(set));
      int amount = set.calculateNumberOfTrades();
      List<TradeSimulator.SimulatedOffer> result = new ArrayList<>();

      while (result.size() < amount && !pool.isEmpty()) {
         int index = rng.nextInt(pool.size());
         VillagerTrade trade = set.allowDuplicates() ? pool.get(index) : pool.remove(index);
         Optional<TradeSimulator.EnchantmentResult> enchantment = Optional.empty();
         if (trade.enchantRandomly().isPresent()) {
            VillagerTrade.EnchantRandomly random = trade.enchantRandomly().get();
            List<Holder<Enchantment>> options = enchantmentOptions(trade);
            Holder<Enchantment> ench = options.get(rng.nextInt(options.size()));
            int min = ench.value().getMinLevel();
            int max = ench.value().getMaxLevel();
            int level = min >= max ? min : min + rng.nextInt(max - min + 1);
            int cost = random.includeAdditionalCost() ? 2 + rng.nextInt(5 + level * 10) + 3 * level : 0;
            if (doublesPrice(trade, ench)) {
               cost *= 2;
            }

            enchantment = Optional.of(new TradeSimulator.EnchantmentResult(ench, level, cost, trade.wants().toItemCount(cost)));
         }

         int extra = enchantment.map(TradeSimulator.EnchantmentResult::additionalCost).orElse(0);
         if (trade.wants().toItemCount(extra) >= 1 && !trade.additionalWants().filter(c -> c.toItemCount(0) < 1).isPresent()) {
            result.add(new TradeSimulator.SimulatedOffer(trade, enchantment));
         } else if (set.allowDuplicates()) {
            pool.remove(index);
         }
      }

      return List.copyOf(result);
   }

   public static List<ObservedOffer> fingerprint(List<TradeSimulator.SimulatedOffer> offers) {
      return offers.stream()
         .map(
            o -> {
               VillagerTrade trade = o.tradeDefinition();
               int extra = o.enchantment().map(TradeSimulator.EnchantmentResult::additionalCost).orElse(0);
               List<ObservedOffer.Book> books = o.enchantment()
                  .map(e -> List.of(new ObservedOffer.Book(e.enchantment().unwrapKey().orElseThrow().identifier().toString(), e.level())))
                  .orElse(List.of());
               String result = o.enchantment().isPresent() ? "minecraft:enchanted_book" : trade.gives().itemId();
               return new ObservedOffer(
                  result,
                  trade.gives().count(),
                  trade.wants().itemId(),
                  trade.wants().toItemCount(extra),
                  trade.additionalWants().map(VillagerTrade.TradeCost::itemId).orElse("minecraft:air"),
                  trade.additionalWants().map(c -> c.toItemCount(0)).orElse(0),
                  books
               );
            }
         )
         .toList();
   }

   public static List<TradeSimulator.SimulatedOffer> fromObserved(List<ObservedOffer> observations) {
      List<TradeSimulator.SimulatedOffer> result = new ArrayList<>();
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         return List.of();
      } else {
         Registry<Enchantment> registry = mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);

         for (ObservedOffer offer : observations) {
            if (offer.books().isEmpty()) {
               result.add(new TradeSimulator.SimulatedOffer(null, Optional.empty()));
            }

            for (ObservedOffer.Book book : offer.books()) {
               registry.get(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(book.key())))
                  .ifPresent(
                     ench -> result.add(
                        new TradeSimulator.SimulatedOffer(
                           null, Optional.of(new TradeSimulator.EnchantmentResult(ench, book.level(), offer.basePrice(), offer.basePrice()))
                        )
                     )
                  );
            }
         }

         return List.copyOf(result);
      }
   }

   public record EnchantmentResult(Holder<Enchantment> enchantment, int level, int additionalCost, int emeraldPrice) {
   }

   public record SimulatedOffer(VillagerTrade tradeDefinition, Optional<TradeSimulator.EnchantmentResult> enchantment) {
   }
}
