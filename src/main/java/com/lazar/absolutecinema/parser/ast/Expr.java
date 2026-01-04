package com.lazar.absolutecinema.parser.ast;

import com.lazar.absolutecinema.semantic.ResolvedType;

public interface Expr extends Node {
	<R> R accept(ExprVisitor<R> v);

	// Metadata methods for semantic enrichment
	void setType(ResolvedType type);
	ResolvedType getType();
}