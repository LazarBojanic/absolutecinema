package com.lazar.absolutecinema.parser.ast;

import com.lazar.absolutecinema.model.Token;

public final class Logical implements Expr {
	public final Expr left; public final Token op; public final Expr right;
	public Logical(Expr left, Token op, Expr right) { this.left = left; this.op = op; this.right = right; }
	@Override public <R> R accept(ExprVisitor<R> v) { return v.visitLogical(this); }
}