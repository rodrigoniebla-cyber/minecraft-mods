package net.manaz.vtp.rng;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;

/**
 * Exposes a {@link SimulatedRandomSource} as a vanilla {@link RandomSource}, so vanilla algorithms
 * that are identical in 1.21.11 and 26.2 (enchantment selection) can consume the simulated sequence.
 * Only the draws a Xoroshiro-backed {@code XoroshiroRandomSource} supports are delegated.
 */
public final class SequenceRandomSource implements RandomSource {
   private final SimulatedRandomSource random;

   public SequenceRandomSource(SimulatedRandomSource random) {
      this.random = random;
   }

   @Override
   public RandomSource fork() {
      throw new UnsupportedOperationException("Trade simulation never forks the sequence");
   }

   @Override
   public PositionalRandomFactory forkPositional() {
      throw new UnsupportedOperationException("Trade simulation never forks the sequence");
   }

   @Override
   public void setSeed(long seed) {
      throw new UnsupportedOperationException("Trade simulation never reseeds the sequence");
   }

   @Override
   public int nextInt() {
      return this.random.nextInt();
   }

   @Override
   public int nextInt(int bound) {
      return this.random.nextInt(bound);
   }

   @Override
   public long nextLong() {
      return this.random.nextLong();
   }

   @Override
   public boolean nextBoolean() {
      return this.random.nextBoolean();
   }

   @Override
   public float nextFloat() {
      return this.random.nextFloat();
   }

   @Override
   public double nextDouble() {
      return this.random.nextDouble();
   }

   @Override
   public double nextGaussian() {
      throw new UnsupportedOperationException("Trade simulation never draws gaussians");
   }
}
