package firstattempt;

// logging
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
// telegram bot api
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Location;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.*;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
// mutable arrays
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
//
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;


public class Bot implements LongPollingSingleThreadUpdateConsumer {

    private final TelegramClient telegramClient;
    private WeatherService weatherService = null;
    private final Logger logger;

    private final String botOptions;

    // these should really be a database or something, no?
    private final ArrayList<Long> currentChatIds = new ArrayList<>();
    private final ArrayList<Long> currentEchoChatIds = new ArrayList<>();
    // Temporary storage for user locations
    private final HashMap<Long, Location> userLocations = new HashMap<>();

    public Bot(String botToken) {
        telegramClient = new OkHttpTelegramClient(botToken);
        weatherService = new WeatherService();
        logger = LoggerFactory.getLogger(Bot.class);
        botOptions = """
                My options are:
                /echo to toggle echos
                /info for any information
                /wx for weather data.""";
    }

    @Override
    public void consume(Update update) {
        if (update.hasMessage()) {
            long chatId = update.getMessage().getChatId();

            if (update.getMessage().hasText()) {
                //
                String receivedMsg = update.getMessage().getText();
                logger.info("Received: {} from {}", receivedMsg, Long.toString(chatId));
                // other info. from received message
                String userFirstName = update.getMessage().getChat().getFirstName();
                String userLastName = update.getMessage().getChat().getLastName();
                String userUsername = update.getMessage().getChat().getUserName();
                long userId = update.getMessage().getChat().getId();

                if (currentChatIds.contains(chatId)) {
                    switch (receivedMsg) {
                        case "/echo" -> {
                            logger.info("/echo called.");
                            if (!currentEchoChatIds.contains(chatId)) {
                                currentEchoChatIds.add(chatId);
                                sendMsg(chatId, "Echoing is now on.");
                            } else {
                                currentEchoChatIds.remove(chatId);
                                sendMsg(chatId, "Echoing is now off.");
                            }
                        }
                        case "/info" -> {
                            logger.info("/info called.");
                            String infoMsg = "You are " + userFirstName + " " + userLastName +
                                    ".\nUsername & id: " + userUsername + " " + userId +
                                    ".\nThis chat ID is: " + chatId + "\n\n" + botOptions;
                            sendMsg(chatId, infoMsg);
                        }
                        case "/wx" -> {
                            logger.info("/wx called.");
                            // if we don't know the location, ask for it
                            if (!userLocations.containsKey(chatId)) {
                                sendLocationRequest(chatId);
                            } else {
                                sendWeatherMsg(chatId);
                            }
                        }
                        default -> {
                            if (currentEchoChatIds.contains(chatId)) {
                                sendMsg(chatId, receivedMsg);
                            } else {
                                sendMsg(chatId, botOptions);
                            }
                        }
                    }
                } else {
                    if (receivedMsg.equals("/start")) {
                        logger.info("/start called.");
                        currentChatIds.add(chatId);
                        sendMsg(chatId, "Hello. " + botOptions);
                    } else {
                        sendMsg(chatId, "Send /start to start...");
                    }
                }
            }
            if (update.getMessage().hasLocation()) {
                // Store the location for the user
                Location location = update.getMessage().getLocation();
                userLocations.put(chatId, location);
                sendWeatherMsg(chatId);
                // sendMsg(chatId, "Location received. Type /wx to get the weather.");
            }
        }
    }

    private void sendMsg(long chatId, String msg) {
        String answer_text = msg + " \uD83E\uDD2E";
        SendMessage sentMsg = SendMessage // Create a message object
                .builder()
                .chatId(chatId)
                .text(answer_text)
                .build();
        try {
            telegramClient.execute(sentMsg);
            logger.info("Sent {} in chat {}", sentMsg, chatId);
        } catch (TelegramApiException e) {
            logger.error(e.getMessage(), e);
        }
    }

    private void sendWeatherMsg(long chatId) {
        // if we have it, store it
        Location location = userLocations.get(chatId);
        String weatherInfo = weatherService.getWeather(location.getLatitude(), location.getLongitude());
        String wxMsg = String.format("Weather at location:\n(%.2f, %.2f)\n", location.getLatitude(), location.getLongitude()) +
                weatherInfo;
        sendMsg(chatId, wxMsg);
    }

    private void sendLocationRequest(long chatId) {
        KeyboardButton locationButton = new KeyboardButton("Share Location");
        locationButton.setRequestLocation(true);

        KeyboardRow keyboardRow = new KeyboardRow();
        keyboardRow.add(locationButton);

        List<KeyboardRow> keyboardRows = new ArrayList<>();
        keyboardRows.add(keyboardRow);

        ReplyKeyboardMarkup keyboardMarkup = ReplyKeyboardMarkup.builder()
                .keyboard(keyboardRows)
                .resizeKeyboard(true)
                .oneTimeKeyboard(true)
                .build();

        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text("Please share your location to get the weather.")
                .replyMarkup(keyboardMarkup)
                .build();
        try {
            telegramClient.execute(message);
        } catch (TelegramApiException e) {
            logger.error(e.getMessage(), e);
        }
    }

}