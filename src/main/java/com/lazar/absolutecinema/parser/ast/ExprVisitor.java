package com.lazar.absolutecinema.parser.ast;

public interface ExprVisitor<R> {
	R visitLiteral(Literal e);
	R visitVariable(Variable e);
	R visitAssign(Assign e);
	R visitBinary(Binary e);
	R visitLogical(Logical e);
	R visitUnary(Unary e);
	R visitGrouping(Grouping e);
	R visitCall(Call e);
	R visitGet(Get e);
	R visitSet(Set e);
	R visitIndex(Index e);
	R visitPostfix(Postfix e);
	R visitThis(This e);
	R visitActionNew(ActionNew e);
	R visitArrayLiteral(ArrayLiteral e);
}