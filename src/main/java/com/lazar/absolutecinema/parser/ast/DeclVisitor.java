package com.lazar.absolutecinema.parser.ast;

public interface DeclVisitor<R> {
	R visitSetup(SetupDecl d);

	R visitScene(SceneDecl d);

	R visitVar(VarDecl d);

}