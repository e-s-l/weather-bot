package bot.weather;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

import org.telegram.telegrambots.meta.api.objects.Location;

import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.*;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import java.util.ArrayList;
import java.util.List;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;

public class Bot implements LongPollingSingleThreadUpdateConsumer {

    private final TelegramClient telegramClient;
    private WeatherService weatherService;
    private Geolocator geolocator;
    private final Logger logger;
    private final String botOptions;

    public Bot(String botToken, String geolocatorToken) {
        telegramClient = new OkHttpTelegramClient(botToken);
        weatherService = new WeatherService();
        geolocator = new Geolocator(geolocatorToken);
        logger = LoggerFactory.getLogger(Bot.class);

        // Utility class
        BotDatabase.initializeDatabase();

        botOptions = """
               My options are:
               /info,
               /wx.""";
    }

    @Override
    public void consume(Update update) {
        if (update.hasMessage()) {
            long chatId = update.getMessage().getChat().getId();
            long messageId = update.getMessage().getMessageId();

            // save all the above into a db table
            BotDatabase.saveUser(chatId, update.getMessage().getChatId(),
                    update.getMessage().getChat().getUserName(),update.getMessage().getChat().getFirstName(),
                    update.getMessage().getChat().getLastName(), update.getMessage().getFrom().getLanguageCode(),
                    update.getMessage().getChat().getType());

            logger.info("Is Weather requested? {}", BotDatabase.isWxRequested(chatId));
            logger.info("Is Info requested? {}", BotDatabase.isInfoRequested(chatId));

            // if there's a location...
            if (update.getMessage().hasLocation() && BotDatabase.isWxRequested(chatId)) {
                Location location = update.getMessage().getLocation();
                String placeName = geolocator.findName(location.getLatitude(), location.getLongitude());
                logger.info("place name = {}", placeName);
                BotDatabase.saveUserLocation(chatId, location.getLatitude(),
                        location.getLongitude(), placeName);
                sendWeatherMsg(chatId);
            }

            // if there's text...
            if (update.getMessage().hasText()) {
                String receivedMsg = update.getMessage().getText();
                BotDatabase.saveUserMessage(messageId, chatId, receivedMsg);

                logger.info("Received: {} from {}", receivedMsg, chatId);

                if (BotDatabase.isChatActive(chatId)) {

                    if (receivedMsg.startsWith("/wx")) {
                        BotDatabase.saveWxRequest(chatId, true);

                        if (receivedMsg.equals("/wx")) {
                            // message is just /wx, so ask for their location
                            logger.info("/wx called.");
                            if (BotDatabase.getUserLocation(chatId) == null) {
                                sendLocationRequest(chatId);
                            } else {
                                sendWeatherMsg(chatId);
                            }
                        } else if (receivedMsg.startsWith("/wx ")) {
                            // message is /wx followed by a string
                            // so assume this string is a place name and have a crack...
                            String place = receivedMsg.substring(4).trim();
                            logger.info("/wx called with place: {}", place);
                            try {
                                // use geolocator here to get location (lat, long) of place
                                Location location = geolocator.findPlace(place);
                                logger.info(location.toString());
                                // then pass to weather
                                sendWeatherMsg(chatId, location);
                            } catch (NullPointerException npe) {
                                // our findPlace function returned a null location
                                sendMsg(chatId, "The requested location does not exist in my records.");
                            }
                        }
                    } else {
                        String msg;

                        switch (receivedMsg.toLowerCase()) {
                            case "/info" -> {

                                logger.info("/info called.");
                                BotDatabase.saveInfoRequest(chatId, true);
                                msg = BotDatabase.getUserInfo(chatId);
                            }
                            case "/clear" -> {
                                BotDatabase.deleteUserData(chatId);
                                msg = "Deleted chat status & location for user.";
                            }
                            case "y", "yes", "/y", "/yes" -> {
                                if (BotDatabase.isInfoRequested(chatId)) {
                                    StringBuilder sb = new StringBuilder();
                                    sb.append("Total messages sent by").append(" ")
                                            .append(BotDatabase.getUserName(chatId)).append(": ")
                                            .append(BotDatabase.getTotalMessages(chatId)).append("\n");

                                   // msg += BotDatabase.convoDuration(chatId);
                                    msg = sb.toString();
                                    // what else...

                                } else {
                                    //back to default
                                   msg = botOptions;
                                }
                            }
                            default -> {
                                msg = botOptions;
                            }
                        }
                        sendMsg(chatId, msg);
                    }
                } else {
                    logger.info("new chat");
                    BotDatabase.saveChatStatus(chatId, false, false);
                    sendMsg(chatId, "Hello, " + BotDatabase.getUserName(chatId) + ".\n" + botOptions);
                }
            }
        }
    }

    private void sendMsg(long chatId, String msg) {

        // String answer_text = msg + " \uD83E\uDD2E";

        SendMessage sentMsg = SendMessage // Create a message object
                .builder()
                .chatId(chatId)
                .text(msg)
                .build();
        try {
            telegramClient.execute(sentMsg);
            logger.info("Sent {} in chat {}", sentMsg, chatId);
        } catch (TelegramApiException e) {
            logger.error(e.getMessage(), e);
        }
    }

    private void sendWeatherMsg(long chatId) {
        Location location = BotDatabase.getUserLocation(chatId);
        String name = geolocator.findName(location.getLatitude(), location.getLongitude());
        String weatherInfo = weatherService.getWeather(location.getLatitude(), location.getLongitude());
        String wxMsg = String.format("Weather in %s:\n(%.2f, %.2f)\n", name, location.getLatitude(), location.getLongitude()) +
                weatherInfo;
        sendMsg(chatId, wxMsg);
    }

    // method overloading
    private void sendWeatherMsg(long chatId, Location location) {
        String weatherInfo = weatherService.getWeather(location.getLatitude(), location.getLongitude());
        String name = geolocator.findName(location.getLatitude(), location.getLongitude());
        String wxMsg = String.format("Weather in %s:\n(%.2f, %.2f)\n", name, location.getLatitude(), location.getLongitude()) +
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