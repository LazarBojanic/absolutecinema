package com.lazar.absolutecinema.parser.ast;

import com.lazar.absolutecinema.lexer.Token;
import com.lazar.absolutecinema.semantic.ResolvedType;

public final class Variable implements Expr {
	public final Token name;
	private ResolvedType resolvedType;
	public Node resolvedDecl; 

	public Variable(Token name) {
		this.name = name;
	}

	@Override public <R> R accept(ExprVisitor<R> v) { return v.visitVariable(this); }
	@Override public void setType(ResolvedType type) { this.resolvedType = type; }
	@Override public ResolvedType getType() { return resolvedType; }
}