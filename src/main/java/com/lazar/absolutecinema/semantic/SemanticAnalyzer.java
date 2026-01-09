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

	private void registerBuiltins() {
		Token projectToken = new Token(TokenType.IDENTIFIER, "project", null, 0, 0);
		Token paramToken = new Token(TokenType.IDENTIFIER, "value", null, 0, 0);
		Token stringTypeToken = new Token(TokenType.STRING, "string", null, 0, 0);
		Token scrapTypeToken = new Token(TokenType.SCRAP, "scrap", null, 0, 0);
		Param projectParam = new Param(paramToken, new LType(stringTypeToken, 0));
		SceneDecl projectScene =
			new SceneDecl(projectToken, List.of(projectParam), new LType(scrapTypeToken, 0), null, false);
		symbolTable.defineScene(projectScene);
		Token captureToken = new Token(TokenType.IDENTIFIER, "capture", null, 0, 0);
		SceneDecl captureScene =
			new SceneDecl(captureToken, new ArrayList<>(), new LType(stringTypeToken, 0), null, false);
		symbolTable.defineScene(captureScene);
	}

	public void analyze() {
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
			if (d.initializer instanceof Call call) {
				if (call.callee instanceof Variable var && var.name.getLexeme().equals("capture")) {
					symbolTable.define(d.name, type, d);
					return null;
				}
			}
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
			else {
				((Stmt) n).accept(this);
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
	public Void visitBreak(Break s) {
		return null;
	}

	@Override
	public Void visitContinue(Continue s) {
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
			throw new RuntimeException("'cut' outside scene at line " + s.keyword.getLine());
		}
		ResolvedType actual = (s.value != null) ? s.value.accept(this) : ResolvedType.SCRAP;
		checkTypeMatch(resolveType(currentScene.returnType), actual, s.keyword, "Return type mismatch");
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
		e.resolvedDecl = sym.declaration;
		e.setType(sym.type);
		return sym.type;
	}

	@Override
	public ResolvedType visitAssign(Assign e) {
		ResolvedType left = e.target.accept(this);
		ResolvedType right = e.value.accept(this);
		if (e.value instanceof Call call) {
			if (call.callee instanceof Variable var && var.name.getLexeme().equals("capture")) {
				e.setType(left);
				return left;
			}
		}
		checkTypeMatch(left, right, e.op, "Assignment mismatch");
		e.setType(left);
		return left;
	}

	@Override
	public ResolvedType visitBinary(Binary e) {
		ResolvedType l = e.left.accept(this);
		ResolvedType r = e.right.accept(this);
		if (e.op.getLexeme().equals("+") &&
			(l.equals(ResolvedType.STRING) || r.equals(ResolvedType.STRING))) {
			e.setType(ResolvedType.STRING);
			return ResolvedType.STRING;
		}
		ResolvedType res =
			(l.isNumeric() && r.isNumeric())
				? ((l == ResolvedType.DOUBLE || r == ResolvedType.DOUBLE)
				? ResolvedType.DOUBLE
				: ResolvedType.INT)
				: ResolvedType.BOOL;
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
		String op = e.op.getLexeme();
		
		if (op.equals("int") || op.equals("double")) {
			ResolvedType targetType = new ResolvedType(op, 0);
			
			if (e.right instanceof Literal lit && lit.value instanceof Double doubleVal) {
				
				if (op.equals("int")) {
					if (doubleVal == Math.floor(doubleVal)) {
						e.setType(targetType);
						return targetType;
					}
					else {
						throw new RuntimeException("Cannot cast " + doubleVal +
							" to int: decimal part is not all zeros at line " + e.op.getLine());
					}
				}
			}
			
			if (op.equals("double") && t.equals(ResolvedType.INT)) {
				e.setType(targetType);
				return targetType;
			}
			
			if (op.equals("int") && t.equals(ResolvedType.DOUBLE)) {
				e.setType(targetType);
				return targetType;
			}
			throw new RuntimeException("Invalid cast from " + t.name() + " to " + op +
				" at line " + e.op.getLine());
		}
		
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
			String funcName = v.name.getLexeme();
			if (funcName.equals("capture")) {
				ret = ResolvedType.STRING;
			}
			else {
				SceneDecl scene = symbolTable.getScene(funcName);
				if (scene == null) {
					throw new RuntimeException("Undefined scene: " + funcName
						+ " at line " + v.name.getLine());
				}
				validateArgs(scene.params, args, v.name);
				ret = resolveType(scene.returnType);
			}
		}
		else if (e.callee instanceof Get g) {
			ResolvedType obj = g.object.accept(this);
			SetupDecl setup = symbolTable.getSetup(obj.name());
			if (setup == null) {
				throw new RuntimeException("Type '" + obj.name()
					+ "' has no methods at line " + g.name.getLine());
			}
			boolean found = false;
			for (SceneDecl m : setup.methods) {
				if (m.name.getLexeme().equals(g.name.getLexeme())) {
					validateArgs(m.params, args, g.name);
					ret = resolveType(m.returnType);
					found = true;
					break;
				}
			}
			if (!found) {
				throw new RuntimeException("Method '" + g.name.getLexeme()
					+ "' not found in setup '" + obj.name()
					+ "' at line " + g.name.getLine());
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
			throw new RuntimeException("Cannot access member of non-setup type '"
				+ obj.name() + "' at line " + e.name.getLine());
		}
		for (VarDecl f : setup.fields) {
			if (f.name.getLexeme().equals(e.name.getLexeme())) {
				ResolvedType t = resolveType(f.type);
				e.setType(t);
				return t;
			}
		}
		throw new RuntimeException("Field '" + e.name.getLexeme()
			+ "' not found in setup '" + obj.name() + "'");
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
			throw new RuntimeException("Field '" + e.name.getLexeme()
				+ "' not found in setup '" + obj.name() + "'");
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
			throw new RuntimeException("'@' used outside setup at line " + e.atToken.getLine());
		}
		ResolvedType t = new ResolvedType(currentSetup.name.getLexeme(), 0);
		e.setType(t);
		return t;
	}

	@Override
	public ResolvedType visitActionNew(ActionNew e) {
		ResolvedType t = new ResolvedType(e.type.name.getLexeme(), e.type.dimension);
		if (e.type.dimension > 0 && e.arrayInitializer != null) {
			ResolvedType elem =
				new ResolvedType(e.type.name.getLexeme(), e.type.dimension - 1);
			for (Expr ex : e.arrayInitializer) {
				ResolvedType it = ex.accept(this);
				if (!elem.equals(it)) {
					throw new RuntimeException("Array initializer element mismatch: expected "
						+ elem.name() + " but got " + it.name());
				}
			}
		}
		e.setType(t);
		return t;
	}

	@Override
	public ResolvedType visitArrayLiteral(ArrayLiteral e) {
		if (e.elements.isEmpty()) {
			return ResolvedType.NULL;
		}
		ResolvedType first = e.elements.get(0).accept(this);
		for (int i = 1; i < e.elements.size(); i++) {
			ResolvedType cur = e.elements.get(i).accept(this);
			if (!first.equals(cur)) {
				throw new RuntimeException("Array literal element type mismatch");
			}
		}
		ResolvedType t = new ResolvedType(first.name(), first.dimensions() + 1);
		e.setType(t);
		return t;
	}

	private ResolvedType resolveType(LType l) {
		return (l == null) ? ResolvedType.SCRAP
			: new ResolvedType(l.name.getLexeme(), l.dimension);
	}

	private void checkTypeMatch(ResolvedType exp, ResolvedType act, Token t, String m) {
		if (exp != ResolvedType.NULL && act != ResolvedType.NULL && !exp.equals(act)) {
			throw new RuntimeException(m + " at line " + t.getLine()
				+ ". Expected " + exp.name() + " but got " + act.name());
		}
	}

	private void validateArgs(List<Param> params, List<ResolvedType> args, Token t) {
		if (params.size() != args.size()) {
			throw new RuntimeException("Arg count mismatch for '"
				+ t.getLexeme() + "' at line " + t.getLine());
		}
		for (int i = 0; i < params.size(); i++) {
			checkTypeMatch(resolveType(params.get(i).type), args.get(i), t, "Param mismatch");
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
			String n = d.name.getLexeme();
			if (setups.containsKey(n)) {
				throw new RuntimeException("Duplicate setup declaration: " + n);
			}
			setups.put(n, d);
		}

		void defineScene(SceneDecl d) {
			String n = d.name.getLexeme();
			if (scenes.containsKey(n)) {
				throw new RuntimeException("Duplicate scene declaration: " + n);
			}
			scenes.put(n, d);
		}

		void defineGlobalVar(VarDecl v) {
			define(v.name,
				new ResolvedType(v.type.name.getLexeme(), v.type.dimension),
				v);
		}

		Symbol resolve(Token n) {
			for (int i = scopes.size() - 1; i >= 0; i--) {
				if (scopes.get(i).containsKey(n.getLexeme())) {
					return scopes.get(i).get(n.getLexeme());
				}
			}
			throw new RuntimeException("Undefined symbol: "
				+ n.getLexeme() + " at line " + n.getLine());
		}

		SetupDecl getSetup(String n) {
			return setups.get(n);
		}

		SceneDecl getScene(String n) {
			return scenes.get(n);
		}
	}
}