package com.lazar.absolutecinema.util;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

public final class JsonAstSwingViewer {
	private JsonAstSwingViewer() {
	}

	public static void show(Path jsonFile) {
		try {
			String json = Files.readString(jsonFile);
			showJson(json);
		}
		catch (IOException e) {
			throw new RuntimeException("Failed to read ast.json", e);
		}
	}

	public static void showJson(String json) {
		SwingUtilities.invokeLater(() -> {
			ObjectMapper mapper = new ObjectMapper();
			JsonNode root = mapper.readTree(json);
			DefaultMutableTreeNode treeRoot = toTreeNode(root, rootLabel(root));

			JTree tree = new JTree(treeRoot);
			tree.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
			for (int i = 0; i < tree.getRowCount(); i++) tree.expandRow(i);

			JFrame f = new JFrame("AbsoluteCinema AST (JSON)");
			f.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
			f.add(new JScrollPane(tree), BorderLayout.CENTER);
			f.setSize(1000, 800);
			f.setLocationByPlatform(true);
			f.setVisible(true);
		});
	}

	// ===== Helpers =====
	private static DefaultMutableTreeNode toTreeNode(JsonNode node, String label) {
		DefaultMutableTreeNode me = new DefaultMutableTreeNode(label);
		if (node == null || node.isNull()) return me;

		if (node.isObject()) {
			ObjectNode obj = (ObjectNode) node;
			// Use properties() which returns Set<Map.Entry<String, JsonNode>>
			Set<Map.Entry<String, JsonNode>> properties = obj.properties();
			for (Map.Entry<String, JsonNode> property : properties) {
				String key = property.getKey();
				JsonNode val = property.getValue();

				// Omit redundant label duplication (cosmetic only)
				if ((key.equals("decl") && label.startsWith("decl:")) ||
					(key.equals("stmt") && label.startsWith("stmt:")) ||
					(key.equals("expr") && label.startsWith("expr:"))) {
					continue;
				}

				String childLabelKey = key + ": " + shortLabel(val, key);
				DefaultMutableTreeNode child = toTreeNode(val, childLabelKey);
				me.add(child);
			}
		}
		else if (node.isArray()) {
			ArrayNode arr = (ArrayNode) node;
			for (int i = 0; i < arr.size(); i++) {
				JsonNode val = arr.get(i);
				DefaultMutableTreeNode child = toTreeNode(val, "[" + i + "] " + shortLabel(val, null));
				me.add(child);
			}
		}
		// primitives are leaves with their textual value already in the label
		return me;
	}

	private static String rootLabel(JsonNode node) {
		if (node != null && node.isObject() && node.has("type")) return node.get("type").asString();
		return "root";
	}

	private static String shortLabel(JsonNode node, String key) {
		if (node == null || node.isNull()) return "null";
		if (node.isString()) return "\"" + node.asString() + "\"";
		if (node.isNumber()) return node.asString();
		if (node.isBoolean()) return String.valueOf(node.booleanValue());
		if (node.isArray()) return "[]";

		if (node.isObject()) {
			// show expr/stmt/decl only if they are simple text
			if (node.has("expr")) {
				JsonNode expr = node.get("expr");
				return expr.isString() ? "expr:" + expr.asString() : "expr:{ }";
			}
			if (node.has("stmt")) {
				JsonNode stmt = node.get("stmt");
				return stmt.isString() ? "stmt:" + stmt.asString() : "stmt:{ }";
			}
			if (node.has("decl")) {
				JsonNode decl = node.get("decl");
				return decl.isString() ? "decl:" + decl.asString() : "decl:{ }";
			}
			if (node.has("name")) {
				JsonNode name = node.get("name");
				return name.isString() ? name.asString() : "{ }";
			}
			return "{ }";
		}

		return node.toString();
	}
}