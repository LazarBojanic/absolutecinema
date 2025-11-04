package com.lazar.absolutecinema.parser.ast;

public interface Stmt extends Node {
	<R> R accept(StmtVisitor<R> v);
}