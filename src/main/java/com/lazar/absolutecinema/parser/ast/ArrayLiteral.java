package com.lazar.absolutecinema.parser.ast;

import java.util.List;

public final class ArrayLiteral implements Expr {
	public final List<Expr> elements;
	public ArrayLiteral(List<Expr> elements) { this.elements = elements; }
	@Override public <R> R accept(ExprVisitor<R> v) { return v.visitArrayLiteral(this); }
}