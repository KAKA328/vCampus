package cn.vcampus.server;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/** 只创建上限测试需要的两张表，所有数据位于 JUnit 临时目录。 */
final class LibraryLimitAccessFixture {
    private LibraryLimitAccessFixture() { }

    static void create(Path database) throws Exception {
        Class.forName("net.ucanaccess.jdbc.UcanaccessDriver");
        try (Connection connection = DriverManager.getConnection("jdbc:ucanaccess://" + database
                + ";newDatabaseVersion=V2010;immediatelyReleaseResources=true");
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE tblBook (book_id VARCHAR(32) NOT NULL,title VARCHAR(120) NOT NULL,"
                    + "author VARCHAR(100) NOT NULL,isbn VARCHAR(32),category VARCHAR(64),publisher VARCHAR(100),"
                    + "price DOUBLE NOT NULL,total_copies INTEGER NOT NULL,available_copies INTEGER NOT NULL,"
                    + "location VARCHAR(64),PRIMARY KEY (book_id))");
            statement.execute("CREATE TABLE tblBorrowRecord (record_id VARCHAR(40) NOT NULL,"
                    + "order_id VARCHAR(40) NOT NULL,user_id VARCHAR(32) NOT NULL,book_id VARCHAR(32) NOT NULL,"
                    + "borrow_date DATETIME NOT NULL,due_date DATETIME NOT NULL,return_date DATETIME,"
                    + "status VARCHAR(16) NOT NULL,PRIMARY KEY (record_id))");
        }
    }
}
