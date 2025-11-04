package com.lazar.absolutecinema.parser.ast;

import com.lazar.absolutecinema.model.Token;
import java.util.List;

public final class SceneDecl implements Decl {
	public final Token name; public final java.util.List<Param> params; public final TypeRef returnType; public final Block body;
	public final boolean isMethod;
	public SceneDecl(Token name, java.util.List<Param> params, TypeRef returnType, Block body, boolean isMethod) {
		this.name = name; this.params = params; this.returnType = returnType; this.body = body; this.isMethod = isMethod;
	}
	@Override public <R> R accept(DeclVisitor<R> v) { return v.visitScene(this); }
}