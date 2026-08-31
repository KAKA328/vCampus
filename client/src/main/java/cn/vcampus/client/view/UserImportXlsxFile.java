package cn.vcampus.client.view;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** Reads the first sheet from a simple .xlsx account import workbook. */
final class UserImportXlsxFile {
    private UserImportXlsxFile() {
    }

    static List<List<String>> read(Path file) throws IOException {
        Map<String, byte[]> entries = zipEntries(file);
        byte[] sheet = entries.get("xl/worksheets/sheet1.xml");
        if (sheet == null) {
            throw new IllegalArgumentException("Excel 文件缺少第一个工作表");
        }
        List<String> sharedStrings = readSharedStrings(entries.get("xl/sharedStrings.xml"));
        Document doc = xml(sheet);
        NodeList rowNodes = doc.getElementsByTagNameNS("*", "row");
        List<List<String>> rows = new ArrayList<List<String>>();
        for (int i = 0; i < rowNodes.getLength(); i++) {
            rows.add(rowValues((Element) rowNodes.item(i), sharedStrings));
        }
        return rows;
    }

    private static List<String> rowValues(Element row, List<String> sharedStrings) {
        List<String> values = new ArrayList<String>();
        NodeList cells = row.getElementsByTagNameNS("*", "c");
        for (int j = 0; j < cells.getLength(); j++) {
            Element cell = (Element) cells.item(j);
            int column = columnIndex(cell.getAttribute("r"));
            while (values.size() <= column) {
                values.add("");
            }
            values.set(column, cellValue(cell, sharedStrings));
        }
        return values;
    }

    private static Map<String, byte[]> zipEntries(Path file) throws IOException {
        Map<String, byte[]> entries = new HashMap<String, byte[]>();
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(file))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.put(entry.getName(), entryBytes(zip));
                zip.closeEntry();
            }
        }
        return entries;
    }

    private static byte[] entryBytes(ZipInputStream zip) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = zip.read(buffer)) >= 0) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static List<String> readSharedStrings(byte[] data) {
        List<String> strings = new ArrayList<String>();
        if (data == null) {
            return strings;
        }
        Document doc = xml(data);
        NodeList items = doc.getElementsByTagNameNS("*", "si");
        for (int i = 0; i < items.getLength(); i++) {
            strings.add(text(items.item(i)));
        }
        return strings;
    }

    private static Document xml(byte[] data) {
        try (InputStream input = new ByteArrayInputStream(data)) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            disableExternalXml(factory);
            return factory.newDocumentBuilder().parse(input);
        } catch (Exception failure) {
            throw new IllegalArgumentException("无法读取 Excel 文件内容", failure);
        }
    }

    private static void disableExternalXml(DocumentBuilderFactory factory) {
        try {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        } catch (Exception ignored) {
            // Some JDK XML parsers do not expose this feature.
        }
    }

    private static String cellValue(Element cell, List<String> sharedStrings) {
        String type = cell.getAttribute("t");
        String raw = childText(cell, "v");
        if ("s".equals(type)) {
            int index = raw.isEmpty() ? -1 : Integer.parseInt(raw);
            return index >= 0 && index < sharedStrings.size() ? sharedStrings.get(index) : "";
        }
        if ("inlineStr".equals(type)) {
            return childText(cell, "t");
        }
        return raw;
    }

    private static int columnIndex(String cellRef) {
        int index = 0;
        boolean found = false;
        for (int i = 0; i < cellRef.length(); i++) {
            char ch = Character.toUpperCase(cellRef.charAt(i));
            if (ch < 'A' || ch > 'Z') {
                break;
            }
            index = index * 26 + (ch - 'A' + 1);
            found = true;
        }
        return found ? index - 1 : 0;
    }

    private static String childText(Element parent, String localName) {
        NodeList nodes = parent.getElementsByTagNameNS("*", localName);
        return nodes.getLength() == 0 ? "" : text(nodes.item(0));
    }

    private static String text(Node node) {
        return node == null || node.getTextContent() == null ? "" : node.getTextContent().trim();
    }
}
