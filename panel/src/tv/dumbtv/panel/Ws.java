package tv.dumbtv.panel;

import android.util.Base64;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import javax.net.ssl.SSLSocketFactory;

/**
 * The little of RFC 6455 a Home Assistant client needs: one text frame at a time, client-side
 * masking, and an answer to the server's pings. No library, so the APK stays small.
 */
final class Ws implements Closeable {
    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;
    private final SecureRandom random = new SecureRandom();

    private Ws(Socket socket) throws IOException {
        this.socket = socket;
        in = new BufferedInputStream(socket.getInputStream());
        out = socket.getOutputStream();
    }

    static Ws open(String url, int timeoutMs) throws IOException {
        URI u = URI.create(url);
        boolean tls = "wss".equals(u.getScheme()) || "https".equals(u.getScheme());
        int port = u.getPort() > 0 ? u.getPort() : tls ? 443 : 80;
        Socket s = tls ? SSLSocketFactory.getDefault().createSocket(u.getHost(), port) : new Socket(u.getHost(), port);
        s.setSoTimeout(timeoutMs);
        Ws ws = new Ws(s);
        ws.handshake(u, port);
        return ws;
    }

    private void handshake(URI u, int port) throws IOException {
        byte[] nonce = new byte[16];
        random.nextBytes(nonce);
        String path = u.getRawPath() == null || u.getRawPath().isEmpty() ? "/" : u.getRawPath();
        String req = "GET " + path + " HTTP/1.1\r\n"
                + "Host: " + u.getHost() + ":" + port + "\r\n"
                + "Upgrade: websocket\r\nConnection: Upgrade\r\n"
                + "Sec-WebSocket-Key: " + Base64.encodeToString(nonce, Base64.NO_WRAP) + "\r\n"
                + "Sec-WebSocket-Version: 13\r\n\r\n";
        out.write(req.getBytes(StandardCharsets.US_ASCII));
        out.flush();
        String status = line();
        if (!status.contains(" 101 ")) throw new IOException("websocket refused: " + status);
        while (!line().isEmpty()) {
            // headers are not needed
        }
    }

    private String line() throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        for (int c; (c = in.read()) != '\n'; ) {
            if (c < 0) throw new EOFException();
            if (c != '\r') b.write(c);
        }
        return b.toString("US-ASCII");
    }

    void setTimeout(int ms) throws IOException {
        socket.setSoTimeout(ms);
    }

    void send(String text) throws IOException {
        frame(0x1, text.getBytes(StandardCharsets.UTF_8));
    }

    private synchronized void frame(int opcode, byte[] payload) throws IOException {
        ByteArrayOutputStream f = new ByteArrayOutputStream(payload.length + 14);
        f.write(0x80 | opcode);
        int n = payload.length;
        if (n < 126) {
            f.write(0x80 | n);
        } else if (n < 65536) {
            f.write(0x80 | 126);
            f.write(n >> 8);
            f.write(n);
        } else {
            f.write(0x80 | 127);
            for (int i = 7; i >= 0; i--) f.write((int) (((long) n) >> (8 * i)));
        }
        byte[] mask = new byte[4];
        random.nextBytes(mask);
        f.write(mask);
        for (int i = 0; i < n; i++) f.write(payload[i] ^ mask[i & 3]);
        out.write(f.toByteArray());
        out.flush();
    }

    /** The next text message; pings are answered and control frames skipped on the way. */
    String text() throws IOException {
        ByteArrayOutputStream message = new ByteArrayOutputStream();
        while (true) {
            int b0 = read(), b1 = read();
            boolean fin = (b0 & 0x80) != 0;
            int opcode = b0 & 0x0F;
            long len = b1 & 0x7F;
            if (len == 126) len = (read() << 8) | read();
            else if (len == 127) {
                len = 0;
                for (int i = 0; i < 8; i++) len = (len << 8) | read();
            }
            byte[] mask = null;
            if ((b1 & 0x80) != 0) {
                mask = new byte[4];
                fill(mask);
            }
            if (len > 16 * 1024 * 1024) throw new IOException("frame too large");
            byte[] payload = new byte[(int) len];
            fill(payload);
            if (mask != null) for (int i = 0; i < payload.length; i++) payload[i] ^= mask[i & 3];
            switch (opcode) {
                case 0x8:
                    throw new EOFException("closed by server");
                case 0x9:
                    frame(0xA, payload);
                    continue;
                case 0xA:
                    continue;
                default:
                    message.write(payload, 0, payload.length);
                    if (fin) return message.toString("UTF-8");
            }
        }
    }

    private int read() throws IOException {
        int c = in.read();
        if (c < 0) throw new EOFException();
        return c;
    }

    private void fill(byte[] buf) throws IOException {
        int at = 0;
        while (at < buf.length) {
            int n = in.read(buf, at, buf.length - at);
            if (n < 0) throw new EOFException();
            at += n;
        }
    }

    @Override
    public void close() {
        try {
            frame(0x8, new byte[0]);
        } catch (IOException ignored) {
            // closing anyway
        }
        try {
            socket.close();
        } catch (IOException ignored) {
            // closing anyway
        }
    }
}
