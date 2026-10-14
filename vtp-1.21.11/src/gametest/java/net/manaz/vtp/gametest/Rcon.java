package net.manaz.vtp.gametest;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/** Minimal Source RCON client used to drive the 26.2 test server. */
final class Rcon implements AutoCloseable {
   private final Socket socket;
   private final DataInputStream in;
   private final OutputStream out;
   private int id;

   Rcon(String host, int port, String password) throws IOException {
      this.socket = new Socket(host, port);
      this.in = new DataInputStream(this.socket.getInputStream());
      this.out = this.socket.getOutputStream();
      this.send(3, password);
      this.receive();
   }

   synchronized String command(String command) throws IOException {
      this.send(2, command);
      return this.receive();
   }

   private void send(int type, String body) throws IOException {
      byte[] payload = body.getBytes(StandardCharsets.UTF_8);
      ByteBuffer buffer = ByteBuffer.allocate(14 + payload.length).order(ByteOrder.LITTLE_ENDIAN);
      buffer.putInt(10 + payload.length).putInt(++this.id).putInt(type).put(payload).put((byte)0).put((byte)0);
      this.out.write(buffer.array());
      this.out.flush();
   }

   private String receive() throws IOException {
      byte[] header = new byte[4];
      this.in.readFully(header);
      int length = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).getInt();
      byte[] data = new byte[length];
      this.in.readFully(data);
      return new String(data, 8, length - 10, StandardCharsets.UTF_8);
   }

   @Override
   public void close() throws IOException {
      this.socket.close();
   }
}
