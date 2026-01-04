package com.lazar.absolutecinema.generator;

import com.lazar.absolutecinema.lexer.Token;
import com.lazar.absolutecinema.lexer.TokenType;
import com.lazar.absolutecinema.parser.ast.*;
import com.lazar.absolutecinema.parser.ast.Set;
import com.lazar.absolutecinema.semantic.ResolvedType;
import org.objectweb.asm.*;
import org.objectweb.asm.util.Printer;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceClassVisitor;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;

import static org.objectweb.asm.Opcodes.*;

public class JVMGenerator implements IGenerator, DeclVisitor<Void>, StmtVisitor<Void>, ExprVisitor<Void> {
	private final GeneratorMode generatorMode;
	private ClassWriter cw;
	private MethodVisitor mv;
	private final Map<Node, Integer> localVars = new HashMap<>();
	private int nextLocalSlot = 0;
	private String className = "Output";
	private final Stack<Label> loopBreakLabels = new Stack<>();
	private final Stack<Label> loopContinueLabels = new Stack<>();

	public JVMGenerator(GeneratorMode generatorMode) {
		this.generatorMode = generatorMode;
	}

	@Override
	public GenerationResult generate(Program program) {
		if (generatorMode.equals(GeneratorMode.LIBRARY)) {
			return generateWithLibrary(program);
		}
		else {
			return generateManually(program);
		}
	}

	private GenerationResult generateWithLibrary(Program program) {
		cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
		cw.visit(V21, ACC_PUBLIC, className, null, "java/lang/Object", null);
		// Initialize Standard Library Scanner
		cw.visitField(ACC_PRIVATE | ACC_STATIC, "scanner", "Ljava/util/Scanner;", null, null).visitEnd();
		MethodVisitor clinit = cw.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
		clinit.visitCode();
		clinit.visitTypeInsn(NEW, "java/util/Scanner");
		clinit.visitInsn(DUP);
		clinit.visitFieldInsn(GETSTATIC, "java/lang/System", "in", "Ljava/io/InputStream;");
		clinit.visitMethodInsn(INVOKESPECIAL, "java/util/Scanner", "<init>", "(Ljava/io/InputStream;)V", false);
		clinit.visitFieldInsn(PUTSTATIC, className, "scanner", "Ljava/util/Scanner;");
		clinit.visitInsn(RETURN);
		clinit.visitMaxs(0, 0);
		clinit.visitEnd();
		for (Node item : program.items) {
			if (item instanceof Decl d) {
				d.accept(this);
			}
		}
		cw.visitEnd();
		byte[] binary = cw.toByteArray();
		StringWriter sw = new StringWriter();
		Printer printer = new Textifier();
		TraceClassVisitor tcv = new TraceClassVisitor(null, printer, new PrintWriter(sw));
		new ClassReader(binary).accept(tcv, 0);
		return new GenerationResult(sw.toString(), binary);
	}

	private GenerationResult generateManually(Program program) {
		return new GenerationResult("; Manual mode not implemented.", new byte[0]);
	}

	@Override
	public Void visitSetup(SetupDecl d) {
		for (VarDecl field : d.fields) {
			cw.visitField(ACC_PUBLIC, field.name.getLexeme(), getDescriptor(field.type), null, null).visitEnd();
		}
		if (d.ctor != null) {
			mv = cw.visitMethod(ACC_PUBLIC, "<init>", getCtorDescriptor(d.ctor), null, null);
			mv.visitCode();
			mv.visitVarInsn(ALOAD, 0);
			mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
			localVars.clear();
			localVars.put(null, 0);
			nextLocalSlot = 1;
			for (Param p : d.ctor.params) {
				localVars.put(p, nextLocalSlot);
				nextLocalSlot += (p.type.name.getLexeme().equals("double") ? 2 : 1);
			}
			d.ctor.body.accept(this);
			mv.visitInsn(RETURN);
			mv.visitMaxs(0, 0);
			mv.visitEnd();
		}
		for (SceneDecl method : d.methods) {
			method.accept(this);
		}
		return null;
	}

	@Override
	public Void visitScene(SceneDecl d) {
		String name = d.name.getLexeme();
		String descriptor = getMethodDescriptor(d);
		int access = d.isMethod ? ACC_PUBLIC : ACC_PUBLIC | ACC_STATIC;
		if (name.equals("entrance")) {
			name = "main";
			descriptor = "([Ljava/lang/String;)V";
			access = ACC_PUBLIC | ACC_STATIC;
		}
		mv = cw.visitMethod(access, name, descriptor, null, null);
		mv.visitCode();
		localVars.clear();
		nextLocalSlot = d.isMethod ? 1 : 0;
		if (name.equals("main")) {
			nextLocalSlot = 1;
		}
		for (Param p : d.params) {
			localVars.put(p, nextLocalSlot);
			nextLocalSlot += (p.type.name.getLexeme().equals("double") ? 2 : 1);
		}
		if (d.body != null) {
			d.body.accept(this);
		}
		if (d.returnType.name.getLexeme().equals("scrap")) {
			mv.visitInsn(RETURN);
		}
		mv.visitMaxs(0, 0);
		mv.visitEnd();
		return null;
	}

	@Override
	public Void visitVar(VarDecl d) {
		cw.visitField(ACC_PUBLIC | ACC_STATIC, d.name.getLexeme(), getDescriptor(d.type), null, null).visitEnd();
		return null;
	}

	@Override
	public Void visitBlock(Block s) {
		for (Node n : s.statements) {
			if (n instanceof Stmt st) {
				st.accept(this);
			}
			else if (n instanceof Var v) {
				v.decl.accept(this);
			}
			else if (n instanceof Decl d) {
				d.accept(this);
			}
		}
		return null;
	}

	@Override
	public Void visitVar(Var s) {
		VarDecl d = s.decl;
		int slot = nextLocalSlot;
		localVars.put(d, slot);
		nextLocalSlot += (d.type.name.getLexeme().equals("double") ? 2 : 1);
		if (d.initializer != null) {
			d.initializer.accept(this);
			mv.visitVarInsn(getStoreOpcode(d.initializer.getType()), slot);
		}
		return null;
	}

	@Override
	public Void visitExpr(ExprStmt s) {
		s.expr.accept(this);
		ResolvedType type = s.expr.getType();
		if (type != null && type != ResolvedType.SCRAP) {
			mv.visitInsn(type == ResolvedType.DOUBLE ? POP2 : POP);
		}
		return null;
	}

	@Override
	public Void visitIf(If s) {
		Label endLabel = new Label();
		Label nextBranch = new Label();
		s.ifBranch.cond.accept(this);
		mv.visitJumpInsn(IFEQ, nextBranch);
		s.ifBranch.block.accept(this);
		mv.visitJumpInsn(GOTO, endLabel);
		mv.visitLabel(nextBranch);
		for (Branch elif : s.elifBranchList) {
			nextBranch = new Label();
			elif.cond.accept(this);
			mv.visitJumpInsn(IFEQ, nextBranch);
			elif.block.accept(this);
			mv.visitJumpInsn(GOTO, endLabel);
			mv.visitLabel(nextBranch);
		}
		if (s.elseBranch != null) {
			s.elseBranch.block.accept(this);
		}
		mv.visitLabel(endLabel);
		return null;
	}

	@Override
	public Void visitWhile(While s) {
		Label start = new Label();
		Label end = new Label();
		loopContinueLabels.push(start);
		loopBreakLabels.push(end);
		mv.visitLabel(start);
		s.condition.accept(this);
		mv.visitJumpInsn(IFEQ, end);
		s.body.accept(this);
		mv.visitJumpInsn(GOTO, start);
		mv.visitLabel(end);
		loopContinueLabels.pop();
		loopBreakLabels.pop();
		return null;
	}

	@Override
	public Void visitFor(For s) {
		Label start = new Label();
		Label end = new Label();
		Label inc = new Label();
		if (s.initializer != null) {
			if (s.initializer instanceof Decl d) {
				d.accept(this);
			}
			else if (s.initializer instanceof Stmt st) {
				st.accept(this);
			}
		}
		mv.visitLabel(start);
		if (s.condition != null) {
			s.condition.accept(this);
			mv.visitJumpInsn(IFEQ, end);
		}
		loopContinueLabels.push(inc);
		loopBreakLabels.push(end);
		s.body.accept(this);
		mv.visitLabel(inc);
		if (s.increment != null) {
			s.increment.accept(this);
		}
		mv.visitJumpInsn(GOTO, start);
		mv.visitLabel(end);
		loopContinueLabels.pop();
		loopBreakLabels.pop();
		return null;
	}

	@Override
	public Void visitReturn(Return s) {
		if (s.value != null) {
			s.value.accept(this);
			mv.visitInsn(getReturnOpcode(s.value.getType()));
		}
		else {
			mv.visitInsn(RETURN);
		}
		return null;
	}

	@Override
	public Void visitBreak(Break s) {
		mv.visitJumpInsn(GOTO, loopBreakLabels.peek());
		return null;
	}

	@Override
	public Void visitContinue(Continue s) {
		mv.visitJumpInsn(GOTO, loopContinueLabels.peek());
		return null;
	}

	@Override
	public Void visitLiteral(Literal e) {
		if (e.value instanceof Integer i) {
			mv.visitLdcInsn(i);
		}
		else if (e.value instanceof Double d) {
			mv.visitLdcInsn(d);
		}
		else if (e.value instanceof String s) {
			mv.visitLdcInsn(s);
		}
		else if (e.value instanceof Boolean b) {
			mv.visitInsn(b ? ICONST_1 : ICONST_0);
		}
		else if (e.value == null) {
			mv.visitInsn(ACONST_NULL);
		}
		return null;
	}

	@Override
	public Void visitVariable(Variable e) {
		Integer slot = localVars.get(e.resolvedDecl);
		if (slot != null) {
			mv.visitVarInsn(getLoadOpcode(e.getType()), slot);
		}
		else {
			mv.visitFieldInsn(GETSTATIC, className, e.name.getLexeme(), getDescriptor(e.getType()));
		}
		return null;
	}

	@Override
	public Void visitAssign(Assign e) {
		if (e.target instanceof Variable v) {
			e.value.accept(this);
			mv.visitInsn(e.getType() == ResolvedType.DOUBLE ? DUP2 : DUP);
			Integer slot = localVars.get(v.resolvedDecl);
			if (slot != null) {
				mv.visitVarInsn(getStoreOpcode(v.getType()), slot);
			}
			else {
				mv.visitFieldInsn(PUTSTATIC, className, v.name.getLexeme(), getDescriptor(v.getType()));
			}
		}
		else if (e.target instanceof Get g) {
			g.object.accept(this);
			e.value.accept(this);
			mv.visitInsn(e.getType() == ResolvedType.DOUBLE ? DUP2_X1 : DUP_X1);
			mv.visitFieldInsn(PUTFIELD, className, g.name.getLexeme(), getDescriptor(g.getType()));
		}
		else if (e.target instanceof Index idx) {
			idx.array.accept(this);
			idx.index.accept(this);
			e.value.accept(this);
			mv.visitInsn(e.getType() == ResolvedType.DOUBLE ? DUP2_X2 : DUP_X2);
			mv.visitInsn(getArrayStoreOpcode(e.getType()));
		}
		return null;
	}

	@Override
	public Void visitBinary(Binary e) {
		e.left.accept(this);
		e.right.accept(this);
		ResolvedType type = e.left.getType();
		switch (e.op.getType()) {
			case PLUS -> mv.visitInsn(type == ResolvedType.DOUBLE ? DADD : IADD);
			case MINUS -> mv.visitInsn(type == ResolvedType.DOUBLE ? DSUB : ISUB);
			case STAR -> mv.visitInsn(type == ResolvedType.DOUBLE ? DMUL : IMUL);
			case SLASH -> mv.visitInsn(type == ResolvedType.DOUBLE ? DDIV : IDIV);
			case PERCENT -> mv.visitInsn(type == ResolvedType.DOUBLE ? DREM : IREM);
			case GREATER, LESS, GREATER_EQUAL, LESS_EQUAL, EQUAL_EQUAL, BANG_EQUAL ->
				emitComparison(e.op.getType(), type);
		}
		return null;
	}

	@Override
	public Void visitLogical(Logical e) {
		Label end = new Label();
		e.left.accept(this);
		mv.visitInsn(DUP);
		if (e.op.getType() == TokenType.OR_OR) {
			mv.visitJumpInsn(IFNE, end);
		}
		else {
			mv.visitJumpInsn(IFEQ, end);
		}
		mv.visitInsn(POP);
		e.right.accept(this);
		mv.visitLabel(end);
		return null;
	}

	@Override
	public Void visitUnary(Unary e) {
		e.right.accept(this);
		ResolvedType type = e.getType();
		if (e.op.getType() == TokenType.MINUS) {
			mv.visitInsn(type == ResolvedType.DOUBLE ? DNEG : INEG);
		}
		else if (e.op.getType() == TokenType.BANG) {
			Label trueL = new Label(), endL = new Label();
			mv.visitJumpInsn(IFNE, trueL);
			mv.visitInsn(ICONST_1);
			mv.visitJumpInsn(GOTO, endL);
			mv.visitLabel(trueL);
			mv.visitInsn(ICONST_0);
			mv.visitLabel(endL);
		}
		return null;
	}

	@Override
	public Void visitGrouping(Grouping e) {
		e.expr.accept(this);
		return null;
	}

	@Override
	public Void visitCall(Call e) {
		if (e.callee instanceof Variable v && v.name.getLexeme().equals("project")) {
			mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
			e.arguments.get(0).accept(this);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(" + getDescriptor(e.arguments.get(0).getType()) + ")V", false);
			return null;
		}
		if (e.callee instanceof Variable v && v.name.getLexeme().equals("capture")) {
			mv.visitFieldInsn(GETSTATIC, className, "scanner", "Ljava/util/Scanner;");
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/Scanner", "nextLine", "()Ljava/lang/String;", false);
			return null;
		}
		if (e.callee instanceof Get g) {
			g.object.accept(this);
			for (Expr arg : e.arguments) {
				arg.accept(this);
			}
			mv.visitMethodInsn(INVOKEVIRTUAL, className, g.name.getLexeme(), getCallDescriptor(e.arguments, e.getType()), false);
		}
		else {
			for (Expr arg : e.arguments) {
				arg.accept(this);
			}
			String methodName = ((Variable) e.callee).name.getLexeme();
			mv.visitMethodInsn(INVOKESTATIC, className, methodName, getCallDescriptor(e.arguments, e.getType()), false);
		}
		return null;
	}

	@Override
	public Void visitGet(Get e) {
		e.object.accept(this);
		mv.visitFieldInsn(GETFIELD, className, e.name.getLexeme(), getDescriptor(e.getType()));
		return null;
	}

	@Override
	public Void visitSet(Set e) {
		e.object.accept(this);
		e.value.accept(this);
		mv.visitInsn(e.value.getType() == ResolvedType.DOUBLE ? DUP2_X1 : DUP_X1);
		mv.visitFieldInsn(PUTFIELD, className, e.name.getLexeme(), getDescriptor(e.getType()));
		return null;
	}

	@Override
	public Void visitIndex(Index e) {
		e.array.accept(this);
		e.index.accept(this);
		mv.visitInsn(getArrayLoadOpcode(e.getType()));
		return null;
	}

	@Override
	public Void visitPostfix(Postfix e) {
		if (e.target instanceof Variable v) {
			Integer slot = localVars.get(v.resolvedDecl);
			v.accept(this);
			mv.visitInsn(e.getType() == ResolvedType.DOUBLE ? DUP2 : DUP);
			mv.visitLdcInsn(e.getType() == ResolvedType.DOUBLE ? 1.0 : 1);
			mv.visitInsn(e.op.getType() == TokenType.PLUS_PLUS ? (e.getType() == ResolvedType.DOUBLE ? DADD : IADD) : (e.getType() == ResolvedType.DOUBLE ? DSUB : ISUB));
			if (slot != null) {
				mv.visitVarInsn(getStoreOpcode(e.getType()), slot);
			}
			else {
				mv.visitFieldInsn(PUTSTATIC, className, v.name.getLexeme(), getDescriptor(e.getType()));
			}
		}
		return null;
	}

	@Override
	public Void visitThis(This e) {
		mv.visitVarInsn(ALOAD, 0);
		return null;
	}

	@Override
	public Void visitActionNew(ActionNew e) {
		if (e.type.dimension > 0) {
			if (e.type.arrayCapacities.isEmpty() && e.arrayInitializer != null) {
				mv.visitLdcInsn(e.arrayInitializer.size());
				mv.visitTypeInsn(ANEWARRAY, getInternalName(e.type.name.getLexeme()));
				for (int i = 0; i < e.arrayInitializer.size(); i++) {
					mv.visitInsn(DUP);
					mv.visitLdcInsn(i);
					e.arrayInitializer.get(i).accept(this);
					mv.visitInsn(getArrayStoreOpcode(e.arrayInitializer.get(i).getType()));
				}
			}
			else {
				for (Token cap : e.type.arrayCapacities) {
					mv.visitLdcInsn(Integer.parseInt(cap.getLexeme()));
				}
				mv.visitMultiANewArrayInsn(getDescriptor(e.getType()), e.type.dimension);
			}
		}
		else {
			mv.visitTypeInsn(NEW, e.type.name.getLexeme());
			mv.visitInsn(DUP);
			for (Expr arg : e.args) {
				arg.accept(this);
			}
			mv.visitMethodInsn(INVOKESPECIAL, e.type.name.getLexeme(), "<init>", getCallDescriptor(e.args, ResolvedType.SCRAP), false);
		}
		return null;
	}

	@Override
	public Void visitArrayLiteral(ArrayLiteral e) {
		mv.visitLdcInsn(e.elements.size());
		mv.visitTypeInsn(ANEWARRAY, getInternalName(e.getType().name()));
		for (int i = 0; i < e.elements.size(); i++) {
			mv.visitInsn(DUP);
			mv.visitLdcInsn(i);
			e.elements.get(i).accept(this);
			mv.visitInsn(getArrayStoreOpcode(e.elements.get(i).getType()));
		}
		return null;
	}

	private void emitComparison(TokenType op, ResolvedType type) {
		Label trueL = new Label(), endL = new Label();
		if (type == ResolvedType.DOUBLE) {
			mv.visitInsn(DCMPG);
			int jmp = switch (op) {
				case GREATER -> IFGT;
				case LESS -> IFLT;
				case GREATER_EQUAL -> IFGE;
				case LESS_EQUAL -> IFLE;
				case EQUAL_EQUAL -> IFEQ;
				default -> IFNE;
			};
			mv.visitJumpInsn(jmp, trueL);
		}
		else {
			int jmp = switch (op) {
				case GREATER -> IF_ICMPGT;
				case LESS -> IF_ICMPLT;
				case GREATER_EQUAL -> IF_ICMPGE;
				case LESS_EQUAL -> IF_ICMPLE;
				case EQUAL_EQUAL -> IF_ICMPEQ;
				default -> IF_ICMPNE;
			};
			mv.visitJumpInsn(jmp, trueL);
		}
		mv.visitInsn(ICONST_0);
		mv.visitJumpInsn(GOTO, endL);
		mv.visitLabel(trueL);
		mv.visitInsn(ICONST_1);
		mv.visitLabel(endL);
	}

	private String getDescriptor(ResolvedType type) {
		if (type == null) {
			return "Ljava/lang/Object;";
		}
		String base = switch (type.name()) {
			case "int" -> "I";
			case "double" -> "D";
			case "bool" -> "Z";
			case "char" -> "C";
			case "string" -> "Ljava/lang/String;";
			case "scrap" -> "V";
			default -> "L" + type.name() + ";";
		};
		return "[".repeat(type.dimensions()) + base;
	}

	private String getDescriptor(LType t) {
		return getDescriptor(new ResolvedType(t.name.getLexeme(), t.dimension));
	}

	private String getMethodDescriptor(SceneDecl d) {
		StringBuilder sb = new StringBuilder("(");
		for (Param p : d.params) {
			sb.append(getDescriptor(p.type));
		}
		return sb.append(")").append(d.returnType.name.getLexeme().equals("scrap") ? "V" : getDescriptor(d.returnType)).toString();
	}

	private String getCtorDescriptor(ConstructorDecl d) {
		StringBuilder sb = new StringBuilder("(");
		for (Param p : d.params) {
			sb.append(getDescriptor(p.type));
		}
		return sb.append(")V").toString();
	}

	private String getCallDescriptor(List<Expr> args, ResolvedType ret) {
		StringBuilder sb = new StringBuilder("(");
		for (Expr a : args) {
			sb.append(getDescriptor(a.getType()));
		}
		return sb.append(")").append(getDescriptor(ret)).toString();
	}

	private String getInternalName(String name) {
		return name.equals("string") ? "java/lang/String" : name;
	}

	private int getLoadOpcode(ResolvedType t) {
		return t.dimensions() > 0 ? ALOAD : (t.name().equals("double") ? DLOAD : ILOAD);
	}

	private int getStoreOpcode(ResolvedType t) {
		return t.dimensions() > 0 ? ASTORE : (t.name().equals("double") ? DSTORE : ISTORE);
	}

	private int getReturnOpcode(ResolvedType t) {
		return t.dimensions() > 0 ? ARETURN : (t.name().equals("double") ? DRETURN : (t.name().equals("scrap") ? RETURN : IRETURN));
	}

	private int getArrayLoadOpcode(ResolvedType t) {
		return t.dimensions() > 0 ? AALOAD : (t.name().equals("double") ? DALOAD : IALOAD);
	}

	private int getArrayStoreOpcode(ResolvedType t) {
		return t.dimensions() > 0 ? AASTORE : (t.name().equals("double") ? DASTORE : IASTORE);
	}
}