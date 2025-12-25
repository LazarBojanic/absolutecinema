package com.lazar.absolutecinema.parser.ast;

import com.lazar.absolutecinema.lexer.Token;

import java.util.List;

public final class ActionNew implements Expr {
	public final Token action;
	public final RType type;
	public final List<Expr> args;
	public final List<Expr> arrayInitializer;

	public ActionNew(Token action, RType type, List<Expr> args, List<Expr> arrayInitializer) {
		this.action = action;
		this.type = type;
		this.args = args;
		this.arrayInitializer = arrayInitializer;
	}

	@Override
	public <R> R accept(ExprVisitor<R> v) {
		return v.visitActionNew(this);
	}
}