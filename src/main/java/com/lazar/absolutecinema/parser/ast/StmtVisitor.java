package com.lazar.absolutecinema.parser.ast;

public interface StmtVisitor<R> {
	R visitBlock(Block s);

	R visitVar(Var s);

	R visitExpr(ExprStmt s);

	R visitIf(If s);

	R visitWhile(While s);

	R visitFor(For s);

	R visitReturn(Return s);

	R visitBreak(Break s);

	R visitContinue(Continue s);
}