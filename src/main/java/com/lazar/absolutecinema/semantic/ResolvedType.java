package com.lazar.absolutecinema.semantic;

public record ResolvedType(String name, int dimensions) {
	static final ResolvedType INT = new ResolvedType("int", 0);
	static final ResolvedType DOUBLE = new ResolvedType("double", 0);
	static final ResolvedType STRING = new ResolvedType("string", 0);
	static final ResolvedType CHAR = new ResolvedType("char", 0);
	static final ResolvedType BOOL = new ResolvedType("bool", 0);
	static final ResolvedType NULL = new ResolvedType("null", 0);
	static final ResolvedType SCRAP = new ResolvedType("scrap", 0);

	public boolean isNumeric() {
		return name.equals("int") || name.equals("double");
	}
}
