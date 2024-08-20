package firstattempt;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.sql.*;
import java.io.File;

public class PopulateFortuneDatabase {

    private static final String DB_URL = "jdbc:sqlite:src/main/resources/databases/fortunes.db";

    public static void main(String[] args) {

        String filePath = "src/main/resources/fortune-generator/quotes.txt";

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            try (Connection connection = DriverManager.getConnection(DB_URL)) {

                if (connection != null) {
                    try (Statement statement = connection.createStatement()) {
                        String createChatStatusTable = """
                                    CREATE TABLE IF NOT EXISTS quotes (
                                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                                        quote TEXT NOT NULL,
                                    );
                                """;
                        statement.execute(createChatStatusTable);
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }

                    connection.setAutoCommit(false);

                    String sql = "INSERT INTO quotes (quote) VALUES (?)";
                    try (PreparedStatement pstatement = connection.prepareStatement(sql)) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            pstatement.setString(1, line);
                            pstatement.addBatch();
                        }
                        pstatement.executeBatch();
                    } catch (SQLException e) {
                        connection.rollback();
                        e.printStackTrace();
                    }
                    connection.commit();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /*
    When you're gettin gconfused about relative file paths...
     */
    public static void mainDEBUG(String[] args){
        String filePath = "src/main/resources/fortune-generator/quotes.txt";
        File file = new File(filePath);
        System.out.println("Absolute path: " + file.getAbsolutePath());
        System.out.println("File exists: " + file.exists());
    }
}