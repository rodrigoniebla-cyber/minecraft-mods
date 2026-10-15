package net.manaz.vtp.trade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.SequencedSet;
import java.util.Set;
import net.manaz.vtp.rng.RandomSequenceSimulator;
import net.manaz.vtp.rng.SimulatedRandomSource;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Replays trades recorded from a real Minecraft 26.2 dedicated server (vanilla world with structures;
 * for every profession and level, villagers of each biome type were summoned and converted by a zombie
 * so the server rolled their offers) and checks that the simulator reproduces every offer of every
 * round, in order: items, prices, book and gear enchantments, stew effects, dye colours and potions.
 */
public class ServerTruthTest {
   private static final List<String> TRUTH_FILES = List.of("seed_8675309.json", "seed_-4172144997902289642.json");

   @BeforeAll
   static void setup() {
      SharedConstants.tryDetectVersion();
      Bootstrap.bootStrap();
      MappedRegistry<Enchantment> registry = new MappedRegistry<>(Registries.ENCHANTMENT, Lifecycle.stable());
      HolderGetter<Enchantment> lookup = registry.createRegistrationLookup();
      Map<Identifier, Enchantment> enchantments = new LinkedHashMap<>();

      // The selection-relevant parts of the 26.2 enchantment definitions (effects are irrelevant to trades).
      for (String name : read("/vtp_truth/26_2/enchantment/index.json").getAsJsonArray().asList().stream().map(JsonElement::getAsString).toList()) {
         JsonObject json = read("/vtp_truth/26_2/enchantment/" + name + ".json").getAsJsonObject();
         Identifier id = Identifier.withDefaultNamespace(name);
         Enchantment.EnchantmentDefinition definition = new Enchantment.EnchantmentDefinition(
            items(json.get("supported_items")),
            json.has("primary_items") ? java.util.Optional.of(items(json.get("primary_items"))) : java.util.Optional.empty(),
            json.get("weight").getAsInt(),
            json.get("max_level").getAsInt(),
            cost(json.getAsJsonObject("min_cost")),
            cost(json.getAsJsonObject("max_cost")),
            json.get("anvil_cost").getAsInt(),
            json.getAsJsonArray("slots").asList().stream().map(s -> EquipmentSlotGroup.CODEC.parse(JsonOps.INSTANCE, s).getOrThrow()).toList()
         );
         Enchantment.Builder builder = Enchantment.enchantment(definition);
         if (json.has("exclusive_set")) {
            builder.exclusiveWith(
               HolderSet.direct(ids(json.get("exclusive_set"), "enchantment").stream().map(e -> lookup.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, e))).toList())
            );
         }

         enchantments.put(id, builder.build(id));
      }

      enchantments.forEach((id, enchantment) -> registry.register(ResourceKey.create(Registries.ENCHANTMENT, id), enchantment, RegistrationInfo.BUILT_IN));
      registry.bindAllTagsToEmpty();
      registry.freeze();
      TradeDataLoader.fallbackRegistry = () -> registry;
   }

   private static Enchantment.Cost cost(JsonObject json) {
      return new Enchantment.Cost(json.get("base").getAsInt(), json.get("per_level_above_first").getAsInt());
   }

   private static HolderSet<Item> items(JsonElement reference) {
      List<Holder<Item>> items = new ArrayList<>();

      for (Identifier id : ids(reference, "item")) {
         BuiltInRegistries.ITEM.get(id).ifPresent(items::add);
      }

      return HolderSet.direct(items);
   }

   /** Resolves "#tag", "id" or a list exactly like the 26.2 tag loader (in order, nested inline, no duplicates). */
   private static List<Identifier> ids(JsonElement reference, String registry) {
      SequencedSet<Identifier> result = new LinkedHashSet<>();

      for (JsonElement element : reference.isJsonArray() ? reference.getAsJsonArray().asList() : List.of(reference)) {
         String value = element.getAsString();
         if (value.startsWith("#")) {
            tag(Identifier.parse(value.substring(1)), registry, result, new HashSet<>());
         } else {
            result.add(Identifier.parse(value));
         }
      }

      return List.copyOf(result);
   }

   private static void tag(Identifier tag, String registry, SequencedSet<Identifier> result, Set<Identifier> visiting) {
      if (visiting.add(tag)) {
         for (JsonElement value : read("/vtp_truth/26_2/tags/" + registry + "/" + tag.getPath() + ".json").getAsJsonObject().getAsJsonArray("values")) {
            String entry = value.isJsonObject() ? value.getAsJsonObject().get("id").getAsString() : value.getAsString();
            if (entry.startsWith("#")) {
               tag(Identifier.parse(entry.substring(1)), registry, result, visiting);
            } else {
               result.add(Identifier.parse(entry));
            }
         }
      }
   }

   private static JsonElement read(String path) {
      try (InputStream in = ServerTruthTest.class.getResourceAsStream(path)) {
         return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
      } catch (Exception e) {
         throw new RuntimeException("Missing test resource " + path, e);
      }
   }

   @Test
   void simulatorMatchesRecorded262ServerTrades() {
      int checkedRounds = 0;
      Set<String> checkedSets = new LinkedHashSet<>();
      List<String> unsupported = new ArrayList<>();
      List<String> failures = new ArrayList<>();

      for (String file : TRUTH_FILES) {
         JsonObject truth = read("/vtp_truth/" + file).getAsJsonObject();
         long seed = truth.get("seed").getAsLong();
         Map<String, List<JsonObject>> bySet = new LinkedHashMap<>();

         for (JsonElement round : truth.getAsJsonArray("rounds")) {
            JsonObject r = round.getAsJsonObject();
            bySet.computeIfAbsent(r.get("profession").getAsString() + "/" + r.get("level").getAsInt(), k -> new ArrayList<>()).add(r);
         }

         for (Map.Entry<String, List<JsonObject>> entry : bySet.entrySet()) {
            String[] parts = entry.getKey().split("/");
            var profession = BuiltInRegistries.VILLAGER_PROFESSION
               .get(ResourceKey.create(Registries.VILLAGER_PROFESSION, Identifier.withDefaultNamespace(parts[0])))
               .orElseThrow();
            ResourceKey<TradeSet> key = TradeDataLoader.getTradeSetKey(profession, Integer.parseInt(parts[1]));
            TradeSet set = TradeDataLoader.getTradeSet(key).orElseThrow();
            String problem = TradeSimulator.compatibilityProblem(set);
            if (!problem.isEmpty()) {
               unsupported.add(entry.getKey() + " (" + problem + ")");
               continue;
            }

            checkedSets.add(entry.getKey());
            SimulatedRandomSource rng = RandomSequenceSimulator.createForSequence(seed, set.randomSequence().orElseThrow().toString());

            for (JsonObject round : entry.getValue()) {
               String type = round.has("type") ? round.get("type").getAsString() : "minecraft:plains";
               List<ObservedOffer> predicted = TradeSimulator.fingerprint(TradeSimulator.simulateTradeRound(set, rng, type));
               List<ObservedOffer> actual = observed(round.getAsJsonArray("offers"));
               checkedRounds++;
               if (!predicted.equals(actual)) {
                  failures.add(
                     "seed " + seed + " " + entry.getKey() + " " + type + " round " + round.get("round").getAsInt() + "\n  predicted " + predicted + "\n  actual    " + actual
                  );
               }
            }
         }
      }

      System.out.println("[VTP test] trade sets compared: " + checkedSets.size() + " " + checkedSets);
      System.out.println("[VTP test] reported unsupported: " + unsupported);
      System.out.println("[VTP test] rounds compared: " + checkedRounds + ", mismatches: " + failures.size());
      failures.stream().limit(10).forEach(System.out::println);
      assertEquals(List.of(), unsupported, "every vanilla villager trade set must be predictable");
      assertEquals(65, checkedSets.size(), "all 13 professions x 5 levels must be covered");
      assertTrue(checkedRounds > 1000, "too few rounds compared: " + checkedRounds);
      assertEquals(List.of(), failures);
   }

   private static List<ObservedOffer> observed(JsonArray offers) {
      List<ObservedOffer> result = new ArrayList<>();

      for (JsonElement element : offers) {
         JsonObject o = element.getAsJsonObject();
         result.add(
            new ObservedOffer(
               o.get("result").getAsString(),
               o.get("count").getAsInt(),
               o.get("costA").getAsString(),
               o.get("priceA").getAsInt(),
               o.get("costB").getAsString(),
               o.get("countB").getAsInt(),
               books(o.getAsJsonArray("books")),
               books(o.getAsJsonArray("gear")),
               o.get("effect").getAsString(),
               o.get("dye").getAsInt(),
               o.get("potion").getAsString()
            )
         );
      }

      return result;
   }

   private static List<ObservedOffer.Book> books(JsonArray books) {
      List<ObservedOffer.Book> result = new ArrayList<>();

      for (JsonElement book : books) {
         result.add(new ObservedOffer.Book(book.getAsJsonArray().get(0).getAsString(), book.getAsJsonArray().get(1).getAsInt()));
      }

      return List.copyOf(result);
   }
}
