package com.lazar.absolutecinema.parser.ast;

import com.lazar.absolutecinema.model.Token;

import java.util.List;

public final class Call implements Expr {
	public final Expr callee;
	public final Token paren;
	public final List<Expr> arguments;

	public Call(Expr callee, Token paren, List<Expr> arguments) {
		this.callee = callee;
		this.paren = paren;
		this.arguments = arguments;
	}

	@Override
	public <R> R accept(ExprVisitor<R> v) {
		return v.visitCall(this);
	}
}