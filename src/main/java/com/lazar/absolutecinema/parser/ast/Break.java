package com.lazar.absolutecinema.parser.ast;

import com.lazar.absolutecinema.model.Token;

public final class Break implements Stmt {
	public final Token keyword;

	public Break(Token keyword) {
		this.keyword = keyword;
	}

	@Override
	public <R> R accept(StmtVisitor<R> v) {
		return v.visitBreak(this);
	}
}