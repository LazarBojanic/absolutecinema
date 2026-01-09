package com.lazar.absolutecinema.parser.ast;

import com.lazar.absolutecinema.semantic.ResolvedType;

public interface Expr extends Node {
	<R> R accept(ExprVisitor<R> v);

	
	void setType(ResolvedType type);
	ResolvedType getType();
}