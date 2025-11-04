package com.lazar.absolutecinema.parser.ast;

import com.lazar.absolutecinema.model.Token;

public final class Postfix implements Expr {
	public final Expr target;
	public final Token op;

	public Postfix(Expr target, Token op) {
		this.target = target;
		this.op = op;
	}

	@Override
	public <R> R accept(ExprVisitor<R> v) {
		return v.visitPostfix(this);
	}
}