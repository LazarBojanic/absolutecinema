package com.lazar.absolutecinema.generator;

import com.lazar.absolutecinema.parser.ast.*;

public class LLVMGenerator implements IGenerator, DeclVisitor<Void>, StmtVisitor<Void>, ExprVisitor<Void> {
	private final GeneratorMode generatorMode;

	public LLVMGenerator(GeneratorMode generatorMode) {
		this.generatorMode = generatorMode;
	}

	@Override
	public GenerationResult generate(Program program) {
		if (generatorMode.equals(GeneratorMode.LIBRARY)) {
			return generateWithLibrary(program);
		}
		else {
			return generateManually(program);
		}
	}

	private GenerationResult generateWithLibrary(Program program) {
		return null;
	}

	private GenerationResult generateManually(Program program) {
		return null;
	}

	@Override
	public Void visitSetup(SetupDecl d) {
		return null;
	}

	@Override
	public Void visitScene(SceneDecl d) {
		return null;
	}

	@Override
	public Void visitVar(VarDecl d) {
		return null;
	}

	@Override
	public Void visitLiteral(Literal e) {
		return null;
	}

	@Override
	public Void visitVariable(Variable e) {
		return null;
	}

	@Override
	public Void visitAssign(Assign e) {
		return null;
	}

	@Override
	public Void visitBinary(Binary e) {
		return null;
	}

	@Override
	public Void visitLogical(Logical e) {
		return null;
	}

	@Override
	public Void visitUnary(Unary e) {
		return null;
	}

	@Override
	public Void visitGrouping(Grouping e) {
		return null;
	}

	@Override
	public Void visitCall(Call e) {
		return null;
	}

	@Override
	public Void visitGet(Get e) {
		return null;
	}

	@Override
	public Void visitSet(Set e) {
		return null;
	}

	@Override
	public Void visitIndex(Index e) {
		return null;
	}

	@Override
	public Void visitPostfix(Postfix e) {
		return null;
	}

	@Override
	public Void visitThis(This e) {
		return null;
	}

	@Override
	public Void visitActionNew(ActionNew e) {
		return null;
	}

	@Override
	public Void visitArrayLiteral(ArrayLiteral e) {
		return null;
	}

	@Override
	public Void visitBlock(Block s) {
		return null;
	}

	@Override
	public Void visitVar(Var s) {
		return null;
	}

	@Override
	public Void visitExpr(ExprStmt s) {
		return null;
	}

	@Override
	public Void visitIf(If s) {
		return null;
	}

	@Override
	public Void visitWhile(While s) {
		return null;
	}

	@Override
	public Void visitFor(For s) {
		return null;
	}

	@Override
	public Void visitReturn(Return s) {
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
}
