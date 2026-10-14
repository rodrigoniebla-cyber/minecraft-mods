package net.manaz.vtp.mixin;

import java.util.List;
import net.manaz.vtp.render.VillagerTracker;
import net.minecraft.network.syncher.SynchedEntityData.DataValue;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({Entity.class})
public class VillagerMixin {
   @Inject(
      method = {"onSyncedDataUpdated(Ljava/util/List;)V"},
      at = {@At("RETURN")}
   )
   private void vtp$onDataUpdated(List<DataValue<?>> values, CallbackInfo ci) {
      if ((Object)this instanceof Villager villager) {
         if (villager.level().isClientSide()) {
            for (DataValue<?> value : values) {
               if (value.value() instanceof VillagerData data) {
                  VillagerTracker.update(villager.getUUID(), data);
                  return;
               }
            }
         }
      }
   }
}
