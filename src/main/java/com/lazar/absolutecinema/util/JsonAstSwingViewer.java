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
import java.util.Iterator;
import java.util.Map; // Added import for Map
import java.util.Set; // Added import for Set

public final class JsonAstSwingViewer {
	private JsonAstSwingViewer() {}

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
		if (node.isTextual()) return "\"" + node.asText() + "\"";
		if (node.isNumber()) return node.asText();
		if (node.isBoolean()) return String.valueOf(node.booleanValue());
		if (node.isArray()) return "[]";
		if (node.isObject()) {
			if (node.has("expr")) return "expr:" + node.get("expr").asString();
			if (node.has("stmt")) return "stmt:" + node.get("stmt").asString();
			if (node.has("decl")) return "decl:" + node.get("decl").asString();
			if (node.has("name") && !(node.has("expr") || node.has("stmt") || node.has("decl"))) {
				return node.get("name").asString();
			}
			return "{ }";
		}
		return node.toString();
	}
}