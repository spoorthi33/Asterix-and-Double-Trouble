package raft;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class DiskLog {
    private String LOG_FILE = "log.txt";
    private final Object fileLock = new Object();
    private ExecutorService executor;

    private int nextLogId =0;

    public DiskLog(String logFilePath){
        LOG_FILE = logFilePath;
        System.out.println("Log file path is: "+logFilePath);
        this.executor = Executors.newSingleThreadExecutor();
    }

    public void appendLogEntry(LogEntry logEntry) {
        synchronized (fileLock){
            try (FileWriter writer = new FileWriter(LOG_FILE, true)) {

                // Append order details to CSV file
                int logId = logEntry.getLogId();
                int term = logEntry.getTerm();
                String orderDetails = logEntry.getOrderDetails();
                String txnStatus = logEntry.getTxnStatus();
                System.out.println("Appending log: " + logId + "," +term +","+ orderDetails + "," + txnStatus );
                writer.append( logId + "," +term + ","+ orderDetails + "," + txnStatus+ "\n");
                writer.flush();

                nextLogId = logId+1; // return next logId.
            } catch (IOException e) {
                e.printStackTrace(); // Handle or log the exception properly
            }
        }
    }

    public int getNextLogId(){
        synchronized (fileLock){
            return nextLogId;
        }
    }

    public void loadNextCommitId(){
        int lastCommitId = 0;
        synchronized (fileLock){
            try (BufferedReader reader = new BufferedReader(new FileReader(LOG_FILE))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(",");
                    int commitId = Integer.parseInt(parts[0]);
                    lastCommitId = Math.max(lastCommitId, commitId);
                }
            } catch (IOException e) {
                e.printStackTrace(); // Handle or log the exception properly
            }
            this.nextLogId = lastCommitId+1;
        }
    }

    public int appendAndCommitLogEntries(List<LogEntry> logEntries){
        int lastCommitId = -1;
        for(int i=0;i<logEntries.size();i++){
            appendLogEntry(logEntries.get(i));
            int commitId = logEntries.get(i).getLogId();
            lastCommitId = Math.max(lastCommitId, commitId);
        }
        return  lastCommitId;
    }

    public LogEntry getLogEntry(int logId,int term) {
        synchronized (fileLock){
            try (BufferedReader reader = new BufferedReader(new FileReader(LOG_FILE))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(",");
                    int currentLogId = Integer.parseInt(parts[0]);
                    int currentTerm = Integer.parseInt(parts[1]);
                    if (currentLogId == logId && currentTerm == term) {
                        String orderDetails = parts[2];
                        return new LogEntry(logId,term,orderDetails);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();

            }
            return null;
        }
    }

    public void updateTxnStatus(int logId,int term, String txnStatus){
        synchronized (fileLock){
            try (BufferedReader reader = new BufferedReader(new FileReader(LOG_FILE))) {
                List<String> updatedLines = new ArrayList<>();
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(",");
                    int currentLogId = Integer.parseInt(parts[0]);
                    int currentTerm = Integer.parseInt(parts[1]);
                    if (currentLogId == logId && currentTerm == term) {
                        parts[3] = txnStatus; // Update the txnStatus
                        updatedLines.add(String.join(",", parts));
                    }else{
                        updatedLines.add(line);
                    }
                }
                try (PrintWriter writer = new PrintWriter(new FileWriter(LOG_FILE, false))) {
                    for (String updatedLine : updatedLines) {
                        writer.println(updatedLine);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    public List<LogEntry> getLostData(int lastCommittedId) {
        synchronized (fileLock){
            List<LogEntry> uncommittedEntries = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new FileReader(LOG_FILE))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(",");
                    int logId = Integer.parseInt(parts[0]);
                    int term = Integer.parseInt(parts[1]);
                    String orderDetails = parts[2];

                    if (logId>lastCommittedId) {
                        LogEntry logEntry = new LogEntry(logId, term, orderDetails);
                        uncommittedEntries.add(logEntry);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            return uncommittedEntries;
        }
    }
}
