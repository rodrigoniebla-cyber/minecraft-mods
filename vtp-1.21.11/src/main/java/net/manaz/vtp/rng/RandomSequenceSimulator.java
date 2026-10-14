package net.manaz.vtp.rng;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class RandomSequenceSimulator {
   private static final long GOLDEN_RATIO_64 = 7640891576956012809L;
   private static final long SILVER_RATIO_64 = -7046029254386353131L;

   public static SimulatedRandomSource createForSequence(long worldSeed, String sequenceId) {
      return createForSequence(worldSeed, sequenceId, 0, true, true);
   }

   public static SimulatedRandomSource createForSequence(long worldSeed, String sequenceId, int salt, boolean includeWorldSeed, boolean includeSequenceId) {
      long effectiveSeed = (includeWorldSeed ? worldSeed : 0L) ^ salt;
      long lo = effectiveSeed ^ 7640891576956012809L;
      long hi = lo + -7046029254386353131L;
      if (includeSequenceId) {
         long[] keyHash = seedForKey(sequenceId);
         lo ^= keyHash[0];
         hi ^= keyHash[1];
      }

      lo = mixStafford13(lo);
      hi = mixStafford13(hi);
      return new SimulatedRandomSource(lo, hi);
   }

   static long[] seedForKey(String sequenceId) {
      try {
         MessageDigest md5 = MessageDigest.getInstance("MD5");
         byte[] hash = md5.digest(sequenceId.getBytes(StandardCharsets.UTF_8));
         long lo = fromBytes(hash[0], hash[1], hash[2], hash[3], hash[4], hash[5], hash[6], hash[7]);
         long hi = fromBytes(hash[8], hash[9], hash[10], hash[11], hash[12], hash[13], hash[14], hash[15]);
         return new long[]{lo, hi};
      } catch (NoSuchAlgorithmException var7) {
         throw new RuntimeException("MD5 not available", var7);
      }
   }

   private static long fromBytes(byte b1, byte b2, byte b3, byte b4, byte b5, byte b6, byte b7, byte b8) {
      return (b1 & 255L) << 56
         | (b2 & 255L) << 48
         | (b3 & 255L) << 40
         | (b4 & 255L) << 32
         | (b5 & 255L) << 24
         | (b6 & 255L) << 16
         | (b7 & 255L) << 8
         | b8 & 255L;
   }

   static long mixStafford13(long value) {
      value = (value ^ value >>> 30) * -4658895280553007687L;
      value = (value ^ value >>> 27) * -7723592293110705685L;
      return value ^ value >>> 31;
   }
}
