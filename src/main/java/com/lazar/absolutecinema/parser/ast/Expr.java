package com.lazar.absolutecinema.parser.ast;

public interface Expr extends Node {
	<R> R accept(ExprVisitor<R> v);
}