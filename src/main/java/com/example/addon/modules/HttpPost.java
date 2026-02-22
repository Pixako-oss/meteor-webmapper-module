package com.example.addon.modules;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

public class HttpPost {

    private static final String ENDPOINT = "https://map.6b6twiki.net/chunk";

    private static byte[] compress(String data) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(data.getBytes(StandardCharsets.UTF_8));
        }
        return out.toByteArray();
    }

    public static void send(String data, boolean retry, String token) throws IOException {
        byte[] compressed = compress(data);
        try {
            HttpURLConnection con = (HttpURLConnection)URI.create(ENDPOINT).toURL().openConnection();

            con.setRequestMethod("POST");
            con.setDoOutput(true);
            con.setRequestProperty("Content-Type", "text/plain");
            con.setRequestProperty("Content-Encoding", "gzip");
            con.setRequestProperty("x-version", "1");
            con.setRequestProperty("x-retry", String.valueOf(retry));
            con.setRequestProperty("x-token", token);

            try (OutputStream os = con.getOutputStream()) {
                os.write(compressed);
            }

            con.getResponseCode();
        } catch (IOException e) {
            if (!retry) send(data, true, token);
        }
    }
}
