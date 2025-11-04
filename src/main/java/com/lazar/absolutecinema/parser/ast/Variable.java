package com.lazar.absolutecinema.parser.ast;

import com.lazar.absolutecinema.model.Token;

public final class Variable implements Expr {
	public final Token name;
	public Variable(Token name) { this.name = name; }
	@Override public <R> R accept(ExprVisitor<R> v) { return v.visitVariable(this); }
}