package com.lazar.absolutecinema.parser.ast;

import com.lazar.absolutecinema.lexer.Token;

public final class Set implements Expr {
	public final Expr object;
	public final Token name;
	public final Token op;
	public final Expr value;

	public Set(Expr object, Token name, Token op, Expr value) {
		this.object = object;
		this.name = name;
		this.op = op;
		this.value = value;
	}

	@Override
	public <R> R accept(ExprVisitor<R> v) {
		return v.visitSet(this);
	}
}
