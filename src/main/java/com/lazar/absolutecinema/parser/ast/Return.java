package com.lazar.absolutecinema.parser.ast;

import com.lazar.absolutecinema.model.Token;

public final class Return implements Stmt {
	public final Token keyword;
	public final Expr value;

	public Return(Token keyword, Expr value) {
		this.keyword = keyword;
		this.value = value;
	}

	@Override
	public <R> R accept(StmtVisitor<R> v) {
		return v.visitReturn(this);
	}
}