package net.manaz.vtp.autoreroll;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.UUID;
import net.manaz.vtp.prediction.EnchantmentTarget;
import net.manaz.vtp.prediction.PredictionEngine;
import net.manaz.vtp.prediction.PredictionResult;
import net.manaz.vtp.render.VillagerHudRenderer;
import net.manaz.vtp.render.VillagerTracker;
import net.manaz.vtp.seed.SeedProvider;
import net.manaz.vtp.trade.TradeDataLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.manaz.vtp.trade.TradeSet;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

public class AutoRerollManager {
   public static final int SLOT_OFFHAND = -1;
   private static final int MAX_REACH_SQ = 20;
   private static final int PHASE_TIMEOUT = 80;
   private static final int BREAK_TIMEOUT = 400;
   private static boolean useTargetCount = true;
   private static int manualCount = 16;
   private static int safetyRerolls = 0;
   private static int toolSlot = 0;
   private static int workstationSlot = 1;
   private static int cycleDelayTicks = 20;
   private static AutoRerollManager.Phase phase = AutoRerollManager.Phase.IDLE;
   private static BlockPos targetPos = null;
   private static Direction face = null;
   private static int plannedRerolls = 0;
   private static int completedRerolls = 0;
   private static int phaseTicks = 0;
   private static int previousSlot = -1;
   private static boolean destroyStarted = false;
   private static String lastStatus = "Idle";

   public static boolean isUseTargetCount() {
      return useTargetCount;
   }

   public static void setUseTargetCount(boolean v) {
      useTargetCount = v;
   }

   public static int getManualCount() {
      return manualCount;
   }

   public static void setManualCount(int v) {
      manualCount = Math.max(0, v);
   }

   public static int getSafetyRerolls() {
      return safetyRerolls;
   }

   public static void setSafetyRerolls(int v) {
      safetyRerolls = Math.max(0, v);
   }

   public static int getToolSlot() {
      return toolSlot;
   }

   public static void setToolSlot(int v) {
      toolSlot = clampHotbar(v);
   }

   public static int getWorkstationSlot() {
      return workstationSlot;
   }

   public static void setWorkstationSlot(int v) {
      workstationSlot = v == -1 ? -1 : clampHotbar(v);
   }

   public static int getCycleDelayTicks() {
      return cycleDelayTicks;
   }

   public static void setCycleDelayTicks(int v) {
      cycleDelayTicks = Math.max(0, v);
   }

   public static boolean isRunning() {
      return phase != AutoRerollManager.Phase.IDLE;
   }

   public static int getPlannedRerolls() {
      return plannedRerolls;
   }

   public static int getCompletedRerolls() {
      return completedRerolls;
   }

   public static String getStatus() {
      return lastStatus;
   }

   private static int clampHotbar(int v) {
      return Math.max(0, Math.min(8, v));
   }

   public static void toggle() {
      if (isRunning()) {
         stop("Auto-reroll stopped.");
      } else {
         start();
      }
   }

   public static void stop(String message) {
      Minecraft mc = Minecraft.getInstance();
      if (phase == AutoRerollManager.Phase.BREAK && mc.gameMode != null) {
         mc.gameMode.stopDestroyBlock();
      }

      if (mc.player != null && previousSlot >= 0 && previousSlot <= 8 && mc.player.getInventory().getSelectedSlot() != previousSlot) {
         mc.player.getInventory().setSelectedSlot(previousSlot);
         mc.player.connection.send(new ServerboundSetCarriedItemPacket(previousSlot));
      }

      phase = AutoRerollManager.Phase.IDLE;
      targetPos = null;
      face = null;
      previousSlot = -1;
      destroyStarted = false;
      if (message != null) {
         lastStatus = message;
         if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("[VTP] " + message), false);
         }
      } else {
         lastStatus = "Idle";
      }
   }

   private static void start() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && mc.level != null && mc.gameMode != null) {
         if (mc.hitResult instanceof BlockHitResult bh && bh.getType() == Type.BLOCK) {
            BlockState state = mc.level.getBlockState(bh.getBlockPos());
            if (state.isAir()) {
               abort(mc, "No block at crosshair.");
            } else {
               Inventory inv = mc.player.getInventory();
               ItemStack toolStack = inv.getItem(toolSlot);
               if (toolStack.isEmpty()) {
                  abort(mc, "Tool slot " + (toolSlot + 1) + " is empty.");
               } else {
                  ItemStack wsStack = workstationSlot == -1 ? mc.player.getOffhandItem() : inv.getItem(workstationSlot);
                  if (wsStack.isEmpty()) {
                     abort(mc, "Workstation slot is empty.");
                  } else {
                     int rerollsTilTarget;
                     if (useTargetCount) {
                        OptionalInt opt = computeRerollsUntilTarget();
                        if (opt.isEmpty()) {
                           abort(mc, "Cannot compute rerolls — need seed, target, and a tracked villager.");
                           return;
                        }

                        rerollsTilTarget = opt.getAsInt();
                     } else {
                        rerollsTilTarget = manualCount;
                     }

                     int planned = Math.max(0, rerollsTilTarget - safetyRerolls);
                     if (planned <= 0) {
                        abort(mc, "Nothing to do: " + rerollsTilTarget + " - " + safetyRerolls + " safety = " + planned + ".");
                     } else {
                        targetPos = bh.getBlockPos();
                        face = bh.getDirection();
                        plannedRerolls = planned;
                        completedRerolls = 0;
                        previousSlot = inv.getSelectedSlot();
                        phase = AutoRerollManager.Phase.SWITCH_TO_TOOL;
                        phaseTicks = 0;
                        lastStatus = "Running";
                        say(mc, "Auto-reroll started: " + planned + " rerolls (" + rerollsTilTarget + " to target, " + safetyRerolls + " safety).");
                     }
                  }
               }
            }
         } else {
            abort(mc, "Look at a workstation first.");
         }
      }
   }

   public static void tick() {
      if (phase != AutoRerollManager.Phase.IDLE) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.player != null && mc.level != null && mc.gameMode != null && targetPos != null) {
            if (mc.player.distanceToSqr(Vec3.atCenterOf(targetPos)) > 20.0) {
               stop("Stopped: out of reach.");
            } else {
               phaseTicks++;

               int timeout = switch (phase) {
                  case BREAK -> 400;
                  case WAIT_CYCLE -> Integer.MAX_VALUE;
                  default -> 80;
               };
               if (phaseTicks > timeout) {
                  stop("Timed out in phase " + phase + ".");
               } else {
                  switch (phase) {
                     case SWITCH_TO_TOOL:
                        selectSlot(mc, toolSlot);
                        phase = AutoRerollManager.Phase.BREAK;
                        phaseTicks = 0;
                        destroyStarted = false;
                        break;
                     case BREAK:
                        BlockState here = mc.level.getBlockState(targetPos);
                        if (here.isAir()) {
                           if (destroyStarted) {
                              mc.gameMode.stopDestroyBlock();
                           }

                           destroyStarted = false;
                           phase = AutoRerollManager.Phase.SWITCH_TO_WS;
                           phaseTicks = 0;
                           return;
                        }

                        if (!destroyStarted) {
                           mc.gameMode.startDestroyBlock(targetPos, face);
                           destroyStarted = true;
                        } else {
                           mc.gameMode.continueDestroyBlock(targetPos, face);
                        }
                        break;
                     case SWITCH_TO_WS:
                        if (workstationSlot != -1) {
                           selectSlot(mc, workstationSlot);
                        }

                        phase = AutoRerollManager.Phase.PLACE;
                        phaseTicks = 0;
                        break;
                     case PLACE:
                        BlockPos placeAgainst = targetPos.relative(face.getOpposite());
                        Direction placeDir = face;
                        if (mc.level.getBlockState(placeAgainst).isAir()) {
                           placeAgainst = targetPos.below();
                           placeDir = Direction.UP;
                           if (mc.level.getBlockState(placeAgainst).isAir()) {
                              stop("Stopped: no solid block to place against.");
                              return;
                           }
                        }

                        InteractionHand hand = workstationSlot == -1 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
                        Vec3 hitVec = new Vec3(
                           placeAgainst.getX() + 0.5 + placeDir.getStepX() * 0.5,
                           placeAgainst.getY() + 0.5 + placeDir.getStepY() * 0.5,
                           placeAgainst.getZ() + 0.5 + placeDir.getStepZ() * 0.5
                        );
                        BlockHitResult placeHit = new BlockHitResult(hitVec, placeDir, placeAgainst, false);
                        mc.gameMode.useItemOn(mc.player, hand, placeHit);
                        mc.player.swing(hand);
                        phase = AutoRerollManager.Phase.WAIT_PLACE;
                        phaseTicks = 0;
                        break;
                     case WAIT_PLACE:
                        if (!mc.level.getBlockState(targetPos).isAir()) {
                           completedRerolls++;
                           if (completedRerolls >= plannedRerolls) {
                              stop("Done: " + completedRerolls + "/" + plannedRerolls + " rerolls.");
                           } else {
                              phase = cycleDelayTicks > 0 ? AutoRerollManager.Phase.WAIT_CYCLE : AutoRerollManager.Phase.SWITCH_TO_TOOL;
                              phaseTicks = 0;
                           }
                        }
                        break;
                     case WAIT_CYCLE:
                        if (phaseTicks >= cycleDelayTicks) {
                           phase = AutoRerollManager.Phase.SWITCH_TO_TOOL;
                           phaseTicks = 0;
                        }
                  }
               }
            }
         } else {
            stop(null);
         }
      }
   }

   private static void selectSlot(Minecraft mc, int slot) {
      Inventory inv = mc.player.getInventory();
      if (inv.getSelectedSlot() != slot) {
         inv.setSelectedSlot(slot);
         mc.player.connection.send(new ServerboundSetCarriedItemPacket(slot));
      }
   }

   private static OptionalInt computeRerollsUntilTarget() {
      OptionalLong seedOpt = SeedProvider.getSeed();
      Optional<VillagerData> dataOpt = VillagerTracker.getLastVillagerData();
      if (!seedOpt.isEmpty() && !dataOpt.isEmpty()) {
         List<EnchantmentTarget> tgts = VillagerHudRenderer.getTargets();
         if (tgts.isEmpty()) {
            return OptionalInt.empty();
         } else {
            VillagerData data = dataOpt.get();
            Holder<VillagerProfession> profession = data.profession();
            Optional<ResourceKey<VillagerProfession>> profKey = profession.unwrapKey();
            if (!profKey.isEmpty() && !profKey.get().equals(VillagerProfession.NONE) && !profKey.get().equals(VillagerProfession.NITWIT)) {
               long worldSeed = seedOpt.getAsLong();
               ResourceKey<TradeSet> tsKey = TradeDataLoader.getTradeSetKey(profession, data.level());
               int typeOffset = tsKey != null ? PredictionEngine.getOffset(tsKey) : 0;
               int tradeSetTotal = VillagerTracker.getTradeSetTotal(data);
               UUID uuid = VillagerTracker.getLastVillagerUuid();
               int nowRound = uuid != null ? VillagerTracker.getCurrentRound(uuid).orElse(typeOffset + tradeSetTotal) : typeOffset + tradeSetTotal;
               PredictionResult best = null;

               for (EnchantmentTarget t : tgts) {
                  PredictionResult r = PredictionEngine.predictFrom(worldSeed, profession, data.level(), t, nowRound);
                  if (r.found() && (best == null || r.rerollsNeeded().get() < best.rerollsNeeded().get())) {
                     best = r;
                  }
               }

               return best == null ? OptionalInt.empty() : OptionalInt.of(Math.max(0, best.rerollsNeeded().get() - nowRound));
            } else {
               return OptionalInt.empty();
            }
         }
      } else {
         return OptionalInt.empty();
      }
   }

   private static void abort(Minecraft mc, String msg) {
      lastStatus = msg;
      say(mc, msg);
   }

   private static void say(Minecraft mc, String msg) {
      if (mc.player != null) {
         mc.player.displayClientMessage(Component.literal("[VTP] " + msg), false);
      }
   }

   private static enum Phase {
      IDLE,
      SWITCH_TO_TOOL,
      BREAK,
      SWITCH_TO_WS,
      PLACE,
      WAIT_PLACE,
      WAIT_CYCLE;
   }
}
