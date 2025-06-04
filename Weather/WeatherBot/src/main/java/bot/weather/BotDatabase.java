package bot.weather;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.sql.*;

import org.telegram.telegrambots.meta.api.objects.Location;

public class BotDatabase {

    /* Utility class handles the interaction between each instance of the bot and the database. */

    // the form for internal to the jar
    // static String dbPath = "databases/botData.db"; // Relative path
    //static String DB_URL = "jdbc:sqlite:%s".formatted(BotDatabase.class.getResourceAsStream(dbPath));
    // the file form (for testing)
    private static final String DB_URL = "jdbc:sqlite:src/main/resources/databases/bot-data.db";
    // not sure what this if for again:
    // static String DB_URL = "jdbc:sqlite:" + new File(dbPath).getAbsolutePath();
    private static final Logger logger = LoggerFactory.getLogger(BotDatabase.class);

    public static void initializeDatabase() {

        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            System.err.println("Failed to register JDBC driver: " + e.getMessage());
        }

        try (Connection connection = DriverManager.getConnection(DB_URL)) {
            if (connection != null) {
                try (Statement statement = connection.createStatement()) {

                    String createUsersTable = """
                        CREATE TABLE IF NOT EXISTS users (
                            user_id        INTEGER PRIMARY KEY,
                            chat_id        DOUBLE NOT NULL UNIQUE,
                            username       TEXT,
                            first_name     TEXT,
                            last_name      TEXT,
                            language_code  TEXT,
                            chat_type      TEXT,
                            first_contact  DATETIME DEFAULT CURRENT_TIMESTAMP,
                            last_contact   DATETIME DEFAULT CURRENT_TIMESTAMP
                        );
                    """;
                    statement.execute(createUsersTable);

                    String createMessagesTable = """
                       CREATE TABLE IF NOT EXISTS user_messages (
                                      message_id     INTEGER PRIMARY KEY,
                                      chat_id        INTEGER NOT NULL,
                                      user_message   TEXT,
                                      received_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
                                      FOREIGN KEY (chat_id) REFERENCES users(chat_id)
                       );
                    """;
                    statement.execute(createMessagesTable);

                    String createChatStatusTable = """
                        CREATE TABLE IF NOT EXISTS chat_status (
                            chat_id LONG PRIMARY KEY,
                            wx_requested BOOLEAN,
                            info_requested BOOLEAN,
                            update_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                            FOREIGN KEY (chat_id) REFERENCES users(chat_id)
                        );
                    """;
                    statement.execute(createChatStatusTable);

                    String createUserLocationsTable = """
                        CREATE TABLE IF NOT EXISTS user_locations (
                            location_id INTEGER PRIMARY KEY AUTOINCREMENT,
                            chat_id LONG NOT NULL,
                            latitude DOUBLE,
                            longitude DOUBLE,
                            place_name TEXT,
                            save_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                            FOREIGN KEY (chat_id) REFERENCES users(chat_id)
                        );
                    """;
                    statement.execute(createUserLocationsTable);
                }
            }
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
    }

    public static void saveChatStatus(long chatId, boolean wxRequested, boolean infoRequested) {
        String sql = "REPLACE INTO chat_status(chat_id, wx_requested, info_requested) VALUES(?, ?, ?)";

        try (Connection connection = DriverManager.getConnection(DB_URL);
             PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, chatId);
                statement.setBoolean(2, wxRequested);
                statement.setBoolean(3, infoRequested);
                statement.executeUpdate();
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
    }

    public static void saveWxRequest(long chatId, boolean wxRequested) {
        String sql = "UPDATE chat_status SET wx_requested = ?, update_time = CURRENT_TIMESTAMP WHERE chat_id = ?";

        logger.info(sql);

        try (Connection connection = DriverManager.getConnection(DB_URL);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBoolean(1, wxRequested);
            statement.setLong(2, chatId);
            statement.executeUpdate();
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
    }

    public static void saveInfoRequest(long chatId, boolean infoRequested) {
        String sql = "UPDATE chat_status SET info_requested = ?, update_time = CURRENT_TIMESTAMP WHERE chat_id = ?";

        try (Connection connection = DriverManager.getConnection(DB_URL);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBoolean(1, infoRequested);
            statement.setLong(2, chatId);
            statement.executeUpdate();
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
    }

    public static void saveUser(long userId, long chatId, String username, String firstName, String lastName,
                                String languageCode, String chatType) {
        String sql = """
            INSERT OR REPLACE INTO users(user_id, chat_id, username, first_name, last_name, language_code, chat_type)
            VALUES(?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(user_id) DO UPDATE SET
                        username = excluded.username,
                        first_name = excluded.first_name,
                        last_name = excluded.last_name,
                        language_code = excluded.language_code,
                        chat_type = excluded.chat_type,
                        chat_id = excluded.chat_id,
                        last_contact = CURRENT_TIMESTAMP
        """;
        try (Connection connection = DriverManager.getConnection(DB_URL);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            statement.setLong(2, chatId);
            statement.setString(3, username);
            statement.setString(4, firstName);
            statement.setString(5, lastName);
            statement.setString(6, languageCode);
            statement.setString(7, chatType);
            statement.executeUpdate();
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
    }

    public static void saveUserMessage(long messageId, long chatId, String messageText) {
        String sql = "INSERT INTO user_messages(message_id, chat_id, user_message) VALUES (?, ?, ?)";
        try (Connection connection = DriverManager.getConnection(DB_URL);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, messageId);
            statement.setLong(2, chatId);
            statement.setString(3, messageText);
            statement.executeUpdate();
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
    }

    public static void saveUserLocation(long chatId, double latitude, double longitude, String placeName) {
        String sql = "INSERT INTO user_locations(chat_id, latitude, longitude, place_name) VALUES(?, ?, ?, ?)";
        try (Connection connection = DriverManager.getConnection(DB_URL)) {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, chatId);
                statement.setDouble(2, latitude);
                statement.setDouble(3, longitude);
                statement.setString(4, placeName);
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
    }

    private static void deleteUserLocation(long chatId) {
        String deleteFromUserLocationsSql = "DELETE FROM user_locations WHERE chat_id = ?";
        try (Connection connection = DriverManager.getConnection(DB_URL)) {
            try (PreparedStatement statement = connection.prepareStatement(deleteFromUserLocationsSql)) {
                statement.setLong(1, chatId);
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            logger.error(e.getMessage(),e);
        }
    }

    private static void deleteUserStatus(long chatId) {
        String deleteFromUserLocationsSql = "DELETE FROM chat_status WHERE chat_id = ?";
        try (Connection connection = DriverManager.getConnection(DB_URL)) {
            try (PreparedStatement statement = connection.prepareStatement(deleteFromUserLocationsSql)) {
                statement.setLong(1, chatId);
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            logger.error(e.getMessage(),e);
        }
    }

    private static void deleteUserMessages(long chatId) {
        String deleteFromUserLocationsSql = "DELETE FROM user_messages WHERE chat_id = ?";
        try (Connection connection = DriverManager.getConnection(DB_URL)) {
            try (PreparedStatement statement = connection.prepareStatement(deleteFromUserLocationsSql)) {
                statement.setLong(1, chatId);
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            logger.error(e.getMessage(),e);
        }
    }

    public static void deleteUserData(long chatId) {
        deleteUserLocation(chatId);
        deleteUserStatus(chatId);
        deleteUserMessages(chatId);
    }

    public static boolean isChatActive(long chatId) {
        String sql = "SELECT 1 FROM chat_status WHERE chat_id = ?";
        try (Connection connection = DriverManager.getConnection(DB_URL)) {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, chatId);
                ResultSet rs = statement.executeQuery();
                return rs.next();
            }
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
            return false;
        }
    }

    public static boolean isWxRequested(long chatId) {
        String sql = "SELECT wx_requested FROM chat_status WHERE chat_id = ?";
        try (Connection connection = DriverManager.getConnection(DB_URL)) {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, chatId);
                ResultSet rs = statement.executeQuery();
                if (rs.next()) {
                    return rs.getBoolean("wx_requested");
                }
            }
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
        return false;
    }

    public static boolean isInfoRequested(long chatId) {
        String sql = "SELECT info_requested FROM chat_status WHERE chat_id = ?";
        try (Connection connection = DriverManager.getConnection(DB_URL)) {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, chatId);
                ResultSet rs = statement.executeQuery();
                if (rs.next()) {
                    return rs.getBoolean("info_requested");
                }
            }
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
        return false;
    }

    public static Location getUserLocation(long chatId) {
        String sql = "SELECT latitude, longitude FROM user_locations WHERE chat_id = ?";
        try (Connection connection = DriverManager.getConnection(DB_URL)) {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, chatId);
                ResultSet rs = statement.executeQuery();
                if (rs.next()) {
                    double latitude = rs.getDouble("latitude");
                    double longitude = rs.getDouble("longitude");
                    return new Location(longitude, latitude);
                }
            }
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }

    public static String getUserName(long chatId) {
        String sql = "SELECT username, first_name, last_name " +
                "FROM users WHERE chat_id = ?";
        try (Connection connection = DriverManager.getConnection(DB_URL)) {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, chatId);
                ResultSet rs = statement.executeQuery();
                if (rs.next()) {
                    StringBuilder sb = new StringBuilder();
                    String firstName = rs.getString("first_name");
                    String lastName = rs.getString("last_name");

                    if (firstName != null || lastName != null) {
                        sb.append(firstName != null ? firstName : "")
                                .append(lastName != null ? " " + lastName : "");
                    }

                    return sb.toString();
                }
            }
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }

    public static String getUserInfo(long chatId) {
        String sql = "SELECT username, first_name, last_name, language_code, chat_type, first_contact " +
                "FROM users WHERE chat_id = ?";
        try (Connection connection = DriverManager.getConnection(DB_URL)) {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, chatId);
                ResultSet rs = statement.executeQuery();
                if (rs.next()) {

                    StringBuilder sb = new StringBuilder();

                    String username = rs.getString("username");
                    if (username != null) {
                        sb.append("Username: ").append(username).append("\n");
                    }

                    String firstName = rs.getString("first_name");
                    String lastName = rs.getString("last_name");

                    if (firstName != null || lastName != null) {
                        sb.append("Name: ").append(firstName != null ? firstName : "").append(" ")
                                .append(lastName != null ? lastName : "").append("\n");
                    }

                    String language = rs.getString("language_code");
                    String chatType = rs.getString("chat_type");
                    if (language != null || chatType != null) {
                        sb.append("Chat: ").append(chatType != null ? chatType : "").append(", ")
                                .append(language != null ? language : "").append("\n");
                    }

                    Timestamp firstContact = rs.getTimestamp("first_contact");
                    if (firstContact != null) {
                        sb.append("First Contact:\n").append(String.format("%tc", firstContact));
                    }

                    sb.append("\n").append("More info?");

                    return sb.toString();
                }
            }
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }

    public static Integer getTotalMessages(long chatId) {
        String sql = "SELECT COUNT(*) AS total_messages FROM user_messages WHERE chat_id = ?";
        try (Connection connection = DriverManager.getConnection(DB_URL)) {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, chatId);
                ResultSet rs = statement.executeQuery();
                if (rs.next()) {
                    return rs.getInt("total_messages");
                }
            }
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }

    public static String convoDuration(long chatId) {
        String sql = "SELECT first_contact, last_contact FROM users WHERE chat_id = ?";
        try (Connection connection = DriverManager.getConnection(DB_URL)) {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, chatId);
                ResultSet rs = statement.executeQuery();
                if (rs.next()) {
                    Timestamp firstContact = rs.getTimestamp("first_contact");
                    Timestamp lastContact = rs.getTimestamp("last_contact");

                    long durationMillis = lastContact.getTime() - firstContact.getTime();
                    long seconds = durationMillis / 1000 % 60;
                    long minutes = durationMillis / (1000 * 60) % 60;
                    long hours = durationMillis / (1000 * 60 * 60) % 24;
                    long days = durationMillis / (1000 * 60 * 60 * 24);

                    return String.format("%d days, %02d:%02d:%02d", days, hours, minutes, seconds);

                }
            }
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }

}
