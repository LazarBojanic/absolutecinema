package com.lazar.absolutecinema.parser.ast;

import com.lazar.absolutecinema.model.Token;

public final class Unary implements Expr {
	public final Token op; public final Expr right;
	public Unary(Token op, Expr right) { this.op = op; this.right = right; }
	@Override public <R> R accept(ExprVisitor<R> v) { return v.visitUnary(this); }
}