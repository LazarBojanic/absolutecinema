package com.lazar.absolutecinema.parser.ast;

public final class Literal implements Expr {
	public final Object value;
	public Literal(Object value) { this.value = value; }
	@Override public <R> R accept(ExprVisitor<R> v) { return v.visitLiteral(this); }
}