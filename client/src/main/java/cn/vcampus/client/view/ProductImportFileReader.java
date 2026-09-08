package cn.vcampus.client.view;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 读取商品批量导入文件（.csv/.tsv），表头支持中英文，返回待新增的商品行。 */
final class ProductImportFileReader {
    List<ProductImportRow> read(Path file) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("请选择导入文件");
        }
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".csv")) {
            return rowsFromTable(UserImportDelimitedFile.read(file, ','));
        }
        if (name.endsWith(".tsv")) {
            return rowsFromTable(UserImportDelimitedFile.read(file, '\t'));
        }
        throw new IllegalArgumentException("仅支持 .csv、.tsv 商品导入文件");
    }

    private static List<ProductImportRow> rowsFromTable(List<List<String>> table) {
        if (table.isEmpty()) {
            return new ArrayList<ProductImportRow>();
        }
        Map<String, Integer> headers = headers(table.get(0));
        int name = requireColumn(headers, "name", "名称");
        int price = requireColumn(headers, "price", "价格");
        int stock = requireColumn(headers, "stock", "库存");
        int category = optionalColumn(headers, "category", "类别");
        int description = optionalColumn(headers, "description", "说明");
        List<ProductImportRow> rows = new ArrayList<ProductImportRow>();
        for (int i = 1; i < table.size(); i++) {
            List<String> values = table.get(i);
            if (isBlank(values)) {
                continue;
            }
            int line = i + 1;
            rows.add(new ProductImportRow(
                    value(values, name),
                    parsePrice(value(values, price), line),
                    parseStock(value(values, stock), line),
                    category < 0 ? "" : value(values, category),
                    description < 0 ? "" : value(values, description)));
        }
        return rows;
    }

    private static double parsePrice(String text, int line) {
        try {
            double price = Double.parseDouble(text);
            if (price < 0) {
                throw new NumberFormatException(text);
            }
            return price;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("第 " + line + " 行价格不正确：" + text);
        }
    }

    private static int parseStock(String text, int line) {
        try {
            int stock = Integer.parseInt(text);
            if (stock < 0) {
                throw new NumberFormatException(text);
            }
            return stock;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("第 " + line + " 行库存不正确：" + text);
        }
    }

    private static Map<String, Integer> headers(List<String> row) {
        Map<String, Integer> headers = new HashMap<String, Integer>();
        for (int i = 0; i < row.size(); i++) {
            headers.put(normalizeHeader(row.get(i)), i);
        }
        return headers;
    }

    private static int requireColumn(Map<String, Integer> headers, String english, String chinese) {
        Integer column = headers.get(normalizeHeader(english));
        if (column == null) {
            column = headers.get(normalizeHeader(chinese));
        }
        if (column == null) {
            throw new IllegalArgumentException("导入文件缺少列：" + chinese);
        }
        return column.intValue();
    }

    private static int optionalColumn(Map<String, Integer> headers, String english, String chinese) {
        Integer column = headers.get(normalizeHeader(english));
        if (column == null) {
            column = headers.get(normalizeHeader(chinese));
        }
        return column == null ? -1 : column.intValue();
    }

    private static String normalizeHeader(String value) {
        String text = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if ("name".equals(text) || "productname".equals(text) || "product_name".equals(text))
            return "name";
        if ("名称".equals(text) || "商品名称".equals(text))
            return "name";
        if ("price".equals(text) || "unitprice".equals(text) || "unit_price".equals(text))
            return "price";
        if ("价格".equals(text) || "单价".equals(text))
            return "price";
        if ("stock".equals(text) || "quantity".equals(text))
            return "stock";
        if ("库存".equals(text))
            return "stock";
        if ("category".equals(text))
            return "category";
        if ("类别".equals(text) || "分类".equals(text))
            return "category";
        if ("description".equals(text) || "desc".equals(text))
            return "description";
        if ("说明".equals(text) || "描述".equals(text))
            return "description";
        return text;
    }

    private static boolean isBlank(List<String> values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static String value(List<String> values, int index) {
        return index < values.size() && values.get(index) != null ? values.get(index).trim() : "";
    }
}
