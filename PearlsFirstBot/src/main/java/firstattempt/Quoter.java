package firstattempt;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class Quoter {

    private final Logger logger;
    private static final String DB_URL = "jdbc:sqlite:src/main/resources/databases/fortunes.db";

    public Quoter() {
        logger = LoggerFactory.getLogger(Quoter.class);
    }

    public String generate() {
        String quote = "";
        int count = 0;
        try (Connection connection = DriverManager.getConnection(DB_URL)) {
            String countSql = "SELECT COUNT(*) AS count FROM quotes";
            try (PreparedStatement countStatement = connection.prepareStatement(countSql)) {
                try (ResultSet countResultSet = countStatement.executeQuery()) {
                    count = countResultSet.getInt("count");
                    if (count == 0) {
                        logger.error("No quotes available in the database.");
                        return "No quotes available.";
                    }
                    Random r = new Random();
                    int randomIndex = r.nextInt(count);
                    String quoteSql = "SELECT quote FROM quotes LIMIT 1 OFFSET ?";
                    try (PreparedStatement quoteStatement = connection.prepareStatement(quoteSql)) {
                        quoteStatement.setInt(1, randomIndex);
                        try (ResultSet quoteResultSet = quoteStatement.executeQuery()) {
                            if (quoteResultSet.next()) {
                                quote = quoteResultSet.getString("quote");
                            }
                        }
                    }
                }
            }
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
        logger.info("Generated quote-{}: {}", count, quote);
        return quote;
    }
}

