package firstattempt;

// telegram bots api
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
// logging
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class Main {

    //
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {

        // this could be stored better
        String botToken = "7368525975:AAGqUPIVEQlJNbInopiT903moyIDv3amTa8";
        // Using try-with-resources to allow autoclose to run upon finishing
        try (TelegramBotsLongPollingApplication botsApplication = new TelegramBotsLongPollingApplication()) {
            botsApplication.registerBot(botToken, new Bot(botToken));
            logger.info("The bot is now live.");
            //
            Thread.currentThread().join();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }
}
