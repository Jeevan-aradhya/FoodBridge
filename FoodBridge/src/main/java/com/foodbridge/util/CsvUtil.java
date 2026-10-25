package com.foodbridge.util;

import java.util.List;

/** Tiny CSV writer. Also neutralises spreadsheet formula injection (cells starting with = + - @). */
public final class CsvUtil {

    private CsvUtil() { }

    public static String toCsv(List<String> header, List<List<String>> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append(line(header));
        for (List<String> row : rows) {
            sb.append(line(row));
        }
        return sb.toString();
    }

    private static String line(List<String> cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escape(cells.get(i)));
        }
        return sb.append("\r\n").toString();
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        String v = value;
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            v = "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }
}
