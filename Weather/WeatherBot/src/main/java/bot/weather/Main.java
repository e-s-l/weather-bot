package bot.weather;

import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.util.Properties;

/*
TODO:
- handle timeouts from getUpdateRequests...
 */

public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {

        // load in the api keys
        logger.info("Loading configuration files.");
        Properties properties = loadProperties("/config.properties");
        String botToken = properties.getProperty("botToken");
        String geolocatorToken = properties.getProperty("openWeatherKey");

        if (botToken == null || botToken.isEmpty()) {
            logger.error("Bot token not found in configuration file.");
            return;
        }
        if (geolocatorToken == null || geolocatorToken.isEmpty()) {
            logger.error("Geolocator API token not found in configuration file.");
            return;
        }

        // start the bot
        try (TelegramBotsLongPollingApplication botsApplication = new TelegramBotsLongPollingApplication()) {
            botsApplication.registerBot(botToken, new Bot(botToken, geolocatorToken));
            logger.info("The bot is now live.");
            Thread.currentThread().join();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    private static Properties loadProperties(String propertiesFilepath) {

        Properties properties = new Properties();
        String propertiesFilePath = "/config.properties";

        /*
         Example config.properties:
         botToken=123456789:abCdEfgHiJkLmnOpQRstuvQWxYz
         openWeatherKey=123ra34do3595mlett3409ters
         */

        try {
            properties.load(Main.class.getResourceAsStream(propertiesFilePath));
            return properties;
        } catch (IOException e) {
            logger.error("Error reading configuration file: ", e);
            return null;
        }
    }

}