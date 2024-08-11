package db;

/**
 * DB interface is used to define the methods that should be implemented by the database classes.
 */
public interface DB {
    /**
     * Method to gracefully shutdown the database connection.
     */
    public void shutdown();
}
