package net.manaz.vtp.prediction;

import java.util.Optional;
import java.util.UUID;
import net.manaz.vtp.rng.SimulatedRandomSource;
import net.manaz.vtp.trade.TradeSet;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;

/**
 * On 26.x this snapshotted the integrated server's trade random sequences. A 1.21.11 integrated
 * server still uses the hard-coded pre-26 trade tables (rolled from each villager's own RNG), so
 * there is no sequence to read; singleplayer prediction is reported as unsupported by
 * {@link PredictionEngine#compatibilityProblem}. Only the seed state record remains in use.
 */
public final class SingleplayerSequences {
   private SingleplayerSequences() {
   }

   public static void clear() {
   }

   public static boolean ready(UUID uuid) {
      return false;
   }

   public static Optional<SingleplayerSequences.Seed> get(ResourceKey<TradeSet> key) {
      return Optional.empty();
   }

   public static void tick(Minecraft client) {
   }

   public record Seed(long lo, long hi) {
      public SimulatedRandomSource copy() {
         return new SimulatedRandomSource(this.lo, this.hi);
      }
   }
}
