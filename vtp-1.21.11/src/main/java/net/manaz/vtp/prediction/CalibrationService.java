package net.manaz.vtp.prediction;

import java.util.List;
import java.util.UUID;
import net.manaz.vtp.VtpPersistence;
import net.manaz.vtp.render.VillagerTracker;
import net.manaz.vtp.seed.SeedProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.npc.villager.VillagerData;

public final class CalibrationService {
   private static CalibrationService.Binding binding;
   private static List<Integer> candidates = List.of();

   private CalibrationService() {
   }

   public static void clear() {
      binding = null;
      candidates = List.of();
   }

   public static String problem(VillagerData data) {
      if (Minecraft.getInstance().getSingleplayerServer() != null) {
         return "Singleplayer uses live server state; calibration is unnecessary.";
      } else {
         return !SeedProvider.hasSeed() ? "No seed set." : PredictionEngine.compatibilityProblem(data.profession(), data.level());
      }
   }

   public static List<Integer> search(UUID uuid, VillagerData data, List<PredictionEngine.CalibrationSpec> specs) {
      clear();
      if (uuid != null && problem(data).isEmpty()) {
         binding = new CalibrationService.Binding(
            VtpPersistence.epoch(), uuid, data, SeedProvider.getSeed().orElseThrow(), VillagerTracker.observationRevision(uuid)
         );
         candidates = PredictionEngine.findCalibrationSequence(binding.seed(), data, specs);
         return candidates;
      } else {
         return List.of();
      }
   }

   public static boolean confirm(UUID uuid, VillagerData data, int round) {
      if (binding != null
         && binding.equals(
            new CalibrationService.Binding(VtpPersistence.epoch(), uuid, data, SeedProvider.getSeed().orElse(0L), VillagerTracker.observationRevision(uuid))
         )
         && candidates.contains(round)) {
         PredictionEngine.applyCalibration(data.profession(), data.level(), round);
         VillagerTracker.resetAfterCalibrate(uuid, data, round);
         VtpPersistence.saveCurrentWorldState();
         clear();
         return true;
      } else {
         return false;
      }
   }

   private record Binding(long epoch, UUID uuid, VillagerData data, long seed, long revision) {
   }
}
