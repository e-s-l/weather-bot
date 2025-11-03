package bot.weather;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.objects.Location;

public class Geolocator {

    private final String apiKey;
    private String limit;
    private final Logger logger;
    private final ObjectMapper objectMapper;
    private static final String DIRECT_URL = "http://api.openweathermap.org/geo/1.0/direct?q=%s&limit=%s&appid=%s";
    private static final String REVERSE_URL = "http://api.openweathermap.org/geo/1.0/reverse?lat=%s&lon=%s&limit=%s&appid=%s";
    private static final RateLimiter limiter = RateLimiter.create(1.0);

    public Geolocator(String apiKey) {
        this.apiKey = apiKey;
        this.logger = LoggerFactory.getLogger(Geolocator.class);
        this.objectMapper = new ObjectMapper();
        this.limit = "1";
    }

    public String findName(double latitude, double longitude) {
        // reverse geocode

        try {
            String lat = URLEncoder.encode(String.valueOf(latitude), StandardCharsets.UTF_8);
            String lon = URLEncoder.encode(String.valueOf(longitude), StandardCharsets.UTF_8);

            String url = String.format(REVERSE_URL, lat, lon, this.limit, this.apiKey);
            logger.debug(url);
            HttpResponse<String> response;

            try (HttpClient client = HttpClient.newHttpClient()) {
                limiter.acquire();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .build();
                response = client.send(request, HttpResponse.BodyHandlers.ofString());
            }

            JsonNode jsonRoot = objectMapper.readTree(response.body());
            if (jsonRoot.isArray() && jsonRoot.size() > 0) {
                JsonNode locationNode = jsonRoot.get(0);
                String name = locationNode.path("name").asText();
                logger.info("Name = {}", name);
                return name;
            } else {
                return null;
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

    }

    public Location findPlace(String place) {
        // geocode

        try {
            String safePlace = URLEncoder.encode(place, StandardCharsets.UTF_8);
            String url = String.format(DIRECT_URL, safePlace, this.limit, this.apiKey);
            logger.debug(url);
            HttpResponse<String> response;

            try (HttpClient client = HttpClient.newHttpClient()) {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .build();
                response = client.send(request, HttpResponse.BodyHandlers.ofString());
                limiter.acquire();
            }

            JsonNode jsonRoot = objectMapper.readTree(response.body());

            if (jsonRoot.isArray() && jsonRoot.size() > 0) {
                JsonNode locationNode = jsonRoot.get(0);
                double longitude = locationNode.path("lon").asDouble();
                double latitude = locationNode.path("lat").asDouble();
                String name = locationNode.path("name").asText();
                logger.info(String.valueOf(longitude));
                logger.info(String.valueOf(latitude));
                return new Location(longitude, latitude);

            } else {
                logger.warn("Empty location result for: {}", place);
                return null;
            }

        } catch (Exception e) {
            logger.error("Oh damn, Caught error in Geolocator.");
            logger.error(e.getMessage(), e);
            return null;
        }
    }

}
