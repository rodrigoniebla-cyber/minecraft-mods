package net.manaz.vtp.trade;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.manaz.vtp.rng.SequenceRandomSource;
import net.manaz.vtp.rng.SimulatedRandomSource;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

/**
 * Reproduces Minecraft 26.2 {@code AbstractVillager.addOffersFromTradeSet} and
 * {@code VillagerTrade.getOffer}, including every item modifier vanilla villager trades use, drawing
 * from the trade set's random sequence in exactly the server's order.
 */
public final class TradeSimulator {
   private static final Set<String> FILTER_PREDICATES = Set.of(
      "minecraft:stored_enchantments", "minecraft:enchantments", "minecraft:dyed_color", "minecraft:map_id"
   );

   private TradeSimulator() {
   }

   public static String compatibilityProblem(TradeSet set) {
      if (set.randomSequence().isEmpty()) {
         return "trade set has no named random sequence";
      } else {
         try {
            if (!Numbers.isSupported(set.amountProvider())) {
               return "unsupported trade amount";
            } else {
               for (VillagerTrade trade : TradeDataLoader.getTradesFromSet(set)) {
                  String problem = tradeProblem(trade);
                  if (!problem.isEmpty()) {
                     return problem;
                  }
               }

               return "";
            }
         } catch (Exception var4) {
            return "trade definitions unavailable";
         }
      }
   }

   private static String tradeProblem(VillagerTrade trade) {
      if (trade.merchantPredicate().isPresent() && villagerTypes(trade.merchantPredicate().get()).isEmpty()) {
         return "unsupported trade condition";
      }

      for (String field : List.of("max_uses", "xp", "reputation_discount")) {
         if (trade.offerValue(field).filter(value -> !Numbers.isSupported(value)).isPresent()) {
            return "unsupported random trade values";
         }
      }

      for (VillagerTrade.TradeCost cost : costs(trade)) {
         if (!cost.hasSupportedCount()) {
            return "unsupported random trade costs";
         }

         if (cost.components().size() > 0) {
            return "custom trade cost components";
         }
      }

      if (trade.gives().components().size() > 0) {
         return "custom output components";
      }

      for (JsonElement element : trade.itemModifiers()) {
         if (!element.isJsonObject()) {
            return "referenced item modifiers";
         }

         JsonObject function = element.getAsJsonObject();
         if (VillagerTrade.hasConditions(function)) {
            return "conditional item modifiers";
         }

         String id = VillagerTrade.functionType(function);
         switch (id) {
            case "minecraft:enchant_randomly":
               if (!function.has("options")) {
                  return "enchantments from the whole registry";
               }

               if (TradeDataLoader.resolveEnchantments(function.get("options")).isEmpty()) {
                  return "empty enchantment pool";
               }
               break;
            case "minecraft:enchant_with_levels":
               if (!function.has("options") || !Numbers.isSupported(function.get("levels"))) {
                  return "unsupported enchant_with_levels";
               }

               if (TradeDataLoader.resolveEnchantments(function.get("options")).isEmpty()) {
                  return "empty enchantment pool";
               }
               break;
            case "minecraft:set_stew_effect":
               if (!function.has("effects")
                  || !function.getAsJsonArray("effects").asList().stream().allMatch(e -> Numbers.isSupported(e.getAsJsonObject().get("duration")))) {
                  return "unsupported stew effects";
               }
               break;
            case "minecraft:set_random_dyes":
               if (!Numbers.isSupported(function.get("number_of_dyes"))) {
                  return "unsupported dye count";
               }
               break;
            case "minecraft:set_random_potion":
               if (!function.has("options")) {
                  return "potions from the whole registry";
               }
               break;
            case "minecraft:exploration_map":
            case "minecraft:set_name":
               break;
            case "minecraft:filtered":
               if (!supportedFilter(function)) {
                  return "custom item filter";
               }
               break;
            default:
               return "unsupported item modifier: " + id;
         }
      }

      return "";
   }

   private static List<VillagerTrade.TradeCost> costs(VillagerTrade trade) {
      List<VillagerTrade.TradeCost> costs = new ArrayList<>();
      costs.add(trade.wants());
      trade.additionalWants().ifPresent(costs::add);
      return costs;
   }

   private static boolean supportedFilter(JsonObject function) {
      if (function.has("on_pass") || !function.has("item_filter") || !function.get("item_filter").isJsonObject()) {
         return false;
      } else if (function.has("on_fail") && !(function.get("on_fail").isJsonObject()
         && VillagerTrade.functionType(function.getAsJsonObject("on_fail")).equals("minecraft:discard")
         && !VillagerTrade.hasConditions(function.getAsJsonObject("on_fail")))) {
         return false;
      } else {
         JsonObject filter = function.getAsJsonObject("item_filter");

         for (String key : filter.keySet()) {
            if (!key.equals("items") && !key.equals("predicates")) {
               return false;
            }
         }

         if (filter.has("items")) {
            for (JsonElement item : VillagerTrade.asArray(filter.get("items"))) {
               if (!item.isJsonPrimitive() || item.getAsString().startsWith("#")) {
                  return false;
               }
            }
         }

         if (filter.has("predicates")) {
            for (Map.Entry<String, JsonElement> predicate : filter.getAsJsonObject("predicates").entrySet()) {
               if (!FILTER_PREDICATES.contains(predicate.getKey()) || !isPresenceCheck(predicate.getValue())) {
                  return false;
               }
            }
         }

         return true;
      }
   }

   /** "{}" (component present) or "[{}]" (at least one enchantment, any kind). */
   private static boolean isPresenceCheck(JsonElement value) {
      if (value.isJsonObject()) {
         return value.getAsJsonObject().isEmpty();
      } else {
         return value.isJsonArray()
            && value.getAsJsonArray().size() == 1
            && value.getAsJsonArray().get(0).isJsonObject()
            && value.getAsJsonArray().get(0).getAsJsonObject().isEmpty();
      }
   }

   /** Villager types allowed by a 26.2 {@code entity_properties} / {@code villager/variant} merchant predicate. */
   static Optional<Set<String>> villagerTypes(JsonObject predicate) {
      if (!"minecraft:entity_properties".equals(qualify(predicate.has("condition") ? predicate.get("condition").getAsString() : ""))
         || !"this".equals(predicate.has("entity") ? predicate.get("entity").getAsString() : "")
         || !predicate.has("predicate")) {
         return Optional.empty();
      } else {
         JsonObject entity = predicate.getAsJsonObject("predicate");
         if (entity.size() != 1 || !entity.has("minecraft:predicates")) {
            return Optional.empty();
         } else {
            JsonObject components = entity.getAsJsonObject("minecraft:predicates");
            if (components.size() != 1 || !components.has("minecraft:villager/variant")) {
               return Optional.empty();
            } else {
               Set<String> types = new HashSet<>();

               for (JsonElement type : VillagerTrade.asArray(components.get("minecraft:villager/variant"))) {
                  if (!type.isJsonPrimitive() || type.getAsString().startsWith("#")) {
                     return Optional.empty();
                  }

                  types.add(qualify(type.getAsString()));
               }

               return Optional.of(types);
            }
         }
      }
   }

   private static String qualify(String id) {
      return id.contains(":") ? id : "minecraft:" + id;
   }

   public static boolean dependsOnVillagerType(TradeSet set) {
      return set.trades().stream().anyMatch(VillagerTrade::dependsOnVillagerType);
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

   /** Trades that offer gear enchanted by {@code enchant_with_levels}. */
   public static boolean enchantsGear(VillagerTrade trade) {
      return trade.itemModifiers()
         .stream()
         .anyMatch(m -> m.isJsonObject() && VillagerTrade.functionType(m.getAsJsonObject()).equals("minecraft:enchant_with_levels"));
   }

   /** Enchantments {@code enchant_with_levels} can put on this trade's item. */
   public static List<Holder<Enchantment>> gearEnchantmentOptions(VillagerTrade trade) {
      for (JsonElement modifier : trade.itemModifiers()) {
         if (modifier.isJsonObject() && VillagerTrade.functionType(modifier.getAsJsonObject()).equals("minecraft:enchant_with_levels")) {
            ItemStack stack = new ItemStack(trade.gives().item());
            return TradeDataLoader.resolveEnchantments(modifier.getAsJsonObject().get("options"))
               .stream()
               .filter(e -> e.value().isPrimaryItem(stack))
               .toList();
         }
      }

      return List.of();
   }

   /** Stew effect ids a trade can give. */
   public static List<String> stewEffects(VillagerTrade trade) {
      List<String> effects = new ArrayList<>();

      for (JsonElement modifier : trade.itemModifiers()) {
         if (modifier.isJsonObject() && VillagerTrade.functionType(modifier.getAsJsonObject()).equals("minecraft:set_stew_effect")) {
            for (JsonElement effect : modifier.getAsJsonObject().getAsJsonArray("effects")) {
               effects.add(qualify(effect.getAsJsonObject().get("type").getAsString()));
            }
         }
      }

      return effects;
   }

   /** Potion ids a trade can put on its item. */
   public static List<String> potions(VillagerTrade trade) {
      for (JsonElement modifier : trade.itemModifiers()) {
         if (modifier.isJsonObject() && VillagerTrade.functionType(modifier.getAsJsonObject()).equals("minecraft:set_random_potion")) {
            return TradeDataLoader.resolvePotions(modifier.getAsJsonObject().get("options")).stream().map(Identifier::toString).toList();
         }
      }

      return List.of();
   }

   /** Item ids a trade buys or sells, as shown in the merchant screen. */
   public static List<String> tradeItems(VillagerTrade trade) {
      List<String> items = new ArrayList<>();
      items.add(resultItem(trade));
      items.add(trade.wants().itemId());
      trade.additionalWants().ifPresent(c -> items.add(c.itemId()));
      return items;
   }

   private static String resultItem(VillagerTrade trade) {
      String item = trade.gives().itemId();

      for (JsonElement modifier : trade.itemModifiers()) {
         if (modifier.isJsonObject()) {
            String type = VillagerTrade.functionType(modifier.getAsJsonObject());
            if (type.equals("minecraft:exploration_map") && item.equals("minecraft:map")) {
               item = "minecraft:filled_map";
            } else if ((type.equals("minecraft:enchant_randomly") || type.equals("minecraft:enchant_with_levels")) && item.equals("minecraft:book")) {
               item = "minecraft:enchanted_book";
            }
         }
      }

      return item;
   }

   /** One call of 26.2 {@code addOffersFromTradeSet}, consuming the trade set's random sequence. */
   public static List<TradeSimulator.SimulatedOffer> simulateTradeRound(TradeSet set, SimulatedRandomSource rng, String villagerType) {
      List<VillagerTrade> pool = new ArrayList<>(TradeDataLoader.getTradesFromSet(set));
      int amount = Numbers.getInt(set.amountProvider(), rng);
      List<TradeSimulator.SimulatedOffer> result = new ArrayList<>();

      while (result.size() < amount && !pool.isEmpty()) {
         int index = rng.nextInt(pool.size());
         VillagerTrade trade = set.allowDuplicates() ? pool.get(index) : pool.remove(index);
         Optional<TradeSimulator.SimulatedOffer> offer = getOffer(trade, rng, villagerType);
         if (offer.isPresent()) {
            result.add(offer.get());
         } else if (set.allowDuplicates()) {
            pool.remove(index);
         }
      }

      return List.copyOf(result);
   }

   /** 26.2 {@code VillagerTrade.getOffer}: empty when the server would return no offer. */
   private static Optional<TradeSimulator.SimulatedOffer> getOffer(VillagerTrade trade, SimulatedRandomSource rng, String villagerType) {
      if (trade.merchantPredicate().isPresent() && !villagerTypes(trade.merchantPredicate().get()).orElse(Set.of()).contains(villagerType)) {
         return Optional.empty();
      } else {
         Roll roll = new Roll(trade.gives().itemId(), trade.gives().count());

         for (JsonElement modifier : trade.itemModifiers()) {
            apply(roll, modifier.getAsJsonObject(), rng);
            if (roll.empty) {
               return Optional.empty();
            }
         }

         int additionalCost = roll.additionalCost;
         if (trade.doubleTradePriceEnchantments().isPresent() && !roll.stored.isEmpty()) {
            List<Holder<Enchantment>> doubled = TradeDataLoader.resolveEnchantments(trade.doubleTradePriceEnchantments().get());
            if (roll.stored.keySet().stream().anyMatch(e -> doubled.stream().anyMatch(d -> d.unwrapKey().equals(e.unwrapKey())))) {
               additionalCost *= 2;
            }
         }

         int priceA = trade.wants().toItemCount(additionalCost, rng);
         if (priceA < 1) {
            return Optional.empty();
         } else {
            int countB = 0;
            if (trade.additionalWants().isPresent()) {
               countB = trade.additionalWants().get().toItemCount(0, rng);
               if (countB < 1) {
                  return Optional.empty();
               }
            }

            // MerchantOffer(maxUses, xp, reputationDiscount) evaluates these in this order.
            trade.offerValue("max_uses").ifPresent(value -> Numbers.getInt(value, rng));
            trade.offerValue("xp").ifPresent(value -> Numbers.getInt(value, rng));
            trade.offerValue("reputation_discount").ifPresent(value -> Numbers.getFloat(value, rng));
            int bookCost = additionalCost;
            Optional<TradeSimulator.EnchantmentResult> book = roll.stored
               .entrySet()
               .stream()
               .findFirst()
               .map(e -> new TradeSimulator.EnchantmentResult(e.getKey(), e.getValue(), bookCost, priceA));
            Rolled rolled = new Rolled(
               roll.item,
               roll.count,
               trade.wants().itemId(),
               priceA,
               trade.additionalWants().map(VillagerTrade.TradeCost::itemId).orElse("minecraft:air"),
               countB,
               books(roll.stored),
               books(roll.enchantments),
               roll.effect,
               roll.dye,
               roll.potion
            );
            return Optional.of(new TradeSimulator.SimulatedOffer(trade, book, rolled));
         }
      }
   }

   private static List<ObservedOffer.Book> books(Map<Holder<Enchantment>, Integer> enchantments) {
      return enchantments.entrySet()
         .stream()
         .map(e -> new ObservedOffer.Book(e.getKey().unwrapKey().orElseThrow().identifier().toString(), e.getValue()))
         .sorted(Comparator.comparing(ObservedOffer.Book::key))
         .toList();
   }

   private static void apply(Roll roll, JsonObject function, SimulatedRandomSource rng) {
      switch (VillagerTrade.functionType(function)) {
         case "minecraft:enchant_randomly" -> enchantRandomly(roll, function, rng);
         case "minecraft:enchant_with_levels" -> enchantWithLevels(roll, function, rng);
         case "minecraft:set_stew_effect" -> {
            if (roll.item.equals("minecraft:suspicious_stew") && function.getAsJsonArray("effects").size() > 0) {
               List<JsonElement> effects = function.getAsJsonArray("effects").asList();
               JsonObject entry = effects.get(rng.nextInt(effects.size())).getAsJsonObject();
               Numbers.getInt(entry.get("duration"), rng);
               roll.effect = qualify(entry.get("type").getAsString());
            }
         }
         case "minecraft:set_random_dyes" -> {
            int rolls = Numbers.getInt(function.get("number_of_dyes"), rng);
            if (rolls > 0) {
               List<DyeColor> dyes = new ArrayList<>(rolls);
               DyeColor[] values = DyeColor.values();

               for (int i = 0; i < rolls; i++) {
                  dyes.add(values[rng.nextInt(values.length)]);
               }

               roll.dye = mixDyes(roll.dye, dyes);
               roll.count = 1;
            }
         }
         case "minecraft:set_random_potion" -> {
            List<Identifier> potions = TradeDataLoader.resolvePotions(function.get("options"));
            if (!potions.isEmpty()) {
               roll.potion = potions.get(rng.nextInt(potions.size())).toString();
            }
         }
         case "minecraft:exploration_map" -> {
            // The server searches for the structure (no random draws). Assume it is found, as it is on
            // practically every world; otherwise the following map_id filter discards the offer.
            if (roll.item.equals("minecraft:map")) {
               roll.item = "minecraft:filled_map";
               roll.count = 1;
               roll.mapId = true;
            }
         }
         case "minecraft:set_name" -> {
         }
         case "minecraft:filtered" -> {
            if (!passesFilter(roll, function.getAsJsonObject("item_filter")) && function.has("on_fail")) {
               roll.empty = true;
            }
         }
         default -> throw new IllegalStateException("Unsupported item modifier " + function);
      }
   }

   private static void enchantRandomly(Roll roll, JsonObject function, SimulatedRandomSource rng) {
      boolean targetIsBook = roll.item.equals("minecraft:book");
      boolean onlyCompatible = !function.has("only_compatible") || function.get("only_compatible").getAsBoolean();
      ItemStack stack = roll.stack();
      List<Holder<Enchantment>> candidates = TradeDataLoader.resolveEnchantments(function.get("options"))
         .stream()
         .filter(e -> targetIsBook || !onlyCompatible || e.value().canEnchant(stack))
         .toList();
      if (!candidates.isEmpty()) {
         Holder<Enchantment> enchantment = candidates.get(rng.nextInt(candidates.size()));
         int min = enchantment.value().getMinLevel();
         int max = enchantment.value().getMaxLevel();
         int level = min >= max ? min : rng.nextInt(max - min + 1) + min;
         if (targetIsBook) {
            roll.item = "minecraft:enchanted_book";
         }

         roll.enchant(enchantment, level);
         if (function.has("include_additional_cost_component") && function.get("include_additional_cost_component").getAsBoolean()) {
            roll.additionalCost = 2 + rng.nextInt(5 + level * 10) + 3 * level;
         }
      }
   }

   private static void enchantWithLevels(Roll roll, JsonObject function, SimulatedRandomSource rng) {
      int cost = Numbers.getInt(function.get("levels"), rng);
      List<Holder<Enchantment>> options = TradeDataLoader.resolveEnchantments(function.get("options"));
      // Identical to 26.2's EnchantmentHelper.selectEnchantment; it draws from the trade sequence.
      List<EnchantmentInstance> chosen = EnchantmentHelper.selectEnchantment(new SequenceRandomSource(rng), roll.stack(), cost, options.stream());
      if (roll.item.equals("minecraft:book")) {
         roll.item = "minecraft:enchanted_book";
         roll.count = 1;
      }

      for (EnchantmentInstance instance : chosen) {
         roll.enchant(instance.enchantment(), instance.level());
      }

      if (function.has("include_additional_cost_component") && function.get("include_additional_cost_component").getAsBoolean() && cost > 0) {
         roll.additionalCost = cost;
      }
   }

   private static boolean passesFilter(Roll roll, JsonObject filter) {
      if (filter.has("items")) {
         boolean matches = false;

         for (JsonElement item : VillagerTrade.asArray(filter.get("items"))) {
            matches |= qualify(item.getAsString()).equals(roll.item);
         }

         if (!matches) {
            return false;
         }
      }

      if (filter.has("predicates")) {
         for (String key : filter.getAsJsonObject("predicates").keySet()) {
            boolean present = switch (key) {
               case "minecraft:stored_enchantments" -> !roll.stored.isEmpty();
               case "minecraft:enchantments" -> !roll.enchantments.isEmpty();
               case "minecraft:dyed_color" -> roll.dye >= 0;
               case "minecraft:map_id" -> roll.mapId;
               default -> false;
            };
            if (!present) {
               return false;
            }
         }
      }

      return true;
   }

   /** 26.2 {@code DyedItemColor.applyDyes}. */
   static int mixDyes(int current, List<DyeColor> dyes) {
      int red = 0;
      int green = 0;
      int blue = 0;
      int intensity = 0;
      int count = 0;
      List<Integer> colors = new ArrayList<>();
      if (current >= 0) {
         colors.add(current);
      }

      dyes.forEach(dye -> colors.add(dye.getTextureDiffuseColor()));

      for (int color : colors) {
         int r = color >> 16 & 0xFF;
         int g = color >> 8 & 0xFF;
         int b = color & 0xFF;
         intensity += Math.max(r, Math.max(g, b));
         red += r;
         green += g;
         blue += b;
         count++;
      }

      red /= count;
      green /= count;
      blue /= count;
      float average = (float)intensity / count;
      float max = Math.max(red, Math.max(green, blue));
      red = (int)(red * average / max);
      green = (int)(green * average / max);
      blue = (int)(blue * average / max);
      return (red & 0xFF) << 16 | (green & 0xFF) << 8 | blue & 0xFF;
   }

   public static List<ObservedOffer> fingerprint(List<TradeSimulator.SimulatedOffer> offers) {
      return offers.stream().map(o -> o.rolled().fingerprint()).toList();
   }

   public static List<TradeSimulator.SimulatedOffer> fromObserved(List<ObservedOffer> observations) {
      List<TradeSimulator.SimulatedOffer> result = new ArrayList<>();
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         return List.of();
      } else {
         Registry<Enchantment> registry = mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);

         for (ObservedOffer offer : observations) {
            Rolled rolled = Rolled.of(offer);
            if (offer.books().isEmpty()) {
               result.add(new TradeSimulator.SimulatedOffer(null, Optional.empty(), rolled));
            }

            for (ObservedOffer.Book book : offer.books()) {
               registry.get(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(book.key())))
                  .ifPresent(
                     ench -> result.add(
                        new TradeSimulator.SimulatedOffer(
                           null, Optional.of(new TradeSimulator.EnchantmentResult(ench, book.level(), offer.basePrice(), offer.basePrice())), rolled
                        )
                     )
                  );
            }
         }

         return List.copyOf(result);
      }
   }

   /** The item being built by the trade's modifiers. */
   private static final class Roll {
      String item;
      int count;
      final Map<Holder<Enchantment>, Integer> stored = new LinkedHashMap<>();
      final Map<Holder<Enchantment>, Integer> enchantments = new LinkedHashMap<>();
      int additionalCost;
      String effect = "";
      int dye = -1;
      String potion = "";
      boolean mapId;
      boolean empty;

      Roll(String item, int count) {
         this.item = item;
         this.count = count;
      }

      ItemStack stack() {
         Item item = VillagerTrade.item(this.item).orElseThrow(() -> new IllegalStateException("Unknown item " + this.item));
         return new ItemStack(item);
      }

      /** {@code ItemStack.enchant}: enchanted books store enchantments, other items apply them; levels only go up. */
      void enchant(Holder<Enchantment> enchantment, int level) {
         Map<Holder<Enchantment>, Integer> target = this.item.equals("minecraft:enchanted_book") ? this.stored : this.enchantments;
         target.merge(enchantment, level, Math::max);
      }
   }

   /** Everything the roll decided about one offer. */
   public record Rolled(
      String result,
      int count,
      String costA,
      int priceA,
      String costB,
      int countB,
      List<ObservedOffer.Book> books,
      List<ObservedOffer.Book> gear,
      String effect,
      int dye,
      String potion
   ) {
      static Rolled of(ObservedOffer offer) {
         return new Rolled(
            offer.result(),
            offer.count(),
            offer.costA(),
            offer.basePrice(),
            offer.costB(),
            offer.countB(),
            offer.books(),
            offer.gear(),
            offer.effect(),
            offer.dye(),
            offer.potion()
         );
      }

      public ObservedOffer fingerprint() {
         return new ObservedOffer(
            this.result, this.count, this.costA, this.priceA, this.costB, this.countB, this.books, this.gear, this.effect, this.dye, this.potion
         );
      }
   }

   public record EnchantmentResult(Holder<Enchantment> enchantment, int level, int additionalCost, int emeraldPrice) {
   }

   public record SimulatedOffer(VillagerTrade tradeDefinition, Optional<TradeSimulator.EnchantmentResult> enchantment, TradeSimulator.Rolled rolled) {
   }
}
