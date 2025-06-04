package bot.weather;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.objects.Location;

import java.sql.*;

public class BotDatabaseV1 {

    /* Utility class handles the interaction between each instance of the bot and the database. */

    // the form for internal to the jar
    // static String dbPath = "databases/botData.db"; // Relative path
    //static String DB_URL = "jdbc:sqlite:%s".formatted(BotDatabase.class.getResourceAsStream(dbPath));
    // the file form (for testing)
    private static final String DB_URL = "jdbc:sqlite:src/main/resources/databases/bot-data.db";
    // not sure what this if for again:
    // static String DB_URL = "jdbc:sqlite:" + new File(dbPath).getAbsolutePath();
    private static final Logger logger = LoggerFactory.getLogger(BotDatabaseV1.class);

    public static void initializeDatabase() {

        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            System.err.println("Failed to register JDBC driver: " + e.getMessage());
        }

        try (Connection connection = DriverManager.getConnection(DB_URL)) {
            if (connection != null) {
                try (Statement statement = connection.createStatement()) {

                    /*

                    String createUserStatusTable = """
                        CREATE TABLE IF NOT EXISTS bot_status (
                            chat_id LONG PRIMARY KEY,
                            user_name STRING,
                            user_first_name STRING,
                            user_last_name STRING,
                            user_id LONG,
                            first_contact DOUBLE,   // time object, however this is handled in java
                            last_contact DOUBLE     // as above, but this should probably be in the below...
                        );
                    """;
                    statement.execute(createUserStatusTable);
                     */

                    String createChatStatusTable = """
                        CREATE TABLE IF NOT EXISTS chat_status (
                            chat_id LONG PRIMARY KEY,
                            wx_requested BOOLEAN
                        );
                    """;
                    statement.execute(createChatStatusTable);

                    String createUserLocationsTable = """
                        CREATE TABLE IF NOT EXISTS user_locations (
                            chat_id LONG PRIMARY KEY,
                            latitude DOUBLE,
                            longitude DOUBLE
                        );
                    """;
                    statement.execute(createUserLocationsTable);
                }
            }
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
    }

    public static void saveChatStatus(long chatId, boolean wxRequested) {
        String sql = "REPLACE INTO chat_status(chat_id, wx_requested) VALUES(?, ?)";

        try (Connection connection = DriverManager.getConnection(DB_URL);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, chatId);
            statement.setBoolean(2, wxRequested);
            statement.executeUpdate();
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
    }

    public static void saveUserLocation(long chatId, double latitude, double longitude) {
        String sql = "REPLACE INTO user_locations(chat_id, latitude, longitude) VALUES(?, ?, ?)";
        try (Connection connection = DriverManager.getConnection(DB_URL)) {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, chatId);
                statement.setDouble(2, latitude);
                statement.setDouble(3, longitude);
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
    }

    public static void deleteUserLocation(long chatId) {
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

    public static void deleteUserStatus(long chatId) {
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

    public static void deleteUserData(long chatId) {
        deleteUserLocation(chatId);
        deleteUserStatus(chatId);
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
}
