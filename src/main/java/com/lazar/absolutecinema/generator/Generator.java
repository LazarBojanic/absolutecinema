
package com.lazar.absolutecinema.generator;

import com.lazar.absolutecinema.parser.ast.*;
import com.lazar.absolutecinema.parser.ast.Set;
import com.lazar.absolutecinema.semantic.ResolvedType;
import org.objectweb.asm.*;

import java.util.*;

public class Generator {
	private final Map<String, SceneDecl> scenes = new HashMap<>();
	private final Map<String, VarDecl> globalVars = new HashMap<>();
	private final Map<String, SetupDecl> setups = new HashMap<>();
	private final String MAIN_CLASS_NAME = "Main";
	private String currentMethodName;
	private Type currentMethodReturnType;
	private Map<String, LocalVarInfo> localVars = new HashMap<>();
	private int nextLocalIndex = 0;
	private List<Label> loopStartLabels = new ArrayList<>();
	private List<Label> loopEndLabels = new ArrayList<>();
	private StringBuilder jasminOutput;
	private int labelCounter = 0;
	private MethodVisitor currentMethodVisitor;

	private static class LocalVarInfo {
		int index;
		Type type;

		LocalVarInfo(int index, Type type) {
			this.index = index;
			this.type = type;
		}
	}

	public GenerationResult generate(Program program) {
		try {
			for (Node item : program.items) {
				if (item instanceof SceneDecl scene) {
					scenes.put(scene.name.getLexeme(), scene);
				}
				else if (item instanceof VarDecl varDecl) {
					globalVars.put(varDecl.name.getLexeme(), varDecl);
				}
				else if (item instanceof SetupDecl setup) {
					setups.put(setup.name.getLexeme(), setup);
				}
			}
			if (!scenes.containsKey("entrance")) {
				throw new RuntimeException("No entrance scene found - required entry point");
			}
			byte[] mainClassBytes = generateMainClass();
			jasminOutput = new StringBuilder();
			generateJasminOutput();
			return new GenerationResult(jasminOutput.toString(), mainClassBytes);
		}
		catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("JVM generation failed: " + e.getMessage(), e);
		}
	}

	private byte[] generateMainClass() {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
		cw.visit(Opcodes.V21,
				Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
				MAIN_CLASS_NAME,
				null,
				"java/lang/Object",
				null);
		cw.visitField(
				Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
				"scanner",
				"Ljava/util/Scanner;",
				null,
				null
		);
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
		mv.visitCode();
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(1, 1);
		mv.visitEnd();

		generateStaticInitializer(cw);
		generateProjectMethod(cw);
		generateCaptureMethod(cw);

		for (VarDecl globalVar : globalVars.values()) {
			generateGlobalField(cw, globalVar);
		}
		for (SceneDecl scene : scenes.values()) {
			generateSceneMethod(cw, scene);
		}
		generateMainMethod(cw);
		cw.visitEnd();
		return cw.toByteArray();
	}

	private void generateStaticInitializer(ClassWriter cw) {
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
		mv.visitCode();
		mv.visitTypeInsn(Opcodes.NEW, "java/util/Scanner");
		mv.visitInsn(Opcodes.DUP);
		mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "in", "Ljava/io/InputStream;");
		mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/Scanner", "<init>", "(Ljava/io/InputStream;)V", false);
		mv.visitFieldInsn(Opcodes.PUTSTATIC, MAIN_CLASS_NAME, "scanner", "Ljava/util/Scanner;");
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(3, 0);
		mv.visitEnd();
	}

	private void generateProjectMethod(ClassWriter cw) {
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
				"project", "(Ljava/lang/String;)V", null, null);
		mv.visitCode();
		mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(2, 1);
		mv.visitEnd();
	}

	private void generateCaptureMethod(ClassWriter cw) {
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
				"capture", "()Ljava/lang/String;", null, null);
		mv.visitCode();
		mv.visitFieldInsn(Opcodes.GETSTATIC, MAIN_CLASS_NAME, "scanner", "Ljava/util/Scanner;");
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/Scanner", "nextLine", "()Ljava/lang/String;", false);
		mv.visitInsn(Opcodes.ARETURN);
		mv.visitMaxs(1, 0);
		mv.visitEnd();
	}

	private void generateGlobalField(ClassWriter cw, VarDecl varDecl) {
		Type fieldType = mapType(varDecl.type);
		String descriptor = fieldType.getDescriptor();
		cw.visitField(
				Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
				varDecl.name.getLexeme(),
				descriptor,
				null,
				null
		);
	}

	private void generateMainMethod(ClassWriter cw) {
		SceneDecl entrance = scenes.get("entrance");
		if (entrance == null) {
			throw new RuntimeException("No entrance scene found");
		}
		currentMethodName = "main";
		currentMethodReturnType = Type.VOID_TYPE;
		localVars.clear();
		nextLocalIndex = 1;
		localVars.put("args", new LocalVarInfo(0, Type.getType("[Ljava/lang/String;")));
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
				"main", "([Ljava/lang/String;)V", null, null);
		currentMethodVisitor = mv;
		mv.visitCode();
		generateMethodBody(mv, entrance);
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
		currentMethodVisitor = null;
	}

	private void generateSceneMethod(ClassWriter cw, SceneDecl scene) {
		if (scene.name.getLexeme().equals("entrance")) {
			return;
		}
		currentMethodName = scene.name.getLexeme();
		currentMethodReturnType = mapType(scene.returnType);
		localVars.clear();
		nextLocalIndex = 0;
		StringBuilder descriptor = new StringBuilder("(");
		for (Param param : scene.params) {
			Type paramType = mapType(param.type);
			descriptor.append(paramType.getDescriptor());
			localVars.put(param.name.getLexeme(), new LocalVarInfo(nextLocalIndex, paramType));
			if (paramType.equals(Type.DOUBLE_TYPE) || paramType.equals(Type.LONG_TYPE)) {
				nextLocalIndex += 2;
			}
			else {
				nextLocalIndex++;
			}
		}
		descriptor.append(")").append(currentMethodReturnType.getDescriptor());
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
				scene.name.getLexeme(),
				descriptor.toString(), null, null);
		currentMethodVisitor = mv;
		mv.visitCode();
		generateMethodBody(mv, scene);
		if (!hasReturnStatement(scene.body)) {
			if (currentMethodReturnType.equals(Type.VOID_TYPE)) {
				mv.visitInsn(Opcodes.RETURN);
			}
			else {
				pushDefaultValue(mv, currentMethodReturnType);
				if (currentMethodReturnType.equals(Type.INT_TYPE)) {
					mv.visitInsn(Opcodes.IRETURN);
				}
				else if (currentMethodReturnType.equals(Type.DOUBLE_TYPE)) {
					mv.visitInsn(Opcodes.DRETURN);
				}
				else if (currentMethodReturnType.equals(Type.getType(String.class))) {
					mv.visitInsn(Opcodes.ARETURN);
				}
				else {
					mv.visitInsn(Opcodes.ARETURN);
				}
			}
		}
		mv.visitMaxs(0, 0);
		mv.visitEnd();
		currentMethodVisitor = null;
	}

	private void generateMethodBody(MethodVisitor mv, SceneDecl scene) {
		if (scene.body == null || scene.body.statements == null) {
			return;
		}
		for (Node node : scene.body.statements) {
			if (node instanceof Stmt stmt) {
				generateStatement(mv, stmt);
			}
			else if (node instanceof Var varDecl) {
				generateLocalVariable(mv, varDecl.decl);
			}
		}
	}

	private void generateStatement(MethodVisitor mv, Stmt stmt) {
		if (stmt instanceof ExprStmt exprStmt) {
			generateExpression(mv, exprStmt.expr);
			if (exprStmt.expr.getType() != null &&
					!exprStmt.expr.getType().name().equals("scrap")) {
				popValue(mv, exprStmt.expr.getType());
			}
		}
		else if (stmt instanceof If ifStmt) {
			generateIfStatement(mv, ifStmt);
		}
		else if (stmt instanceof Return returnStmt) {
			generateReturnStatement(mv, returnStmt);
		}
		else if (stmt instanceof Block block) {
			generateBlock(mv, block);
		}
		else if (stmt instanceof com.lazar.absolutecinema.parser.ast.Var varStmt) {
			generateLocalVariable(mv, varStmt.decl);
		}
		else if (stmt instanceof While whileStmt) {
			generateWhileStatement(mv, whileStmt);
		}
		else if (stmt instanceof For forStmt) {
			generateForStatement(mv, forStmt);
		}
		else if (stmt instanceof Break breakStmt) {
			generateBreakStatement(mv);
		}
		else if (stmt instanceof Continue continueStmt) {
			generateContinueStatement(mv);
		}
	}

	private void generateBlock(MethodVisitor mv, Block block) {
		if (block == null || block.statements == null) {
			return;
		}
		for (Node node : block.statements) {
			if (node instanceof Stmt stmt) {
				generateStatement(mv, stmt);
			}
			else if (node instanceof Var varDecl) {
				generateLocalVariable(mv, varDecl.decl);
			}
		}
	}

	private void generateIfStatement(MethodVisitor mv, If ifStmt) {
		Label elseLabel = new Label();
		Label endLabel = new Label();

		generateExpression(mv, ifStmt.ifBranch.cond);
		convertToBoolean(mv, ifStmt.ifBranch.cond.getType());
		mv.visitJumpInsn(Opcodes.IFEQ, elseLabel);
		generateBlock(mv, ifStmt.ifBranch.block);
		mv.visitJumpInsn(Opcodes.GOTO, endLabel);

		mv.visitLabel(elseLabel);
		if (!ifStmt.elifBranchList.isEmpty()) {
			for (int i = 0; i < ifStmt.elifBranchList.size(); i++) {
				Branch elifBranch = ifStmt.elifBranchList.get(i);
				Label nextElifLabel = (i < ifStmt.elifBranchList.size() - 1) ? new Label() :
						(ifStmt.elseBranch != null && ifStmt.elseBranch.block != null ? new Label() : endLabel);

				generateExpression(mv, elifBranch.cond);
				convertToBoolean(mv, elifBranch.cond.getType());
				mv.visitJumpInsn(Opcodes.IFEQ, nextElifLabel);
				generateBlock(mv, elifBranch.block);
				mv.visitJumpInsn(Opcodes.GOTO, endLabel);

				mv.visitLabel(nextElifLabel);
				elseLabel = nextElifLabel;
			}
		}

		if (ifStmt.elseBranch != null && ifStmt.elseBranch.block != null) {
			generateBlock(mv, ifStmt.elseBranch.block);
		}

		mv.visitLabel(endLabel);
	}

	private void generateWhileStatement(MethodVisitor mv, While whileStmt) {
		Label startLabel = new Label();
		Label endLabel = new Label();
		loopStartLabels.add(startLabel);
		loopEndLabels.add(endLabel);

		mv.visitLabel(startLabel);
		generateExpression(mv, whileStmt.condition);
		convertToBoolean(mv, whileStmt.condition.getType());
		mv.visitJumpInsn(Opcodes.IFEQ, endLabel);
		generateStatement(mv, whileStmt.body);
		mv.visitJumpInsn(Opcodes.GOTO, startLabel);

		mv.visitLabel(endLabel);
		loopStartLabels.remove(loopStartLabels.size() - 1);
		loopEndLabels.remove(loopEndLabels.size() - 1);
	}

	private void generateForStatement(MethodVisitor mv, For forStmt) {
		Map<String, LocalVarInfo> previousLocals = new HashMap<>(localVars);
		int previousNextLocal = nextLocalIndex;

		if (forStmt.initializer != null) {
			if (forStmt.initializer instanceof Var varDecl) {
				generateLocalVariable(mv, varDecl.decl);
			}
			else if (forStmt.initializer instanceof ExprStmt exprStmt) {
				generateExpression(mv, exprStmt.expr);
				popValue(mv, exprStmt.expr.getType());
			}
		}

		Label startLabel = new Label();
		Label endLabel = new Label();
		loopStartLabels.add(startLabel);
		loopEndLabels.add(endLabel);

		mv.visitLabel(startLabel);
		if (forStmt.condition != null) {
			generateExpression(mv, forStmt.condition);
			convertToBoolean(mv, forStmt.condition.getType());
			mv.visitJumpInsn(Opcodes.IFEQ, endLabel);
		}
		generateStatement(mv, forStmt.body);

		if (forStmt.increment != null) {
			generateExpression(mv, forStmt.increment);
			popValue(mv, forStmt.increment.getType());
		}
		mv.visitJumpInsn(Opcodes.GOTO, startLabel);

		mv.visitLabel(endLabel);
		loopStartLabels.remove(loopStartLabels.size() - 1);
		loopEndLabels.remove(loopEndLabels.size() - 1);

		localVars = previousLocals;
		nextLocalIndex = previousNextLocal;
	}

	private void generateBreakStatement(MethodVisitor mv) {
		if (loopEndLabels.isEmpty()) {
			throw new RuntimeException("Break statement outside of loop");
		}
		mv.visitJumpInsn(Opcodes.GOTO, loopEndLabels.get(loopEndLabels.size() - 1));
	}

	private void generateContinueStatement(MethodVisitor mv) {
		if (loopStartLabels.isEmpty()) {
			throw new RuntimeException("Continue statement outside of loop");
		}
		mv.visitJumpInsn(Opcodes.GOTO, loopStartLabels.get(loopStartLabels.size() - 1));
	}

	private void generateReturnStatement(MethodVisitor mv, Return returnStmt) {
		if (returnStmt.value != null) {
			generateExpression(mv, returnStmt.value);
			ResolvedType returnType = returnStmt.value.getType();
			if (returnType != null) {
				if (returnType.equals(ResolvedType.INT)) {
					mv.visitInsn(Opcodes.IRETURN);
				}
				else if (returnType.equals(ResolvedType.DOUBLE)) {
					mv.visitInsn(Opcodes.DRETURN);
				}
				else if (returnType.equals(ResolvedType.BOOL)) {
					mv.visitInsn(Opcodes.IRETURN);
				}
				else if (returnType.equals(ResolvedType.STRING)) {
					mv.visitInsn(Opcodes.ARETURN);
				}
				else {
					mv.visitInsn(Opcodes.ARETURN);
				}
			}
			else {
				mv.visitInsn(Opcodes.ARETURN);
			}
		}
		else {
			mv.visitInsn(Opcodes.RETURN);
		}
	}

	private void generateLocalVariable(MethodVisitor mv, VarDecl varDecl) {
		String varName = varDecl.name.getLexeme();
		Type varType = mapType(varDecl.type);
		int varIndex = nextLocalIndex;

		if (varType.equals(Type.DOUBLE_TYPE)) {
			nextLocalIndex += 2;
		}
		else {
			nextLocalIndex++;
		}

		localVars.put(varName, new LocalVarInfo(varIndex, varType));

		if (varDecl.initializer != null) {
			generateExpression(mv, varDecl.initializer);
			storeLocalVariable(mv, varIndex, varType);
		}
		else {
			pushDefaultValue(mv, varType);
			storeLocalVariable(mv, varIndex, varType);
		}
	}

	private void storeLocalVariable(MethodVisitor mv, int index, Type type) {
		switch (type.getSort()) {
			case Type.INT:
			case Type.BOOLEAN:
				mv.visitVarInsn(Opcodes.ISTORE, index);
				break;
			case Type.DOUBLE:
				mv.visitVarInsn(Opcodes.DSTORE, index);
				break;
			case Type.OBJECT:
			case Type.ARRAY:
				mv.visitVarInsn(Opcodes.ASTORE, index);
				break;
			default:
				mv.visitVarInsn(Opcodes.ASTORE, index);
		}
	}

	private void generateExpression(MethodVisitor mv, Expr expr) {
		if (expr == null) {
			return;
		}
		if (expr instanceof Literal literal) {
			generateLiteral(mv, literal);
		}
		else if (expr instanceof Variable variable) {
			generateVariable(mv, variable);
		}
		else if (expr instanceof Binary binary) {
			generateBinaryExpression(mv, binary);
		}
		else if (expr instanceof Logical logical) {
			generateLogicalExpression(mv, logical);
		}
		else if (expr instanceof Unary unary) {
			generateUnaryExpression(mv, unary);
		}
		else if (expr instanceof Call call) {
			generateCall(mv, call);
		}
		else if (expr instanceof Grouping grouping) {
			generateExpression(mv, grouping.expr);
		}
		else if (expr instanceof Assign assign) {
			generateAssign(mv, assign);
		}
		else if (expr instanceof Postfix postfix) {
			generatePostfix(mv, postfix);
		}
		else if (expr instanceof Get get) {
			generateGet(mv, get);
		}
		else if (expr instanceof Set set) {
			generateSet(mv, set);
		}
		else if (expr instanceof This thisExpr) {
			generateThis(mv, thisExpr);
		}
		else if (expr instanceof ActionNew actionNew) {
			generateActionNew(mv, actionNew);
		}
		else if (expr instanceof Index index) {
			generateIndex(mv, index);
		}
	}

	private void generateLiteral(MethodVisitor mv, Literal literal) {
		Object value = literal.value;
		ResolvedType type = literal.getType();
		if (value == null) {
			mv.visitInsn(Opcodes.ACONST_NULL);
		}
		else if (type.equals(ResolvedType.INT)) {
			int intVal = ((Number) value).intValue();
			if (intVal >= -1 && intVal <= 5) {
				switch (intVal) {
					case -1:
						mv.visitInsn(Opcodes.ICONST_M1);
						break;
					case 0:
						mv.visitInsn(Opcodes.ICONST_0);
						break;
					case 1:
						mv.visitInsn(Opcodes.ICONST_1);
						break;
					case 2:
						mv.visitInsn(Opcodes.ICONST_2);
						break;
					case 3:
						mv.visitInsn(Opcodes.ICONST_3);
						break;
					case 4:
						mv.visitInsn(Opcodes.ICONST_4);
						break;
					case 5:
						mv.visitInsn(Opcodes.ICONST_5);
						break;
				}
			}
			else if (intVal >= Byte.MIN_VALUE && intVal <= Byte.MAX_VALUE) {
				mv.visitIntInsn(Opcodes.BIPUSH, intVal);
			}
			else if (intVal >= Short.MIN_VALUE && intVal <= Short.MAX_VALUE) {
				mv.visitIntInsn(Opcodes.SIPUSH, intVal);
			}
			else {
				mv.visitLdcInsn(intVal);
			}
		}
		else if (type.equals(ResolvedType.DOUBLE)) {
			double doubleVal = ((Number) value).doubleValue();
			if (doubleVal == 0.0) {
				mv.visitInsn(Opcodes.DCONST_0);
			}
			else if (doubleVal == 1.0) {
				mv.visitInsn(Opcodes.DCONST_1);
			}
			else {
				mv.visitLdcInsn(doubleVal);
			}
		}
		else if (type.equals(ResolvedType.STRING)) {
			mv.visitLdcInsn(value.toString());
		}
		else if (type.equals(ResolvedType.BOOL)) {
			boolean boolVal = (Boolean) value;
			mv.visitInsn(boolVal ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
		}
		else if (type.equals(ResolvedType.CHAR)) {
			char charVal = (Character) value;
			mv.visitIntInsn(Opcodes.BIPUSH, charVal);
		}
	}

	private void generateVariable(MethodVisitor mv, Variable variable) {
		String varName = variable.name.getLexeme();
		LocalVarInfo localVar = localVars.get(varName);
		if (localVar != null) {
			loadLocalVariable(mv, localVar.index, localVar.type);
			return;
		}
		VarDecl globalVar = globalVars.get(varName);
		if (globalVar != null) {
			Type fieldType = mapType(globalVar.type);
			mv.visitFieldInsn(Opcodes.GETSTATIC, MAIN_CLASS_NAME, varName, fieldType.getDescriptor());
			return;
		}
		throw new RuntimeException("Undefined variable: " + varName);
	}

	private void loadLocalVariable(MethodVisitor mv, int index, Type type) {
		switch (type.getSort()) {
			case Type.INT:
			case Type.BOOLEAN:
				mv.visitVarInsn(Opcodes.ILOAD, index);
				break;
			case Type.DOUBLE:
				mv.visitVarInsn(Opcodes.DLOAD, index);
				break;
			case Type.OBJECT:
			case Type.ARRAY:
				mv.visitVarInsn(Opcodes.ALOAD, index);
				break;
			default:
				mv.visitVarInsn(Opcodes.ALOAD, index);
		}
	}

	private void generateBinaryExpression(MethodVisitor mv, Binary binary) {
		ResolvedType leftType = binary.left.getType();
		ResolvedType rightType = binary.right.getType();
		String op = binary.op.getLexeme();

		if (op.equals("+") && (leftType.equals(ResolvedType.STRING) || rightType.equals(ResolvedType.STRING))) {
			generateStringConcatenation(mv, binary);
			return;
		}

		generateExpression(mv, binary.left);
		generateExpression(mv, binary.right);

		if (leftType.equals(ResolvedType.INT) && rightType.equals(ResolvedType.INT)) {
			switch (op) {
				case "+":
					mv.visitInsn(Opcodes.IADD);
					break;
				case "-":
					mv.visitInsn(Opcodes.ISUB);
					break;
				case "*":
					mv.visitInsn(Opcodes.IMUL);
					break;
				case "/":
					mv.visitInsn(Opcodes.IDIV);
					break;
				case "%":
					mv.visitInsn(Opcodes.IREM);
					break;
				case "<=":
					generateIntComparison(mv, Opcodes.IFLE);
					break;
				case "==":
					generateIntComparison(mv, Opcodes.IF_ICMPEQ);
					break;
				case "!=":
					generateIntComparison(mv, Opcodes.IF_ICMPNE);
					break;
				case "<":
					generateIntComparison(mv, Opcodes.IF_ICMPLT);
					break;
				case ">":
					generateIntComparison(mv, Opcodes.IF_ICMPGT);
					break;
				case ">=":
					generateIntComparison(mv, Opcodes.IF_ICMPGE);
					break;
				default:
					throw new RuntimeException("Unsupported integer operation: " + op);
			}
		}
		else if (leftType.equals(ResolvedType.DOUBLE) && rightType.equals(ResolvedType.DOUBLE)) {
			switch (op) {
				case "+":
					mv.visitInsn(Opcodes.DADD);
					break;
				case "-":
					mv.visitInsn(Opcodes.DSUB);
					break;
				case "*":
					mv.visitInsn(Opcodes.DMUL);
					break;
				case "/":
					mv.visitInsn(Opcodes.DDIV);
					break;
				case "%":
					mv.visitInsn(Opcodes.DREM);
					break;
				case "==":
					generateDoubleComparison(mv, Opcodes.IFEQ);
					break;
				case "!=":
					generateDoubleComparison(mv, Opcodes.IFNE);
					break;
				case "<":
					generateDoubleComparison(mv, Opcodes.IFLT);
					break;
				case "<=":
					generateDoubleComparison(mv, Opcodes.IFLE);
					break;
				case ">":
					generateDoubleComparison(mv, Opcodes.IFGT);
					break;
				case ">=":
					generateDoubleComparison(mv, Opcodes.IFGE);
					break;
				default:
					throw new RuntimeException("Unsupported double operation: " + op);
			}
		}
		else if ((leftType.equals(ResolvedType.INT) && rightType.equals(ResolvedType.DOUBLE)) ||
				(leftType.equals(ResolvedType.DOUBLE) && rightType.equals(ResolvedType.INT))) {
			throw new RuntimeException("Cannot mix int and double in binary operation without explicit conversion");
		}
		else {
			throw new RuntimeException("Unsupported binary operation types: " +
					leftType + " " + op + " " + rightType);
		}
	}

	private void generateStringConcatenation(MethodVisitor mv, Binary binary) {
		mv.visitTypeInsn(Opcodes.NEW, "java/lang/StringBuilder");
		mv.visitInsn(Opcodes.DUP);
		mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);

		generateExpression(mv, binary.left);
		ResolvedType leftType = binary.left.getType();
		appendToStringBuilder(mv, leftType);

		generateExpression(mv, binary.right);
		ResolvedType rightType = binary.right.getType();
		appendToStringBuilder(mv, rightType);

		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
	}

	private void appendToStringBuilder(MethodVisitor mv, ResolvedType type) {
		if (type.equals(ResolvedType.INT)) {
			mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;", false);
		}
		else if (type.equals(ResolvedType.DOUBLE)) {
			mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(D)Ljava/lang/StringBuilder;", false);
		}
		else if (type.equals(ResolvedType.BOOL)) {
			mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Z)Ljava/lang/StringBuilder;", false);
		}
		else {
			mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/Object;)Ljava/lang/StringBuilder;", false);
		}
	}

	private void generateIntComparison(MethodVisitor mv, int jumpOpcode) {
		Label trueLabel = new Label();
		Label endLabel = new Label();

		if (jumpOpcode == Opcodes.IFLE) {
			mv.visitInsn(Opcodes.ISUB);
			mv.visitJumpInsn(jumpOpcode, trueLabel);
		}
		else {
			mv.visitJumpInsn(jumpOpcode, trueLabel);
		}

		mv.visitInsn(Opcodes.ICONST_0);
		mv.visitJumpInsn(Opcodes.GOTO, endLabel);
		mv.visitLabel(trueLabel);
		mv.visitInsn(Opcodes.ICONST_1);
		mv.visitLabel(endLabel);
	}

	private void generateDoubleComparison(MethodVisitor mv, int jumpOpcode) {
		Label trueLabel = new Label();
		Label endLabel = new Label();

		mv.visitInsn(Opcodes.DCMPL);
		mv.visitJumpInsn(jumpOpcode, trueLabel);
		mv.visitInsn(Opcodes.ICONST_0);
		mv.visitJumpInsn(Opcodes.GOTO, endLabel);
		mv.visitLabel(trueLabel);
		mv.visitInsn(Opcodes.ICONST_1);
		mv.visitLabel(endLabel);
	}

	private void generateLogicalExpression(MethodVisitor mv, Logical logical) {
		String op = logical.op.getLexeme();
		Label falseLabel = new Label();
		Label endLabel = new Label();

		if (op.equals("&&")) {
			generateExpression(mv, logical.left);
			convertToBoolean(mv, logical.left.getType());
			mv.visitJumpInsn(Opcodes.IFEQ, falseLabel);
			generateExpression(mv, logical.right);
			convertToBoolean(mv, logical.right.getType());
			mv.visitJumpInsn(Opcodes.IFEQ, falseLabel);
			mv.visitInsn(Opcodes.ICONST_1);
			mv.visitJumpInsn(Opcodes.GOTO, endLabel);
			mv.visitLabel(falseLabel);
			mv.visitInsn(Opcodes.ICONST_0);
			mv.visitLabel(endLabel);
		}
		else if (op.equals("||")) {
			Label trueLabel = new Label();
			generateExpression(mv, logical.left);
			convertToBoolean(mv, logical.left.getType());
			mv.visitJumpInsn(Opcodes.IFNE, trueLabel);
			generateExpression(mv, logical.right);
			convertToBoolean(mv, logical.right.getType());
			mv.visitJumpInsn(Opcodes.IFNE, trueLabel);
			mv.visitInsn(Opcodes.ICONST_0);
			mv.visitJumpInsn(Opcodes.GOTO, endLabel);
			mv.visitLabel(trueLabel);
			mv.visitInsn(Opcodes.ICONST_1);
			mv.visitLabel(endLabel);
		}
	}

	private void generateUnaryExpression(MethodVisitor mv, Unary unary) {
		generateExpression(mv, unary.right);
		ResolvedType type = unary.right.getType();
		String op = unary.op.getLexeme();

		switch (op) {
			case "-":
				if (type.equals(ResolvedType.INT)) {
					mv.visitInsn(Opcodes.INEG);
				}
				else if (type.equals(ResolvedType.DOUBLE)) {
					mv.visitInsn(Opcodes.DNEG);
				}
				break;
			case "+":
				break;
			case "!":
				convertToBoolean(mv, type);
				mv.visitInsn(Opcodes.ICONST_1);
				mv.visitInsn(Opcodes.IXOR);
				break;
			case "++":
			case "--":
				throw new RuntimeException("Prefix ++ and -- not yet implemented");
		}
	}

	private void generatePostfix(MethodVisitor mv, Postfix postfix) {
		if (postfix.target instanceof Variable) {
			Variable var = (Variable) postfix.target;
			LocalVarInfo localVar = localVars.get(var.name.getLexeme());
			if (localVar != null) {
				String op = postfix.op.getLexeme();

				if (localVar.type.equals(Type.INT_TYPE)) {
					loadLocalVariable(mv, localVar.index, localVar.type);
					mv.visitInsn(Opcodes.DUP);

					if (op.equals("++")) {
						mv.visitInsn(Opcodes.ICONST_1);
						mv.visitInsn(Opcodes.IADD);
					}
					else if (op.equals("--")) {
						mv.visitInsn(Opcodes.ICONST_1);
						mv.visitInsn(Opcodes.ISUB);
					}

					storeLocalVariable(mv, localVar.index, localVar.type);
				}
				else if (localVar.type.equals(Type.DOUBLE_TYPE)) {
					loadLocalVariable(mv, localVar.index, localVar.type);
					mv.visitInsn(Opcodes.DUP2);

					if (op.equals("++")) {
						mv.visitInsn(Opcodes.DCONST_1);
						mv.visitInsn(Opcodes.DADD);
					}
					else if (op.equals("--")) {
						mv.visitInsn(Opcodes.DCONST_1);
						mv.visitInsn(Opcodes.DSUB);
					}

					storeLocalVariable(mv, localVar.index, localVar.type);
				}
			}
		}
	}

	private void generateCall(MethodVisitor mv, Call call) {
		if (call.callee instanceof Variable) {
			Variable callee = (Variable) call.callee;
			String funcName = callee.name.getLexeme();

			if (funcName.equals("project")) {
				for (Expr arg : call.arguments) {
					generateExpression(mv, arg);
				}
				mv.visitMethodInsn(Opcodes.INVOKESTATIC,
						MAIN_CLASS_NAME,
						"project",
						"(Ljava/lang/String;)V",
						false);
				return;
			}

			if (funcName.equals("capture")) {
				mv.visitMethodInsn(Opcodes.INVOKESTATIC,
						MAIN_CLASS_NAME,
						"capture",
						"()Ljava/lang/String;",
						false);
				return;
			}

			for (Expr arg : call.arguments) {
				generateExpression(mv, arg);
			}

			StringBuilder descriptor = new StringBuilder("(");
			for (Expr arg : call.arguments) {
				Type argType = mapType(arg.getType());
				descriptor.append(argType.getDescriptor());
			}
			descriptor.append(")").append(mapType(call.getType()).getDescriptor());

			mv.visitMethodInsn(Opcodes.INVOKESTATIC,
					MAIN_CLASS_NAME,
					funcName,
					descriptor.toString(),
					false);
		}
	}

	private void generateAssign(MethodVisitor mv, Assign assign) {
		generateExpression(mv, assign.value);

		if (assign.target instanceof Variable) {
			Variable target = (Variable) assign.target;
			String varName = target.name.getLexeme();
			LocalVarInfo localVar = localVars.get(varName);

			if (localVar != null) {
				if (localVar.type.equals(Type.INT_TYPE) || localVar.type.equals(Type.BOOLEAN_TYPE)) {
					mv.visitInsn(Opcodes.DUP);
				}
				else if (localVar.type.equals(Type.DOUBLE_TYPE)) {
					mv.visitInsn(Opcodes.DUP2);
				}
				else {
					mv.visitInsn(Opcodes.DUP);
				}
				storeLocalVariable(mv, localVar.index, localVar.type);
				return;
			}

			VarDecl globalVar = globalVars.get(varName);
			if (globalVar != null) {
				Type fieldType = mapType(globalVar.type);
				if (fieldType.equals(Type.INT_TYPE) || fieldType.equals(Type.BOOLEAN_TYPE)) {
					mv.visitInsn(Opcodes.DUP);
				}
				else if (fieldType.equals(Type.DOUBLE_TYPE)) {
					mv.visitInsn(Opcodes.DUP2);
				}
				else {
					mv.visitInsn(Opcodes.DUP);
				}
				mv.visitFieldInsn(Opcodes.PUTSTATIC, MAIN_CLASS_NAME, varName, fieldType.getDescriptor());
				return;
			}

			throw new RuntimeException("Undefined variable: " + varName);
		}
		else if (assign.target instanceof Index) {
			Index indexExpr = (Index) assign.target;
			generateExpression(mv, indexExpr.array);
			generateExpression(mv, indexExpr.index);

			ResolvedType elementType = indexExpr.getType();
			if (elementType.equals(ResolvedType.INT)) {
				mv.visitInsn(Opcodes.DUP2_X1);
				mv.visitInsn(Opcodes.POP2);
				mv.visitInsn(Opcodes.IASTORE);
			}
			else if (elementType.equals(ResolvedType.DOUBLE)) {
				mv.visitInsn(Opcodes.DUP2_X2);
				mv.visitInsn(Opcodes.POP2);
				mv.visitInsn(Opcodes.DASTORE);
			}
			else {
				mv.visitInsn(Opcodes.DUP2_X1);
				mv.visitInsn(Opcodes.POP2);
				mv.visitInsn(Opcodes.AASTORE);
			}
		}
	}

	private void generateGet(MethodVisitor mv, Get get) {
		if (get.name.getLexeme().equals("length")) {
			generateExpression(mv, get.object);
			mv.visitInsn(Opcodes.ARRAYLENGTH);
		}
		else {
			throw new RuntimeException("Field access not supported without classes");
		}
	}

	private void generateSet(MethodVisitor mv, Set set) {
		throw new RuntimeException("Field assignment not supported without classes");
	}

	private void generateThis(MethodVisitor mv, This thisExpr) {
		throw new RuntimeException("@ (this) not supported without classes");
	}

	private void generateActionNew(MethodVisitor mv, ActionNew actionNew) {
		ResolvedType type = actionNew.getType();
		String typeName = type.name();
		int dimensions = type.dimensions();

		if (dimensions > 0) {
			generateArrayCreation(mv, typeName, dimensions, actionNew);
		}
		else {
			throw new RuntimeException("Object creation not supported without classes");
		}
	}

	private void generateArrayCreation(MethodVisitor mv, String typeName, int dimensions, ActionNew actionNew) {
		if (actionNew.arrayInitializer != null && !actionNew.arrayInitializer.isEmpty()) {
			generateArrayWithInitializer(mv, typeName, dimensions, actionNew.arrayInitializer);
		}
		else {
			generateMultidimensionalArray(mv, typeName, dimensions, actionNew.args);
		}
	}

	private void generateMultidimensionalArray(MethodVisitor mv, String typeName, int dimensions, List<Expr> args) {
		if (dimensions == 0) {
			throw new RuntimeException("Cannot create array with 0 dimensions");
		}

		if (args == null || args.isEmpty()) {
			throw new RuntimeException("Array dimensions must be specified");
		}

		Type baseType = getBaseType(typeName);

		for (Expr arg : args) {
			generateExpression(mv, arg);
		}

		if (dimensions == 1) {
			mv.visitIntInsn(Opcodes.NEWARRAY, getArrayTypeCode(baseType));
		}
		else {
			String descriptor = baseType.getDescriptor();
			StringBuilder arrayDesc = new StringBuilder();
			for (int i = 0; i < dimensions; i++) {
				arrayDesc.append("[");
			}
			arrayDesc.append(descriptor);
			mv.visitMultiANewArrayInsn(arrayDesc.toString(), dimensions);
		}
	}

	private void generateArrayWithInitializer(MethodVisitor mv, String typeName, int dimensions, List<Expr> elements) {
		Type baseType = getBaseType(typeName);

		mv.visitLdcInsn(elements.size());
		mv.visitIntInsn(Opcodes.NEWARRAY, getArrayTypeCode(baseType));

		for (int i = 0; i < elements.size(); i++) {
			mv.visitInsn(Opcodes.DUP);
			mv.visitLdcInsn(i);
			generateExpression(mv, elements.get(i));

			if (baseType.equals(Type.INT_TYPE)) {
				mv.visitInsn(Opcodes.IASTORE);
			}
			else if (baseType.equals(Type.DOUBLE_TYPE)) {
				mv.visitInsn(Opcodes.DASTORE);
			}
			else {
				mv.visitInsn(Opcodes.AASTORE);
			}
		}
	}

	private Type getBaseType(String typeName) {
		return switch (typeName) {
			case "int" -> Type.INT_TYPE;
			case "double" -> Type.DOUBLE_TYPE;
			case "string" -> Type.getType(String.class);
			case "bool" -> Type.BOOLEAN_TYPE;
			case "char" -> Type.CHAR_TYPE;
			default -> Type.getType("L" + typeName + ";");
		};
	}

	private int getArrayTypeCode(Type baseType) {
		return switch (baseType.getSort()) {
			case Type.INT -> Opcodes.T_INT;
			case Type.DOUBLE -> Opcodes.T_DOUBLE;
			case Type.BOOLEAN -> Opcodes.T_BOOLEAN;
			case Type.CHAR -> Opcodes.T_CHAR;
			case Type.BYTE -> Opcodes.T_BYTE;
			case Type.SHORT -> Opcodes.T_SHORT;
			case Type.LONG -> Opcodes.T_LONG;
			case Type.FLOAT -> Opcodes.T_FLOAT;
			default -> throw new RuntimeException("Cannot create array of type: " + baseType);
		};
	}

	private void generateIndex(MethodVisitor mv, Index index) {
		generateExpression(mv, index.array);
		generateExpression(mv, index.index);

		ResolvedType elementType = index.getType();

		if (elementType.equals(ResolvedType.INT)) {
			mv.visitInsn(Opcodes.IALOAD);
		}
		else if (elementType.equals(ResolvedType.DOUBLE)) {
			mv.visitInsn(Opcodes.DALOAD);
		}
		else if (elementType.equals(ResolvedType.BOOL)) {
			mv.visitInsn(Opcodes.BALOAD);
		}
		else if (elementType.equals(ResolvedType.CHAR)) {
			mv.visitInsn(Opcodes.CALOAD);
		}
		else if (elementType.equals(ResolvedType.STRING)) {
			mv.visitInsn(Opcodes.AALOAD);
		}
		else {
			mv.visitInsn(Opcodes.AALOAD);
		}
	}

	private Type mapType(LType ltype) {
		if (ltype == null) {
			return Type.VOID_TYPE;
		}
		return mapType(ltype.name.getLexeme(), ltype.dimension);
	}

	private Type mapType(ResolvedType rtype) {
		if (rtype == null) {
			return Type.VOID_TYPE;
		}
		return mapType(rtype.name(), rtype.dimensions());
	}

	private Type mapType(String typeName, int dimensions) {
		Type baseType;
		switch (typeName) {
			case "int":
				baseType = Type.INT_TYPE;
				break;
			case "double":
				baseType = Type.DOUBLE_TYPE;
				break;
			case "string":
				baseType = Type.getType(String.class);
				break;
			case "bool":
				baseType = Type.BOOLEAN_TYPE;
				break;
			case "char":
				baseType = Type.CHAR_TYPE;
				break;
			case "scrap":
				baseType = Type.VOID_TYPE;
				break;
			default:
				baseType = Type.getType("L" + typeName + ";");
				break;
		}

		if (dimensions > 0) {
			StringBuilder desc = new StringBuilder();
			for (int i = 0; i < dimensions; i++) {
				desc.append('[');
			}
			desc.append(baseType.getDescriptor());
			return Type.getType(desc.toString());
		}
		return baseType;
	}

	private void convertToBoolean(MethodVisitor mv, ResolvedType type) {
		if (type == null || type.equals(ResolvedType.BOOL)) {
			return;
		}
		if (type.equals(ResolvedType.INT)) {
			Label nonZero = new Label();
			Label end = new Label();
			mv.visitInsn(Opcodes.DUP);
			mv.visitJumpInsn(Opcodes.IFNE, nonZero);
			mv.visitInsn(Opcodes.POP);
			mv.visitInsn(Opcodes.ICONST_0);
			mv.visitJumpInsn(Opcodes.GOTO, end);
			mv.visitLabel(nonZero);
			mv.visitInsn(Opcodes.POP);
			mv.visitInsn(Opcodes.ICONST_1);
			mv.visitLabel(end);
		}
	}

	private void pushDefaultValue(MethodVisitor mv, Type type) {
		switch (type.getSort()) {
			case Type.INT:
			case Type.BOOLEAN:
				mv.visitInsn(Opcodes.ICONST_0);
				break;
			case Type.DOUBLE:
				mv.visitInsn(Opcodes.DCONST_0);
				break;
			case Type.OBJECT:
			case Type.ARRAY:
				mv.visitInsn(Opcodes.ACONST_NULL);
				break;
			default:
				mv.visitInsn(Opcodes.ACONST_NULL);
		}
	}

	private void popValue(MethodVisitor mv, ResolvedType type) {
		if (type == null) {
			return;
		}
		if (type.equals(ResolvedType.DOUBLE)) {
			mv.visitInsn(Opcodes.POP2);
		}
		else if (!type.equals(ResolvedType.SCRAP)) {
			mv.visitInsn(Opcodes.POP);
		}
	}

	private boolean hasReturnStatement(Block block) {
		if (block == null || block.statements == null) {
			return false;
		}
		for (Node node : block.statements) {
			if (node instanceof Return) {
				return true;
			}
			else if (node instanceof If) {
				If ifStmt = (If) node;
				if (hasReturnInIf(ifStmt)) {
					return true;
				}
			}
			else if (node instanceof Block) {
				if (hasReturnStatement((Block) node)) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean hasReturnInIf(If ifStmt) {
		boolean ifReturns = hasReturnStatement(ifStmt.ifBranch.block);
		boolean elseReturns = ifStmt.elseBranch != null && ifStmt.elseBranch.block != null &&
				hasReturnStatement(ifStmt.elseBranch.block);
		return ifReturns && elseReturns;
	}

	private void generateJasminOutput() {
		jasminOutput.append(".class public ").append(MAIN_CLASS_NAME).append("\n");
		jasminOutput.append(".super java/lang/Object\n\n");
		jasminOutput.append(".field public static scanner Ljava/util/Scanner;\n\n");

		for (VarDecl globalVar : globalVars.values()) {
			Type type = mapType(globalVar.type);
			jasminOutput.append(".field public static ")
					.append(globalVar.name.getLexeme())
					.append(" ")
					.append(type.getDescriptor())
					.append("\n");
		}

		if (!globalVars.isEmpty()) {
			jasminOutput.append("\n");
		}

		jasminOutput.append(".method static <clinit>()V\n");
		jasminOutput.append("    .limit stack 3\n");
		jasminOutput.append("    .limit locals 0\n");
		jasminOutput.append("    new java/util/Scanner\n");
		jasminOutput.append("    dup\n");
		jasminOutput.append("    getstatic java/lang/System/in Ljava/io/InputStream;\n");
		jasminOutput.append("    invokespecial java/util/Scanner/<init>(Ljava/io/InputStream;)V\n");
		jasminOutput.append("    putstatic ").append(MAIN_CLASS_NAME).append("/scanner Ljava/util/Scanner;\n");
		jasminOutput.append("    return\n");
		jasminOutput.append(".end method\n\n");

		generateJasminProjectMethod();
		generateJasminCaptureMethod();
		generateJasminMainMethod();

		for (SceneDecl scene : scenes.values()) {
			if (!scene.name.getLexeme().equals("entrance")) {
				generateJasminSceneMethod(scene);
			}
		}
	}

	private void generateJasminProjectMethod() {
		jasminOutput.append(".method public static project(Ljava/lang/String;)V\n");
		jasminOutput.append("    .limit stack 2\n");
		jasminOutput.append("    .limit locals 1\n");
		jasminOutput.append("    getstatic java/lang/System/out Ljava/io/PrintStream;\n");
		jasminOutput.append("    aload 0\n");
		jasminOutput.append("    invokevirtual java/io/PrintStream/println(Ljava/lang/String;)V\n");
		jasminOutput.append("    return\n");
		jasminOutput.append(".end method\n\n");
	}

	private void generateJasminCaptureMethod() {
		jasminOutput.append(".method public static capture()Ljava/lang/String;\n");
		jasminOutput.append("    .limit stack 1\n");
		jasminOutput.append("    .limit locals 0\n");
		jasminOutput.append("    getstatic ").append(MAIN_CLASS_NAME).append("/scanner Ljava/util/Scanner;\n");
		jasminOutput.append("    invokevirtual java/util/Scanner/nextLine()Ljava/lang/String;\n");
		jasminOutput.append("    areturn\n");
		jasminOutput.append(".end method\n\n");
	}

	private void generateJasminMainMethod() {
		SceneDecl entrance = scenes.get("entrance");
		if (entrance == null) {
			return;
		}
		jasminOutput.append(".method public static main([Ljava/lang/String;)V\n");
		jasminOutput.append("    .limit stack 10\n");
		jasminOutput.append("    .limit locals 10\n");
		jasminOutput.append("    ; Generated from AbsoluteCinema\n");
		generateJasminBlock(entrance.body, 1);
		jasminOutput.append("    return\n");
		jasminOutput.append(".end method\n\n");
	}

	private void generateJasminSceneMethod(SceneDecl scene) {
		jasminOutput.append(".method public static ")
				.append(scene.name.getLexeme())
				.append("(");
		for (Param param : scene.params) {
			Type paramType = mapType(param.type);
			jasminOutput.append(paramType.getDescriptor());
		}
		jasminOutput.append(")").append(mapType(scene.returnType).getDescriptor()).append("\n");
		jasminOutput.append("    .limit stack 10\n");
		jasminOutput.append("    .limit locals 10\n");
		generateJasminBlock(scene.body, 1);

		if (!hasReturnStatement(scene.body)) {
			if (mapType(scene.returnType).equals(Type.VOID_TYPE)) {
				jasminOutput.append("    return\n");
			}
			else if (mapType(scene.returnType).equals(Type.INT_TYPE)) {
				jasminOutput.append("    iconst_0\n");
				jasminOutput.append("    ireturn\n");
			}
			else if (mapType(scene.returnType).equals(Type.DOUBLE_TYPE)) {
				jasminOutput.append("    dconst_0\n");
				jasminOutput.append("    dreturn\n");
			}
			else if (mapType(scene.returnType).equals(Type.getType(String.class))) {
				jasminOutput.append("    ldc \"\"\n");
				jasminOutput.append("    areturn\n");
			}
		}
		jasminOutput.append(".end method\n\n");
	}

	private void generateJasminBlock(Block block, int indentLevel) {
		if (block == null || block.statements == null) {
			return;
		}
		String indent = "    ".repeat(indentLevel);
		for (Node node : block.statements) {
			if (node instanceof Var varStmt) {
				generateJasminVarDecl(varStmt.decl, indent);
			}
			else if (node instanceof Return returnStmt) {
				generateJasminReturn(returnStmt, indent);
			}
			else if (node instanceof If ifStmt) {
				generateJasminIf(ifStmt, indent);
			}
			else if (node instanceof While whileStmt) {
				generateJasminWhile(whileStmt, indent);
			}
			else if (node instanceof For forStmt) {
				generateJasminFor(forStmt, indent);
			}
			else if (node instanceof ExprStmt exprStmt) {
				generateJasminExprStmt(exprStmt, indent);
			}
		}
	}

	private void generateJasminVarDecl(VarDecl varDecl, String indent) {
		if (varDecl.initializer != null) {
			generateJasminExpression(varDecl.initializer, indent);
			Type varType = mapType(varDecl.type);
			if (varType.equals(Type.INT_TYPE)) {
				jasminOutput.append(indent).append("istore ").append(nextLocalIndex++).append(" ; ").append(varDecl.name.getLexeme()).append("\n");
			}
			else if (varType.equals(Type.DOUBLE_TYPE)) {
				jasminOutput.append(indent).append("dstore ").append(nextLocalIndex).append(" ; ").append(varDecl.name.getLexeme()).append("\n");
				nextLocalIndex += 2;
			}
			else if (varType.equals(Type.getType(String.class))) {
				jasminOutput.append(indent).append("astore ").append(nextLocalIndex++).append(" ; ").append(varDecl.name.getLexeme()).append("\n");
			}
			else {
				jasminOutput.append(indent).append("astore ").append(nextLocalIndex++).append(" ; ").append(varDecl.name.getLexeme()).append("\n");
			}
		}
	}

	private void generateJasminReturn(Return returnStmt, String indent) {
		if (returnStmt.value != null) {
			generateJasminExpression(returnStmt.value, indent);
			ResolvedType returnType = returnStmt.value.getType();
			if (returnType.equals(ResolvedType.INT)) {
				jasminOutput.append(indent).append("ireturn\n");
			}
			else if (returnType.equals(ResolvedType.DOUBLE)) {
				jasminOutput.append(indent).append("dreturn\n");
			}
			else if (returnType.equals(ResolvedType.STRING)) {
				jasminOutput.append(indent).append("areturn\n");
			}
			else if (returnType.equals(ResolvedType.BOOL)) {
				jasminOutput.append(indent).append("ireturn\n");
			}
			else {
				jasminOutput.append(indent).append("areturn\n");
			}
		}
		else {
			jasminOutput.append(indent).append("return\n");
		}
	}

	private void generateJasminIf(If ifStmt, String indent) {
		String label1 = "L" + labelCounter++;
		String label2 = "L" + labelCounter++;

		generateJasminExpression(ifStmt.ifBranch.cond, indent);
		jasminOutput.append(indent).append("ifeq ").append(label1).append("\n");

		if (ifStmt.ifBranch.block != null) {
			generateJasminBlock(ifStmt.ifBranch.block, 2);
		}

		jasminOutput.append(indent).append("goto ").append(label2).append("\n");
		jasminOutput.append(indent).append(label1).append(":\n");

		if (!ifStmt.elifBranchList.isEmpty()) {
			for (Branch elifBranch : ifStmt.elifBranchList) {
				String elifLabel = "L" + labelCounter++;
				generateJasminExpression(elifBranch.cond, indent);
				jasminOutput.append(indent).append("ifeq ").append(elifLabel).append("\n");

				if (elifBranch.block != null) {
					generateJasminBlock(elifBranch.block, 2);
				}

				jasminOutput.append(indent).append("goto ").append(label2).append("\n");
				jasminOutput.append(indent).append(elifLabel).append(":\n");
			}
		}

		if (ifStmt.elseBranch != null && ifStmt.elseBranch.block != null) {
			generateJasminBlock(ifStmt.elseBranch.block, 2);
		}

		jasminOutput.append(indent).append(label2).append(":\n");
	}

	private void generateJasminWhile(While whileStmt, String indent) {
		String startLabel = "L" + labelCounter++;
		String endLabel = "L" + labelCounter++;

		jasminOutput.append(indent).append(startLabel).append(":\n");
		generateJasminExpression(whileStmt.condition, indent);
		jasminOutput.append(indent).append("ifeq ").append(endLabel).append("\n");

		if (whileStmt.body instanceof Block block) {
			generateJasminBlock(block, 2);
		}
		else {
			generateJasminStatement(whileStmt.body, indent);
		}

		jasminOutput.append(indent).append("goto ").append(startLabel).append("\n");
		jasminOutput.append(indent).append(endLabel).append(":\n");
	}

	private void generateJasminFor(For forStmt, String indent) {
		String startLabel = "L" + labelCounter++;
		String endLabel = "L" + labelCounter++;

		if (forStmt.initializer != null) {
			if (forStmt.initializer instanceof Var varStmt) {
				generateJasminVarDecl(varStmt.decl, indent);
			}
			else if (forStmt.initializer instanceof ExprStmt exprStmt) {
				generateJasminExpression(exprStmt.expr, indent);
				jasminOutput.append(indent).append("pop\n");
			}
		}

		jasminOutput.append(indent).append(startLabel).append(":\n");
		if (forStmt.condition != null) {
			generateJasminExpression(forStmt.condition, indent);
			jasminOutput.append(indent).append("ifeq ").append(endLabel).append("\n");
		}

		if (forStmt.body instanceof Block block) {
			generateJasminBlock(block, 2);
		}
		else {
			generateJasminStatement(forStmt.body, indent);
		}

		if (forStmt.increment != null) {
			generateJasminExpression(forStmt.increment, indent);
			jasminOutput.append(indent).append("pop\n");
		}

		jasminOutput.append(indent).append("goto ").append(startLabel).append("\n");
		jasminOutput.append(indent).append(endLabel).append(":\n");
	}

	private void generateJasminExprStmt(ExprStmt exprStmt, String indent) {
		generateJasminExpression(exprStmt.expr, indent);
		if (exprStmt.expr.getType() != null && !exprStmt.expr.getType().name().equals("scrap")) {
			if (exprStmt.expr.getType().equals(ResolvedType.DOUBLE)) {
				jasminOutput.append(indent).append("pop2\n");
			}
			else {
				jasminOutput.append(indent).append("pop\n");
			}
		}
	}

	private void generateJasminExpression(Expr expr, String indent) {
		if (expr instanceof Literal literal) {
			generateJasminLiteral(literal, indent);
		}
		else if (expr instanceof Variable variable) {
			generateJasminVariable(variable, indent);
		}
		else if (expr instanceof Call call) {
			generateJasminCall(call, indent);
		}
		else if (expr instanceof Binary binary) {
			generateJasminBinary(binary, indent);
		}
		else if (expr instanceof Logical logical) {
			generateJasminLogical(logical, indent);
		}
		else if (expr instanceof Unary unary) {
			generateJasminUnary(unary, indent);
		}
		else if (expr instanceof Index index) {
			generateJasminIndex(index, indent);
		}
		else if (expr instanceof Assign assign) {
			generateJasminAssign(assign, indent);
		}
	}

	private void generateJasminLiteral(Literal literal, String indent) {
		Object value = literal.value;
		ResolvedType type = literal.getType();
		if (value == null) {
			jasminOutput.append(indent).append("aconst_null\n");
		}
		else if (type.equals(ResolvedType.INT)) {
			int intVal = ((Number) value).intValue();
			if (intVal >= -1 && intVal <= 5) {
				switch (intVal) {
					case -1:
						jasminOutput.append(indent).append("iconst_m1\n");
						break;
					case 0:
						jasminOutput.append(indent).append("iconst_0\n");
						break;
					case 1:
						jasminOutput.append(indent).append("iconst_1\n");
						break;
					case 2:
						jasminOutput.append(indent).append("iconst_2\n");
						break;
					case 3:
						jasminOutput.append(indent).append("iconst_3\n");
						break;
					case 4:
						jasminOutput.append(indent).append("iconst_4\n");
						break;
					case 5:
						jasminOutput.append(indent).append("iconst_5\n");
						break;
				}
			}
			else if (intVal >= Byte.MIN_VALUE && intVal <= Byte.MAX_VALUE) {
				jasminOutput.append(indent).append("bipush ").append(intVal).append("\n");
			}
			else if (intVal >= Short.MIN_VALUE && intVal <= Short.MAX_VALUE) {
				jasminOutput.append(indent).append("sipush ").append(intVal).append("\n");
			}
			else {
				jasminOutput.append(indent).append("ldc ").append(intVal).append("\n");
			}
		}
		else if (type.equals(ResolvedType.DOUBLE)) {
			double doubleVal = ((Number) value).doubleValue();
			if (doubleVal == 0.0) {
				jasminOutput.append(indent).append("dconst_0\n");
			}
			else if (doubleVal == 1.0) {
				jasminOutput.append(indent).append("dconst_1\n");
			}
			else {
				jasminOutput.append(indent).append("ldc2_w ").append(doubleVal).append("\n");
			}
		}
		else if (type.equals(ResolvedType.STRING)) {
			jasminOutput.append(indent).append("ldc \"").append(value.toString().replace("\"", "\\\"")).append("\"\n");
		}
		else if (type.equals(ResolvedType.BOOL)) {
			boolean boolVal = (Boolean) value;
			jasminOutput.append(indent).append(boolVal ? "iconst_1\n" : "iconst_0\n");
		}
		else if (type.equals(ResolvedType.CHAR)) {
			char charVal = (Character) value;
			jasminOutput.append(indent).append("bipush ").append((int) charVal).append("\n");
		}
	}

	private void generateJasminVariable(Variable variable, String indent) {
		String varName = variable.name.getLexeme();
		LocalVarInfo localVar = localVars.get(varName);
		if (localVar != null) {
			if (localVar.type.equals(Type.INT_TYPE) || localVar.type.equals(Type.BOOLEAN_TYPE)) {
				jasminOutput.append(indent).append("iload ").append(localVar.index).append(" ; ").append(varName).append("\n");
			}
			else if (localVar.type.equals(Type.DOUBLE_TYPE)) {
				jasminOutput.append(indent).append("dload ").append(localVar.index).append(" ; ").append(varName).append("\n");
			}
			else {
				jasminOutput.append(indent).append("aload ").append(localVar.index).append(" ; ").append(varName).append("\n");
			}
		}
		else {
			VarDecl globalVar = globalVars.get(varName);
			if (globalVar != null) {
				Type fieldType = mapType(globalVar.type);
				jasminOutput.append(indent).append("getstatic ").append(MAIN_CLASS_NAME).append("/").append(varName).append(" ").append(fieldType.getDescriptor()).append("\n");
			}
		}
	}

	private void generateJasminCall(Call call, String indent) {
		if (call.callee instanceof Variable var) {
			String funcName = var.name.getLexeme();
			if (funcName.equals("project")) {
				generateJasminExpression(call.arguments.get(0), indent);
				jasminOutput.append(indent).append("invokestatic ").append(MAIN_CLASS_NAME).append("/project(Ljava/lang/String;)V\n");
				return;
			}
			if (funcName.equals("capture")) {
				jasminOutput.append(indent).append("invokestatic ").append(MAIN_CLASS_NAME).append("/capture()Ljava/lang/String;\n");
				return;
			}

			for (Expr arg : call.arguments) {
				generateJasminExpression(arg, indent);
			}

			StringBuilder descriptor = new StringBuilder("(");
			for (Expr arg : call.arguments) {
				Type argType = mapType(arg.getType());
				descriptor.append(argType.getDescriptor());
			}
			descriptor.append(")").append(mapType(call.getType()).getDescriptor());

			jasminOutput.append(indent).append("invokestatic ").append(MAIN_CLASS_NAME).append("/")
					.append(funcName).append(descriptor.toString()).append("\n");
		}
	}

	private void generateJasminBinary(Binary binary, String indent) {
		generateJasminExpression(binary.left, indent);
		generateJasminExpression(binary.right, indent);
		String op = binary.op.getLexeme();

		if (binary.left.getType().equals(ResolvedType.INT) && binary.right.getType().equals(ResolvedType.INT)) {
			switch (op) {
				case "+":
					jasminOutput.append(indent).append("iadd\n");
					break;
				case "-":
					jasminOutput.append(indent).append("isub\n");
					break;
				case "*":
					jasminOutput.append(indent).append("imul\n");
					break;
				case "/":
					jasminOutput.append(indent).append("idiv\n");
					break;
				case "%":
					jasminOutput.append(indent).append("irem\n");
					break;
				case "<=":
					String label1 = "L" + labelCounter++;
					String label2 = "L" + labelCounter++;
					jasminOutput.append(indent).append("isub\n");
					jasminOutput.append(indent).append("ifle ").append(label1).append("\n");
					jasminOutput.append(indent).append("iconst_0\n");
					jasminOutput.append(indent).append("goto ").append(label2).append("\n");
					jasminOutput.append(indent).append(label1).append(":\n");
					jasminOutput.append(indent).append("iconst_1\n");
					jasminOutput.append(indent).append(label2).append(":\n");
					break;
			}
		}
	}

	private void generateJasminLogical(Logical logical, String indent) {
		String op = logical.op.getLexeme();

		if (op.equals("&&")) {
			generateJasminExpression(logical.left, indent);
			String label1 = "L" + labelCounter++;
			String label2 = "L" + labelCounter++;
			jasminOutput.append(indent).append("ifeq ").append(label1).append("\n");
			generateJasminExpression(logical.right, indent);
			jasminOutput.append(indent).append("ifeq ").append(label1).append("\n");
			jasminOutput.append(indent).append("iconst_1\n");
			jasminOutput.append(indent).append("goto ").append(label2).append("\n");
			jasminOutput.append(indent).append(label1).append(":\n");
			jasminOutput.append(indent).append("iconst_0\n");
			jasminOutput.append(indent).append(label2).append(":\n");
		}
	}

	private void generateJasminUnary(Unary unary, String indent) {
		generateJasminExpression(unary.right, indent);
		String op = unary.op.getLexeme();
		if (op.equals("-") && unary.right.getType().equals(ResolvedType.INT)) {
			jasminOutput.append(indent).append("ineg\n");
		}
	}

	private void generateJasminIndex(Index index, String indent) {
		generateJasminExpression(index.array, indent);
		generateJasminExpression(index.index, indent);

		ResolvedType elementType = index.getType();
		if (elementType.equals(ResolvedType.INT)) {
			jasminOutput.append(indent).append("iaload\n");
		}
		else if (elementType.equals(ResolvedType.DOUBLE)) {
			jasminOutput.append(indent).append("daload\n");
		}
		else {
			jasminOutput.append(indent).append("aaload\n");
		}
	}

	private void generateJasminAssign(Assign assign, String indent) {
		generateJasminExpression(assign.value, indent);
		if (assign.target instanceof Variable) {
			Variable target = (Variable) assign.target;
			String varName = target.name.getLexeme();
			LocalVarInfo localVar = localVars.get(varName);

			if (localVar != null) {
				if (localVar.type.equals(Type.INT_TYPE) || localVar.type.equals(Type.BOOLEAN_TYPE)) {
					jasminOutput.append(indent).append("istore ").append(localVar.index).append(" ; ").append(varName).append("\n");
				}
				else if (localVar.type.equals(Type.DOUBLE_TYPE)) {
					jasminOutput.append(indent).append("dstore ").append(localVar.index).append(" ; ").append(varName).append("\n");
				}
				else {
					jasminOutput.append(indent).append("astore ").append(localVar.index).append(" ; ").append(varName).append("\n");
				}
			}
			else {
				VarDecl globalVar = globalVars.get(varName);
				if (globalVar != null) {
					Type fieldType = mapType(globalVar.type);
					jasminOutput.append(indent).append("putstatic ").append(MAIN_CLASS_NAME).append("/").append(varName).append(" ").append(fieldType.getDescriptor()).append("\n");
				}
			}
		}
	}

	private void generateJasminStatement(Stmt stmt, String indent) {
		if (stmt instanceof Block block) {
			generateJasminBlock(block, 2);
		}
		else if (stmt instanceof ExprStmt exprStmt) {
			generateJasminExprStmt(exprStmt, indent);
		}
		else if (stmt instanceof If ifStmt) {
			generateJasminIf(ifStmt, indent);
		}
		else if (stmt instanceof While whileStmt) {
			generateJasminWhile(whileStmt, indent);
		}
		else if (stmt instanceof For forStmt) {
			generateJasminFor(forStmt, indent);
		}
		else if (stmt instanceof Return returnStmt) {
			generateJasminReturn(returnStmt, indent);
		}
		else if (stmt instanceof Var varStmt) {
			generateJasminVarDecl(varStmt.decl, indent);
		}
	}
}