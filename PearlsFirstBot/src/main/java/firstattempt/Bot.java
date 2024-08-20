package firstattempt;


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
    private WeatherService weatherService = null;
    private final Logger logger;
    private final String botOptions;
    private final Quoter quoter = new Quoter();

    public Bot(String botToken) {
        telegramClient = new OkHttpTelegramClient(botToken);
        weatherService = new WeatherService();
        logger = LoggerFactory.getLogger(Bot.class);
        BotDatabase.initializeDatabase();   // Utility class
        botOptions = """
                My options are:
                /echo to toggle echos,
                /info for any information,
                /wx for weather data,
                /fortune for a fortune,
                /clear to start again.""";
            /*
                /remindme for reminders,
                /pester to be pestered,
                /update to update location
             */
    }

    @Override
    public void consume(Update update) {
        if (update.hasMessage()) {
            long chatId = update.getMessage().getChatId();

            if (update.getMessage().hasText()) {
                String receivedMsg = update.getMessage().getText();
                logger.info("Received: {} from {}", receivedMsg, chatId);
                // other info. from received message
                String userFirstName = update.getMessage().getChat().getFirstName();
                String userLastName = update.getMessage().getChat().getLastName();
                String userUsername = update.getMessage().getChat().getUserName();
                long userId = update.getMessage().getChat().getId();

                if (BotDatabase.isChatActive(chatId)) {
                    switch (receivedMsg) {
                        case "/echo" -> {
                            logger.info("/echo called.");
                            boolean echoEnabled = !BotDatabase.isEchoEnabled(chatId);
                            BotDatabase.saveChatStatus(chatId, echoEnabled, BotDatabase.isWxRequested(chatId));
                            sendMsg(chatId, "Echoing is now " + (echoEnabled ? "on." : "off."));
                        }
                        case "/info" -> {
                            logger.info("/info called.");
                            String infoMsg = "You are " + userFirstName + " " + userLastName +
                                    ".\nUsername & ID: " + userUsername + " " + userId +
                                    ".\nThis chat ID is: " + chatId + "\n\n" + botOptions;
                            sendMsg(chatId, infoMsg);
                        }
                        case "/wx" -> {
                            logger.info("/wx called.");
                            BotDatabase.saveChatStatus(chatId, BotDatabase.isEchoEnabled(chatId), true);
                            if (BotDatabase.getUserLocation(chatId) == null) {
                                sendLocationRequest(chatId);
                            } else {
                                sendWeatherMsg(chatId);
                            }
                        }
                        case "/fortune" -> {
                            sendMsg(chatId, quoter.generate());
                        }
                        case "/clear" -> {
                            BotDatabase.deleteUserData(chatId);
                            sendMsg(chatId, "Deleted chat status & location for user.");
                        }
                        default -> {
                            if (BotDatabase.isEchoEnabled(chatId) && receivedMsg.charAt(0) != '/') {
                                sendMsg(chatId, receivedMsg);
                            } else {
                                sendMsg(chatId, botOptions);
                            }
                        }
                    }
                } else {
                    if (receivedMsg.equals("/start")) {
                        logger.info("/start called.");
                        BotDatabase.saveChatStatus(chatId, false, false);
                        sendMsg(chatId, "Hello, " + userFirstName + ".\n" + botOptions);
                    } else {
                        sendMsg(chatId, "Send /start to start...");
                    }
                }
            }
            if (update.getMessage().hasLocation() && BotDatabase.isWxRequested(chatId)) {
                Location location = update.getMessage().getLocation();
                BotDatabase.saveUserLocation(chatId, location.getLatitude(), location.getLongitude());
                sendWeatherMsg(chatId);
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
        Location location = BotDatabase.getUserLocation(chatId);
        assert location != null;
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