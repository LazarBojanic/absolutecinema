package com.lazar.absolutecinema.parser.ast;

import com.lazar.absolutecinema.model.Token;

public final class This implements Expr {
	public final Token atToken;
	public This(Token atToken) { this.atToken = atToken; }
	@Override public <R> R accept(ExprVisitor<R> v) { return v.visitThis(this); }
}
