package net.manaz.vtp.rng;

public class SimulatedRandomSource {
   private static final float FLOAT_UNIT = 5.9604645E-8F;
   private static final double DOUBLE_UNIT = 1.110223E-16F;
   private final SimulatedXoroshiro128PlusPlus rng;

   public SimulatedRandomSource(SimulatedXoroshiro128PlusPlus rng) {
      this.rng = rng;
   }

   public SimulatedRandomSource(long seedLo, long seedHi) {
      this.rng = new SimulatedXoroshiro128PlusPlus(seedLo, seedHi);
   }

   public int nextInt() {
      return (int)this.rng.nextLong();
   }

   public int nextInt(int bound) {
      if (bound <= 0) {
         throw new IllegalArgumentException("Bound must be positive");
      } else {
         long unsignedInt = Integer.toUnsignedLong(this.nextInt());
         long product = unsignedInt * bound;
         long low = product & 4294967295L;
         if (low < bound) {
            for (int threshold = Integer.remainderUnsigned(~bound + 1, bound); low < threshold; low = product & 4294967295L) {
               unsignedInt = Integer.toUnsignedLong(this.nextInt());
               product = unsignedInt * bound;
            }
         }

         return (int)(product >> 32);
      }
   }

   public long nextLong() {
      return this.rng.nextLong();
   }

   public boolean nextBoolean() {
      return (this.rng.nextLong() & 1L) != 0L;
   }

   public float nextFloat() {
      return (float)this.nextBits(24) * 5.9604645E-8F;
   }

   public double nextDouble() {
      return this.nextBits(53) * 1.110223E-16F;
   }

   public int nextIntBetweenInclusive(int min, int max) {
      return this.nextInt(max - min + 1) + min;
   }

   private long nextBits(int bits) {
      return this.rng.nextLong() >>> 64 - bits;
   }

   public SimulatedXoroshiro128PlusPlus getInternalRng() {
      return this.rng;
   }
}
