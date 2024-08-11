package raft;

public class LogEntry {
    private int logId;
    private int term;
    private String orderDetails;
    private String txnStatus;

    public LogEntry(int logId, int term, String orderDetails) {
        this.logId = logId;
        this.term = term;
        this.orderDetails = orderDetails;
        this.txnStatus = "P"; // In pending state, waiting for vote.
    }

    public int getLogId() {
        return logId;
    }

    public int getTerm() {
        return term;
    }

    public String getOrderDetails() {
        return orderDetails;
    }

    public String getTxnStatus() {
        return txnStatus;
    }

    public void setTxnStatus(String committed) {
        this.txnStatus = txnStatus;
    }
}
