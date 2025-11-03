package bot.weather;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import com.google.common.util.concurrent.RateLimiter;

public class WeatherService {
    private static final String USER_AGENT = "ATelegramBot/0.2 github.com/e-s-l";
    private static final String API_URL = "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=%s&lon=%s";

    private final ObjectMapper objectMapper;
    private final Logger logger;
    private static final RateLimiter limiter = RateLimiter.create(1.0);

    public WeatherService() {
        objectMapper = new ObjectMapper();
        logger = LoggerFactory.getLogger(WeatherService.class);
    }

    public String getWeather(double latitude, double longitude) {
        try {
            String url = String.format(API_URL, latitude, longitude);
            HttpResponse<String> response;

            try (HttpClient client = HttpClient.newHttpClient()) {
                // concurrently, don't overload the api, thanks joshua
                limiter.acquire();

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("User-Agent", USER_AGENT)
                        .build();
                response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    logger.warn("bad response: {}", response.statusCode());
                    throw new Exception();
                }

            } catch (Exception e) {
                logger.warn("Exception thrown while polling weather data api.");
                return "Problem accessing weather data.";
            }

            JsonNode jsonNode = objectMapper.readTree(response.body());

            JsonNode timeSeries = jsonNode.path("properties").path("timeseries");

            if (!timeSeries.isArray() || timeSeries.isEmpty()) {
                return "Weather data unavailable.";
            }

            JsonNode details = timeSeries.get(0).path("data").path("instant").path("details");

            double temperature = details.path("air_temperature").asDouble();
            double windSpeed = details.path("wind_speed").asDouble();
            double humidity = details.path("relative_humidity").asDouble();
            double pressure = details.path("air_pressure_at_sea_level").asDouble();
            double cloudCoverage = details.path("cloud_area_fraction").asDouble();
            double precipitation = timeSeries.path("data").path("next_1_hours").path("details").path("precipitation_amount").asDouble();

            return String.format("Temperature: %.2f°C\nWind Speed: %.2f m/s\nHumidity: %.2f%%\nPressure: %.2f hPa\nCloud Coverage: %.2f%%\nPrecipitation: %.2f mm",
                    temperature, windSpeed, humidity, pressure, cloudCoverage, precipitation);


        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return "Error.";
        }
    }
}