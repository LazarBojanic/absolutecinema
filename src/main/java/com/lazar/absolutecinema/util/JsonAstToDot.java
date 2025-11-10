package com.lazar.absolutecinema.util;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;


public final class JsonAstToDot {
	private final StringBuilder sb = new StringBuilder();
	private int nextId = 0;

	private JsonAstToDot() {
	}

	public static String fromFile(Path jsonFile) {
		try {
			String json = Files.readString(jsonFile);
			return fromString(json);
		}
		catch (IOException e) {
			throw new RuntimeException("Failed to read ast.json", e);
		}
	}

	@SuppressWarnings("unchecked")
	public static String fromString(String json) {
		ObjectMapper mapper = new ObjectMapper();
		Object root;
		root = mapper.readValue(json, Object.class);
		return new JsonAstToDot().toDot(root);
	}

	public String toDot(Object root) {
		sb.setLength(0);
		sb.append("digraph AST {\n");
		sb.append("  node [shape=box, fontname=Courier, fontsize=11];\n");
		sb.append("  edge [fontname=Courier, fontsize=10];\n");
		sb.append("  rankdir=TB;\n");
		int rootId = emit(root, labelFor(root));
		sb.append("  root [label=\"program\", shape=oval, style=filled, fillcolor=lightgray];\n");
		sb.append("  root -> n").append(rootId).append(";\n");
		sb.append("}\n");
		return sb.toString();
	}

	private int id() {
		return nextId++;
	}

	private String esc(String s) {
		if (s == null) {
			return "null";
		}
		return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
	}

	private String labelFor(Object node) {
		switch (node) {
			case null -> {
				return "null";
			}
			case String s -> {
				return '"' + s + '"';
			}
			case Number n -> {
				return n.toString();
			}
			case Boolean b -> {
				return String.valueOf(b);
			}
			case List<?> objects -> {
				return "[]";
			}
			case Map<?, ?> m -> {
				Object type = m.get("type");
				if (type instanceof String ts) {
					return ts;
				}
				Object decl = m.get("decl");
				if (decl instanceof String ds) {
					return "decl:" + ds;
				}
				Object stmt = m.get("stmt");
				if (stmt instanceof String ss) {
					return "stmt:" + ss;
				}
				Object expr = m.get("expr");
				if (expr instanceof String es) {
					return "expr:" + es;
				}
				Object name = m.get("name");
				if (name instanceof String ns)
					return ns;
				return "{ }";
			}
			default -> {
			}
		}
		return String.valueOf(node);
	}

	private void node(int id, String label) {
		sb.append("  n").append(id).append(" [label=\"").append(esc(label)).append("\"];\n");
	}

	private void edge(int from, int to, String name) {
		sb.append("  n").append(from).append(" -> n").append(to);
		if (name != null && !name.isEmpty()) {
			sb.append(" [label=\"").append(esc(name)).append("\"];\n");
		}
		else {
			sb.append(";\n");
		}
	}

	@SuppressWarnings("unchecked")
	private int emit(Object node, String label) {
		int me = id();
		node(me, label);

		switch (node) {
			case null -> {
				return me;
			}
			case Map<?, ?> map -> {
				for (Map.Entry<?, ?> e : ((Map<Object, Object>) map).entrySet()) {
					String key = String.valueOf(e.getKey());
					Object val = e.getValue();
					if (("decl".equals(key) && label.startsWith("decl:")) ||
						("stmt".equals(key) && label.startsWith("stmt:")) ||
						("expr".equals(key) && label.startsWith("expr:"))) {
						continue;
					}
					int ch = emit(val, labelFor(valForKey(key, val)));
					edge(me, ch, key);
				}
			}
			case List<?> list -> {
				for (int i = 0; i < list.size(); i++) {
					Object val = list.get(i);
					int ch = emit(val, labelFor(val));
					edge(me, ch, String.valueOf(i));
				}
			}
			default -> {
			}
		}

		return me;
	}

	private Object valForKey(String key, Object val) {
		if (!(val instanceof Map<?, ?> m)) {
			return val;
		}
		Object name = m.get("name");
		Object expr = m.get("expr");
		Object stmt = m.get("stmt");
		Object decl = m.get("decl");
		if (name instanceof String ns && !(expr instanceof String) && !(stmt instanceof String) && !(decl instanceof String)) {
			return ns;
		}
		return val;
	}
}