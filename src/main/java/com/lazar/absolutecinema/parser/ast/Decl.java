package com.lazar.absolutecinema.parser.ast;

public interface Decl extends Node {
	<R> R accept(DeclVisitor<R> v);
}