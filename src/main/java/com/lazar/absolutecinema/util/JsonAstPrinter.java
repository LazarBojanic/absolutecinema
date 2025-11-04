package com.lazar.absolutecinema.util;

import com.lazar.absolutecinema.parser.ast.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

public final class JsonAstPrinter {
	private final ObjectMapper mapper = new ObjectMapper();

	public String print(Program program) {
		ObjectNode root = mapper.createObjectNode();
		root.put("type", "program");
		ArrayNode decls = mapper.createArrayNode();
		for (Decl d : program.declarations) {
			decls.add(printDecl(d));
		}
		root.set("declarations", decls);
		try {
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to serialize AST to JSON", e);
		}
	}

	private ObjectNode printDecl(Decl d) {
		if (d instanceof SetupDecl s) {
			ObjectNode o = mapper.createObjectNode();
			o.put("decl", "setup");
			o.put("name", s.name.getLexeme());
			ArrayNode fields = mapper.createArrayNode();
			for (VarDecl f : s.fields) fields.add(printVarDecl(f));
			o.set("fields", fields);
			if (s.ctor != null) o.set("ctor", printCtor(s.ctor));
			ArrayNode methods = mapper.createArrayNode();
			for (SceneDecl m : s.methods) methods.add(printScene(m));
			o.set("methods", methods);
			return o;
		}
		if (d instanceof SceneDecl s) {
			ObjectNode o = printScene(s);
			o.put("decl", "scene");
			return o;
		}
		if (d instanceof VarDecl v) {
			ObjectNode o = printVarDecl(v);
			o.put("decl", "var");
			return o;
		}
		ObjectNode o = mapper.createObjectNode();
		o.put("decl", "unknown");
		return o;
	}

	private ObjectNode printCtor(ConstructorDecl c) {
		ObjectNode o = mapper.createObjectNode();
		o.put("kind", "ctor");
		o.put("name", c.name.getLexeme());
		o.set("params", printParams(c.params));
		o.set("body", printBlock(c.body));
		return o;
	}

	private ObjectNode printScene(SceneDecl s) {
		ObjectNode o = mapper.createObjectNode();
		o.put("kind", "scene");
		o.put("name", s.name.getLexeme());
		o.put("isMethod", s.isMethod);
		o.set("returnType", printType(s.returnType));
		o.set("params", printParams(s.params));
		o.set("body", printBlock(s.body));
		return o;
	}

	private ArrayNode printParams(java.util.List<Param> params) {
		ArrayNode a = mapper.createArrayNode();
		for (Param p : params) {
			ObjectNode o = mapper.createObjectNode();
			o.put("name", p.name.getLexeme());
			o.set("type", printType(p.type));
			a.add(o);
		}
		return a;
	}

	private ObjectNode printType(TypeRef t) {
		ObjectNode o = mapper.createObjectNode();
		o.put("name", t.name.getLexeme());
		o.put("arrayDepth", t.arrayDepth);
		return o;
	}

	private ArrayNode printBlock(Block b) {
		ArrayNode a = mapper.createArrayNode();
		for (Node n : b.statements) {
			if (n instanceof Decl d) a.add(printDecl(d));
			else if (n instanceof Stmt s) a.add(printStmt(s));
		}
		return a;
	}

	private ObjectNode printStmt(Stmt s) {
		if (s instanceof Block b) {
			ObjectNode o = mapper.createObjectNode();
			o.put("stmt", "block");
			o.set("body", printBlock(b));
			return o;
		}
		if (s instanceof Var v) {
			ObjectNode o = mapper.createObjectNode();
			o.put("stmt", "var");
			o.set("decl", printVarDecl(v.decl));
			return o;
		}
		if (s instanceof ExprStmt e) {
			ObjectNode o = mapper.createObjectNode();
			o.put("stmt", "expr");
			o.set("expr", printExpr(e.expr));
			return o;
		}
		if (s instanceof If i) {
			ObjectNode o = mapper.createObjectNode();
			o.put("stmt", "if");
			o.set("cond", printExpr(i.condition));
			o.set("then", printStmt(i.thenBranch));
			if (i.elseBranch != null) o.set("else", printStmt(i.elseBranch));
			return o;
		}
		if (s instanceof While w) {
			ObjectNode o = mapper.createObjectNode();
			o.put("stmt", "while");
			o.set("cond", printExpr(w.condition));
			o.set("body", printStmt(w.body));
			return o;
		}
		if (s instanceof For f) {
			ObjectNode o = mapper.createObjectNode();
			o.put("stmt", "for");
			if (f.initializer != null) o.set("init", printNode(f.initializer));
			if (f.condition != null) o.set("cond", printExpr(f.condition));
			if (f.increment != null) o.set("incr", printExpr(f.increment));
			o.set("body", printStmt(f.body));
			return o;
		}
		if (s instanceof Return r) {
			ObjectNode o = mapper.createObjectNode();
			o.put("stmt", "return");
			if (r.value != null) o.set("value", printExpr(r.value));
			else o.putNull("value");
			return o;
		}
		if (s instanceof Break) {
			ObjectNode o = mapper.createObjectNode();
			o.put("stmt", "break");
			return o;
		}
		if (s instanceof Continue) {
			ObjectNode o = mapper.createObjectNode();
			o.put("stmt", "continue");
			return o;
		}
		ObjectNode o = mapper.createObjectNode();
		o.put("stmt", "unknown");
		return o;
	}

	private ObjectNode printVarDecl(VarDecl v) {
		ObjectNode o = mapper.createObjectNode();
		o.put("name", v.name.getLexeme());
		o.set("type", printType(v.type));
		if (v.initializer != null) o.set("init", printExpr(v.initializer));
		return o;
	}

	private JsonNode printNode(Node n) {
		if (n instanceof Decl d) return printDecl(d);
		if (n instanceof Stmt s) return printStmt(s);
		ObjectNode o = mapper.createObjectNode();
		o.put("node", "unknown");
		return o;
	}

	private JsonNode printExpr(Expr e) {
		if (e instanceof Literal l) return printLiteral(l);
		if (e instanceof Variable v) {
			ObjectNode o = mapper.createObjectNode();
			o.put("expr", "var");
			o.put("name", v.name.getLexeme());
			return o;
		}
		if (e instanceof Assign a) {
			ObjectNode o = mapper.createObjectNode();
			o.put("expr", "assign");
			o.set("target", printExpr(a.target));
			o.put("op", a.op.getLexeme());
			o.set("value", printExpr(a.value));
			return o;
		}
		if (e instanceof Binary b) {
			ObjectNode o = mapper.createObjectNode();
			o.put("expr", "binary");
			o.put("op", b.op.getLexeme());
			o.set("left", printExpr(b.left));
			o.set("right", printExpr(b.right));
			return o;
		}
		if (e instanceof Logical l) {
			ObjectNode o = mapper.createObjectNode();
			o.put("expr", "logical");
			o.put("op", l.op.getLexeme());
			o.set("left", printExpr(l.left));
			o.set("right", printExpr(l.right));
			return o;
		}
		if (e instanceof Unary u) {
			ObjectNode o = mapper.createObjectNode();
			o.put("expr", "unary");
			o.put("op", u.op.getLexeme());
			o.set("right", printExpr(u.right));
			return o;
		}
		if (e instanceof Grouping g) {
			ObjectNode o = mapper.createObjectNode();
			o.put("expr", "group");
			o.set("inner", printExpr(g.expr));
			return o;
		}
		if (e instanceof Call c) {
			ObjectNode o = mapper.createObjectNode();
			o.put("expr", "call");
			o.set("callee", printExpr(c.callee));
			ArrayNode args = mapper.createArrayNode();
			for (Expr ex : c.arguments) args.add(printExpr(ex));
			o.set("args", args);
			return o;
		}
		if (e instanceof Get g) {
			ObjectNode o = mapper.createObjectNode();
			o.put("expr", "get");
			o.set("object", printExpr(g.object));
			o.put("name", g.name.getLexeme());
			return o;
		}
		if (e instanceof Set s) {
			ObjectNode o = mapper.createObjectNode();
			o.put("expr", "set");
			o.set("object", printExpr(s.object));
			o.put("name", s.name.getLexeme());
			o.put("op", s.op.getLexeme());
			o.set("value", printExpr(s.value));
			return o;
		}
		if (e instanceof Index i) {
			ObjectNode o = mapper.createObjectNode();
			o.put("expr", "index");
			o.set("array", printExpr(i.array));
			o.set("index", printExpr(i.index));
			return o;
		}
		if (e instanceof Postfix p) {
			ObjectNode o = mapper.createObjectNode();
			o.put("expr", "postfix");
			o.set("target", printExpr(p.target));
			o.put("op", p.op.getLexeme());
			return o;
		}
		if (e instanceof This) {
			ObjectNode o = mapper.createObjectNode();
			o.put("expr", "this");
			return o;
		}
		if (e instanceof ActionNew n) {
			ObjectNode o = mapper.createObjectNode();
			o.put("expr", "action");
			o.set("type", printType(n.type));
			if (n.args != null) {
				ArrayNode a = mapper.createArrayNode();
				for (Expr ex : n.args) a.add(printExpr(ex));
				o.set("args", a);
			}
			if (n.arrayCapacity != null) o.set("capacity", printExpr(n.arrayCapacity));
			if (n.arrayInitializer != null) {
				ArrayNode a = mapper.createArrayNode();
				for (Expr ex : n.arrayInitializer) a.add(printExpr(ex));
				o.set("initializer", a);
			}
			return o;
		}
		if (e instanceof ArrayLiteral al) {
			ObjectNode o = mapper.createObjectNode();
			o.put("expr", "arrayLiteral");
			ArrayNode a = mapper.createArrayNode();
			for (Expr ex : al.elements) a.add(printExpr(ex));
			o.set("elements", a);
			return o;
		}
		ObjectNode o = mapper.createObjectNode();
		o.put("expr", "unknown");
		return o;
	}

	private JsonNode printLiteral(Literal l) {
		Object v = l.value;
		if (v == null) return mapper.getNodeFactory().nullNode();
		if (v instanceof String s) return mapper.getNodeFactory().textNode(s);
		if (v instanceof Character c) return mapper.getNodeFactory().textNode(c.toString());
		if (v instanceof Boolean b) return mapper.getNodeFactory().booleanNode(b);
		if (v instanceof Integer i) return mapper.getNodeFactory().numberNode(i);
		if (v instanceof Long ln) return mapper.getNodeFactory().numberNode(ln);
		if (v instanceof Double d) return mapper.getNodeFactory().numberNode(d);
		if (v instanceof Float f) return mapper.getNodeFactory().numberNode(f);
		// Fallback to string representation
		return mapper.getNodeFactory().textNode(String.valueOf(v));
	}
}