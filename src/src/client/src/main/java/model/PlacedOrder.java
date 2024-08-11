package model;

public class PlacedOrder {
    public PlacedOrder(int orderId, String itemName, int quantity) {
        this.orderId = orderId;
        this.itemName = itemName;
        this.quantity = quantity;
    }

    public int getOrderId() {
        return orderId;
    }

    public void setOrderId(int orderId) {
        this.orderId = orderId;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    private int orderId;
    private String itemName;
    private int quantity;
}
