package net.manaz.vtp.mixin;

import net.manaz.vtp.render.VillagerTracker;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({MultiPlayerGameMode.class})
public class MultiPlayerGameModeMixin {
   @Inject(
      method = {"interact(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;"},
      at = {@At("HEAD")}
   )
   private void vtp$selectInteractedVillager(Player player, Entity entity, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
      if (entity instanceof Villager villager) {
         VillagerTracker.beginMerchantInteraction(villager.getUUID(), villager.getVillagerData());
      } else {
         VillagerTracker.clearMerchantInteraction();
      }
   }
}
