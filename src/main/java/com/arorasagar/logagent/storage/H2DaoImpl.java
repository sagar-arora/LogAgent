package com.arorasagar.logagent.storage;

import com.arorasagar.logagent.model.LogFile;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class H2DaoImpl implements LogFileDao {

/*    Connection connection;

    public static Connection createConnection() throws SQLException {
        Connection conn = DriverManager.
                getConnection("jdbc:h2:~/test", "sa", "");
        System.out.println("Connection with the database is established..");
        return conn;
    }

    public H2DaoImpl(Connection connection) {
        this.connection = connection;
    }

    public H2DaoImpl() throws SQLException {
        this(createConnection());
    }*/

/*    public void close() throws SQLException {
        connection.close();
    }*/

    private final Logger logger = LoggerFactory.getLogger(H2DaoImpl.class);
    @Override
    public void writeLogfile(LogFile logFile) {

        Transaction transaction = null;
        try(Session session = HibernateUtil.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();

            session.save(logFile);

            transaction.commit();
            logger.info("Transcation complete.");
        } catch (Exception e) {
            if (transaction != null) {
                transaction.rollback();
            }
            e.printStackTrace();
        }
    }

    @Override
    public LogFile getLogfile(String file) {

        try(Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "FROM LogFile L WHERE L.filePath = :file_path";
            Query<LogFile> query = session.createQuery(hql).setParameter("file_path", file);
            List<LogFile> results = query.list();

            if (results != null && results.size() == 1) {
                return results.get(0);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    @Override
    public boolean isPresent(String file) {
        return getLogfile(file) != null;
    }

    @Override
    public boolean removeLogfile(String file) {
        return false;
    }
}
