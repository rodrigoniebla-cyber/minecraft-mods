package net.manaz.vtp.prediction;

import java.util.List;
import java.util.Optional;
import net.manaz.vtp.trade.TradeSimulator;

public record PredictionResult(
   Optional<Integer> rerollsNeeded,
   int maxRerollsSearched,
   List<TradeSimulator.SimulatedOffer> targetRollOffers,
   List<TradeSimulator.SimulatedOffer> firstRollOffers
) {
   public boolean found() {
      return this.rerollsNeeded.isPresent();
   }
}
