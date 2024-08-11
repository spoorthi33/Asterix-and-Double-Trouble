package model;

public class OrderServerReplica {
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public OrderServerReplica(int id, String url) {
        this.id = id;
        this.url = url;
    }

    private int id;
    private String url;
}
