package net.manaz.vtp.trade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Lifecycle;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.manaz.vtp.rng.RandomSequenceSimulator;
import net.manaz.vtp.rng.SimulatedRandomSource;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderSet;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.enchantment.Enchantment;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Replays trades recorded from a real Minecraft 26.2 dedicated server (vanilla, fresh world, villagers
 * summoned per profession/level and converted by a zombie so the server rolls their offers) and checks
 * that the simulator reproduces every offer of every round, in order.
 */
public class ServerTruthTest {
   private static final List<String> TRUTH_FILES = List.of("seed_8675309.json", "seed_-4172144997902289642.json");

   @BeforeAll
   static void setup() {
      SharedConstants.tryDetectVersion();
      Bootstrap.bootStrap();
      MappedRegistry<Enchantment> registry = new MappedRegistry<>(Registries.ENCHANTMENT, Lifecycle.stable());

      // Same ids and level ranges as the 26.2 enchantment registry (min level is always 1 in vanilla).
      for (Map.Entry<String, Integer> entry : maxLevels().entrySet()) {
         Identifier id = Identifier.withDefaultNamespace(entry.getKey());
         Enchantment enchantment = Enchantment.enchantment(
               Enchantment.definition(HolderSet.direct(), 1, entry.getValue(), Enchantment.constantCost(1), Enchantment.constantCost(1), 1, EquipmentSlotGroup.ANY)
            )
            .build(id);
         registry.register(ResourceKey.create(Registries.ENCHANTMENT, id), enchantment, RegistrationInfo.BUILT_IN);
      }

      registry.bindAllTagsToEmpty();
      registry.freeze();
      TradeDataLoader.fallbackRegistry = () -> registry;
   }

   private static Map<String, Integer> maxLevels() {
      Map<String, Integer> levels = new TreeMap<>();
      JsonObject list = read("/vtp_truth/enchantment_max_levels_26_2.json").getAsJsonObject();
      list.entrySet().forEach(e -> levels.put(e.getKey(), e.getValue().getAsInt()));
      return levels;
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
      List<String> supportedSets = new ArrayList<>();
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

            if (!supportedSets.contains(entry.getKey())) {
               supportedSets.add(entry.getKey());
            }

            SimulatedRandomSource rng = RandomSequenceSimulator.createForSequence(seed, set.randomSequence().orElseThrow().toString());

            for (JsonObject round : entry.getValue()) {
               List<ObservedOffer> predicted = TradeSimulator.fingerprint(TradeSimulator.simulateTradeRound(set, rng, List.of()));
               List<ObservedOffer> actual = observed(round.getAsJsonArray("offers"));
               checkedRounds++;
               if (!predicted.equals(actual)) {
                  failures.add("seed " + seed + " " + entry.getKey() + " round " + round.get("round").getAsInt() + "\n  predicted " + predicted + "\n  actual    " + actual);
               }
            }
         }
      }

      System.out.println("[VTP test] supported trade sets: " + supportedSets);
      System.out.println("[VTP test] reported unsupported: " + unsupported);
      System.out.println("[VTP test] rounds compared: " + checkedRounds + ", mismatches: " + failures.size());
      failures.stream().limit(10).forEach(System.out::println);
      assertTrue(supportedSets.containsAll(List.of("librarian/1", "librarian/2", "librarian/3", "librarian/4", "librarian/5")), "librarian must be predictable");
      assertTrue(checkedRounds > 300, "too few rounds compared: " + checkedRounds);
      assertEquals(List.of(), failures);
   }

   private static List<ObservedOffer> observed(JsonArray offers) {
      List<ObservedOffer> result = new ArrayList<>();

      for (JsonElement element : offers) {
         JsonObject o = element.getAsJsonObject();
         List<ObservedOffer.Book> books = new ArrayList<>();

         for (JsonElement book : o.getAsJsonArray("books")) {
            books.add(new ObservedOffer.Book(book.getAsJsonArray().get(0).getAsString(), book.getAsJsonArray().get(1).getAsInt()));
         }

         result.add(
            new ObservedOffer(
               o.get("result").getAsString(),
               o.get("count").getAsInt(),
               o.get("costA").getAsString(),
               o.get("priceA").getAsInt(),
               o.get("costB").getAsString(),
               o.get("countB").getAsInt(),
               List.copyOf(books)
            )
         );
      }

      return result;
   }
}
