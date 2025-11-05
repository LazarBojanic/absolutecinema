package com.lazar.absolutecinema.parser.ast;

import com.lazar.absolutecinema.lexer.Token;

public final class Get implements Expr {
	public final Expr object;
	public final Token name;

	public Get(Expr object, Token name) {
		this.object = object;
		this.name = name;
	}

	@Override
	public <R> R accept(ExprVisitor<R> v) {
		return v.visitGet(this);
	}
}