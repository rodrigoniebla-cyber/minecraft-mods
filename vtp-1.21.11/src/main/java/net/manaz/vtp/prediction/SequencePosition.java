package net.manaz.vtp.prediction;

public final class SequencePosition {
   private SequencePosition() {
   }

   public static int remaining(int targetRound, int nextRound, boolean offersPending) {
      if (targetRound >= nextRound && nextRound >= 0) {
         return targetRound - nextRound + (offersPending ? 0 : 1);
      } else {
         throw new IllegalArgumentException("Target must be in the unconsumed future");
      }
   }
}
