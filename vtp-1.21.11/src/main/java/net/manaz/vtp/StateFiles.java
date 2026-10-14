package net.manaz.vtp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class StateFiles {
   private StateFiles() {
   }

   public static String filename(String identity, long seed) {
      try {
         byte[] digest = MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8));
         return HexFormat.of().formatHex(digest, 0, 12) + "-" + seed + ".json";
      } catch (NoSuchAlgorithmException var4) {
         throw new IllegalStateException(var4);
      }
   }

   public static void writeAtomic(Path file, String content) throws IOException {
      Files.createDirectories(file.getParent());
      Path temp = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".tmp");

      try {
         Files.writeString(temp, content, StandardCharsets.UTF_8);

         try {
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
         } catch (AtomicMoveNotSupportedException var7) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
         }
      } finally {
         Files.deleteIfExists(temp);
      }
   }
}
