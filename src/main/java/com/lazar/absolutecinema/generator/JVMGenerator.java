package com.lazar.absolutecinema.generator;

import com.lazar.absolutecinema.lexer.TokenType;
import com.lazar.absolutecinema.parser.ast.*;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.util.TraceClassVisitor;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import static org.objectweb.asm.Opcodes.*;

public class JVMGenerator implements IGenerator, DeclVisitor<Void>, StmtVisitor<Void>, ExprVisitor<Void> {
	private final GeneratorMode generatorMode;
	private ClassWriter cw;
	private MethodVisitor mv;
	private String className = "Output";
	private final Map<String, Integer> localVariables = new HashMap<>();
	private int nextLocalSlot = 0;
	private org.objectweb.asm.ClassVisitor mvProxy;

	public JVMGenerator(GeneratorMode generatorMode) {
		this.generatorMode = generatorMode;
	}

	@Override
	public GenerationResult generate(Program program) {
		if (generatorMode.equals(GeneratorMode.LIBRARY)) {
			return generateWithLibrary(program);
		}
		else {
			return new GenerationResult("; Manual mode not implemented", new byte[0]);
		}
	}

	private GenerationResult generateWithLibrary(Program program) {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
		TraceClassVisitor tcv = new TraceClassVisitor(cw, pw);
		this.mvProxy = tcv;
		tcv.visit(V17, ACC_PUBLIC, className, null, "java/lang/Object", null);
		// Default constructor
		MethodVisitor cv = tcv.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
		cv.visitCode();
		cv.visitVarInsn(ALOAD, 0);
		cv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
		cv.visitInsn(RETURN);
		cv.visitMaxs(1, 1);
		cv.visitEnd();
		for (Node item : program.items) {
			if (item instanceof Decl) {
				((Decl) item).accept(this);
			}
		}
		tcv.visitEnd();
		return new GenerationResult(sw.toString(), cw.toByteArray());
	}

	@Override
	public Void visitScene(SceneDecl d) {
		String name = d.name.getLexeme();
		String descriptor = getMethodDescriptor(d);
		if (name.equals("entrance")) {
			generateMainBridge(name, descriptor);
		}
		mv = mvProxy.visitMethod(ACC_PUBLIC + ACC_STATIC, name, descriptor, null, null);
		mv.visitCode();
		localVariables.clear();
		nextLocalSlot = 0;
		for (Param p : d.params) {
			localVariables.put(p.name.getLexeme(), nextLocalSlot++);
			if (p.type.name.getLexeme().equals("double") && p.type.dimension == 0) {
				nextLocalSlot++;
			}
		}
		d.body.accept(this);
		if (d.returnType.name.getLexeme().equals("scrap")) {
			mv.visitInsn(RETURN);
		}
		mv.visitMaxs(0, 0);
		mv.visitEnd();
		return null;
	}

	private void generateMainBridge(String entranceName, String entranceDescriptor) {
		MethodVisitor mainMv = mvProxy.visitMethod(ACC_PUBLIC + ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
		mainMv.visitCode();
		mainMv.visitInsn(ACONST_NULL);
		mainMv.visitMethodInsn(INVOKESTATIC, className, entranceName, entranceDescriptor, false);
		mainMv.visitInsn(RETURN);
		mainMv.visitMaxs(1, 1);
		mainMv.visitEnd();
	}

	@Override
	public Void visitVar(VarDecl d) {
		if (mv == null) {
			mvProxy.visitField(ACC_PUBLIC + ACC_STATIC, d.name.getLexeme(), getTypeDescriptor(d.type), null, null).visitEnd();
		}
		else {
			int slot = nextLocalSlot++;
			localVariables.put(d.name.getLexeme(), slot);
			if (d.type.name.getLexeme().equals("double") && d.type.dimension == 0) {
				nextLocalSlot++;
			}
			if (d.initializer != null) {
				d.initializer.accept(this);
				mv.visitVarInsn(getStoreInsn(d.type), slot);
			}
		}
		return null;
	}

	@Override
	public Void visitBlock(Block s) {
		for (Node stmt : s.statements) {
			if (stmt instanceof Decl) {
				((Decl) stmt).accept(this);
			}
			else if (stmt instanceof Stmt) {
				((Stmt) stmt).accept(this);
			}
		}
		return null;
	}

	@Override
	public Void visitIf(If s) {
		Label elseLabel = new Label();
		Label endLabel = new Label();
		s.ifBranch.cond.accept(this);
		mv.visitJumpInsn(IFEQ, elseLabel);
		s.ifBranch.block.accept(this);
		mv.visitJumpInsn(GOTO, endLabel);
		mv.visitLabel(elseLabel);
		if (s.elseBranch != null && s.elseBranch.block != null) {
			s.elseBranch.block.accept(this);
		}
		mv.visitLabel(endLabel);
		return null;
	}

	@Override
	public Void visitBinary(Binary e) {
		e.left.accept(this);
		e.right.accept(this);
		switch (e.op.getType()) {
			case PLUS -> mv.visitInsn(IADD);
			case MINUS -> mv.visitInsn(ISUB);
			case STAR -> mv.visitInsn(IMUL);
			case SLASH -> mv.visitInsn(IDIV);
			case GREATER -> {
				Label t = new Label(), f = new Label();
				mv.visitJumpInsn(IF_ICMPGT, t);
				mv.visitInsn(ICONST_0);
				mv.visitJumpInsn(GOTO, f);
				mv.visitLabel(t);
				mv.visitInsn(ICONST_1);
				mv.visitLabel(f);
			}
			case LESS -> {
				Label t = new Label(), f = new Label();
				mv.visitJumpInsn(IF_ICMPLT, t);
				mv.visitInsn(ICONST_0);
				mv.visitJumpInsn(GOTO, f);
				mv.visitLabel(t);
				mv.visitInsn(ICONST_1);
				mv.visitLabel(f);
			}
		}
		return null;
	}

	@Override
	public Void visitCall(Call e) {
		if (e.callee instanceof Variable v) {
			String name = v.name.getLexeme();
			if (name.equals("project")) {
				mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
				e.arguments.get(0).accept(this);
				mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/Object;)V", false);
			}
			else if (name.equals("capture")) {
				mv.visitTypeInsn(NEW, "java/util/Scanner");
				mv.visitInsn(DUP);
				mv.visitFieldInsn(GETSTATIC, "java/lang/System", "in", "Ljava/io/InputStream;");
				mv.visitMethodInsn(INVOKESPECIAL, "java/util/Scanner", "<init>", "(Ljava/io/InputStream;)V", false);
				mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/Scanner", "nextLine", "()Ljava/lang/String;", false);
			}
			else {
				for (Expr arg : e.arguments) {
					arg.accept(this);
				}
				mv.visitMethodInsn(INVOKESTATIC, className, name, "()V", false); // Simplified descriptor
			}
		}
		return null;
	}

	@Override
	public Void visitLiteral(Literal e) {
		if (e.value instanceof Integer) {
			mv.visitLdcInsn(e.value);
		}
		else if (e.value instanceof Double) {
			mv.visitLdcInsn(e.value);
		}
		else if (e.value instanceof String) {
			mv.visitLdcInsn(e.value);
		}
		else if (e.value instanceof Boolean) {
			mv.visitInsn((boolean) e.value ? ICONST_1 : ICONST_0);
		}
		else if (e.value == null) {
			mv.visitInsn(ACONST_NULL);
		}
		return null;
	}

	@Override
	public Void visitVariable(Variable e) {
		Integer slot = localVariables.get(e.name.getLexeme());
		if (slot != null) {
			mv.visitVarInsn(ALOAD, slot);
		}
		else {
			mv.visitFieldInsn(GETSTATIC, className, e.name.getLexeme(), "Ljava/lang/Object;");
		}
		return null;
	}

	@Override
	public Void visitReturn(Return s) {
		if (s.value != null) {
			s.value.accept(this);
			mv.visitInsn(ARETURN);
		}
		else {
			mv.visitInsn(RETURN);
		}
		return null;
	}

	@Override
	public Void visitAssign(Assign e) {
		e.value.accept(this);
		if (e.target instanceof Variable v) {
			Integer slot = localVariables.get(v.name.getLexeme());
			mv.visitVarInsn(ASTORE, slot);
		}
		return null;
	}

	private String getTypeDescriptor(LType type) {
		String base = switch (type.name.getLexeme()) {
			case "int" -> "I";
			case "double" -> "D";
			case "bool" -> "Z";
			case "string" -> "Ljava/lang/String;";
			default -> "Ljava/lang/Object;";
		};
		return "[".repeat(type.dimension) + base;
	}

	private String getMethodDescriptor(SceneDecl d) {
		StringBuilder sb = new StringBuilder("(");
		for (Param p : d.params) {
			sb.append(getTypeDescriptor(p.type));
		}
		sb.append(")");
		if (d.returnType.name.getLexeme().equals("scrap")) {
			sb.append("V");
		}
		else {
			sb.append(getTypeDescriptor(d.returnType));
		}
		return sb.toString();
	}

	private int getStoreInsn(LType type) {
		if (type.dimension > 0) {
			return ASTORE;
		}
		return switch (type.name.getLexeme()) {
			case "int", "bool" -> ISTORE;
			case "double" -> DSTORE;
			default -> ASTORE;
		};
	}

	// Boilerplate for missing specified specification
	@Override
	public Void visitSetup(SetupDecl d) {
		return null;
	}

	@Override
	public Void visitExpr(ExprStmt s) {
		s.expr.accept(this);
		return null;
	}

	@Override
	public Void visitVar(Var s) {
		return visitVar(s.decl);
	}

	@Override
	public Void visitWhile(While s) {
		return null;
	}

	@Override
	public Void visitFor(For s) {
		return null;
	}

	@Override
	public Void visitBreak(Break s) {
		return null;
	}

	@Override
	public Void visitContinue(Continue s) {
		return null;
	}

	@Override
	public Void visitLogical(Logical e) {
		e.left.accept(this);
		e.right.accept(this);
		return null;
	}

	@Override
	public Void visitUnary(Unary e) {
		e.right.accept(this);
		return null;
	}

	@Override
	public Void visitGrouping(Grouping e) {
		return e.expr.accept(this);
	}

	@Override
	public Void visitGet(Get e) {
		return null;
	}

	@Override
	public Void visitSet(Set e) {
		return null;
	}

	@Override
	public Void visitIndex(Index e) {
		e.array.accept(this);
		e.index.accept(this);
		return null;
	}

	@Override
	public Void visitPostfix(Postfix e) {
		return null;
	}

	@Override
	public Void visitThis(This e) {
		return null;
	}

	@Override
	public Void visitActionNew(ActionNew e) {
		return null;
	}

	@Override
	public Void visitArrayLiteral(ArrayLiteral e) {
		for (Expr el : e.elements) {
			el.accept(this);
		}
		return null;
	}
}