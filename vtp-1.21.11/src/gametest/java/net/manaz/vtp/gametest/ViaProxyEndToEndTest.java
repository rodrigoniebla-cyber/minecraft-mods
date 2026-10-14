package net.manaz.vtp.gametest;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.manaz.vtp.gui.GuiAccess;
import net.manaz.vtp.gui.VtpSettingsScreen;
import net.manaz.vtp.prediction.EnchantmentTarget;
import net.manaz.vtp.prediction.OfferMatching;
import net.manaz.vtp.prediction.PredictionEngine;
import net.manaz.vtp.render.HudView;
import net.manaz.vtp.render.VillagerHudRenderer;
import net.manaz.vtp.render.VillagerTracker;
import net.manaz.vtp.seed.SeedProvider;
import net.manaz.vtp.trade.ObservedOffer;
import net.manaz.vtp.trade.TradeSimulator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.inventory.MerchantMenu;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Joins a vanilla 26.2 server through ViaProxy with this 1.21.11 client and checks the mod against
 * real server behaviour: observed trades must be recognised at the right sequence round, and the
 * forecast round for a target enchantment must really produce that enchantment on the server.
 * Skipped unless {@code -Dvtp.e2e.address} and {@code -Dvtp.e2e.seed} are set.
 */
public class ViaProxyEndToEndTest implements FabricClientGameTest {
   private static final Logger LOGGER = LoggerFactory.getLogger("vtp-e2e");
   private static final String SPOT = "0.5 -60 0.5";

   @Override
   public void runTest(ClientGameTestContext context) {
      String address = System.getProperty("vtp.e2e.address");
      String seedProperty = System.getProperty("vtp.e2e.seed");
      if (address == null || seedProperty == null) {
         LOGGER.info("[VTP e2e] skipped: -Dvtp.e2e.address / -Dvtp.e2e.seed not set");
         return;
      }

      long seed = Long.parseLong(seedProperty);
      String[] rconTarget = System.getProperty("vtp.e2e.rcon", "127.0.0.1:25575").split(":");

      try (Rcon rcon = new Rcon(rconTarget[0], Integer.parseInt(rconTarget[1]), "test")) {
         run(context, rcon, address, seed);
      } catch (java.io.IOException e) {
         throw new AssertionError("RCON failed", e);
      }
   }

   private static void run(ClientGameTestContext context, Rcon rcon, String address, long seed) throws java.io.IOException {
      context.runOnClient(
         mc -> ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(address), new ServerData("e2e", address, ServerData.Type.OTHER), false, null)
      );
      context.waitFor(mc -> mc.player != null && mc.level != null && mc.screen == null, 20 * 60);
      context.waitTicks(40);
      String name = context.computeOnClient(mc -> mc.player.getGameProfile().name());
      LOGGER.info("[VTP e2e] joined {} as {}", address, name);
      rcon.command("kill @e[type=!minecraft:player]");
      rcon.command("forceload add 0 0");
      rcon.command("gamemode creative " + name);
      rcon.command("tp " + name + " 0.5 -60 -2.5 0 0");
      context.waitTicks(20);

      context.runOnClient(mc -> mc.player.connection.sendCommand("vtp seed " + seed));
      context.runOnClient(mc -> mc.player.connection.sendCommand("vtp target mending 1"));
      context.waitTicks(10);
      check(SeedProvider.getSeed().orElse(Long.MIN_VALUE) == seed, "client command /vtp seed did not apply");
      check(!VillagerHudRenderer.getTargets().isEmpty(), "client command /vtp target did not apply");

      // Villager A: first observation on a fresh world must be recognised as round 0.
      Villager a = summonAndOpen(context, rcon, "A");
      List<ObservedOffer> offersA = currentMenuOffers(context);
      Holder<VillagerProfession> profession = a.getVillagerData().profession();
      assertRound(context, a, offersA, seed, profession, 0);
      context.takeScreenshot("vtp-merchant-A");
      closeMenu(context);

      // Villager B: the next server roll must be round 1, recognised as the expected continuation.
      rcon.command("kill @e[type=minecraft:villager]");
      Villager b = summonAndOpen(context, rcon, "B");
      List<ObservedOffer> offersB = currentMenuOffers(context);
      assertRound(context, b, offersB, seed, profession, 1);
      String status = context.computeOnClient(mc -> VillagerTracker.status(b.getUUID(), b.getVillagerData()));
      LOGGER.info("[VTP e2e] status after B: {}", status);
      check(status.startsWith("Observed match"), "unexpected sync status: " + status);
      context.takeScreenshot("vtp-merchant-B");
      closeMenu(context);
      context.waitTicks(40);
      HudView view = context.computeOnClient(mc -> HudView.current());
      LOGGER.info("[VTP e2e] HUD: {}", view);
      context.takeScreenshot("vtp-hud-B");

      // Forecast: the mod's first round with Mending, then make the server roll up to it.
      int next = context.computeOnClient(mc -> VillagerTracker.getNextRoundForLevel(profession, 1));
      check(next == 2, "next round should be 2 but was " + next);
      EnchantmentTarget target = VillagerHudRenderer.getTargets().getFirst();
      int targetRound = context.computeOnClient(mc -> PredictionEngine.predictFrom(seed, profession, 1, target, next).rerollsNeeded().orElse(-1));
      check(targetRound >= next, "no mending forecast within the reroll limit");
      LOGGER.info("[VTP e2e] mod forecasts mending at round {} ({} rerolls from now)", targetRound, targetRound - next);
      if (view != null) {
         check(view.found(), "HUD did not find the mending target: " + view);
      }

      for (int round = next; round < targetRound; round++) {
         rcon.command("kill @e[type=!minecraft:player]");
         rcon.command("summon minecraft:villager " + SPOT + " {NoAI:1b,Tags:[\"r\"],VillagerData:{profession:\"minecraft:librarian\",level:1,type:\"minecraft:plains\"}}");
         rcon.command("summon minecraft:zombie 2.5 -60 0.5 {NoAI:1b,Tags:[\"z\"]}");
         String damage = rcon.command("damage @e[tag=r,limit=1] 1000 minecraft:mob_attack by @e[tag=z,limit=1]");
         check(damage.startsWith("Applied"), "server reroll failed: " + damage);
      }

      rcon.command("kill @e[type=!minecraft:player]");
      Villager z = summonAndOpen(context, rcon, "Target");
      List<ObservedOffer> offersZ = currentMenuOffers(context);
      LOGGER.info("[VTP e2e] server offers at forecast round {}: {}", targetRound, offersZ);
      check(offersZ.stream().anyMatch(o -> o.books().stream().anyMatch(book -> book.key().equals("minecraft:mending"))), "forecast round did not sell mending: " + offersZ);
      assertRound(context, z, offersZ, seed, profession, targetRound);
      context.takeScreenshot("vtp-merchant-mending");
      closeMenu(context);

      // Every settings tab and sub-screen must open and render against live server data.
      String[] tabs = {"settings", "targets", "sequence", "trades", "calibrate"};

      for (int tab = 0; tab < tabs.length; tab++) {
         int index = tab;
         context.runOnClient(mc -> mc.setScreen(GuiAccess.settingsOnTab(index)));
         context.waitTicks(10);
         context.takeScreenshot("vtp-settings-" + tabs[tab]);
      }

      context.runOnClient(mc -> mc.setScreen(GuiAccess.hudSettings(new VtpSettingsScreen())));
      context.waitTicks(10);
      context.takeScreenshot("vtp-hud-settings");
      context.runOnClient(mc -> mc.setScreen(GuiAccess.enchantmentPicker(new VtpSettingsScreen())));
      context.waitTicks(10);
      context.takeScreenshot("vtp-enchantment-picker");
      context.runOnClient(mc -> mc.setScreen(GuiAccess.settingsOnTab(0)));
      context.runOnClient(mc -> mc.setScreen(null));
      context.runOnClient(mc -> mc.disconnect(new TitleScreen(), false));
      context.waitForScreen(TitleScreen.class);
      LOGGER.info("[VTP e2e] PASSED");
   }

   private static Villager summonAndOpen(ClientGameTestContext context, Rcon rcon, String name) throws java.io.IOException {
      String summoned = rcon.command(
         "summon minecraft:villager " + SPOT + " {NoAI:1b,CustomName:\"" + name + "\",VillagerData:{profession:\"minecraft:librarian\",level:1,type:\"minecraft:plains\"}}"
      );
      check(summoned.startsWith("Summoned"), "summon failed: " + summoned);
      try {
         context.waitFor(mc -> find(mc, name).filter(v -> v.getVillagerData().profession().is(VillagerProfession.LIBRARIAN)).isPresent(), 200);
      } catch (AssertionError e) {
         context.runOnClient(mc -> {
            for (Entity entity : mc.level.entitiesForRendering()) {
               LOGGER.error("[VTP e2e] client entity {} name={} pos={} data={}", entity.getType(), entity.getCustomName(), entity.position(), entity instanceof Villager v ? v.getVillagerData() : null);
            }
         });
         throw e;
      }
      context.waitTicks(10);
      Villager villager = context.computeOnClient(mc -> find(mc, name).orElseThrow());
      // Right-click exactly like a player does, so the real 1.21.11 interact packets go through ViaProxy.
      context.waitFor(mc -> mc.crosshairPickEntity == villager, 100);
      context.getInput().pressKey(options -> options.keyUse);
      context.waitForScreen(MerchantScreen.class);
      context.waitFor(mc -> mc.player.containerMenu instanceof MerchantMenu menu && !menu.getOffers().isEmpty(), 100);
      context.waitTicks(10);
      return villager;
   }

   private static Optional<Villager> find(Minecraft mc, String name) {
      for (Entity entity : mc.level.entitiesForRendering()) {
         if (entity instanceof Villager villager && villager.getCustomName() != null && villager.getCustomName().getString().equals(name)) {
            return Optional.of(villager);
         }
      }

      return Optional.empty();
   }

   private static List<ObservedOffer> currentMenuOffers(ClientGameTestContext context) {
      return context.computeOnClient(mc -> ObservedOffer.copyOf(((MerchantMenu)mc.player.containerMenu).getOffers()));
   }

   private static void assertRound(
      ClientGameTestContext context, Villager villager, List<ObservedOffer> observed, long seed, Holder<VillagerProfession> profession, int round
   ) {
      List<ObservedOffer> simulated = context.computeOnClient(mc -> TradeSimulator.fingerprint(PredictionEngine.simulateRound(seed, profession, 1, round)));
      LOGGER.info("[VTP e2e] round {} observed={} simulated={}", round, observed, simulated);
      check(OfferMatching.same(observed, simulated), "round " + round + " mismatch: observed " + observed + " simulated " + simulated);
      OptionalInt tracked = context.computeOnClient(mc -> VillagerTracker.getCurrentRound(villager.getUUID()));
      check(tracked.isPresent() && tracked.getAsInt() == round, "tracker placed villager at " + tracked + ", expected " + round);
   }

   private static void closeMenu(ClientGameTestContext context) {
      context.runOnClient(mc -> mc.player.closeContainer());
      context.waitForScreen(null);
   }

   private static void check(boolean condition, String message) {
      if (!condition) {
         throw new AssertionError("[VTP e2e] " + message);
      }
   }
}
