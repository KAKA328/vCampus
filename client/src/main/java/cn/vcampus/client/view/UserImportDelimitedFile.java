package cn.vcampus.client.view;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Reads CSV/TSV account import tables. */
final class UserImportDelimitedFile {
    private UserImportDelimitedFile() {
    }

    static List<List<String>> read(Path file, char delimiter) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        List<List<String>> rows = new ArrayList<List<String>>();
        for (String line : lines) {
            rows.add(parseLine(stripBom(line), delimiter));
        }
        return rows;
    }

    private static List<String> parseLine(String line, char delimiter) {
        List<String> values = new ArrayList<String>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (ch == delimiter && !quoted) {
                values.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        values.add(current.toString().trim());
        return values;
    }

    private static String stripBom(String line) {
        return line != null && !line.isEmpty() && line.charAt(0) == '\uFEFF' ? line.substring(1) : line;
    }
}
