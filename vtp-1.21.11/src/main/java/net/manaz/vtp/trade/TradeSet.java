package net.manaz.vtp.trade;

import com.google.gson.JsonElement;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * Client-side copy of the Minecraft 26.2 {@code trade_set} registry entry.
 * Minecraft 1.21.11 has no data-driven trades, so the definitions are loaded from the
 * 26.2 data bundled with the mod (see {@link TradeDataLoader}).
 */
public final class TradeSet {
   public static final ResourceKey<Registry<TradeSet>> REGISTRY = ResourceKey.createRegistryKey(Identifier.withDefaultNamespace("trade_set"));
   private final Identifier id;
   private final List<VillagerTrade> trades;
   private final JsonElement amount;
   private final boolean allowDuplicates;
   private final Optional<Identifier> randomSequence;

   public TradeSet(Identifier id, List<VillagerTrade> trades, JsonElement amount, boolean allowDuplicates, Optional<Identifier> randomSequence) {
      this.id = id;
      this.trades = List.copyOf(trades);
      this.amount = amount;
      this.allowDuplicates = allowDuplicates;
      this.randomSequence = randomSequence;
   }

   public Identifier id() {
      return this.id;
   }

   public List<VillagerTrade> trades() {
      return this.trades;
   }

   public JsonElement amountProvider() {
      return this.amount;
   }

   public boolean hasConstantAmount() {
      return Numbers.isConstant(this.amount);
   }

   public int calculateNumberOfTrades() {
      return Numbers.constantInt(this.amount);
   }

   public boolean allowDuplicates() {
      return this.allowDuplicates;
   }

   public Optional<Identifier> randomSequence() {
      return this.randomSequence;
   }

   @Override
   public String toString() {
      return "TradeSet[" + this.id + "]";
   }
}
