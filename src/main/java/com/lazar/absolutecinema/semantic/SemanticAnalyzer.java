package com.lazar.absolutecinema.semantic;

import com.lazar.absolutecinema.lexer.Token;
import com.lazar.absolutecinema.lexer.TokenType;
import com.lazar.absolutecinema.parser.ast.*;
import com.lazar.absolutecinema.parser.ast.Set;

import java.util.*;

public class SemanticAnalyzer implements DeclVisitor<Void>, StmtVisitor<Void>, ExprVisitor<ResolvedType> {
	private final Program program;
	private final SymbolTable symbolTable = new SymbolTable();
	private SetupDecl currentSetup = null;
	private SceneDecl currentScene = null;

	public SemanticAnalyzer(Program program) {
		this.program = program;
		registerBuiltins();
	}

	/**
	 * Registers the built-in functions (Standard Library) into the global scope.
	 */
	private void registerBuiltins() {
		// Register 'project' (printline): takes any type (modeled here as string for simplicity) and returns scrap
		Token projectToken = new Token(TokenType.IDENTIFIER, "project", null, 0, 0);
		Token paramToken = new Token(TokenType.IDENTIFIER, "value", null, 0, 0);
		Token stringTypeToken = new Token(TokenType.STRING, "string", null, 0, 0);
		Token scrapTypeToken = new Token(TokenType.SCRAP, "scrap", null, 0, 0);
		Param projectParam = new Param(paramToken, new LType(stringTypeToken, 0));
		SceneDecl projectScene = new SceneDecl(projectToken, List.of(projectParam), new LType(scrapTypeToken, 0), null, false);
		symbolTable.defineScene(projectScene);
		// Register 'capture' (readline): takes no arguments and returns string
		Token captureToken = new Token(TokenType.IDENTIFIER, "capture", null, 0, 0);
		SceneDecl captureScene = new SceneDecl(captureToken, new ArrayList<>(), new LType(stringTypeToken, 0), null, false);
		symbolTable.defineScene(captureScene);
	}

	public void analyze() {
		// Phase 1: Register top-level symbols (Setup, Scene, Global Var)
		for (Node item : program.items) {
			if (item instanceof SetupDecl d) {
				symbolTable.defineSetup(d);
			}
			else if (item instanceof SceneDecl d) {
				symbolTable.defineScene(d);
			}
			else if (item instanceof VarDecl d) {
				symbolTable.defineGlobalVar(d);
			}
		}
		// Phase 2: Recursive Enrichment
		for (Node item : program.items) {
			if (item instanceof Decl d) {
				d.accept(this);
			}
		}
	}

	@Override
	public Void visitSetup(SetupDecl d) {
		currentSetup = d;
		symbolTable.enterScope();
		for (VarDecl field : d.fields) {
			symbolTable.define(field.name, resolveType(field.type), field);
		}
		if (d.ctor != null) {
			symbolTable.enterScope();
			for (Param p : d.ctor.params) {
				symbolTable.define(p.name, resolveType(p.type), p);
			}
			d.ctor.body.accept(this);
			symbolTable.exitScope();
		}
		for (SceneDecl method : d.methods) {
			method.accept(this);
		}
		symbolTable.exitScope();
		currentSetup = null;
		return null;
	}

	@Override
	public Void visitScene(SceneDecl d) {
		SceneDecl prev = currentScene;
		currentScene = d;
		symbolTable.enterScope();
		for (Param p : d.params) {
			symbolTable.define(p.name, resolveType(p.type), p);
		}
		if (d.body != null) {
			d.body.accept(this);
		}
		symbolTable.exitScope();
		currentScene = prev;
		return null;
	}

	@Override
	public Void visitVar(VarDecl d) {
		ResolvedType type = resolveType(d.type);
		if (d.initializer != null) {
			ResolvedType init = d.initializer.accept(this);
			checkTypeMatch(type, init, d.name, "Type mismatch in initializer");
		}
		symbolTable.define(d.name, type, d);
		return null;
	}

	@Override
	public Void visitBlock(Block s) {
		symbolTable.enterScope();
		for (Node n : s.statements) {
			if (n instanceof Decl d) {
				d.accept(this);
			}
			else if (n instanceof Stmt st) {
				st.accept(this);
			}
		}
		symbolTable.exitScope();
		return null;
	}

	@Override
	public Void visitVar(Var s) {
		return s.decl.accept(this);
	}

	@Override
	public Void visitExpr(ExprStmt s) {
		s.expr.accept(this);
		return null;
	}

	@Override
	public Void visitIf(If s) {
		s.ifBranch.cond.accept(this);
		s.ifBranch.block.accept(this);
		for (Branch b : s.elifBranchList) {
			b.cond.accept(this);
			b.block.accept(this);
		}
		if (s.elseBranch != null) {
			s.elseBranch.block.accept(this);
		}
		return null;
	}

	@Override
	public Void visitWhile(While s) {
		s.condition.accept(this);
		s.body.accept(this);
		return null;
	}

	@Override
	public Void visitFor(For s) {
		symbolTable.enterScope();
		if (s.initializer instanceof Decl d) {
			d.accept(this);
		}
		else if (s.initializer instanceof Stmt st) {
			st.accept(this);
		}
		if (s.condition != null) {
			s.condition.accept(this);
		}
		if (s.increment != null) {
			s.increment.accept(this);
		}
		s.body.accept(this);
		symbolTable.exitScope();
		return null;
	}

	@Override
	public Void visitReturn(Return s) {
		if (currentScene == null) {
			throw new RuntimeException("'cut' (return) used outside of scene at line " + s.keyword.getLine());
		}
		ResolvedType actual = (s.value != null) ? s.value.accept(this) : ResolvedType.SCRAP;
		checkTypeMatch(resolveType(currentScene.returnType), actual, s.keyword, "Return type mismatch");
		return null;
	}

	@Override
	public Void visitBreak(Break s) {
		return null;
	}

	@Override
	public Void visitContinue(Continue s) {
		return null;
	}

	@Override
	public ResolvedType visitLiteral(Literal e) {
		ResolvedType type = ResolvedType.NULL;
		if (e.value instanceof Integer) {
			type = ResolvedType.INT;
		}
		else if (e.value instanceof Double) {
			type = ResolvedType.DOUBLE;
		}
		else if (e.value instanceof String) {
			type = ResolvedType.STRING;
		}
		else if (e.value instanceof Character) {
			type = ResolvedType.CHAR;
		}
		else if (e.value instanceof Boolean) {
			type = ResolvedType.BOOL;
		}
		e.setType(type);
		return type;
	}

	@Override
	public ResolvedType visitVariable(Variable e) {
		SymbolTable.Symbol sym = symbolTable.resolve(e.name);
		e.setType(sym.type);
		e.resolvedDecl = sym.declaration;
		return sym.type;
	}

	@Override
	public ResolvedType visitAssign(Assign e) {
		ResolvedType left = e.target.accept(this);
		ResolvedType right = e.value.accept(this);
		checkTypeMatch(left, right, e.op, "Assignment mismatch");
		e.setType(left);
		return left;
	}

	@Override
	public ResolvedType visitBinary(Binary e) {
		ResolvedType l = e.left.accept(this);
		ResolvedType r = e.right.accept(this);
		ResolvedType res = (l.isNumeric() && r.isNumeric()) ?
			((l == ResolvedType.DOUBLE || r == ResolvedType.DOUBLE) ? ResolvedType.DOUBLE : ResolvedType.INT) :
			ResolvedType.BOOL;
		e.setType(res);
		return res;
	}

	@Override
	public ResolvedType visitLogical(Logical e) {
		e.left.accept(this);
		e.right.accept(this);
		e.setType(ResolvedType.BOOL);
		return ResolvedType.BOOL;
	}

	@Override
	public ResolvedType visitUnary(Unary e) {
		ResolvedType t = e.right.accept(this);
		e.setType(t);
		return t;
	}

	@Override
	public ResolvedType visitGrouping(Grouping e) {
		ResolvedType t = e.expr.accept(this);
		e.setType(t);
		return t;
	}

	@Override
	public ResolvedType visitCall(Call e) {
		List<ResolvedType> args = new ArrayList<>();
		for (Expr a : e.arguments) {
			args.add(a.accept(this));
		}
		ResolvedType ret = ResolvedType.NULL;
		if (e.callee instanceof Variable v) {
			SceneDecl scene = symbolTable.getScene(v.name.getLexeme());
			if (scene == null) {
				throw new RuntimeException("Undefined scene: " + v.name.getLexeme() + " at line " + v.name.getLine());
			}
			validateArgs(scene.params, args, v.name);
			ret = resolveType(scene.returnType);
		}
		else if (e.callee instanceof Get g) {
			ResolvedType obj = g.object.accept(this);
			SetupDecl setup = symbolTable.getSetup(obj.name());
			if (setup == null) {
				throw new RuntimeException("Type '" + obj.name() + "' has no methods at line " + g.name.getLine());
			}
			for (SceneDecl m : setup.methods) {
				if (m.name.getLexeme().equals(g.name.getLexeme())) {
					validateArgs(m.params, args, g.name);
					ret = resolveType(m.returnType);
					break;
				}
			}
		}
		e.setType(ret);
		return ret;
	}

	@Override
	public ResolvedType visitGet(Get e) {
		ResolvedType obj = e.object.accept(this);
		SetupDecl setup = symbolTable.getSetup(obj.name());
		if (setup == null) {
			throw new RuntimeException("Cannot access member of non-setup type '" + obj.name() + "' at line " + e.name.getLine());
		}
		for (VarDecl f : setup.fields) {
			if (f.name.getLexeme().equals(e.name.getLexeme())) {
				ResolvedType t = resolveType(f.type);
				e.setType(t);
				return t;
			}
		}
		throw new RuntimeException("Field '" + e.name.getLexeme() + "' not found in setup '" + obj.name() + "'");
	}

	@Override
	public ResolvedType visitSet(Set e) {
		ResolvedType obj = e.object.accept(this);
		ResolvedType val = e.value.accept(this);
		SetupDecl setup = symbolTable.getSetup(obj.name());
		if (setup == null) {
			throw new RuntimeException("Cannot set member of non-setup type '" + obj.name() + "'");
		}
		ResolvedType fieldT = null;
		for (VarDecl f : setup.fields) {
			if (f.name.getLexeme().equals(e.name.getLexeme())) {
				fieldT = resolveType(f.type);
				break;
			}
		}
		if (fieldT == null) {
			throw new RuntimeException("Field '" + e.name.getLexeme() + "' not found in setup '" + obj.name() + "'");
		}
		checkTypeMatch(fieldT, val, e.name, "Field assignment mismatch");
		e.setType(val);
		return val;
	}

	@Override
	public ResolvedType visitIndex(Index e) {
		ResolvedType arr = e.array.accept(this);
		ResolvedType idx = e.index.accept(this);
		if (!idx.equals(ResolvedType.INT)) {
			throw new RuntimeException("Array index must be int, got " + idx.name());
		}
		if (arr.dimensions() <= 0) {
			throw new RuntimeException("Cannot index non-array type '" + arr.name() + "'");
		}
		ResolvedType res = new ResolvedType(arr.name(), arr.dimensions() - 1);
		e.setType(res);
		return res;
	}

	@Override
	public ResolvedType visitPostfix(Postfix e) {
		ResolvedType t = e.target.accept(this);
		e.setType(t);
		return t;
	}

	@Override
	public ResolvedType visitThis(This e) {
		if (currentSetup == null) {
			throw new RuntimeException("'@' (this) used outside of setup context at line " + e.atToken.getLine());
		}
		ResolvedType t = new ResolvedType(currentSetup.name.getLexeme(), 0);
		e.setType(t);
		return t;
	}

	@Override
	public ResolvedType visitActionNew(ActionNew e) {
		ResolvedType t = new ResolvedType(e.type.name.getLexeme(), e.type.dimension);
		e.setType(t);
		return t;
	}

	@Override
	public ResolvedType visitArrayLiteral(ArrayLiteral e) {
		if (e.elements.isEmpty()) {
			return ResolvedType.NULL;
		}
		ResolvedType first = e.elements.get(0).accept(this);
		ResolvedType t = new ResolvedType(first.name(), first.dimensions() + 1);
		e.setType(t);
		return t;
	}

	private ResolvedType resolveType(LType l) {
		return (l == null) ? ResolvedType.SCRAP : new ResolvedType(l.name.getLexeme(), l.dimension);
	}

	private void checkTypeMatch(ResolvedType exp, ResolvedType act, Token t, String m) {
		if (exp != ResolvedType.NULL && act != ResolvedType.NULL && !exp.equals(act)) {
			throw new RuntimeException(m + " at line " + t.getLine() + ". Expected " + exp.name() + " but got " + act.name());
		}
	}

	private void validateArgs(List<Param> params, List<ResolvedType> args, Token t) {
		if (params.size() != args.size()) {
			throw new RuntimeException("Argument count mismatch for '" + t.getLexeme() + "'. Expected " + params.size() + " but got " + args.size());
		}
		for (int i = 0; i < params.size(); i++) {
			checkTypeMatch(resolveType(params.get(i).type), args.get(i), t, "Parameter type mismatch");
		}
	}

	private static class SymbolTable {
		record Symbol(ResolvedType type, Node declaration) {
		}

		private final List<Map<String, Symbol>> scopes = new ArrayList<>();
		private final Map<String, SetupDecl> setups = new HashMap<>();
		private final Map<String, SceneDecl> scenes = new HashMap<>();

		SymbolTable() {
			enterScope();
		}

		void enterScope() {
			scopes.add(new HashMap<>());
		}

		void exitScope() {
			scopes.remove(scopes.size() - 1);
		}

		void define(Token n, ResolvedType t, Node d) {
			scopes.get(scopes.size() - 1).put(n.getLexeme(), new Symbol(t, d));
		}

		void defineSetup(SetupDecl d) {
			setups.put(d.name.getLexeme(), d);
		}

		void defineScene(SceneDecl d) {
			scenes.put(d.name.getLexeme(), d);
		}

		void defineGlobalVar(VarDecl v) {
			define(v.name, new ResolvedType(v.type.name.getLexeme(), v.type.dimension), v);
		}

		Symbol resolve(Token n) {
			for (int i = scopes.size() - 1; i >= 0; i--) {
				if (scopes.get(i).containsKey(n.getLexeme())) {
					return scopes.get(i).get(n.getLexeme());
				}
			}
			throw new RuntimeException("Undefined symbol: " + n.getLexeme() + " at line " + n.getLine());
		}

		SetupDecl getSetup(String n) {
			return setups.get(n);
		}

		SceneDecl getScene(String n) {
			return scenes.get(n);
		}
	}
}