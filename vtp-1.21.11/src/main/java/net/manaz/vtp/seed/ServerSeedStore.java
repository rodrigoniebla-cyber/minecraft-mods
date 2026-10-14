package net.manaz.vtp.seed;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Map.Entry;
import net.fabricmc.loader.api.FabricLoader;
import net.manaz.vtp.VillagerTradingPredictor;

public class ServerSeedStore {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("vtp").resolve("servers.json");

   public static void saveSeedForServer(String address, long seed) {
      Map<String, Long> map = loadMap();
      map.put(normalize(address), seed);
      writeMap(map);
      VillagerTradingPredictor.LOGGER.info("[VTP] Saved seed {} for server {}", seed, address);
   }

   public static OptionalLong getSeedForServer(String address) {
      Long seed = loadMap().get(normalize(address));
      return seed != null ? OptionalLong.of(seed) : OptionalLong.empty();
   }

   private static String normalize(String address) {
      return address.toLowerCase().trim();
   }

   private static Map<String, Long> loadMap() {
      if (!Files.exists(FILE)) {
         return new LinkedHashMap<>();
      } else {
         try {
            Object var10;
            try (Reader reader = Files.newBufferedReader(FILE)) {
               JsonObject obj = (JsonObject)GSON.fromJson(reader, JsonObject.class);
               Map<String, Long> map = new LinkedHashMap<>();
               if (obj != null) {
                  for (Entry<String, JsonElement> e : obj.entrySet()) {
                     try {
                        map.put(e.getKey(), e.getValue().getAsLong());
                     } catch (Exception var7) {
                     }
                  }
               }

               var10 = map;
            }

            return (Map<String, Long>)var10;
         } catch (Exception var9) {
            VillagerTradingPredictor.LOGGER.error("[VTP] Failed to read servers.json", var9);
            return new LinkedHashMap<>();
         }
      }
   }

   private static void writeMap(Map<String, Long> map) {
      try {
         Files.createDirectories(FILE.getParent());
         JsonObject obj = new JsonObject();
         map.forEach(obj::addProperty);

         try (Writer writer = Files.newBufferedWriter(FILE)) {
            GSON.toJson(obj, writer);
         }
      } catch (Exception var7) {
         VillagerTradingPredictor.LOGGER.error("[VTP] Failed to write servers.json", var7);
      }
   }
}
