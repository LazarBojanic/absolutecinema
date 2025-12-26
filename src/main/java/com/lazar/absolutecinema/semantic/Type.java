package com.lazar.absolutecinema.semantic;

import java.util.Objects;

public class Type {
	public static final Type INT = new Type("int", 0);
	public static final Type DOUBLE = new Type("double", 0);
	public static final Type CHAR = new Type("char", 0);
	public static final Type STRING = new Type("string", 0);
	public static final Type BOOL = new Type("bool", 0);
	public static final Type VOID = new Type("scrap", 0);
	public static final Type NULL = new Type("null", 0);
	public static final Type ERROR = new Type("<error>", 0);
	private final String name;
	private final int dimensions;

	public Type(String name, int dimensions) {
		this.name = name;
		this.dimensions = dimensions;
	}

	public String getName() {
		return name;
	}

	public int getDimensions() {
		return dimensions;
	}

	public boolean isArray() {
		return dimensions > 0;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof Type)) {
			return false;
		}
		Type type = (Type) o;
		return dimensions == type.dimensions && Objects.equals(name, type.name);
	}

	@Override
	public int hashCode() {
		return Objects.hash(name, dimensions);
	}

	@Override
	public String toString() {
		return name + "[]".repeat(dimensions);
	}

	public boolean isAssignableTo(Type other) {
		if (this.equals(ERROR) || other.equals(ERROR)) {
			return true;
		}
		if (this.equals(NULL) && !other.isPrimitive()) {
			return true;
		}
		if (this.equals(INT) && other.equals(DOUBLE)) {
			return true; // Implicit cast
		}
		return this.equals(other);
	}

	public boolean isPrimitive() {
		return dimensions == 0 && (name.equals("int") || name.equals("double") ||
			name.equals("char") || name.equals("bool") || name.equals("string"));
	}
}