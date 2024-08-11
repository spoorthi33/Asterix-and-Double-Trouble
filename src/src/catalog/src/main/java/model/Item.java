package model;

public class Item {
    public Item(String name, Integer quantity, Double price) {
        this.name = name;
        this.quantity = quantity;
        this.price = price;
    }

    public String getName() {
        return name;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public Double getPrice() {
        return price;
    }


    public void setName(String name) {
        this.name = name;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    private String name;
    private Integer quantity;
    private Double price;

    @Override
    public String toString(){
        return "{\"name\":\""+name+"\",\"quantity\":"+quantity+",\"price\":"+price+"}";
    }
}
