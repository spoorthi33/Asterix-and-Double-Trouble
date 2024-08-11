package dto;

public class LostOrdersDto {
    public LostOrdersDto(String name, int quantity, int orderId) {
        this.name = name;
        this.quantity = quantity;
        this.orderId = orderId;
    }

    public String getName() {
        return name;
    }

    public int getQuantity() {
        return quantity;
    }

    public int getOrderId() {
        return orderId;
    }

    private String name;
    private int quantity;
    private int orderId;

}
