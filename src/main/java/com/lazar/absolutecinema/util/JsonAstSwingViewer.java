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
			// Use fieldNames() for compatibility
			Iterator<String> it = obj.fieldNames();
			while (it.hasNext()) {
				String key = it.next();
				JsonNode val = obj.get(key);

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
		if (node != null && node.isObject() && node.has("type")) return node.get("type").asText();
		return "root";
	}

	private static String shortLabel(JsonNode node, String key) {
		if (node == null || node.isNull()) return "null";
		if (node.isTextual()) return node.asText();
		if (node.isNumber()) return node.asText();
		if (node.isBoolean()) return String.valueOf(node.booleanValue());
		if (node.isArray()) return "[]";
		if (node.isObject()) {
			ObjectNode o = (ObjectNode) node;
			if (o.has("expr")) return "expr:" + o.get("expr").asText();
			if (o.has("stmt")) return "stmt:" + o.get("stmt").asText();
			if (o.has("decl")) return "decl:" + o.get("decl").asText();
			if (o.has("name") && !(o.has("expr") || o.has("stmt") || o.has("decl"))) return o.get("name").asText();
			return "{ }";
		}
		return node.toString();
	}
}