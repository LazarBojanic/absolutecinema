package com.lazar.absolutecinema.util;

import com.lazar.absolutecinema.parser.ast.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

public final class AstJsonConverter {
	private final ObjectMapper mapper = new ObjectMapper();

	public String convert(Program program) {
		ObjectNode root = mapper.createObjectNode();
		root.put("type", "program");
		ArrayNode decls = mapper.createArrayNode();
		for (Node n : program.items) {
			decls.add(convertNode(n));
		}
		root.set("declarations", decls);
		try {
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to serialize AST to JSON", e);
		}
	}

	private ObjectNode convertDecl(Decl d) {
		switch (d) {
			case SetupDecl s -> {
				ObjectNode o = mapper.createObjectNode();
				o.put("decl", "setup");
				o.put("name", s.name.getLexeme());
				ArrayNode fields = mapper.createArrayNode();
				for (VarDecl f : s.fields) fields.add(convertVarDecl(f));
				o.set("fields", fields);
				if (s.ctor != null) o.set("ctor", convertCtor(s.ctor));
				ArrayNode methods = mapper.createArrayNode();
				for (SceneDecl m : s.methods) methods.add(convertScene(m));
				o.set("methods", methods);
				return o;
			}
			case SceneDecl s -> {
				ObjectNode o = convertScene(s);
				o.put("decl", "scene");
				return o;
			}
			case VarDecl v -> {
				ObjectNode o = convertVarDecl(v);
				o.put("decl", "var");
				return o;
			}
			case null, default -> {
			}
		}
		ObjectNode o = mapper.createObjectNode();
		o.put("decl", "unknown");
		return o;
	}

	private ObjectNode convertCtor(ConstructorDecl c) {
		ObjectNode o = mapper.createObjectNode();
		o.put("kind", "ctor");
		o.put("name", c.name.getLexeme());
		o.set("params", convertParams(c.params));
		o.set("body", convertBlock(c.body));
		return o;
	}

	private ObjectNode convertScene(SceneDecl s) {
		ObjectNode o = mapper.createObjectNode();
		o.put("kind", "scene");
		o.put("name", s.name.getLexeme());
		o.put("isMethod", s.isMethod);
		o.set("returnType", convertType(s.returnType));
		o.set("params", convertParams(s.params));
		o.set("body", convertBlock(s.body));
		return o;
	}

	private ArrayNode convertParams(java.util.List<Param> params) {
		ArrayNode a = mapper.createArrayNode();
		for (Param p : params) {
			ObjectNode o = mapper.createObjectNode();
			o.put("name", p.name.getLexeme());
			o.set("type", convertType(p.type));
			a.add(o);
		}
		return a;
	}

	private ObjectNode convertType(TypeRef t) {
		ObjectNode o = mapper.createObjectNode();
		o.put("name", t.name.getLexeme());
		o.put("arrayDepth", t.arrayCapacities.size());
		for (int i = 0; i < t.arrayCapacities.size(); i++) {
			o.put("arrayCapacities[" + i + "]", t.arrayCapacities.get(i).getLexeme());
		}
		return o;
	}

	private ArrayNode convertBlock(Block b) {
		ArrayNode a = mapper.createArrayNode();
		for (Node n : b.statements) {
			switch (n) {
				case Decl d -> {
					a.add(convertDecl(d));
				}
				case Stmt s -> {
					a.add(convertStmt(s));
				}
				case null, default -> {
				}
			}
		}
		return a;
	}

	private ObjectNode convertStmt(Stmt s) {
		switch (s) {
			case Block b -> {
				ObjectNode o = mapper.createObjectNode();
				o.put("stmt", "block");
				o.set("body", convertBlock(b));
				return o;
			}
			case Var v -> {
				ObjectNode o = mapper.createObjectNode();
				o.put("stmt", "var");
				o.set("decl", convertVarDecl(v.decl));
				return o;
			}
			case ExprStmt e -> {
				ObjectNode o = mapper.createObjectNode();
				o.put("stmt", "expr");
				o.set("expr", convertExpr(e.expr));
				return o;
			}
			case If i -> {
				ObjectNode o = mapper.createObjectNode();
				o.put("stmt", "if");
				o.set("cond", convertExpr(i.condition));
				o.set("then", convertStmt(i.thenBranch));
				if (i.elseBranch != null) o.set("else", convertStmt(i.elseBranch));
				return o;
			}
			case While w -> {
				ObjectNode o = mapper.createObjectNode();
				o.put("stmt", "while");
				o.set("cond", convertExpr(w.condition));
				o.set("body", convertStmt(w.body));
				return o;
			}
			case For f -> {
				ObjectNode o = mapper.createObjectNode();
				o.put("stmt", "for");
				if (f.initializer != null) o.set("init", convertNode(f.initializer));
				if (f.condition != null) o.set("cond", convertExpr(f.condition));
				if (f.increment != null) o.set("incr", convertExpr(f.increment));
				o.set("body", convertStmt(f.body));
				return o;
			}
			case Return r -> {
				ObjectNode o = mapper.createObjectNode();
				o.put("stmt", "return");
				if (r.value != null) o.set("value", convertExpr(r.value));
				else o.putNull("value");
				return o;
			}
			case Break aBreak -> {
				ObjectNode o = mapper.createObjectNode();
				o.put("stmt", "break");
				return o;
			}
			case Continue aContinue -> {
				ObjectNode o = mapper.createObjectNode();
				o.put("stmt", "continue");
				return o;
			}
			case null, default -> {
			}
		}
		ObjectNode o = mapper.createObjectNode();
		o.put("stmt", "unknown");
		return o;
	}

	private ObjectNode convertVarDecl(VarDecl v) {
		ObjectNode o = mapper.createObjectNode();
		o.put("name", v.name.getLexeme());
		o.set("type", convertType(v.type));
		if (v.initializer != null) {
			o.set("init", convertExpr(v.initializer));
		}
		return o;
	}

	private JsonNode convertNode(Node n) {
		switch (n) {
			case Decl d -> {
				return convertDecl(d);
			}
			case Stmt s -> {
				return convertStmt(s);
			}
			case null, default -> {
			}
		}
		ObjectNode o = mapper.createObjectNode();
		o.put("node", "unknown");
		return o;
	}

	private JsonNode convertExpr(Expr e) {
		switch (e) {
			case Literal l -> {
				return convertLiteral(l);
			}
			case Variable v -> {
				ObjectNode o = mapper.createObjectNode();
				o.put("expr", "var");
				o.put("name", v.name.getLexeme());
				return o;
			}
			case Assign a -> {
				ObjectNode o = mapper.createObjectNode();
				o.put("expr", "assign");
				o.set("target", convertExpr(a.target));
				o.put("op", a.op.getLexeme());
				o.set("value", convertExpr(a.value));
				return o;
			}
			case Binary b -> {
				ObjectNode o = mapper.createObjectNode();
				o.put("expr", "binary");
				o.put("op", b.op.getLexeme());
				o.set("left", convertExpr(b.left));
				o.set("right", convertExpr(b.right));
				return o;
			}
			case Logical l -> {
				ObjectNode o = mapper.createObjectNode();
				o.put("expr", "logical");
				o.put("op", l.op.getLexeme());
				o.set("left", convertExpr(l.left));
				o.set("right", convertExpr(l.right));
				return o;
			}
			case Unary u -> {
				ObjectNode o = mapper.createObjectNode();
				o.put("expr", "unary");
				o.put("op", u.op.getLexeme());
				o.set("right", convertExpr(u.right));
				return o;
			}
			case Grouping g -> {
				ObjectNode o = mapper.createObjectNode();
				o.put("expr", "group");
				o.set("inner", convertExpr(g.expr));
				return o;
			}
			case Call c -> {
				ObjectNode o = mapper.createObjectNode();
				o.put("expr", "call");
				o.set("callee", convertExpr(c.callee));
				ArrayNode args = mapper.createArrayNode();
				for (Expr ex : c.arguments) args.add(convertExpr(ex));
				o.set("args", args);
				return o;
			}
			case Get g -> {
				ObjectNode o = mapper.createObjectNode();
				o.put("expr", "get");
				o.set("object", convertExpr(g.object));
				o.put("name", g.name.getLexeme());
				return o;
			}
			case Set s -> {
				ObjectNode o = mapper.createObjectNode();
				o.put("expr", "set");
				o.set("object", convertExpr(s.object));
				o.put("name", s.name.getLexeme());
				o.put("op", s.op.getLexeme());
				o.set("value", convertExpr(s.value));
				return o;
			}
			case Index i -> {
				ObjectNode o = mapper.createObjectNode();
				o.put("expr", "index");
				o.set("array", convertExpr(i.array));
				o.set("index", convertExpr(i.index));
				return o;
			}
			case Postfix p -> {
				ObjectNode o = mapper.createObjectNode();
				o.put("expr", "postfix");
				o.set("target", convertExpr(p.target));
				o.put("op", p.op.getLexeme());
				return o;
			}
			case This aThis -> {
				ObjectNode o = mapper.createObjectNode();
				o.put("expr", "this");
				return o;
			}
			case ActionNew n -> {
				ObjectNode o = mapper.createObjectNode();
				o.put("expr", "action");
				o.set("type", convertType(n.type));
				if (n.args != null) {
					ArrayNode a = mapper.createArrayNode();
					for (Expr ex : n.args) a.add(convertExpr(ex));
					o.set("args", a);
				}
				if (n.arrayInitializer != null) {
					ArrayNode a = mapper.createArrayNode();
					for (Expr ex : n.arrayInitializer) a.add(convertExpr(ex));
					o.set("initializer", a);
				}
				return o;
			}
			case ArrayLiteral al -> {
				ObjectNode o = mapper.createObjectNode();
				o.put("expr", "arrayLiteral");
				ArrayNode a = mapper.createArrayNode();
				for (Expr ex : al.elements) a.add(convertExpr(ex));
				o.set("elements", a);
				return o;
			}
			case null, default -> {
			}
		}
		ObjectNode o = mapper.createObjectNode();
		o.put("expr", "unknown");
		return o;
	}

	private JsonNode convertLiteral(Literal l) {
		Object v = l.value;
		return switch (v) {
			case null -> mapper.getNodeFactory().nullNode();
			case String s -> mapper.getNodeFactory().stringNode(s);
			case Character c -> mapper.getNodeFactory().stringNode(c.toString());
			case Boolean b -> mapper.getNodeFactory().booleanNode(b);
			case Integer i -> mapper.getNodeFactory().numberNode(i);
			case Long ln -> mapper.getNodeFactory().numberNode(ln);
			case Double d -> mapper.getNodeFactory().numberNode(d);
			case Float f -> mapper.getNodeFactory().numberNode(f);
			default -> mapper.getNodeFactory().stringNode(String.valueOf(v));
		};
	}
}