package com.lazar.absolutecinema.generator;

import com.lazar.absolutecinema.lexer.TokenType;
import com.lazar.absolutecinema.parser.ast.*;
import com.lazar.absolutecinema.parser.ast.Set;
import com.lazar.absolutecinema.semantic.ResolvedType;
import org.objectweb.asm.*;
import org.objectweb.asm.util.TraceClassVisitor;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;

import static org.objectweb.asm.Opcodes.*;

public class JVMGenerator implements IGenerator {
	private final GeneratorMode generatorMode;

	public JVMGenerator(GeneratorMode generatorMode) {
		this.generatorMode = generatorMode;
	}

	@Override
	public GenerationResult generate(Program program) {
		if (generatorMode.equals(GeneratorMode.LIBRARY)) {
			return generateWithLibrary(program);
		} else {
			return generateManually(program);
		}
	}

	private GenerationResult generateWithLibrary(Program program) {
		JVMCodeGenerator codeGen = new JVMCodeGenerator(program);
		return codeGen.generate();
	}

	private GenerationResult generateManually(Program program) {
		return new GenerationResult("; Manual mode not implemented.", new byte[0]);
	}

	private static class JVMCodeGenerator implements DeclVisitor<Void>, StmtVisitor<Void>, ExprVisitor<Void> {
		private final Program program;
		private final Map<String, byte[]> generatedClasses = new LinkedHashMap<>();
		private final Map<String, SetupDecl> setupMap = new HashMap<>();

		private ClassWriter cw;
		private MethodVisitor mv;
		private String currentClassName;
		private SetupDecl currentSetup;
		private SceneDecl currentScene;

		private final Map<String, Integer> localVarIndexMap = new HashMap<>();
		private int nextLocalVarIndex = 0;

		private Label breakLabel;
		private Label continueLabel;

		public JVMCodeGenerator(Program program) {
			this.program = program;

			for (Node item : program.items) {
				if (item instanceof SetupDecl setup) {
					setupMap.put(setup.name.getLexeme(), setup);
				}
			}
		}

		public GenerationResult generate() {
			List<SceneDecl> topLevelScenes = new ArrayList<>();
			List<VarDecl> globalVars = new ArrayList<>();

			for (Node item : program.items) {
				if (item instanceof SetupDecl setup) {
					generateSetup(setup);
				} else if (item instanceof SceneDecl scene) {
					topLevelScenes.add(scene);
				} else if (item instanceof VarDecl var) {
					globalVars.add(var);
				}
			}

			generateMainClass(topLevelScenes, globalVars);

			StringBuilder plainText = new StringBuilder();
			byte[] mainClassBytes = null;

			for (Map.Entry<String, byte[]> entry : generatedClasses.entrySet()) {
				plainText.append("=== Class: ").append(entry.getKey()).append(" ===\n");
				plainText.append(disassemble(entry.getValue()));
				plainText.append("\n\n");

				if (entry.getKey().equals("Main")) {
					mainClassBytes = entry.getValue();
				}
			}

			return new GenerationResult(plainText.toString(), mainClassBytes != null ? mainClassBytes : new byte[0]);
		}

		private void generateSetup(SetupDecl setup) {
			String className = setup.name.getLexeme();
			currentClassName = className;
			currentSetup = setup;

			cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
			cw.visit(V21, ACC_PUBLIC, className, null, "java/lang/Object", null);

			for (VarDecl field : setup.fields) {
				String fieldDesc = typeDescriptor(field.type);
				FieldVisitor fv = cw.visitField(ACC_PUBLIC, field.name.getLexeme(), fieldDesc, null, null);
				fv.visitEnd();
			}

			if (setup.ctor != null) {
				generateConstructor(setup.ctor);
			} else {
				generateDefaultConstructor();
			}

			for (SceneDecl method : setup.methods) {
				generateMethod(method);
			}

			cw.visitEnd();
			generatedClasses.put(className, cw.toByteArray());

			currentSetup = null;
			currentClassName = null;
		}

		private void generateConstructor(ConstructorDecl ctor) {
			StringBuilder methodDesc = new StringBuilder("(");
			for (Param param : ctor.params) {
				methodDesc.append(typeDescriptor(param.type));
			}
			methodDesc.append(")V");

			mv = cw.visitMethod(ACC_PUBLIC, "<init>", methodDesc.toString(), null, null);
			mv.visitCode();

			localVarIndexMap.clear();
			nextLocalVarIndex = 0;
			localVarIndexMap.put("this", nextLocalVarIndex++);

			for (Param param : ctor.params) {
				localVarIndexMap.put(param.name.getLexeme(), nextLocalVarIndex);
				nextLocalVarIndex += getTypeSize(param.type);
			}

			mv.visitVarInsn(ALOAD, 0);
			mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);

			ctor.body.accept(this);

			mv.visitInsn(RETURN);
			mv.visitMaxs(0, 0);
			mv.visitEnd();
		}

		private void generateDefaultConstructor() {
			mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
			mv.visitCode();

			mv.visitVarInsn(ALOAD, 0);
			mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);

			mv.visitInsn(RETURN);
			mv.visitMaxs(0, 0);
			mv.visitEnd();
		}

		private void generateMethod(SceneDecl method) {
			currentScene = method;

			StringBuilder methodDesc = new StringBuilder("(");
			for (Param param : method.params) {
				methodDesc.append(typeDescriptor(param.type));
			}
			methodDesc.append(")");
			methodDesc.append(returnTypeDescriptor(method.returnType));

			mv = cw.visitMethod(ACC_PUBLIC, method.name.getLexeme(), methodDesc.toString(), null, null);
			mv.visitCode();

			localVarIndexMap.clear();
			nextLocalVarIndex = 0;
			localVarIndexMap.put("this", nextLocalVarIndex++);

			for (Param param : method.params) {
				localVarIndexMap.put(param.name.getLexeme(), nextLocalVarIndex);
				nextLocalVarIndex += getTypeSize(param.type);
			}

			method.body.accept(this);

			if (method.returnType.name.getLexeme().equals("scrap")) {
				mv.visitInsn(RETURN);
			}

			mv.visitMaxs(0, 0);
			mv.visitEnd();

			currentScene = null;
		}

		private void generateMainClass(List<SceneDecl> scenes, List<VarDecl> globalVars) {
			currentClassName = "Main";

			cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
			cw.visit(V21, ACC_PUBLIC, "Main", null, "java/lang/Object", null);

			for (VarDecl var : globalVars) {
				String fieldDesc = typeDescriptor(var.type);
				FieldVisitor fv = cw.visitField(ACC_PUBLIC | ACC_STATIC, var.name.getLexeme(), fieldDesc, null, null);
				fv.visitEnd();
			}

			generateStaticInitializer(globalVars);

			for (SceneDecl scene : scenes) {
				generateStaticMethod(scene);
			}

			SceneDecl entrance = scenes.stream()
				.filter(s -> s.name.getLexeme().equals("entrance"))
				.findFirst()
				.orElse(null);

			if (entrance != null) {
				generateMainMethod(entrance);
			}

			cw.visitEnd();
			generatedClasses.put("Main", cw.toByteArray());

			currentClassName = null;
		}

		private void generateStaticInitializer(List<VarDecl> globalVars) {
			boolean hasInitializers = globalVars.stream().anyMatch(v -> v.initializer != null);
			if (!hasInitializers) return;

			mv = cw.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
			mv.visitCode();

			localVarIndexMap.clear();
			nextLocalVarIndex = 0;

			for (VarDecl var : globalVars) {
				if (var.initializer != null) {
					var.initializer.accept(this);
					String fieldDesc = typeDescriptor(var.type);
					mv.visitFieldInsn(PUTSTATIC, "Main", var.name.getLexeme(), fieldDesc);
				}
			}

			mv.visitInsn(RETURN);
			mv.visitMaxs(0, 0);
			mv.visitEnd();
		}

		private void generateStaticMethod(SceneDecl scene) {
			currentScene = scene;

			StringBuilder methodDesc = new StringBuilder("(");
			for (Param param : scene.params) {
				methodDesc.append(typeDescriptor(param.type));
			}
			methodDesc.append(")");
			methodDesc.append(returnTypeDescriptor(scene.returnType));

			mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, scene.name.getLexeme(), methodDesc.toString(), null, null);
			mv.visitCode();

			localVarIndexMap.clear();
			nextLocalVarIndex = 0;

			for (Param param : scene.params) {
				localVarIndexMap.put(param.name.getLexeme(), nextLocalVarIndex);
				nextLocalVarIndex += getTypeSize(param.type);
			}

			scene.body.accept(this);

			if (scene.returnType.name.getLexeme().equals("scrap")) {
				mv.visitInsn(RETURN);
			}

			mv.visitMaxs(0, 0);
			mv.visitEnd();

			currentScene = null;
		}

		private void generateMainMethod(SceneDecl entrance) {
			mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
			mv.visitCode();

			localVarIndexMap.clear();
			nextLocalVarIndex = 0;
			localVarIndexMap.put("args", nextLocalVarIndex++);

			mv.visitVarInsn(ALOAD, 0);
			mv.visitMethodInsn(INVOKESTATIC, "Main", "entrance", "([Ljava/lang/String;)V", false);

			mv.visitInsn(RETURN);
			mv.visitMaxs(0, 0);
			mv.visitEnd();
		}

		@Override
		public Void visitSetup(SetupDecl d) {
			return null;
		}

		@Override
		public Void visitScene(SceneDecl d) {
			return null;
		}

		@Override
		public Void visitVar(VarDecl d) {
			if (!localVarIndexMap.containsKey(d.name.getLexeme())) {
				localVarIndexMap.put(d.name.getLexeme(), nextLocalVarIndex);
				nextLocalVarIndex += getTypeSize(d.type);
			}

			if (d.initializer != null) {
				d.initializer.accept(this);
				storeLocal(d.name.getLexeme(), d.type);
			}

			return null;
		}

		@Override
		public Void visitBlock(Block s) {
			for (Node node : s.statements) {
				if (node instanceof Decl decl) {
					decl.accept(this);
				} else if (node instanceof Stmt stmt) {
					stmt.accept(this);
				}
			}
			return null;
		}

		@Override
		public Void visitVar(Var s) {
			return s.decl.accept(this);
		}

		@Override
		public Void visitExpr(ExprStmt s) {
			s.expr.accept(this);

			ResolvedType type = s.expr.getType();
			if (type != null && !type.equals(ResolvedType.SCRAP)) {
				if (type.equals(ResolvedType.DOUBLE)) {
					mv.visitInsn(POP2);
				} else {
					mv.visitInsn(POP);
				}
			}

			return null;
		}

		@Override
		public Void visitIf(If s) {
			Label endLabel = new Label();

			s.ifBranch.cond.accept(this);
			Label nextLabel = new Label();
			mv.visitJumpInsn(IFEQ, nextLabel);
			s.ifBranch.block.accept(this);
			mv.visitJumpInsn(GOTO, endLabel);
			mv.visitLabel(nextLabel);

			for (Branch elif : s.elifBranchList) {
				elif.cond.accept(this);
				nextLabel = new Label();
				mv.visitJumpInsn(IFEQ, nextLabel);
				elif.block.accept(this);
				mv.visitJumpInsn(GOTO, endLabel);
				mv.visitLabel(nextLabel);
			}

			if (s.elseBranch != null) {
				s.elseBranch.block.accept(this);
			}

			mv.visitLabel(endLabel);
			return null;
		}

		@Override
		public Void visitWhile(While s) {
			Label startLabel = new Label();
			Label endLabel = new Label();
			Label savedBreak = breakLabel;
			Label savedContinue = continueLabel;

			breakLabel = endLabel;
			continueLabel = startLabel;

			mv.visitLabel(startLabel);
			s.condition.accept(this);
			mv.visitJumpInsn(IFEQ, endLabel);
			s.body.accept(this);
			mv.visitJumpInsn(GOTO, startLabel);
			mv.visitLabel(endLabel);

			breakLabel = savedBreak;
			continueLabel = savedContinue;
			return null;
		}

		@Override
		public Void visitFor(For s) {
			Label startLabel = new Label();
			Label contLabel = new Label();
			Label endLabel = new Label();
			Label savedBreak = breakLabel;
			Label savedContinue = continueLabel;

			breakLabel = endLabel;
			this.continueLabel = contLabel;

			if (s.initializer instanceof Decl decl) {
				decl.accept(this);
			} else if (s.initializer instanceof Stmt stmt) {
				stmt.accept(this);
			}

			mv.visitLabel(startLabel);

			if (s.condition != null) {
				s.condition.accept(this);
				mv.visitJumpInsn(IFEQ, endLabel);
			}

			s.body.accept(this);

			mv.visitLabel(contLabel);
			if (s.increment != null) {
				s.increment.accept(this);
				ResolvedType type = s.increment.getType();
				if (type != null && !type.equals(ResolvedType.SCRAP)) {
					if (type.equals(ResolvedType.DOUBLE)) {
						mv.visitInsn(POP2);
					} else {
						mv.visitInsn(POP);
					}
				}
			}

			mv.visitJumpInsn(GOTO, startLabel);
			mv.visitLabel(endLabel);

			breakLabel = savedBreak;
			this.continueLabel = savedContinue;
			return null;
		}

		@Override
		public Void visitReturn(Return s) {
			if (s.value != null) {
				s.value.accept(this);
				ResolvedType type = s.value.getType();

				if (type.equals(ResolvedType.INT) || type.equals(ResolvedType.BOOL) || type.equals(ResolvedType.CHAR)) {
					mv.visitInsn(IRETURN);
				} else if (type.equals(ResolvedType.DOUBLE)) {
					mv.visitInsn(DRETURN);
				} else {
					mv.visitInsn(ARETURN);
				}
			} else {
				mv.visitInsn(RETURN);
			}
			return null;
		}

		@Override
		public Void visitBreak(Break s) {
			if (breakLabel != null) {
				mv.visitJumpInsn(GOTO, breakLabel);
			}
			return null;
		}

		@Override
		public Void visitContinue(Continue s) {
			if (continueLabel != null) {
				mv.visitJumpInsn(GOTO, continueLabel);
			}
			return null;
		}

		@Override
		public Void visitLiteral(Literal e) {
			if (e.value == null) {
				mv.visitInsn(ACONST_NULL);
			} else if (e.value instanceof Integer i) {
				pushInt(i);
			} else if (e.value instanceof Double d) {
				mv.visitLdcInsn(d);
			} else if (e.value instanceof String s) {
				mv.visitLdcInsn(s);
			} else if (e.value instanceof Character c) {
				pushInt((int) c);
			} else if (e.value instanceof Boolean b) {
				pushInt(b ? 1 : 0);
			}
			return null;
		}

		@Override
		public Void visitVariable(Variable e) {
			String name = e.name.getLexeme();
			ResolvedType type = e.getType();

			if (localVarIndexMap.containsKey(name)) {
				loadLocal(name, type);
			} else {
				String owner = currentSetup != null ? currentClassName : "Main";
				String fieldDesc = typeDescriptorFromResolved(type);

				if (currentSetup != null) {
					mv.visitVarInsn(ALOAD, 0);
					mv.visitFieldInsn(GETFIELD, owner, name, fieldDesc);
				} else {
					mv.visitFieldInsn(GETSTATIC, owner, name, fieldDesc);
				}
			}
			return null;
		}

		@Override
		public Void visitAssign(Assign e) {
			if (e.target instanceof Variable var) {
				String name = var.name.getLexeme();
				ResolvedType type = var.getType();

				e.value.accept(this);

				if (localVarIndexMap.containsKey(name)) {
					if (type.equals(ResolvedType.DOUBLE)) {
						mv.visitInsn(DUP2);
					} else {
						mv.visitInsn(DUP);
					}
					storeLocal(name, type);
				} else {
					String owner = currentSetup != null ? currentClassName : "Main";
					String fieldDesc = typeDescriptorFromResolved(type);

					if (type.equals(ResolvedType.DOUBLE)) {
						mv.visitInsn(DUP2);
					} else {
						mv.visitInsn(DUP);
					}

					if (currentSetup != null) {
						mv.visitVarInsn(ALOAD, 0);
						if (type.equals(ResolvedType.DOUBLE)) {
							mv.visitInsn(DUP_X2);
							mv.visitInsn(POP);
						} else {
							mv.visitInsn(SWAP);
						}
						mv.visitFieldInsn(PUTFIELD, owner, name, fieldDesc);
					} else {
						mv.visitFieldInsn(PUTSTATIC, owner, name, fieldDesc);
					}
				}
			} else if (e.target instanceof Index idx) {
				idx.array.accept(this);
				idx.index.accept(this);
				e.value.accept(this);

				ResolvedType elemType = idx.getType();

				if (elemType.equals(ResolvedType.DOUBLE)) {
					mv.visitInsn(DUP2_X2);
				} else if (elemType.dimensions() > 0 || !isPrimitive(elemType.name())) {
					mv.visitInsn(DUP_X2);
				} else {
					mv.visitInsn(DUP_X2);
				}

				storeArray(elemType);
			} else if (e.target instanceof Get get) {
				get.object.accept(this);
				e.value.accept(this);

				ResolvedType objType = get.object.getType();
				String className = objType.name();
				String fieldName = get.name.getLexeme();
				String fieldDesc = typeDescriptorFromResolved(get.getType());

				if (get.getType().equals(ResolvedType.DOUBLE)) {
					mv.visitInsn(DUP2_X1);
				} else {
					mv.visitInsn(DUP_X1);
				}

				mv.visitFieldInsn(PUTFIELD, className, fieldName, fieldDesc);
			}

			return null;
		}

		@Override
		public Void visitBinary(Binary e) {
			TokenType op = e.op.getType();
			ResolvedType resultType = e.getType();

			e.left.accept(this);

			if (resultType.equals(ResolvedType.DOUBLE)) {
				ResolvedType leftType = e.left.getType();
				if (!leftType.equals(ResolvedType.DOUBLE)) {
					mv.visitInsn(I2D);
				}
			}

			e.right.accept(this);

			if (resultType.equals(ResolvedType.DOUBLE)) {
				ResolvedType rightType = e.right.getType();
				if (!rightType.equals(ResolvedType.DOUBLE)) {
					mv.visitInsn(I2D);
				}
			}

			switch (op) {
				case PLUS:
					mv.visitInsn(resultType.equals(ResolvedType.DOUBLE) ? DADD : IADD);
					break;
				case MINUS:
					mv.visitInsn(resultType.equals(ResolvedType.DOUBLE) ? DSUB : ISUB);
					break;
				case STAR:
					mv.visitInsn(resultType.equals(ResolvedType.DOUBLE) ? DMUL : IMUL);
					break;
				case SLASH:
					mv.visitInsn(resultType.equals(ResolvedType.DOUBLE) ? DDIV : IDIV);
					break;
				case PERCENT:
					mv.visitInsn(resultType.equals(ResolvedType.DOUBLE) ? DREM : IREM);
					break;
				case EQUAL_EQUAL:
					generateComparison(e.left.getType(), IFEQ, IF_ICMPEQ, IF_ACMPEQ);
					break;
				case BANG_EQUAL:
					generateComparison(e.left.getType(), IFNE, IF_ICMPNE, IF_ACMPNE);
					break;
				case LESS:
					generateComparison(e.left.getType(), IFLT, IF_ICMPLT, -1);
					break;
				case LESS_EQUAL:
					generateComparison(e.left.getType(), IFLE, IF_ICMPLE, -1);
					break;
				case GREATER:
					generateComparison(e.left.getType(), IFGT, IF_ICMPGT, -1);
					break;
				case GREATER_EQUAL:
					generateComparison(e.left.getType(), IFGE, IF_ICMPGE, -1);
					break;
			}
			return null;
		}

		private void generateComparison(ResolvedType type, int doubleOp, int intOp, int refOp) {
			Label trueLabel = new Label();
			Label endLabel = new Label();

			if (type.equals(ResolvedType.DOUBLE)) {
				mv.visitInsn(DCMPG);
				mv.visitJumpInsn(doubleOp, trueLabel);
			} else if (type.dimensions() > 0 || !isPrimitive(type.name())) {
				mv.visitJumpInsn(refOp, trueLabel);
			} else {
				mv.visitJumpInsn(intOp, trueLabel);
			}

			mv.visitInsn(ICONST_0);
			mv.visitJumpInsn(GOTO, endLabel);
			mv.visitLabel(trueLabel);
			mv.visitInsn(ICONST_1);
			mv.visitLabel(endLabel);
		}

		@Override
		public Void visitLogical(Logical e) {
			Label shortCircuit = new Label();
			Label end = new Label();

			e.left.accept(this);

			if (e.op.getType() == TokenType.AND_AND) {
				mv.visitInsn(DUP);
				mv.visitJumpInsn(IFEQ, shortCircuit);
				mv.visitInsn(POP);
				e.right.accept(this);
				mv.visitJumpInsn(GOTO, end);
				mv.visitLabel(shortCircuit);
			} else if (e.op.getType() == TokenType.OR_OR) {
				mv.visitInsn(DUP);
				mv.visitJumpInsn(IFNE, shortCircuit);
				mv.visitInsn(POP);
				e.right.accept(this);
				mv.visitJumpInsn(GOTO, end);
				mv.visitLabel(shortCircuit);
			}

			mv.visitLabel(end);
			return null;
		}

		@Override
		public Void visitUnary(Unary e) {
			TokenType op = e.op.getType();

			switch (op) {
				case MINUS:
					e.right.accept(this);
					mv.visitInsn(e.getType().equals(ResolvedType.DOUBLE) ? DNEG : INEG);
					break;
				case PLUS:
					e.right.accept(this);
					break;
				case BANG:
					e.right.accept(this);
					Label falseLabel = new Label();
					Label endLabel = new Label();
					mv.visitJumpInsn(IFEQ, falseLabel);
					mv.visitInsn(ICONST_0);
					mv.visitJumpInsn(GOTO, endLabel);
					mv.visitLabel(falseLabel);
					mv.visitInsn(ICONST_1);
					mv.visitLabel(endLabel);
					break;
				case PLUS_PLUS:
					if (e.right instanceof Variable var) {
						String name = var.name.getLexeme();
						loadLocal(name, var.getType());
						mv.visitInsn(ICONST_1);
						mv.visitInsn(IADD);
						mv.visitInsn(DUP);
						storeLocal(name, var.getType());
					}
					break;
				case MINUS_MINUS:
					if (e.right instanceof Variable var) {
						String name = var.name.getLexeme();
						loadLocal(name, var.getType());
						mv.visitInsn(ICONST_1);
						mv.visitInsn(ISUB);
						mv.visitInsn(DUP);
						storeLocal(name, var.getType());
					}
					break;
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
			if (e.callee instanceof Variable var) {
				String funcName = var.name.getLexeme();

				if (funcName.equals("project")) {
					for (Expr arg : e.arguments) {
						mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
						arg.accept(this);

						ResolvedType argType = arg.getType();
						String printDesc = getPrintDescriptor(argType);
						mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", printDesc, false);
					}
				} else if (funcName.equals("capture")) {
					mv.visitTypeInsn(NEW, "java/util/Scanner");
					mv.visitInsn(DUP);
					mv.visitFieldInsn(GETSTATIC, "java/lang/System", "in", "Ljava/io/InputStream;");
					mv.visitMethodInsn(INVOKESPECIAL, "java/util/Scanner", "<init>", "(Ljava/io/InputStream;)V", false);
					mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/Scanner", "nextLine", "()Ljava/lang/String;", false);
				} else {
					for (Expr arg : e.arguments) {
						arg.accept(this);
					}

					StringBuilder methodDesc = new StringBuilder("(");
					SceneDecl scene = findScene(funcName);
					if (scene != null) {
						for (Param param : scene.params) {
							methodDesc.append(typeDescriptor(param.type));
						}
						methodDesc.append(")");
						methodDesc.append(returnTypeDescriptor(scene.returnType));

						mv.visitMethodInsn(INVOKESTATIC, "Main", funcName, methodDesc.toString(), false);
					}
				}
			} else if (e.callee instanceof Get get) {
				get.object.accept(this);

				for (Expr arg : e.arguments) {
					arg.accept(this);
				}

				ResolvedType objType = get.object.getType();
				String className = objType.name();
				String methodName = get.name.getLexeme();

				SetupDecl setup = setupMap.get(className);
				if (setup != null) {
					for (SceneDecl method : setup.methods) {
						if (method.name.getLexeme().equals(methodName)) {
							StringBuilder methodDesc = new StringBuilder("(");
							for (Param param : method.params) {
								methodDesc.append(typeDescriptor(param.type));
							}
							methodDesc.append(")");
							methodDesc.append(returnTypeDescriptor(method.returnType));

							mv.visitMethodInsn(INVOKEVIRTUAL, className, methodName, methodDesc.toString(), false);
							break;
						}
					}
				}
			}
			return null;
		}

		@Override
		public Void visitGet(Get e) {
			e.object.accept(this);

			ResolvedType objType = e.object.getType();
			String className = objType.name();
			String fieldName = e.name.getLexeme();
			String fieldDesc = typeDescriptorFromResolved(e.getType());

			mv.visitFieldInsn(GETFIELD, className, fieldName, fieldDesc);
			return null;
		}

		@Override
		public Void visitSet(Set e) {
			e.object.accept(this);
			e.value.accept(this);

			ResolvedType objType = e.object.getType();
			String className = objType.name();
			String fieldName = e.name.getLexeme();
			String fieldDesc = typeDescriptorFromResolved(e.getType());

			if (e.getType().equals(ResolvedType.DOUBLE)) {
				mv.visitInsn(DUP2_X1);
			} else {
				mv.visitInsn(DUP_X1);
			}

			mv.visitFieldInsn(PUTFIELD, className, fieldName, fieldDesc);
			return null;
		}

		@Override
		public Void visitIndex(Index e) {
			e.array.accept(this);
			e.index.accept(this);

			loadArray(e.getType());
			return null;
		}

		@Override
		public Void visitPostfix(Postfix e) {
			if (e.target instanceof Variable var) {
				String name = var.name.getLexeme();
				loadLocal(name, var.getType());

				if (e.op.getType() == TokenType.PLUS_PLUS) {
					loadLocal(name, var.getType());
					mv.visitInsn(ICONST_1);
					mv.visitInsn(IADD);
					storeLocal(name, var.getType());
				} else if (e.op.getType() == TokenType.MINUS_MINUS) {
					loadLocal(name, var.getType());
					mv.visitInsn(ICONST_1);
					mv.visitInsn(ISUB);
					storeLocal(name, var.getType());
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
			String typeName = e.type.name.getLexeme();
			int dimensions = e.type.dimension;

			if (dimensions > 0) {
				if (e.arrayInitializer != null && !e.arrayInitializer.isEmpty()) {
					pushInt(e.arrayInitializer.size());
					String arrayDesc = getArrayTypeDescriptor(typeName, dimensions);
					mv.visitTypeInsn(ANEWARRAY, arrayDesc.substring(2, arrayDesc.length() - 1));

					for (int i = 0; i < e.arrayInitializer.size(); i++) {
						mv.visitInsn(DUP);
						pushInt(i);
						e.arrayInitializer.get(i).accept(this);
						storeArray(new ResolvedType(typeName, dimensions - 1));
					}
				} else {
					for (int i = 0; i < dimensions; i++) {
						if (i < e.type.arrayCapacities.size()) {
							com.lazar.absolutecinema.lexer.Token cap = e.type.arrayCapacities.get(i);
							if (cap.getType() == com.lazar.absolutecinema.lexer.TokenType.INT_LITERAL) {
								pushInt((Integer) cap.getLiteral());
							}
						}
					}

					if (dimensions == 1) {
						if (isPrimitive(typeName)) {
							int arrayType = getPrimitiveArrayType(typeName);
							mv.visitIntInsn(NEWARRAY, arrayType);
						} else {
							mv.visitTypeInsn(ANEWARRAY, typeName);
						}
					} else {
						String arrayDesc = getArrayTypeDescriptor(typeName, dimensions);
						mv.visitMultiANewArrayInsn(arrayDesc, dimensions);
					}
				}
			} else {
				mv.visitTypeInsn(NEW, typeName);
				mv.visitInsn(DUP);

				StringBuilder ctorDesc = new StringBuilder("(");

				SetupDecl setup = setupMap.get(typeName);
				if (setup != null && setup.ctor != null) {
					for (int i = 0; i < e.args.size(); i++) {
						e.args.get(i).accept(this);
						if (i < setup.ctor.params.size()) {
							ctorDesc.append(typeDescriptor(setup.ctor.params.get(i).type));
						}
					}
				} else {
					for (Expr arg : e.args) {
						arg.accept(this);
						ResolvedType argType = arg.getType();
						if (argType != null) {
							ctorDesc.append(typeDescriptorFromResolved(argType));
						}
					}
				}
				ctorDesc.append(")V");

				mv.visitMethodInsn(INVOKESPECIAL, typeName, "<init>", ctorDesc.toString(), false);
			}
			return null;
		}

		@Override
		public Void visitArrayLiteral(ArrayLiteral e) {
			pushInt(e.elements.size());

			if (!e.elements.isEmpty()) {
				ResolvedType firstType = e.elements.get(0).getType();
				String typeName = firstType.name();

				if (isPrimitive(typeName)) {
					int arrayType = getPrimitiveArrayType(typeName);
					mv.visitIntInsn(NEWARRAY, arrayType);
				} else {
					mv.visitTypeInsn(ANEWARRAY, typeName);
				}

				for (int i = 0; i < e.elements.size(); i++) {
					mv.visitInsn(DUP);
					pushInt(i);
					e.elements.get(i).accept(this);
					storeArray(firstType);
				}
			}
			return null;
		}

		private String typeDescriptor(LType type) {
			String base = type.name.getLexeme();
			int dims = type.dimension;

			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < dims; i++) {
				sb.append("[");
			}

			switch (base) {
				case "int" -> sb.append("I");
				case "double" -> sb.append("D");
				case "char" -> sb.append("C");
				case "bool" -> sb.append("Z");
				case "string" -> sb.append("Ljava/lang/String;");
				default -> sb.append("L").append(base).append(";");
			}

			return sb.toString();
		}

		private String typeDescriptorFromResolved(ResolvedType type) {
			if (type == null) {
				return "Ljava/lang/Object;";
			}

			String base = type.name();
			int dims = type.dimensions();

			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < dims; i++) {
				sb.append("[");
			}

			switch (base) {
				case "int" -> sb.append("I");
				case "double" -> sb.append("D");
				case "char" -> sb.append("C");
				case "bool" -> sb.append("Z");
				case "string" -> sb.append("Ljava/lang/String;");
				case "scrap" -> sb.append("V");
				default -> sb.append("L").append(base).append(";");
			}

			return sb.toString();
		}

		private String returnTypeDescriptor(LType type) {
			if (type == null || type.name.getLexeme().equals("scrap")) {
				return "V";
			}
			return typeDescriptor(type);
		}

		private String getArrayTypeDescriptor(String base, int dims) {
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < dims; i++) {
				sb.append("[");
			}

			switch (base) {
				case "int" -> sb.append("I");
				case "double" -> sb.append("D");
				case "char" -> sb.append("C");
				case "bool" -> sb.append("Z");
				case "string" -> sb.append("Ljava/lang/String;");
				default -> sb.append("L").append(base).append(";");
			}

			return sb.toString();
		}

		private boolean isPrimitive(String type) {
			return type.equals("int") || type.equals("double") || type.equals("char") || type.equals("bool");
		}

		private int getPrimitiveArrayType(String type) {
			return switch (type) {
				case "int" -> T_INT;
				case "double" -> T_DOUBLE;
				case "char" -> T_CHAR;
				case "bool" -> T_BOOLEAN;
				default -> T_INT;
			};
		}

		private int getTypeSize(LType type) {
			if (type.dimension > 0) return 1;
			return type.name.getLexeme().equals("double") ? 2 : 1;
		}

		private void loadLocal(String name, ResolvedType type) {
			int index = localVarIndexMap.get(name);

			if (type.dimensions() > 0 || !isPrimitive(type.name())) {
				mv.visitVarInsn(ALOAD, index);
			} else if (type.equals(ResolvedType.DOUBLE)) {
				mv.visitVarInsn(DLOAD, index);
			} else {
				mv.visitVarInsn(ILOAD, index);
			}
		}

		private void storeLocal(String name, LType type) {
			int index = localVarIndexMap.get(name);

			if (type.dimension > 0 || (!isPrimitive(type.name.getLexeme()) && !type.name.getLexeme().equals("string"))) {
				mv.visitVarInsn(ASTORE, index);
			} else if (type.name.getLexeme().equals("double")) {
				mv.visitVarInsn(DSTORE, index);
			} else if (type.name.getLexeme().equals("string")) {
				mv.visitVarInsn(ASTORE, index);
			} else {
				mv.visitVarInsn(ISTORE, index);
			}
		}

		private void storeLocal(String name, ResolvedType type) {
			int index = localVarIndexMap.get(name);

			if (type.dimensions() > 0 || !isPrimitive(type.name())) {
				mv.visitVarInsn(ASTORE, index);
			} else if (type.equals(ResolvedType.DOUBLE)) {
				mv.visitVarInsn(DSTORE, index);
			} else {
				mv.visitVarInsn(ISTORE, index);
			}
		}

		private void loadArray(ResolvedType elemType) {
			if (elemType.dimensions() > 0 || !isPrimitive(elemType.name())) {
				mv.visitInsn(AALOAD);
			} else if (elemType.equals(ResolvedType.DOUBLE)) {
				mv.visitInsn(DALOAD);
			} else if (elemType.equals(ResolvedType.CHAR)) {
				mv.visitInsn(CALOAD);
			} else if (elemType.equals(ResolvedType.BOOL)) {
				mv.visitInsn(BALOAD);
			} else {
				mv.visitInsn(IALOAD);
			}
		}

		private void storeArray(ResolvedType elemType) {
			if (elemType.dimensions() > 0 || !isPrimitive(elemType.name())) {
				mv.visitInsn(AASTORE);
			} else if (elemType.equals(ResolvedType.DOUBLE)) {
				mv.visitInsn(DASTORE);
			} else if (elemType.equals(ResolvedType.CHAR)) {
				mv.visitInsn(CASTORE);
			} else if (elemType.equals(ResolvedType.BOOL)) {
				mv.visitInsn(BASTORE);
			} else {
				mv.visitInsn(IASTORE);
			}
		}

		private void pushInt(int value) {
			if (value >= -1 && value <= 5) {
				mv.visitInsn(ICONST_0 + value);
			} else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
				mv.visitIntInsn(BIPUSH, value);
			} else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
				mv.visitIntInsn(SIPUSH, value);
			} else {
				mv.visitLdcInsn(value);
			}
		}

		private String getPrintDescriptor(ResolvedType type) {
			if (type.equals(ResolvedType.INT) || type.equals(ResolvedType.BOOL)) {
				return "(I)V";
			} else if (type.equals(ResolvedType.DOUBLE)) {
				return "(D)V";
			} else if (type.equals(ResolvedType.CHAR)) {
				return "(C)V";
			} else {
				return "(Ljava/lang/String;)V";
			}
		}

		private SceneDecl findScene(String name) {
			for (Node item : program.items) {
				if (item instanceof SceneDecl scene && scene.name.getLexeme().equals(name)) {
					return scene;
				}
			}
			return null;
		}

		private String disassemble(byte[] classBytes) {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);

			ClassReader cr = new ClassReader(classBytes);
			TraceClassVisitor tcv = new TraceClassVisitor(pw);
			cr.accept(tcv, 0);

			return sw.toString();
		}
	}
}