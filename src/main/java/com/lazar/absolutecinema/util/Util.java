package com.lazar.absolutecinema.util;

import com.lazar.absolutecinema.lexer.Token;

import java.util.ArrayList;
import java.util.List;

public class Util {
	public static void printTokenTable(List<Token> tokens) {
		String[] headers = {"#", "Type", "Lexeme", "Literal", "Line", "Col"};
		boolean[] rightAlign = {true, false, false, false, true, true};

		List<String[]> rows = new ArrayList<>();
		int index = 1;
		for (var t : tokens) {
			String lexeme = escapeAndTrim(t.getLexeme(), 40);
			String literal = t.getLiteral() == null ? "" : escapeAndTrim(String.valueOf(t.getLiteral()), 40);
			rows.add(new String[]{
				String.valueOf(index++),
				String.valueOf(t.getType()),
				lexeme,
				literal,
				String.valueOf(t.getLine()),
				String.valueOf(t.getColumn())
			});
		}

		int[] widths = computeWidths(headers, rows);
		String border = buildBorder(widths);

		System.out.println(border);
		System.out.println(renderRow(headers, widths, new boolean[headers.length]));
		System.out.println(border);

		for (var r : rows) {
			System.out.println(renderRow(r, widths, rightAlign));
		}
		System.out.println(border);
	}

	private static String escapeAndTrim(String s, int maxLen) {
		if (s == null) {
			return "";
		}
		String escaped = s
			.replace("\\", "\\\\")
			.replace("\n", "\\n")
			.replace("\r", "\\r")
			.replace("\t", "\\t");
		if (escaped.length() > maxLen) {
			return escaped.substring(0, maxLen - 1) + "…";
		}
		return escaped;
	}

	private static String pad(String s, int width, boolean rightAlign) {
		if (s == null) {
			s = "";
		}
		int pad = width - s.length();
		if (pad <= 0) {
			return s;
		}
		String spaces = " ".repeat(pad);
		return rightAlign ? spaces + s : s + spaces;
	}

	private static String renderRow(String[] cells, int[] widths, boolean[] rightAlign) {
		StringBuilder line = new StringBuilder("|");
		for (int c = 0; c < widths.length; c++) {
			String cell = (c < cells.length && cells[c] != null) ? cells[c] : "";
			boolean right = (c < rightAlign.length) && rightAlign[c];
			line.append(' ').append(pad(cell, widths[c], right)).append(' ').append('|');
		}
		return line.toString();
	}

	private static String buildBorder(int[] widths) {
		StringBuilder sep = new StringBuilder();
		sep.append('+');
		for (int w : widths) {
			sep.append("-".repeat(w + 2)).append('+');
		}
		return sep.toString();
	}

	private static int[] computeWidths(String[] headers, List<String[]> rows) {
		int cols = headers.length;
		int[] widths = new int[cols];
		for (int c = 0; c < cols; c++) {
			widths[c] = headers[c].length();
		}
		for (var r : rows) {
			for (int c = 0; c < cols; c++) {
				String cell = (c < r.length && r[c] != null) ? r[c] : "";
				widths[c] = Math.max(widths[c], cell.length());
			}
		}
		return widths;
	}
}
