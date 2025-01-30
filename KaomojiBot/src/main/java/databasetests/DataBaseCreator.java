package databasetests;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;

/*---------------------------------------
TODO:
Clean up the database...
    - delete any emojis (be pure)
    - delete any words (pureer)

Create a clean-up-ed json file output...
---------------------------------------*/

public class DataBaseCreator {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Logger logger = LoggerFactory.getLogger(DataBaseCreator.class);
    private static final String DB_URL = "jdbc:sqlite:src/main/resources/databases/kaomoji.db";
    private static final String INPUT_EMOJI_DICT = "src/main/java/databasetests/emoticon_dict_ekohrt.json";
    private static final String OUTPUT_KAOMOJIS = "src/main/java/databasetests/kaomojis.json";


    public static void main(String[] args) {

        // Load in from JSON Dict (web scrapped)
        List<KaomojiRecord> kaomojiRecords = importJsonFile();

        // Wash the data

        // Upload to DB
        if(!kaomojiRecords.isEmpty()) {
            createDatabaseTable();
            insertRecords(kaomojiRecords);
        } else {
            logger.warn("Empty list of kaomoji records.");
        }

        // Debug:
        // printRecordsFromDB();
        printRandomKaomoji();
        printTableInfo();

       // exportJsonFile();
    }

    /*-------------------
    Parse the JSON file:
    --------------------*/
    private static List<KaomojiRecord> importJsonFile() {

        List<KaomojiRecord> records = new ArrayList<>();
        try {

            JsonNode jsonNode = mapper.readTree(new File(DataBaseCreator.INPUT_EMOJI_DICT));
            Iterator<Map.Entry<String, JsonNode>> fields = jsonNode.fields();

            // Regex to skip non-kaomojis
            final Pattern emojiPattern = Pattern.compile("[\\p{InEmoticons} +" +
                    "\\p{InDingbats}" +
                    "\\p{InMiscellaneousSymbolsAndPictographs}" +
                    "\\p{InTransportAndMapSymbols}" +
                    "\\u2757\\u2049\\u0023\\uFE0F\\u20E3" +
                    "\\u25AA\\u25AB\\u25FB]");

            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();

                // The Kaomoji
                String kaomoji = field.getKey();

                if (emojiPattern.matcher(kaomoji).find()) {
                    continue;
                }

                // The Tags
                JsonNode originalTags = field.getValue().get("original_tags");
                JsonNode newTags = field.getValue().get("new_tags");
                List<String> tags = new ArrayList<>();
                originalTags.forEach(tag -> tags.add(tag.asText().toLowerCase()));
                newTags.forEach(tag -> tags.add(tag.asText().toLowerCase()));

                // Debug:
                // System.out.println("Kaomoji: " + kaomoji);
                // System.out.println("Tags:");
                // tags.forEach(tag -> System.out.println("  - " + tag));
                // System.out.println();

                // Create new record and add to the list
                records.add(new KaomojiRecord(kaomoji, tags));
            }
            logger.info("Load file: " + DataBaseCreator.INPUT_EMOJI_DICT + " into object list.");
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
        return records;
    }

    private static void exportJsonFile() {

        String sql = "SELECT kaomoji, tags FROM kaomojis";

        Map<String, Map<String, List<String>>> dataMap = new HashMap<>();

        try (Connection connection = DriverManager.getConnection(DataBaseCreator.DB_URL);

             PreparedStatement pstmt = connection.prepareStatement(sql);
             ResultSet resultSet = pstmt.executeQuery()) {

            while (resultSet.next()) {
                String kaomoji = resultSet.getString("kaomoji");
                String tagsString = resultSet.getString("tags");
                List<String> tags = List.of(tagsString.split(" "));
                Map<String, List<String>> tagData = new HashMap<>();
                tagData.put("tags", tags);
                dataMap.put(kaomoji, tagData);
            }

            // mapper.writeValue(new File(DataBaseCreator.OUTPUT_KAOMOJIS), dataMap);
            mapper.writerWithDefaultPrettyPrinter().writeValue(new File(DataBaseCreator.OUTPUT_KAOMOJIS), dataMap);

            logger.info("Exported DB to " + DataBaseCreator.OUTPUT_KAOMOJIS);

        } catch (SQLException | IOException e) {
            logger.error(e.getMessage());
        }
    }

    /*-------------------------------------------------
    Object representing a Kaomoji
    with properties of the unicode rep. & array of tags
    --------------------------------------------------*/

    private static class KaomojiRecord {

        private final String kaomoji;
        private final List<String> tags;

        public KaomojiRecord(String kaomoji, List<String> tags) {
            this.kaomoji = kaomoji;
            this.tags = tags;
        }

        public String getKaomoji() {
            return kaomoji;
        }

        public List<String> getTags(){
            return tags;
        }
    }

    /*-------------
    Database Things
    --------------*/

    private static void createDatabaseTable() {
        try (Connection connection = DriverManager.getConnection(DataBaseCreator.DB_URL)) {
            if (connection != null) {
                try (Statement statement = connection.createStatement()) {
                    String createChatStatusTable = """
                                CREATE TABLE IF NOT EXISTS kaomojis (
                                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                                    kaomoji TEXT NOT NULL UNIQUE,
                                    tags TEXT,
                                    time_stamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                                );
                            """;
                    statement.execute(createChatStatusTable);
                }
            }
        } catch (SQLException e) {
            logger.error(e.getMessage());
        }
    }

    private static void insertRecords(List<KaomojiRecord> records) {
        String sql = "INSERT OR IGNORE INTO kaomojis (kaomoji, tags) VALUES (?, ?)";
        try (Connection connection = DriverManager.getConnection(DataBaseCreator.DB_URL)) {
            connection.setAutoCommit(false);
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                for (KaomojiRecord record : records) {
                    String kaomoji = record.getKaomoji();
                    List<String> tags = record.getTags();
                    String tagsString = String.join(" ", tags);
                    pstmt.setString(1, kaomoji);
                    pstmt.setString(2, tagsString);
                    pstmt.addBatch();
                }
                pstmt.executeBatch();
            }
            connection.commit();
        } catch (SQLException e) {
            logger.error(e.getMessage());
        }
    }

    /*-----------------------
    Some quick test functions
    ------------------------*/

    private static void printRecordsFromDB() {
        String sql = "SELECT kaomoji, tags FROM kaomojis";
        try (Connection connection = DriverManager.getConnection(DataBaseCreator.DB_URL);
             PreparedStatement pstmt = connection.prepareStatement(sql);
             ResultSet set = pstmt.executeQuery()) {
            while (set.next()) {
                System.out.println(set.getString("kaomoji") + " : " + set.getString("tags"));
            }
        } catch (SQLException e) {
            logger.error(e.getMessage());
        }
    }

    private static void printRandomKaomoji() {
        String sql = "SELECT kaomoji FROM kaomojis ORDER BY RANDOM() LIMIT 1";
        try (Connection connection = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = connection.prepareStatement(sql);
             ResultSet set = pstmt.executeQuery()) {
            if (set.next()) {
                System.out.println(set.getString("kaomoji"));
            }
        } catch (SQLException e) {
            logger.error(e.getMessage());
        }
    }

    private static void printTableInfo() {
        String sqlRowCount = "SELECT COUNT(*) AS rowCount FROM kaomojis";
        String sqlColCount = """
                                 SELECT COUNT(*)
                                 FROM PRAGMA_TABLE_INFO('kaomojis');
        """;
        int rowCount = -1, colCount = 1;

        try (Connection connection = DriverManager.getConnection(DB_URL)) {

             try (PreparedStatement pstmt = connection.prepareStatement(sqlRowCount);
                  ResultSet resultSet = pstmt.executeQuery()) {
                        if (resultSet.next()) {
                            rowCount = resultSet.getInt("rowCount");

                        }
                }

            try (PreparedStatement pstmt = connection.prepareStatement(sqlColCount);
                 ResultSet resultSet = pstmt.executeQuery()) {
                while (resultSet.next()) {
                    colCount++;
                }
            }

            logger.info("Table Info: {}, {}", rowCount, colCount);

        } catch (SQLException e) {
            logger.error(e.getMessage());
        }

    }

}
