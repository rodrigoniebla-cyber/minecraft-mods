package net.manaz.vtp.prediction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.manaz.vtp.VtpPersistence;
import net.manaz.vtp.render.VillagerTracker;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;

public final class CalibrationListStore {
   private static final List<CalibrationListStore.Observation> observations = new ArrayList<>();
   private static String binding;

   private static String currentBinding() {
      return VtpPersistence.epoch()
         + ":"
         + VillagerTracker.getLastVillagerUuid()
         + ":"
         + VillagerTracker.getLastVillagerData().map(Object::toString).orElse("");
   }

   private CalibrationListStore() {
   }

   public static List<CalibrationListStore.Observation> getAll() {
      return Collections.unmodifiableList(observations);
   }

   public static int size() {
      return observations.size();
   }

   public static void add(CalibrationListStore.Observation obs) {
      String current = currentBinding();
      if (!current.equals(binding)) {
         observations.clear();
         binding = current;
      }

      observations.add(obs);
      CalibrationService.clear();
   }

   public static void remove(int index) {
      if (index >= 0 && index < observations.size()) {
         observations.remove(index);
      }
   }

   public static void clear() {
      binding = null;
      CalibrationService.clear();
      observations.clear();
   }

   public static void replaceAll(List<CalibrationListStore.Observation> newObs) {
      observations.clear();
      observations.addAll(newObs);
   }

   public static List<PredictionEngine.CalibrationSpec> toSpecs() {
      if (!currentBinding().equals(binding)) {
         return List.of();
      } else {
         List<PredictionEngine.CalibrationSpec> specs = new ArrayList<>(observations.size());

         for (CalibrationListStore.Observation obs : observations) {
            if (obs.noTrade()) {
               specs.add(PredictionEngine.CalibrationSpec.missing());
            } else {
               specs.add(PredictionEngine.CalibrationSpec.of(obs.enchantment(), obs.enchantmentLevel(), obs.price()));
            }
         }

         return specs;
      }
   }

   public record Observation(ResourceKey<Enchantment> enchantment, int enchantmentLevel, int price, boolean noTrade) {
      public static CalibrationListStore.Observation ofTrade(ResourceKey<Enchantment> ench, int level, int price) {
         return new CalibrationListStore.Observation(ench, level, price, false);
      }

      public static CalibrationListStore.Observation missing() {
         return new CalibrationListStore.Observation(null, 0, -1, true);
      }
   }
}
