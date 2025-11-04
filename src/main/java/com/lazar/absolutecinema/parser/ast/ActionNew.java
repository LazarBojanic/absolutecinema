package com.lazar.absolutecinema.parser.ast;

import com.lazar.absolutecinema.model.Token;

public final class ActionNew implements Expr {
	public final Token action; public final TypeRef type; public final java.util.List<Expr> args;
	public final Expr arrayCapacity; public final java.util.List<Expr> arrayInitializer;
	public ActionNew(Token action, TypeRef type, java.util.List<Expr> args, Expr arrayCapacity, java.util.List<Expr> arrayInitializer) {
		this.action = action; this.type = type; this.args = args; this.arrayCapacity = arrayCapacity; this.arrayInitializer = arrayInitializer;
	}
	@Override public <R> R accept(ExprVisitor<R> v) { return v.visitActionNew(this); }
}