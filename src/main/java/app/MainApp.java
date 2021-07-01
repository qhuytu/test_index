package app;

import org.apache.commons.lang3.RandomStringUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class MainApp {

    private static final String DB_URL = "jdbc:mysql://localhost:3306/school?autoReconnect=true&useSSL=false";
    private static final String DB_USERNAME = "school";
    private static final String DB_PASSWORD = "school";
    private static final String DB_TABLE = "performance";
    private static final String DB_TABLE_PRIMARY = "id";
    private static final String DB_TABLE_TEXT = "name";
    private static final String DB_TABLE_STATUS = "type";
    private static final String DB_TABLE_INDEX_NAME = "type_id";
    private static final int BATCH_SIZE = 10000;
    private static final Object SYNC_OBJECT = new Object();
    private static Connection connection = null;

    public static void main(String[] args) throws SQLException {
        try {
//            initRandomToDatabase(100000);
            TestEntity item = getItem(100000);
            System.out.println(item);

            int type = 1;

            long startPrimary = System.currentTimeMillis();
            List<TestEntity> allItemPrimary = getAllItemPrimary(type);
            System.out.println(allItemPrimary.size());
            System.out.println("PrimaryConsumed:" + (System.currentTimeMillis() - startPrimary));

            long startIndex = System.currentTimeMillis();
            List<TestEntity> allItemIndex = getAllItemIndex(type);
            System.out.println(allItemIndex.size());
            System.out.println("IndexConsumed:" + (System.currentTimeMillis() - startIndex));

            System.exit(0);
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
    }

    private static TestEntity getItem(int id) throws SQLException {
        Connection connection = getConnection();
        Statement statement = connection.createStatement();
        String query = String.format("SELECT * FROM %s WHERE %s=%d", DB_TABLE, DB_TABLE_PRIMARY, id);
        ResultSet resultSet = statement.executeQuery(query);
        if (!resultSet.isBeforeFirst()) {
            return null;
        }
        resultSet.next();
        int _id = resultSet.getInt(DB_TABLE_PRIMARY);
        String name = resultSet.getString(DB_TABLE_TEXT);
        int type = resultSet.getInt(DB_TABLE_STATUS);
        return TestEntity.newInstance(_id, name, type);
    }

    private static List<TestEntity> getAllItemIndex(int type) throws SQLException {
        String queryFormat = String.format(
                "SELECT SQL_NO_CACHE * FROM %s FORCE INDEX (%s) WHERE %s=%d AND %s>",
                DB_TABLE, DB_TABLE_INDEX_NAME, DB_TABLE_STATUS, type, DB_TABLE_PRIMARY
        ) + "%d" + " LIMIT 0," + BATCH_SIZE;
        return getAllItemByQueryFormat(queryFormat);
    }

    private static List<TestEntity> getAllItemPrimary(int type) throws SQLException {
        String queryFormat = String.format(
                "SELECT SQL_NO_CACHE * FROM %s WHERE %s=%d AND %s>",
                DB_TABLE, DB_TABLE_STATUS, type, DB_TABLE_PRIMARY
        ) + "%d" + " LIMIT 0," + BATCH_SIZE;
        return getAllItemByQueryFormat(queryFormat);
    }

    private static List<TestEntity> getAllItemByQueryFormat(String queryFormat) throws SQLException {
        Connection connection = getConnection();
        Statement statement = connection.createStatement();
        List<TestEntity> result = new ArrayList<>();
        boolean isMore = false;
        int lastId = 0;
        do {
            String query = String.format(queryFormat, lastId);
            ResultSet resultSet = statement.executeQuery(query);
            while (resultSet.next()) {
                lastId = resultSet.getInt(DB_TABLE_PRIMARY);
                String name = resultSet.getString(DB_TABLE_TEXT);
                int _type = resultSet.getInt(DB_TABLE_STATUS);
                TestEntity entity = TestEntity.newInstance(lastId, name, _type);
                result.add(entity);
            }
            isMore = resultSet.isAfterLast();
        } while (isMore);
        return result;
    }

    private static boolean initRandomToDatabase(int n) throws SQLException {
        if (n < 1) {
            return false;
        }
        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append("INSERT INTO ").append(DB_TABLE).append("(").append(DB_TABLE_TEXT).append(",").append(DB_TABLE_STATUS).append(")")
                .append(" VALUES ");
        List<TestEntity> entities = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            entities.add(TestEntity.newRandomInstanceWithoutId());
        }
        String collect = entities.stream()
                .map(entity -> "(\"" + entity.getName() + "\"," + entity.getType() + ")")
                .collect(Collectors.joining(","));
        queryBuilder.append(collect);
        String query = queryBuilder.toString();

        Connection connection = getConnection();
        connection.setAutoCommit(false);
        Statement statement = connection.createStatement();
        boolean result = statement.execute(query);
        connection.commit();
        return result;
    }

    private static Connection getConnection() {
        if (connection == null) {
            synchronized (SYNC_OBJECT) {
                if (connection == null) {
                    try {
                        Class.forName("com.mysql.jdbc.Driver");
                        connection = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        System.exit(-1);
                    }
                }
            }
        }
        return connection;
    }

    private static class TestEntity {
        private static final Random RANDOM = new Random();
        private int id;
        private String name;
        private int type;

        private TestEntity() {
        }

        public static TestEntity newInstance(int id, String name, int type) {
            TestEntity entity = new TestEntity();
            entity.id = id;
            entity.name = name;
            entity.type = type;
            return entity;
        }

        public static TestEntity newRandomInstanceWithoutId() {
            TestEntity entity = new TestEntity();
            entity.name = RandomStringUtils.randomAlphabetic(10);
            entity.type = RANDOM.nextInt(2);
            return entity;
        }

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getType() {
            return type;
        }

        public void setType(int type) {
            this.type = type;
        }

        @Override
        public String toString() {
            return "TestEntity{" +
                    "id=" + id +
                    ", name='" + name + '\'' +
                    ", type=" + type +
                    '}';
        }
    }

}
