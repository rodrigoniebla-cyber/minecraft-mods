package net.manaz.vtp.render;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;

public final class HudVisibility {
   private static HudVisibility.Mode mode = HudVisibility.Mode.NEVER;
   private static int seconds = 10;
   private static int range = 16;
   private static Object world;
   private static UUID selected;
   private static Villager entity;
   private static long lastAttention;
   private static boolean looking;
   private static double distanceSquared = Double.POSITIVE_INFINITY;

   private HudVisibility() {
   }

   public static HudVisibility.Mode mode() {
      return mode;
   }

   public static int seconds() {
      return seconds;
   }

   public static int range() {
      return range;
   }

   public static void configure(HudVisibility.Mode value, int timeoutSeconds, int rangeBlocks) {
      mode = value == null ? HudVisibility.Mode.NEVER : value;
      seconds = Math.clamp((long)timeoutSeconds, 1, 3600);
      range = Math.clamp((long)rangeBlocks, 1, 256);
   }

   public static void reset() {
      world = null;
      selected = null;
      entity = null;
      lastAttention = 0L;
      looking = false;
      distanceSquared = Double.POSITIVE_INFINITY;
   }

   public static void tick(Minecraft mc) {
      UUID uuid = VillagerTracker.getLastVillagerUuid();
      if (mc.level != null && mc.player != null && uuid != null) {
         long now = System.nanoTime();
         if (world != mc.level || !Objects.equals(selected, uuid)) {
            reset();
            world = mc.level;
            selected = uuid;
            lastAttention = now;
         }

         looking = mc.screen == null && mc.crosshairPickEntity instanceof Villager villager && villager.isAlive() && uuid.equals(villager.getUUID());
         if (looking) {
            entity = (Villager)mc.crosshairPickEntity;
         }

         boolean trading = mc.screen instanceof MerchantScreen && uuid.equals(VillagerTracker.getMenuVillagerUuid());
         if (looking || trading) {
            lastAttention = now;
         }

         if (entity != null && (entity.isRemoved() || !entity.isAlive())) {
            entity = null;
         }

         if (mode == HudVisibility.Mode.RANGE && entity == null) {
            for (Entity candidate : mc.level.entitiesForRendering()) {
               if (candidate instanceof Villager villagerx && villagerx.isAlive() && uuid.equals(villagerx.getUUID())) {
                  entity = villagerx;
                  break;
               }
            }
         }

         distanceSquared = entity == null ? Double.POSITIVE_INFINITY : mc.player.distanceToSqr(entity);
      } else {
         reset();
      }
   }

   public static boolean visible(UUID uuid) {
      return uuid != null && uuid.equals(selected) && permits(mode, looking, System.nanoTime() - lastAttention, distanceSquared, seconds, range);
   }

   public static boolean permits(HudVisibility.Mode mode, boolean looking, long elapsedNanos, double distanceSquared, int seconds, int range) {
      return switch (mode) {
         case NEVER -> true;
         case TIME -> looking || Math.max(0L, elapsedNanos) < seconds * 1000000000L;
         case RANGE -> Double.isFinite(distanceSquared) && distanceSquared <= (double)range * range;
         case LOOKING -> looking;
      };
   }

   public static enum Mode {
      NEVER("Never hide"),
      TIME("After time"),
      RANGE("Out of range"),
      LOOKING("Looking only");

      public final String label;

      private Mode(String label) {
         this.label = label;
      }

      public static HudVisibility.Mode parse(String name) {
         for (HudVisibility.Mode mode : values()) {
            if (mode.name().equalsIgnoreCase(name)) {
               return mode;
            }
         }

         return NEVER;
      }
   }
}
