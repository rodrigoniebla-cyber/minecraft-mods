package net.manaz.vtp.trade;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Client-side copy of a Minecraft 26.2 {@code villager_trade} definition.
 * The raw JSON is kept so {@link TradeSimulator} can apply the item modifiers exactly as the server
 * does and reject anything it cannot reproduce.
 */
public final class VillagerTrade {
   private final Identifier id;
   private final JsonObject json;
   private final TradeCost wants;
   private final Optional<TradeCost> additionalWants;
   private final ItemTemplate gives;
   private final List<JsonElement> itemModifiers;
   private final Optional<EnchantRandomly> enchantRandomly;
   private final Optional<JsonElement> doubleTradePriceEnchantments;
   private final Optional<JsonObject> merchantPredicate;

   private VillagerTrade(
      Identifier id,
      JsonObject json,
      TradeCost wants,
      Optional<TradeCost> additionalWants,
      ItemTemplate gives,
      List<JsonElement> itemModifiers,
      Optional<EnchantRandomly> enchantRandomly,
      Optional<JsonElement> doubleTradePriceEnchantments,
      Optional<JsonObject> merchantPredicate
   ) {
      this.id = id;
      this.json = json;
      this.wants = wants;
      this.additionalWants = additionalWants;
      this.gives = gives;
      this.itemModifiers = itemModifiers;
      this.enchantRandomly = enchantRandomly;
      this.doubleTradePriceEnchantments = doubleTradePriceEnchantments;
      this.merchantPredicate = merchantPredicate;
   }

   public static VillagerTrade parse(Identifier id, JsonObject json) {
      TradeCost wants = TradeCost.parse(json.getAsJsonObject("wants"));
      Optional<TradeCost> additional = json.has("additional_wants") ? Optional.of(TradeCost.parse(json.getAsJsonObject("additional_wants"))) : Optional.empty();
      ItemTemplate gives = ItemTemplate.parse(json.getAsJsonObject("gives"));
      List<JsonElement> modifiers = new ArrayList<>();
      // 26.2 stores a list under "given_item_modifiers"; 26.3 renamed it to "given_item_modifier".
      JsonElement rawModifiers = json.has("given_item_modifiers") ? json.get("given_item_modifiers") : json.get("given_item_modifier");
      if (rawModifiers != null) {
         collectModifiers(rawModifiers, modifiers);
      }

      Optional<EnchantRandomly> enchant = Optional.empty();

      for (JsonElement modifier : modifiers) {
         if (modifier.isJsonObject() && "minecraft:enchant_randomly".equals(functionType(modifier.getAsJsonObject()))) {
            JsonObject object = modifier.getAsJsonObject();
            enchant = Optional.of(
               new EnchantRandomly(
                  Optional.ofNullable(object.get("options")),
                  !object.has("only_compatible") || object.get("only_compatible").getAsBoolean(),
                  object.has("include_additional_cost_component") && object.get("include_additional_cost_component").getAsBoolean()
               )
            );
            break;
         }
      }

      return new VillagerTrade(
         id,
         json,
         wants,
         additional,
         gives,
         List.copyOf(modifiers),
         enchant,
         Optional.ofNullable(json.get("double_trade_price_enchantments")),
         json.has("merchant_predicate") && json.get("merchant_predicate").isJsonObject() ? Optional.of(json.getAsJsonObject("merchant_predicate")) : Optional.empty()
      );
   }

   /** Flattens unconditional {@code minecraft:sequence} functions, like the server applies them. */
   static void collectModifiers(JsonElement modifier, List<JsonElement> result) {
      if (modifier.isJsonArray()) {
         modifier.getAsJsonArray().forEach(element -> collectModifiers(element, result));
      } else if (modifier.isJsonObject()
         && "minecraft:sequence".equals(functionType(modifier.getAsJsonObject()))
         && !hasConditions(modifier.getAsJsonObject())
         && modifier.getAsJsonObject().has("functions")) {
         collectModifiers(modifier.getAsJsonObject().get("functions"), result);
      } else {
         result.add(modifier);
      }
   }

   /** 26.2 names the function type "function"; 26.3 uses "type". */
   static String functionType(JsonObject function) {
      JsonElement type = function.has("function") ? function.get("function") : function.get("type");
      if (type == null || !type.isJsonPrimitive()) {
         return "";
      } else {
         String value = type.getAsString();
         return value.contains(":") ? value : "minecraft:" + value;
      }
   }

   static boolean hasConditions(JsonObject function) {
      for (String key : List.of("conditions", "condition")) {
         JsonElement value = function.get(key);
         if (value != null && !(value.isJsonArray() && value.getAsJsonArray().isEmpty())) {
            return true;
         }
      }

      return false;
   }

   public Identifier id() {
      return this.id;
   }

   public JsonObject json() {
      return this.json;
   }

   public TradeCost wants() {
      return this.wants;
   }

   public Optional<TradeCost> additionalWants() {
      return this.additionalWants;
   }

   public ItemTemplate gives() {
      return this.gives;
   }

   public List<JsonElement> itemModifiers() {
      return this.itemModifiers;
   }

   public Optional<EnchantRandomly> enchantRandomly() {
      return this.enchantRandomly;
   }

   public Optional<JsonElement> doubleTradePriceEnchantments() {
      return this.doubleTradePriceEnchantments;
   }

   public Optional<JsonObject> merchantPredicate() {
      return this.merchantPredicate;
   }

   /** Whether this trade's availability depends on the villager's biome type. */
   public boolean dependsOnVillagerType() {
      return this.merchantPredicate.isPresent();
   }

   /** Number provider for a field the server evaluates when it builds the offer (max_uses, xp, reputation_discount). */
   public Optional<JsonElement> offerValue(String field) {
      return Optional.ofNullable(this.json.get(field));
   }

   @Override
   public String toString() {
      return "VillagerTrade[" + this.id + "]";
   }

   static Optional<Item> item(String id) {
      Identifier identifier = Identifier.tryParse(id);
      return identifier == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(identifier);
   }

   static int maxStackSize(String id) {
      return item(id).map(Item::getDefaultMaxStackSize).orElse(64);
   }

   /** {@code options} is a tag ("#..."), a single id, or a list of ids, exactly as in the JSON. */
   public record EnchantRandomly(Optional<JsonElement> options, boolean onlyCompatible, boolean includeAdditionalCost) {
   }

   public record TradeCost(String itemId, JsonElement count, JsonObject components) {
      static TradeCost parse(JsonObject json) {
         return new TradeCost(json.get("id").getAsString(), json.has("count") ? json.get("count") : null, json.has("components") ? json.getAsJsonObject("components") : new JsonObject());
      }

      public boolean hasConstantCount() {
         return this.count == null || Numbers.isConstant(this.count);
      }

      public boolean hasSupportedCount() {
         return this.count == null || Numbers.isSupported(this.count);
      }

      public int baseCount() {
         return this.count == null ? 1 : Numbers.constantInt(this.count);
      }

      public Item item() {
         return VillagerTrade.item(this.itemId).orElse(Items.AIR);
      }

      /** Mirrors 26.2 {@code TradeCost.toItemCost}: clamp(count + additional, 0, max stack size). */
      public int toItemCount(int additionalCost) {
         return Mth.clamp(this.baseCount() + additionalCost, 0, VillagerTrade.maxStackSize(this.itemId));
      }

      /** As {@link #toItemCount(int)}, drawing a random count from the sequence when the count is random. */
      public int toItemCount(int additionalCost, net.manaz.vtp.rng.SimulatedRandomSource random) {
         int base = this.count == null ? 1 : Numbers.getInt(this.count, random);
         return Mth.clamp(base + additionalCost, 0, VillagerTrade.maxStackSize(this.itemId));
      }

      public ItemStack displayStack() {
         Item item = this.item();
         return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item, Math.max(1, this.baseCount()));
      }
   }

   public record ItemTemplate(String itemId, int count, JsonObject components) {
      static ItemTemplate parse(JsonObject json) {
         return new ItemTemplate(
            json.get("id").getAsString(), json.has("count") ? json.get("count").getAsInt() : 1, json.has("components") ? json.getAsJsonObject("components") : new JsonObject()
         );
      }

      public Item item() {
         return VillagerTrade.item(this.itemId).orElse(Items.AIR);
      }

      public ItemStack displayStack() {
         Item item = this.item();
         return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item, Math.max(1, this.count));
      }
   }

   static JsonArray asArray(JsonElement element) {
      if (element.isJsonArray()) {
         return element.getAsJsonArray();
      } else {
         JsonArray array = new JsonArray();
         array.add(element);
         return array;
      }
   }
}
