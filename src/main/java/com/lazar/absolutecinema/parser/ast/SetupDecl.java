package com.lazar.absolutecinema.parser.ast;

import com.lazar.absolutecinema.lexer.Token;

import java.util.List;

public final class SetupDecl implements Decl {
	public final Token name;
	public final List<VarDecl> fields;
	public final ConstructorDecl ctor;
	public final List<SceneDecl> methods;

	public SetupDecl(Token name, List<VarDecl> fields, ConstructorDecl ctor, List<SceneDecl> methods) {
		this.name = name;
		this.fields = fields;
		this.ctor = ctor;
		this.methods = methods;
	}

	@Override
	public <R> R accept(DeclVisitor<R> v) {
		return v.visitSetup(this);
	}
}