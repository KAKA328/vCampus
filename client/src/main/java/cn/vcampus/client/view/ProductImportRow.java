package cn.vcampus.client.view;

/** 商品批量导入的一行预览数据：名称/价格/库存/类别/说明，提交时逐行复用新增商品消息。 */
final class ProductImportRow {
    private final String name;
    private final double price;
    private final int stock;
    private final String category;
    private final String description;

    ProductImportRow(String name, double price, int stock, String category, String description) {
        this.name = name;
        this.price = price;
        this.stock = stock;
        this.category = category;
        this.description = description;
    }

    String getName() {
        return name;
    }

    double getPrice() {
        return price;
    }

    int getStock() {
        return stock;
    }

    String getCategory() {
        return category;
    }

    String getDescription() {
        return description;
    }
}
