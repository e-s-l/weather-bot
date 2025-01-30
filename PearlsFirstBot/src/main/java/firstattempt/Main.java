package firstattempt;


import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);


    public static void main(String[] args) {

        Properties properties = new Properties();
        String propertiesFilePath = "src/main/resources/config.properties";

        try (FileInputStream fis = new FileInputStream(propertiesFilePath)) {
            properties.load(fis);
        } catch (IOException e) {
            logger.error("Error reading configuration file: ", e);
            return;
        }

        String botToken = properties.getProperty("botToken");
        if (botToken == null || botToken.isEmpty()) {
            logger.error("Bot token not found in configuration file.");
            return;
        }

        // Using try-with-resources to allow autoclose to run upon finishing
        try (TelegramBotsLongPollingApplication botsApplication = new TelegramBotsLongPollingApplication()) {
            botsApplication.registerBot(botToken, new Bot(botToken));
            logger.info("The bot is now live.");
            Thread.currentThread().join();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }
}
