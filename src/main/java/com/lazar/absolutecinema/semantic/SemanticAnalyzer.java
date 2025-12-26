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
		registerStdLib();
		// Pass 1: Global declarations
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

	private void registerStdLib() {
		currentScope.define(new FunctionSymbol("project", Type.VOID, Collections.singletonList(Type.STRING)));
		currentScope.define(new FunctionSymbol("capture", Type.STRING, Collections.emptyList()));
	}

	private void declareSetup(SetupDecl d) {
		SetupSymbol ss = new SetupSymbol(d.name.getLexeme());
		currentScope.define(ss);
		for (VarDecl field : d.fields) {
			ss.members.put(field.name.getLexeme(), new VarSymbol(field.name.getLexeme(), mapType(field.type)));
		}
		for (SceneDecl method : d.methods) {
			ss.members.put(method.name.getLexeme(), new FunctionSymbol(method.name.getLexeme(), mapType(method.returnType), method.params.stream().map(p -> mapType(p.type)).collect(Collectors.toList())));
		}
	}

	private void declareScene(SceneDecl d) {
		currentScope.define(new FunctionSymbol(d.name.getLexeme(), mapType(d.returnType), d.params.stream().map(p -> mapType(p.type)).collect(Collectors.toList())));
	}

	private void declareGlobalVar(VarDecl d) {
		currentScope.define(new VarSymbol(d.name.getLexeme(), mapType(d.type)));
	}

	private Type mapType(LType lType) {
		return lType == null ? Type.VOID : new Type(lType.name.getLexeme(), lType.dimension);
	}

	private Type mapType(RType rType) {
		return rType == null ? Type.VOID : new Type(rType.name.getLexeme(), rType.dimension);
	}

	@Override
	public Void visitSetup(SetupDecl d) {
		currentSetup = (SetupSymbol) currentScope.resolve(d.name.getLexeme());
		currentScope = new Scope(currentScope);
		for (VarDecl field : d.fields) {
			currentScope.define(new VarSymbol(field.name.getLexeme(), mapType(field.type)));
		}
		for (SceneDecl method : d.methods) {
			currentScope.define(new FunctionSymbol(method.name.getLexeme(), mapType(method.returnType), method.params.stream().map(p -> mapType(p.type)).collect(Collectors.toList())));
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
			error(s.keyword, "Expected " + expectedReturnType + " but got " + actual);
		}
		return null;
	}

	@Override
	public Void visitIf(If s) {
		checkCondition(s.ifBranch.cond);
		s.ifBranch.block.accept(this);
		for (Branch elif : s.elifBranchList) {
			checkCondition(elif.cond);
			elif.block.accept(this);
		}
		if (s.elseBranch.block != null) {
			s.elseBranch.block.accept(this);
		}
		return null;
	}

	private void checkCondition(Expr cond) {
		if (cond != null && !cond.accept(this).equals(Type.BOOL)) {
			throw new RuntimeException("SEMANTIC ERROR: Condition must be bool");
		}
	}

	@Override
	public Void visitWhile(While s) {
		checkCondition(s.condition);
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
		if (s.initializer instanceof Decl) {
			((Decl) s.initializer).accept(this);
		}
		else if (s.initializer instanceof Stmt) {
			((Stmt) s.initializer).accept(this);
		}
		checkCondition(s.condition);
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
		return Type.NULL;
	}

	@Override
	public Type visitVariable(Variable e) {
		Symbol s = currentScope.resolve(e.name.getLexeme());
		if (s == null) {
			error(e.name, "Undefined variable '" + e.name.getLexeme() + "'");
		}
		return s.type;
	}

	@Override
	public Type visitBinary(Binary e) {
		Type left = e.left.accept(this), right = e.right.accept(this);
		return switch (e.op.getType()) {
			case PLUS ->
				(left.equals(Type.STRING) || right.equals(Type.STRING)) ? Type.STRING : ((left.equals(Type.INT) && right.equals(Type.INT)) ? Type.INT : Type.DOUBLE);
			case MINUS, STAR, SLASH, PERCENT ->
				(left.equals(Type.INT) && right.equals(Type.INT)) ? Type.INT : Type.DOUBLE;
			case GREATER, GREATER_EQUAL, LESS, LESS_EQUAL -> Type.BOOL;
			case EQUAL_EQUAL, BANG_EQUAL -> Type.BOOL;
			default -> Type.ERROR;
		};
	}

	@Override
	public Type visitAssign(Assign e) {
		Type target = e.target.accept(this), value = e.value.accept(this);
		if (!value.isAssignableTo(target)) {
			error(e.op, "Type mismatch in assignment");
		}
		return target;
	}

	@Override
	public Type visitCall(Call e) {
		Type calleeType = e.callee.accept(this);
		if (e.callee instanceof Variable v) {
			Symbol s = currentScope.resolve(v.name.getLexeme());
			if (s instanceof FunctionSymbol fs) {
				if (fs.params.size() != e.arguments.size()) {
					error(v.name, "Arg count mismatch");
				}
				for (int i = 0; i < fs.params.size(); i++) {
					Type arg = e.arguments.get(i).accept(this);
					if (fs.name.equals("project") && arg.isPrimitive()) {
						continue;
					}
					if (!arg.isAssignableTo(fs.params.get(i))) {
						error(v.name, "Arg type mismatch");
					}
				}
				return fs.type;
			}
		}
		return calleeType;
	}

	@Override
	public Type visitThis(This e) {
		if (currentSetup == null) {
			error(e.atToken, "Cannot use '@' outside setup");
		}
		return currentSetup.type;
	}

	@Override
	public Type visitLogical(Logical e) {
		if (!e.left.accept(this).equals(Type.BOOL) || !e.right.accept(this).equals(Type.BOOL)) {
			error(e.op, "Logical operands must be bool");
		}
		return Type.BOOL;
	}

	@Override
	public Type visitUnary(Unary e) {
		Type r = e.right.accept(this);
		return switch (e.op.getType()) {
			case BANG -> Type.BOOL;
			case MINUS, PLUS, PLUS_PLUS, MINUS_MINUS -> r;
			default -> Type.ERROR;
		};
	}

	@Override
	public Type visitGrouping(Grouping e) {
		return e.expr.accept(this);
	}

	@Override
	public Type visitGet(Get e) {
		Type obj = e.object.accept(this);
		Symbol s = currentScope.resolve(obj.getName());
		if (s instanceof SetupSymbol ss) {
			Symbol m = ss.members.get(e.name.getLexeme());
			if (m != null) {
				return m.type;
			}
		}
		error(e.name, "Member not found");
		return Type.ERROR;
	}

	@Override
	public Type visitSet(Set e) {
		Type obj = e.object.accept(this), val = e.value.accept(this);
		Symbol s = currentScope.resolve(obj.getName());
		if (s instanceof SetupSymbol ss) {
			Symbol m = ss.members.get(e.name.getLexeme());
			if (m != null && val.isAssignableTo(m.type)) {
				return m.type;
			}
		}
		error(e.name, "Invalid member assignment");
		return Type.ERROR;
	}

	@Override
	public Type visitIndex(Index e) {
		Type arr = e.array.accept(this);
		e.index.accept(this);
		return new Type(arr.getName(), arr.getDimensions() - 1);
	}

	@Override
	public Type visitPostfix(Postfix e) {
		return e.target.accept(this);
	}

	@Override
	public Type visitActionNew(ActionNew e) {
		return mapType(e.type);
	}

	@Override
	public Type visitArrayLiteral(ArrayLiteral e) {
		Type first = e.elements.get(0).accept(this);
		return new Type(first.getName(), first.getDimensions() + 1);
	}

	private void error(Token t, String msg) {
		throw new RuntimeException("SEMANTIC ERROR at '" + t.getLexeme() + "': " + msg + " (" + t.getLine() + ":" + t.getColumn() + ")");
	}
}