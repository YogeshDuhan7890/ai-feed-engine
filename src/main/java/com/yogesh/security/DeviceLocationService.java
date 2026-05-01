package com.yogesh.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.bitwalker.useragentutils.UserAgent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

@Service
@RequiredArgsConstructor
public class DeviceLocationService {

    private final ObjectMapper objectMapper;

    // ───────────────── DEVICE INFO ─────────────────
    public Map<String, Object> extractDevice(String userAgentStr) {

        Map<String, Object> device = new HashMap<>();

        try {
            UserAgent ua = UserAgent.parseUserAgentString(userAgentStr);

            device.put("browser", ua.getBrowser().getName());
            device.put("os", ua.getOperatingSystem().getName());
            device.put("deviceType", ua.getOperatingSystem().getDeviceType().getName());

        } catch (Exception e) {
            device.put("browser", "Unknown");
            device.put("os", "Unknown");
            device.put("deviceType", "Unknown");
        }

        return device;
    }

    // ───────────────── LOCATION ─────────────────
    public Map<String, Object> getLocation(String ip) {

        Map<String, Object> location = new HashMap<>();

        try {

            // ✅ LOCALHOST 
            if (ip == null || ip.isBlank()
                    || ip.equals("127.0.0.1")
                    || ip.equals("0:0:0:0:0:0:0:1")
                    || ip.startsWith("192.168")
                    || ip.startsWith("10.")
                    || ip.startsWith("172.")) {

                location.put("city", "Localhost");
                location.put("country", "Local Network");
                location.put("lat", 0);
                location.put("lon", 0);
                return location;
            }

            // ✅ CORRECT API CALL
            URL url = new URL("https://ipapi.co/" + ip + "/json/");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            conn.setRequestMethod("GET");

            int status = conn.getResponseCode();

            if (status != 200) {
                throw new RuntimeException("Location API failed with status " + status);
            }

            Scanner sc = new Scanner(conn.getInputStream());
            String response = sc.useDelimiter("\\A").next();
            sc.close();

            JsonNode json = objectMapper.readTree(response);

            // 🔍 DEBUG (optional)
            // System.out.println("IP = " + ip);
            // System.out.println("Location JSON = " + json.toString());

            // ✅ SAFE PARSING
            location.put("country",
                    json.has("country_name") ? json.get("country_name").asText() : "Unknown");

            location.put("city",
                    json.has("city") ? json.get("city").asText() : "Unknown");

            location.put("lat",
                    json.has("latitude") ? json.get("latitude").asDouble() : 0);

            location.put("lon",
                    json.has("longitude") ? json.get("longitude").asDouble() : 0);

        } catch (Exception e) {

            // ❗ fallback (no hardcoded India)
            location.put("country", "Unknown");
            location.put("city", "Unknown");
            location.put("lat", 0);
            location.put("lon", 0);
        }

        return location;
    }
}