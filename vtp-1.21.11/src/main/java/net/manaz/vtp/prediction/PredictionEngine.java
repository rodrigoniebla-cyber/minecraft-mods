package net.manaz.vtp.prediction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.Map.Entry;
import net.manaz.vtp.render.VillagerHudRenderer;
import net.manaz.vtp.render.VillagerTracker;
import net.manaz.vtp.rng.RandomSequenceSimulator;
import net.manaz.vtp.rng.SimulatedRandomSource;
import net.manaz.vtp.rng.SimulatedXoroshiro128PlusPlus;
import net.manaz.vtp.seed.SeedProvider;
import net.manaz.vtp.trade.ObservedOffer;
import net.manaz.vtp.trade.TradeDataLoader;
import net.manaz.vtp.trade.TradeSimulator;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.enchantment.Enchantment;
import net.manaz.vtp.trade.TradeSet;

public final class PredictionEngine {
   public static final int MAX_ROUND = 1000000;
   private static int maxRerolls = 500;
   private static long revision;
   private static long preparedRevision = -1L;
   private static UUID preparedUuid;
   private static boolean preparing;
   private static final Map<PredictionEngine.TargetKey, PredictionResult> targetCache = new LinkedHashMap<PredictionEngine.TargetKey, PredictionResult>(
      256, 0.75F, true
   ) {
      @Override
      protected boolean removeEldestEntry(Entry<PredictionEngine.TargetKey, PredictionResult> e) {
         return this.size() > 256;
      }
   };
   private static final Map<ResourceKey<TradeSet>, Integer> offsets = new HashMap<>();
   private static final Map<TradeSet, String> compatibility = new IdentityHashMap<>();
   private static final Map<PredictionEngine.TimelineKey, PredictionEngine.Timeline> timelines = new LinkedHashMap<PredictionEngine.TimelineKey, PredictionEngine.Timeline>(
      16, 0.75F, true
   ) {
      @Override
      protected boolean removeEldestEntry(Entry<PredictionEngine.TimelineKey, PredictionEngine.Timeline> entry) {
         return this.size() > 8;
      }
   };

   public static long revision() {
      return revision;
   }

   public static void changed() {
      revision++;
      targetCache.clear();
   }

   public static void setMaxRerolls(int max) {
      maxRerolls = Math.clamp((long)max, 1, 10000);
      changed();
   }

   public static int getMaxRerolls() {
      return maxRerolls;
   }

   public static ResourceKey<TradeSet> sequenceKey(ResourceKey<TradeSet> key) {
      return TradeDataLoader.getTradeSet(key).<Identifier>flatMap(TradeSet::randomSequence).map(id -> ResourceKey.create(TradeSet.REGISTRY, id)).orElse(key);
   }

   public static int getOffset(ResourceKey<TradeSet> key) {
      return offsets.getOrDefault(sequenceKey(key), 0);
   }

   public static void setOffset(ResourceKey<TradeSet> key, int value) {
      offsets.put(sequenceKey(key), Math.clamp((long)value, 0, 1000000));
      VillagerTracker.invalidateSynchronization();
      clearCache();
   }

   public static Map<ResourceKey<TradeSet>, Integer> getAllOffsets() {
      return Map.copyOf(offsets);
   }

   public static void loadOffsets(Map<ResourceKey<TradeSet>, Integer> saved) {
      offsets.clear();
      saved.forEach((k, v) -> offsets.put((ResourceKey<TradeSet>)k, Math.clamp((long)v.intValue(), 0, 1000000)));
      clearCache();
   }

   public static void clearAllOffsets() {
      offsets.clear();
      clearCache();
   }

   public static void clearCache() {
      timelines.clear();
      compatibility.clear();
      changed();
   }

   public static String compatibilityProblem(Holder<VillagerProfession> profession, int level) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.getSingleplayerServer() != null) {
         return "singleplayer runs 1.21.11 trades; predictions need a 26.2 server";
      } else if (mc.level != null && mc.level.enabledFeatures().contains(FeatureFlags.TRADE_REBALANCE)) {
         return "Trade Rebalance is not supported";
      } else {
         ResourceKey<TradeSet> key = TradeDataLoader.getTradeSetKey(profession, level);
         if (key == null) {
            return "no trades at this level";
         } else {
            Optional<TradeSet> set = TradeDataLoader.getTradeSet(key);
            return set.isEmpty() ? "trade definitions unavailable" : compatibility.computeIfAbsent(set.get(), TradeSimulator::compatibilityProblem);
         }
      }
   }

   public static List<List<TradeSimulator.SimulatedOffer>> getUpcomingRounds(long seed, Holder<VillagerProfession> profession, int level, int start, int count) {
      if (start >= 0 && start <= 1000000 && count > 0 && compatibilityProblem(profession, level).isEmpty()) {
         ResourceKey<TradeSet> key = TradeDataLoader.getTradeSetKey(profession, level);
         TradeSet set = TradeDataLoader.getTradeSet(key).orElseThrow();
         SimulatedXoroshiro128PlusPlus initial = RandomSequenceSimulator.createForSequence(seed, ((Identifier)set.randomSequence().orElseThrow()).toString())
            .getInternalRng();
         return read(seed, key, set, new SingleplayerSequences.Seed(initial.getSeedLo(), initial.getSeedHi()), start, count);
      } else {
         return List.of();
      }
   }

   private static List<List<TradeSimulator.SimulatedOffer>> read(
      long seed, ResourceKey<TradeSet> key, TradeSet set, SingleplayerSequences.Seed state, int start, int count
   ) {
      int bounded = Math.min(Math.min(count, 20000), 1000000 - start + 1);
      return timelines.computeIfAbsent(
            new PredictionEngine.TimelineKey(seed, key, set, state.lo(), state.hi()), ignored -> new PredictionEngine.Timeline(set, state)
         )
         .read(start, bounded);
   }

   public static List<List<TradeSimulator.SimulatedOffer>> getForecastRounds(long seed, Holder<VillagerProfession> profession, int level, int count) {
      if (!compatibilityProblem(profession, level).isEmpty()) {
         return List.of();
      } else {
         ResourceKey<TradeSet> key = TradeDataLoader.getTradeSetKey(profession, level);
         if (Minecraft.getInstance().getSingleplayerServer() != null) {
            Optional<SingleplayerSequences.Seed> snapshot = SingleplayerSequences.get(key);
            return snapshot.isEmpty() ? List.of() : read(seed, key, TradeDataLoader.getTradeSet(key).orElseThrow(), snapshot.get(), 0, count);
         } else {
            return getUpcomingRounds(seed, profession, level, VillagerTracker.getNextRoundForLevel(profession, level), count);
         }
      }
   }

   public static List<TradeSimulator.SimulatedOffer> simulateRound(long seed, Holder<VillagerProfession> profession, int level, int round) {
      List<List<TradeSimulator.SimulatedOffer>> rounds = getUpcomingRounds(seed, profession, level, round, 1);
      return rounds.isEmpty() ? List.of() : rounds.getFirst();
   }

   public static PredictionResult predictFrom(long seed, Holder<VillagerProfession> profession, int level, EnchantmentTarget target, int startRound) {
      if (startRound >= 0 && startRound <= 1000000 && compatibilityProblem(profession, level).isEmpty()) {
         List<List<TradeSimulator.SimulatedOffer>> rounds = getUpcomingRounds(seed, profession, level, startRound, maxRerolls);
         List<TradeSimulator.SimulatedOffer> first = rounds.isEmpty() ? List.of() : rounds.getFirst();

         for (int i = 0; i < rounds.size(); i++) {
            List<TradeSimulator.SimulatedOffer> offers = rounds.get(i);
            if (matchesTarget(offers, target)) {
               int round = startRound + i;
               return new PredictionResult(Optional.of(round), round, offers, first);
            }
         }

         return new PredictionResult(Optional.empty(), startRound + rounds.size(), List.of(), first);
      } else {
         return new PredictionResult(Optional.empty(), 0, List.of(), List.of());
      }
   }

   public static PredictionResult predictForVillager(long seed, UUID uuid, VillagerData data, EnchantmentTarget target) {
      List<TradeSimulator.SimulatedOffer> actual = VillagerTracker.getCurrentOffers(uuid).orElse(List.of());
      if (matchesTarget(actual, target)) {
         return new PredictionResult(Optional.of(0), 0, actual, actual);
      } else if (data.level() <= 1 && !VillagerTracker.isTradeLocked(uuid)) {
         boolean pending = VillagerTracker.isAwaitingOffers(uuid);
         ResourceKey<TradeSet> tradeKey = TradeDataLoader.getTradeSetKey(data.profession(), data.level());
         PredictionEngine.TargetKey cacheKey = new PredictionEngine.TargetKey(
            seed,
            uuid,
            data,
            target,
            actual,
            tradeKey == null ? null : SingleplayerSequences.get(tradeKey).orElse(null),
            VillagerTracker.getNextRoundForLevel(data.profession(), data.level()),
            pending,
            maxRerolls
         );
         PredictionResult cached = targetCache.get(cacheKey);
         if (cached != null) {
            return cached;
         } else if (!preparing) {
            return new PredictionResult(Optional.empty(), 0, List.of(), actual);
         } else {
            List<List<TradeSimulator.SimulatedOffer>> future = getForecastRounds(seed, data.profession(), data.level(), maxRerolls);

            for (int i = 0; i < future.size(); i++) {
               if (matchesTarget(future.get(i), target)) {
                  int remaining = SequencePosition.remaining(i, 0, pending);
                  PredictionResult result = new PredictionResult(Optional.of(remaining), i + 1, future.get(i), actual);
                  targetCache.put(cacheKey, result);
                  return result;
               }
            }

            PredictionResult result = new PredictionResult(Optional.empty(), future.size(), List.of(), actual);
            if (!future.isEmpty()) {
               targetCache.put(cacheKey, result);
            }

            return result;
         }
      } else {
         return new PredictionResult(Optional.empty(), 0, List.of(), actual);
      }
   }

   public static void prepareView() {
      UUID uuid = VillagerTracker.getLastVillagerUuid();
      Optional<VillagerData> data = VillagerTracker.getLastVillagerData();
      if (uuid != null && !data.isEmpty() && SeedProvider.hasSeed()) {
         if (preparedRevision != revision || !uuid.equals(preparedUuid)) {
            preparing = true;

            try {
               long seed = SeedProvider.getSeed().orElseThrow();

               for (int level = 1; level <= 5; level++) {
                  getForecastRounds(seed, data.get().profession(), level, 50);
               }

               for (EnchantmentTarget target : VillagerHudRenderer.getTargets()) {
                  predictForVillager(seed, uuid, data.get(), target);
               }

               preparedRevision = revision;
               preparedUuid = uuid;
            } finally {
               preparing = false;
            }
         }
      }
   }

   private static boolean matchesTarget(List<TradeSimulator.SimulatedOffer> offers, EnchantmentTarget target) {
      return offers.stream().anyMatch(o -> o.enchantment().filter(e -> target.matches(e.enchantment(), e.level(), e.emeraldPrice())).isPresent());
   }

   public static boolean matchesObserved(VillagerData data, int round, List<ObservedOffer> observed) {
      List<TradeSimulator.SimulatedOffer> predicted = simulateRound(SeedProvider.getSeed().orElseThrow(), data.profession(), data.level(), round);
      return !predicted.isEmpty() && OfferMatching.same(TradeSimulator.fingerprint(predicted), observed);
   }

   public static List<Integer> findObserved(VillagerData data, List<ObservedOffer> observed, int expected) {
      int start = Math.max(0, expected - maxRerolls);
      int count = Math.min(1000000 - start + 1, Math.max(maxRerolls, expected - start + maxRerolls));
      List<List<TradeSimulator.SimulatedOffer>> rounds = getUpcomingRounds(SeedProvider.getSeed().orElseThrow(), data.profession(), data.level(), start, count);
      List<Integer> matches = new ArrayList<>();

      for (int i = 0; i < rounds.size(); i++) {
         if (OfferMatching.same(TradeSimulator.fingerprint(rounds.get(i)), observed)) {
            matches.add(start + i);
         }

         if (matches.size() > 1) {
            break;
         }
      }

      return matches;
   }

   public static List<Integer> findCalibrationSequence(
      long seed, Holder<VillagerProfession> profession, int level, List<PredictionEngine.CalibrationSpec> specs
   ) {
      if (!specs.isEmpty() && specs.size() <= 100) {
         int next = VillagerTracker.getNextRoundForLevel(profession, level);
         int start = Math.max(0, next - maxRerolls);
         int count = Math.min(20000, maxRerolls + next - start + specs.size() - 1);
         List<List<TradeSimulator.SimulatedOffer>> rounds = getUpcomingRounds(seed, profession, level, start, count);
         List<Integer> matches = new ArrayList<>();

         for (int i = 0; i + specs.size() <= rounds.size(); i++) {
            boolean match = true;

            for (int j = 0; j < specs.size(); j++) {
               PredictionEngine.CalibrationSpec spec = specs.get(j);
               List<TradeSimulator.EnchantmentResult> books = rounds.get(i + j).stream().flatMap(o -> o.enchantment().stream()).toList();
               if (spec.noTrade()
                  ? !books.isEmpty()
                  : books.stream()
                     .noneMatch(
                        e -> e.enchantment().is(spec.enchantment()) && e.level() == spec.level() && (spec.price() < 0 || e.emeraldPrice() == spec.price())
                     )) {
                  match = false;
                  break;
               }
            }

            if (match) {
               matches.add(start + i + specs.size() - 1);
            }
         }

         return List.copyOf(matches);
      } else {
         return List.of();
      }
   }

   public static Optional<ResourceKey<TradeSet>> applyCalibration(Holder<VillagerProfession> profession, int level, int round) {
      ResourceKey<TradeSet> key = TradeDataLoader.getTradeSetKey(profession, level);
      if (key == null) {
         return Optional.empty();
      } else {
         setOffset(key, round);
         return Optional.of(key);
      }
   }

   public record CalibrationSpec(ResourceKey<Enchantment> enchantment, int level, int price, boolean noTrade) {
      public CalibrationSpec(ResourceKey<Enchantment> ench, int level) {
         this(ench, level, -1, false);
      }

      public static PredictionEngine.CalibrationSpec of(ResourceKey<Enchantment> ench, int level, int price) {
         return new PredictionEngine.CalibrationSpec(ench, level, price, false);
      }

      public static PredictionEngine.CalibrationSpec missing() {
         return new PredictionEngine.CalibrationSpec(null, 0, -1, true);
      }
   }

   private record TargetKey(
      long seed,
      UUID uuid,
      VillagerData data,
      EnchantmentTarget target,
      List<TradeSimulator.SimulatedOffer> actual,
      SingleplayerSequences.Seed snapshot,
      int next,
      boolean pending,
      int max
   ) {
   }

   private static final class Timeline {
      final TradeSet set;
      final NavigableMap<Integer, SingleplayerSequences.Seed> checkpoints = new TreeMap<>();
      final Map<Integer, List<TradeSimulator.SimulatedOffer>> rounds = new LinkedHashMap<Integer, List<TradeSimulator.SimulatedOffer>>(1024, 0.75F, true) {
         {
            Objects.requireNonNull(Timeline.this);
         }

         @Override
         protected boolean removeEldestEntry(Entry<Integer, List<TradeSimulator.SimulatedOffer>> e) {
            return this.size() > 1024;
         }
      };

      Timeline(TradeSet set, SingleplayerSequences.Seed seed) {
         this.set = set;
         this.checkpoints.put(0, seed);
      }

      List<List<TradeSimulator.SimulatedOffer>> read(int start, int count) {
         List<List<TradeSimulator.SimulatedOffer>> result = new ArrayList<>();
         boolean cached = true;

         for (int i = start; i < start + count; i++) {
            if (!this.rounds.containsKey(i)) {
               cached = false;
               break;
            }
         }

         if (!cached) {
            Entry<Integer, SingleplayerSequences.Seed> checkpoint = this.checkpoints.floorEntry(start);
            SimulatedRandomSource rng = checkpoint.getValue().copy();

            for (int ix = checkpoint.getKey(); ix < start + count; ix++) {
               if (ix % 128 == 0) {
                  SimulatedXoroshiro128PlusPlus state = rng.getInternalRng();
                  this.checkpoints.put(ix, new SingleplayerSequences.Seed(state.getSeedLo(), state.getSeedHi()));
               }

               List<TradeSimulator.SimulatedOffer> offers = TradeSimulator.simulateTradeRound(this.set, rng, List.of());
               if (ix >= start) {
                  this.rounds.put(ix, offers);
                  result.add(offers);
               }
            }

            return List.copyOf(result);
         } else {
            for (int ix = start; ix < start + count; ix++) {
               result.add(this.rounds.get(ix));
            }

            return List.copyOf(result);
         }
      }
   }

   private record TimelineKey(long worldSeed, ResourceKey<TradeSet> key, TradeSet definition, long lo, long hi) {
   }
}
