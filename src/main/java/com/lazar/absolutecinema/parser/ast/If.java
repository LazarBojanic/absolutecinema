package com.lazar.absolutecinema.parser.ast;

import java.util.List;

public final class If implements Stmt {
	public final Branch ifBranch;
	public final List<Branch> elifBranchList;
	public final Branch elseBranch;

	public If(Branch ifBranch, List<Branch> elifBranchList, Branch elseBranch) {
		this.ifBranch = ifBranch;
		this.elifBranchList = elifBranchList;
		this.elseBranch = elseBranch;
	}

	@Override
	public <R> R accept(StmtVisitor<R> v) {
		return v.visitIf(this);
	}
}