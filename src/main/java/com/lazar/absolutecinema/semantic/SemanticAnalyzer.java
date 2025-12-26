package com.lazar.absolutecinema.semantic;

import com.lazar.absolutecinema.lexer.Token;
import com.lazar.absolutecinema.lexer.TokenType;
import com.lazar.absolutecinema.parser.ast.*;
import com.lazar.absolutecinema.parser.ast.Set;

import java.util.*;
import java.util.stream.Collectors;

public class SemanticAnalyzer implements DeclVisitor<Void>, StmtVisitor<Void>, ExprVisitor<Type> {
	private Scope currentScope = new Scope(null);
	private Type expectedReturnType = null;
	private SetupSymbol currentSetup = null;

	public void analyze(Program program) {
		// Pass 1: Global declarations (Setups, Scenes, Global Vars)
		for (Node node : program.items) {
			if (node instanceof SetupDecl) {
				declareSetup((SetupDecl) node);
			}
			else if (node instanceof SceneDecl) {
				declareScene((SceneDecl) node);
			}
			else if (node instanceof VarDecl) {
				declareGlobalVar((VarDecl) node);
			}
		}
		// Pass 2: Full analysis
		for (Node node : program.items) {
			if (node instanceof Decl) {
				((Decl) node).accept(this);
			}
		}
	}

	private void declareSetup(SetupDecl d) {
		SetupSymbol ss = new SetupSymbol(d.name.getLexeme());
		currentScope.define(ss);
		// Pre-populate members for member access resolution
		for (VarDecl field : d.fields) {
			ss.members.put(field.name.getLexeme(), new VarSymbol(field.name.getLexeme(), mapType(field.type)));
		}
		for (SceneDecl method : d.methods) {
			ss.members.put(method.name.getLexeme(), new FunctionSymbol(method.name.getLexeme(),
				mapType(method.returnType),
				method.params.stream().map(p -> mapType(p.type)).collect(Collectors.toList())));
		}
	}

	private void declareScene(SceneDecl d) {
		currentScope.define(new FunctionSymbol(d.name.getLexeme(),
			mapType(d.returnType),
			d.params.stream().map(p -> mapType(p.type)).collect(Collectors.toList())));
	}

	private void declareGlobalVar(VarDecl d) {
		currentScope.define(new VarSymbol(d.name.getLexeme(), mapType(d.type)));
	}

	private Type mapType(LType lType) {
		if (lType == null) {
			return Type.VOID;
		}
		return new Type(lType.name.getLexeme(), lType.dimension);
	}

	private Type mapType(RType rType) {
		if (rType == null) {
			return Type.VOID;
		}
		return new Type(rType.name.getLexeme(), rType.dimension);
	}

	// --- DeclVisitor ---
	@Override
	public Void visitSetup(SetupDecl d) {
		SetupSymbol ss = (SetupSymbol) currentScope.resolve(d.name.getLexeme());
		currentSetup = ss;
		currentScope = new Scope(currentScope);
		// Re-define members in the local scope of the class for '@' access
		for (VarDecl field : d.fields) {
			field.accept(this);
		}
		if (d.ctor != null) {
			expectedReturnType = Type.VOID;
			analyzeBlock(d.ctor.body, d.ctor.params);
		}
		for (SceneDecl method : d.methods) {
			method.accept(this);
		}
		currentScope = currentScope.getParent();
		currentSetup = null;
		return null;
	}

	@Override
	public Void visitScene(SceneDecl d) {
		FunctionSymbol fs = (FunctionSymbol) currentScope.resolve(d.name.getLexeme());
		expectedReturnType = fs.type;
		analyzeBlock(d.body, d.params);
		return null;
	}

	@Override
	public Void visitVar(VarDecl d) {
		Type type = mapType(d.type);
		if (d.initializer != null) {
			Type initType = d.initializer.accept(this);
			if (!initType.isAssignableTo(type)) {
				error(d.name, "Cannot assign " + initType + " to " + type);
			}
		}
		currentScope.define(new VarSymbol(d.name.getLexeme(), type));
		return null;
	}

	private void analyzeBlock(Block b, List<Param> params) {
		currentScope = new Scope(currentScope);
		if (params != null) {
			for (Param p : params) {
				currentScope.define(new VarSymbol(p.name.getLexeme(), mapType(p.type)));
			}
		}
		b.accept(this);
		currentScope = currentScope.getParent();
	}

	// --- StmtVisitor ---
	@Override
	public Void visitBlock(Block s) {
		currentScope = new Scope(currentScope);
		for (Node item : s.statements) {
			if (item instanceof Decl) {
				((Decl) item).accept(this);
			}
			else if (item instanceof Stmt) {
				((Stmt) item).accept(this);
			}
		}
		currentScope = currentScope.getParent();
		return null;
	}

	@Override
	public Void visitReturn(Return s) {
		Type actual = s.value != null ? s.value.accept(this) : Type.VOID;
		if (expectedReturnType != null && !actual.isAssignableTo(expectedReturnType)) {
			error(s.keyword, "Expected return type " + expectedReturnType + " but got " + actual);
		}
		return null;
	}

	@Override
	public Void visitIf(If s) {
		checkCondition(s.ifBranch.cond, TokenType.IF);
		s.ifBranch.block.accept(this);
		for (Branch elif : s.elifBranchList) {
			checkCondition(elif.cond, TokenType.ELIF);
			elif.block.accept(this);
		}
		if (s.elseBranch != null) {
			s.elseBranch.block.accept(this);
		}
		return null;
	}

	private void checkCondition(Expr cond, TokenType type) {
		if (cond != null) {
			Type t = cond.accept(this);
			if (!t.equals(Type.BOOL)) {
				throw new SemanticError("SEMANTIC ERROR: Condition must be bool, got " + t);
			}
		}
	}

	@Override
	public Void visitWhile(While s) {
		if (!s.condition.accept(this).equals(Type.BOOL)) {
			throw new SemanticError("SEMANTIC ERROR: 'keepRollingIf' condition must be bool");
		}
		s.body.accept(this);
		return null;
	}

	@Override
	public Void visitVar(Var s) {
		visitVar(s.decl);
		return null;
	}

	@Override
	public Void visitExpr(ExprStmt s) {
		s.expr.accept(this);
		return null;
	}

	@Override
	public Void visitFor(For s) {
		currentScope = new Scope(currentScope);
		if (s.initializer != null) {
			if (s.initializer instanceof Decl) {
				((Decl) s.initializer).accept(this);
			}
			else if (s.initializer instanceof Stmt) {
				((Stmt) s.initializer).accept(this);
			}
		}
		if (s.condition != null && !s.condition.accept(this).equals(Type.BOOL)) {
			throw new SemanticError("SEMANTIC ERROR: 'keepRollingDuring' condition must be bool");
		}
		if (s.increment != null) {
			s.increment.accept(this);
		}
		s.body.accept(this);
		currentScope = currentScope.getParent();
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

	// --- ExprVisitor ---
	@Override
	public Type visitLiteral(Literal e) {
		if (e.value instanceof Integer) {
			return Type.INT;
		}
		if (e.value instanceof Double) {
			return Type.DOUBLE;
		}
		if (e.value instanceof String) {
			return Type.STRING;
		}
		if (e.value instanceof Character) {
			return Type.CHAR;
		}
		if (e.value instanceof Boolean) {
			return Type.BOOL;
		}
		if (e.value == null) {
			return Type.NULL;
		}
		return Type.ERROR;
	}

	@Override
	public Type visitVariable(Variable e) {
		Symbol s = currentScope.resolve(e.name.getLexeme());
		if (s == null) {
			error(e.name, "Undefined variable '" + e.name.getLexeme() + "'");
			return Type.ERROR;
		}
		return s.type;
	}

	@Override
	public Type visitBinary(Binary e) {
		Type left = e.left.accept(this);
		Type right = e.right.accept(this);
		switch (e.op.getType()) {
			case PLUS:
				if (left.equals(Type.STRING) || right.equals(Type.STRING)) {
					return Type.STRING;
				}
				// Fallthrough
			case MINUS:
			case STAR:
			case SLASH:
			case PERCENT:
				if (left.equals(Type.INT) && right.equals(Type.INT)) {
					return Type.INT;
				}
				if ((left.equals(Type.INT) || left.equals(Type.DOUBLE)) &&
					(right.equals(Type.INT) || right.equals(Type.DOUBLE))) {
					return Type.DOUBLE;
				}
				break;
			case GREATER:
			case GREATER_EQUAL:
			case LESS:
			case LESS_EQUAL:
				if ((left.equals(Type.INT) || left.equals(Type.DOUBLE)) &&
					(right.equals(Type.INT) || right.equals(Type.DOUBLE))) {
					return Type.BOOL;
				}
				break;
			case EQUAL_EQUAL:
			case BANG_EQUAL:
				if (left.equals(right) || left.isAssignableTo(right) || right.isAssignableTo(left)) {
					return Type.BOOL;
				}
				break;
		}
		error(e.op, "Operator '" + e.op.getLexeme() + "' cannot be applied to " + left + " and " + right);
		return Type.ERROR;
	}

	@Override
	public Type visitAssign(Assign e) {
		Type target = e.target.accept(this);
		Type value = e.value.accept(this);
		if (!value.isAssignableTo(target)) {
			error(e.op, "Type mismatch in assignment. Cannot assign " + value + " to " + target);
		}
		return target;
	}

	@Override
	public Type visitCall(Call e) {
		Type calleeType = e.callee.accept(this);
		// If it's a direct variable call, we can check the FunctionSymbol
		if (e.callee instanceof Variable) {
			Symbol s = currentScope.resolve(((Variable) e.callee).name.getLexeme());
			if (s instanceof FunctionSymbol) {
				FunctionSymbol fs = (FunctionSymbol) s;
				if (fs.params.size() != e.arguments.size()) {
					error(((Variable) e.callee).name, "Expected " + fs.params.size() + " arguments but got " + e.arguments.size());
				}
				for (int i = 0; i < fs.params.size(); i++) {
					Type argType = e.arguments.get(i).accept(this);
					if (!argType.isAssignableTo(fs.params.get(i))) {
						error(((Variable) e.callee).name, "Argument " + i + " expected " + fs.params.get(i) + " but got " + argType);
					}
				}
			}
		}
		return calleeType;
	}

	@Override
	public Type visitThis(This e) {
		if (currentSetup == null) {
			error(e.atToken, "Cannot use '@' outside of a setup");
		}
		return currentSetup != null ? currentSetup.type : Type.ERROR;
	}

	@Override
	public Type visitLogical(Logical e) {
		Type left = e.left.accept(this);
		Type right = e.right.accept(this);
		if (!left.equals(Type.BOOL) || !right.equals(Type.BOOL)) {
			error(e.op, "Operands of logical operator must be bool");
		}
		return Type.BOOL;
	}

	@Override
	public Type visitUnary(Unary e) {
		Type right = e.right.accept(this);
		switch (e.op.getType()) {
			case BANG:
				if (right.equals(Type.BOOL)) {
					return Type.BOOL;
				}
				break;
			case MINUS:
			case PLUS:
				if (right.equals(Type.INT)) {
					return Type.INT;
				}
				if (right.equals(Type.DOUBLE)) {
					return Type.DOUBLE;
				}
				break;
			case PLUS_PLUS:
			case MINUS_MINUS:
				if (right.equals(Type.INT) || right.equals(Type.DOUBLE)) {
					return right;
				}
				break;
		}
		error(e.op, "Unary operator '" + e.op.getLexeme() + "' cannot be applied to " + right);
		return Type.ERROR;
	}

	@Override
	public Type visitGrouping(Grouping e) {
		return e.expr.accept(this);
	}

	@Override
	public Type visitGet(Get e) {
		Type objType = e.object.accept(this);
		Symbol setup = currentScope.resolve(objType.getName());
		if (setup instanceof SetupSymbol) {
			Symbol member = ((SetupSymbol) setup).members.get(e.name.getLexeme());
			if (member != null) {
				return member.type;
			}
			error(e.name, "Member '" + e.name.getLexeme() + "' not found in setup '" + objType.getName() + "'");
		}
		return Type.ERROR;
	}

	@Override
	public Type visitSet(Set e) {
		Type objType = e.object.accept(this);
		Type valType = e.value.accept(this);
		Symbol setup = currentScope.resolve(objType.getName());
		if (setup instanceof SetupSymbol) {
			Symbol member = ((SetupSymbol) setup).members.get(e.name.getLexeme());
			if (member != null) {
				if (!valType.isAssignableTo(member.type)) {
					error(e.op, "Type mismatch");
				}
				return member.type;
			}
		}
		return Type.ERROR;
	}

	@Override
	public Type visitIndex(Index e) {
		Type arrType = e.array.accept(this);
		Type idxType = e.index.accept(this);
		if (!idxType.equals(Type.INT)) {
			error(null, "Array index must be int");
		}
		if (arrType.getDimensions() == 0) {
			error(null, "Cannot index non-array type");
		}
		return new Type(arrType.getName(), arrType.getDimensions() - 1);
	}

	@Override
	public Type visitPostfix(Postfix e) {
		Type target = e.target.accept(this);
		if (e.op.getType() == TokenType.PLUS_PLUS || e.op.getType() == TokenType.MINUS_MINUS) {
			if (!target.equals(Type.INT) && !target.equals(Type.DOUBLE)) {
				error(e.op, "Postfix operator '" + e.op.getLexeme() + "' must be applied to a numeric type");
			}
		}
		return target;
	}

	@Override
	public Type visitActionNew(ActionNew e) {
		return mapType(e.type);
	}

	@Override
	public Type visitArrayLiteral(ArrayLiteral e) {
		if (e.elements.isEmpty()) {
			return Type.ERROR;
		}
		Type elementType = e.elements.get(0).accept(this);
		for (Expr element : e.elements) {
			Type t = element.accept(this);
			if (!t.isAssignableTo(elementType)) {
				throw new SemanticError("SEMANTIC ERROR: Array literal elements must have consistent types");
			}
		}
		return new Type(elementType.getName(), elementType.getDimensions() + 1);
	}

	private void error(Token token, String message) {
		String where = (token == null || token.getType() == TokenType.EOF) ? " at end" : " at '" + token.getLexeme() + "'";
		int line = token != null ? token.getLine() : 0;
		int col = token != null ? token.getColumn() : 0;
		throw new SemanticError("SEMANTIC ERROR" + where + ": " + message + " (line: " + line + ", col: " + col + ")");
	}

	private static final class SemanticError extends RuntimeException {
		SemanticError(String msg) {
			super(msg);
		}
	}
}