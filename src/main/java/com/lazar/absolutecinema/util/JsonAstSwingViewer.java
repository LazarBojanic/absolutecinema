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
			try {
				JsonNode root = mapper.readTree(json);
				DefaultMutableTreeNode treeRoot = toTreeNode(root, rootLabel(root));
				JTree tree = new JTree(treeRoot);
				tree.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
				for (int i = 0; i < tree.getRowCount(); i++) {
					tree.expandRow(i);
				}
				JFrame f = new JFrame("AbsoluteCinema AST (JSON)");
				f.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
				f.add(new JScrollPane(tree), BorderLayout.CENTER);
				f.setSize(1000, 800);
				f.setLocationByPlatform(true);
				f.setVisible(true);
			}
			catch (Exception e) {
				e.printStackTrace();
			}
		});
	}

	private static DefaultMutableTreeNode toTreeNode(JsonNode node, String label) {
		DefaultMutableTreeNode me = new DefaultMutableTreeNode(label);
		if (node == null || node.isNull()) {
			return me;
		}
		if (node.isObject()) {
			ObjectNode obj = (ObjectNode) node;
			Set<Map.Entry<String, JsonNode>> properties = obj.properties();
			for (Map.Entry<String, JsonNode> property : properties) {
				String key = property.getKey();
				JsonNode val = property.getValue();
				
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
		return me;
	}

	private static String rootLabel(JsonNode node) {
		if (node != null && node.isObject() && node.has("type")) {
			JsonNode typeNode = node.get("type");
			return typeNode.isTextual() ? typeNode.asText() : "root";
		}
		return "root";
	}

	private static String shortLabel(JsonNode node, String key) {
		if (node == null || node.isNull()) {
			return "null";
		}
		if (node.isString()) {
			return "\"" + node.asText() + "\"";
		}
		if (node.isNumber()) {
			return node.asText();
		}
		if (node.isBoolean()) {
			return String.valueOf(node.booleanValue());
		}
		if (node.isArray()) {
			return "[]";
		}
		if (node.isObject()) {
			StringBuilder sb = new StringBuilder();
			
			if (node.has("expr") && node.get("expr").isTextual()) {
				sb.append("expr:").append(node.get("expr").asText());
			}
			else if (node.has("stmt") && node.get("stmt").isTextual()) {
				sb.append("stmt:").append(node.get("stmt").asText());
			}
			else if (node.has("decl") && node.get("decl").isTextual()) {
				sb.append("decl:").append(node.get("decl").asText());
			}
			else if (node.has("name") && node.get("name").isTextual()) {
				sb.append(node.get("name").asText());
			}
			else {
				sb.append("{ }");
			}
			
			if (node.has("resolvedType")) {
				JsonNode rt = node.get("resolvedType");
				sb.append(" <type: ").append(rt.isTextual() ? rt.asText() : "complex").append(">");
			}
			if (node.has("resolvedDecl")) {
				JsonNode rd = node.get("resolvedDecl");
				sb.append(" <ref: ").append(rd.isTextual() ? rd.asText() : "complex").append(">");
			}
			return sb.toString();
		}
		return node.toString();
	}
}