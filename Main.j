=== Class: Shirt ===
// class version 65.0 (65)
// access flags 0x1
public class Shirt {


  // access flags 0x1
  public Ljava/lang/String; color

  // access flags 0x1
  public I size

  // access flags 0x1
  public <init>(Ljava/lang/String;I)V
    ALOAD 0
    INVOKESPECIAL java/lang/Object.<init> ()V
    ALOAD 0
    ALOAD 1
    DUP_X1
    PUTFIELD Shirt.color : Ljava/lang/String;
    POP
    ALOAD 0
    ILOAD 2
    DUP_X1
    PUTFIELD Shirt.size : I
    POP
    RETURN
    MAXSTACK = 3
    MAXLOCALS = 3

  // access flags 0x1
  public getColor()Ljava/lang/String;
    ALOAD 0
    GETFIELD Shirt.color : Ljava/lang/String;
    ARETURN
    MAXSTACK = 1
    MAXLOCALS = 1
}


=== Class: Main ===
// class version 65.0 (65)
// access flags 0x1
public class Main {


  // access flags 0x9
  public static entrance([Ljava/lang/String;)V
    NEW Shirt
    DUP
    LDC "red"
    BIPUSH 32
    INVOKESPECIAL Shirt.<init> (Ljava/lang/String;I)V
    ASTORE 1
    GETSTATIC java/lang/System.out : Ljava/io/PrintStream;
    ALOAD 1
    INVOKEVIRTUAL Shirt.getColor ()Ljava/lang/String;
    INVOKEVIRTUAL java/io/PrintStream.println (Ljava/lang/String;)V
    ALOAD 1
    GETFIELD Shirt.size : I
    BIPUSH 30
    IF_ICMPGT L0
    ICONST_0
    GOTO L1
   L0
   FRAME APPEND [Shirt]
    ICONST_1
   L1
   FRAME SAME1 I
    IFEQ L2
    GETSTATIC java/lang/System.out : Ljava/io/PrintStream;
    LDC "large"
    INVOKEVIRTUAL java/io/PrintStream.println (Ljava/lang/String;)V
    GOTO L3
   L2
   FRAME SAME
    ALOAD 1
    GETFIELD Shirt.size : I
    BIPUSH 20
    IF_ICMPGT L4
    ICONST_0
    GOTO L5
   L4
   FRAME SAME
    ICONST_1
   L5
   FRAME SAME1 I
    DUP
    IFEQ L6
    POP
    ALOAD 1
    GETFIELD Shirt.size : I
    BIPUSH 30
    IF_ICMPLE L7
    ICONST_0
    GOTO L8
   L7
   FRAME SAME
    ICONST_1
   L8
   FRAME SAME1 I
    GOTO L6
   L6
   FRAME SAME1 I
    IFEQ L9
    GETSTATIC java/lang/System.out : Ljava/io/PrintStream;
    LDC "medium"
    INVOKEVIRTUAL java/io/PrintStream.println (Ljava/lang/String;)V
    GOTO L3
   L9
   FRAME SAME
    GETSTATIC java/lang/System.out : Ljava/io/PrintStream;
    LDC "small"
    INVOKEVIRTUAL java/io/PrintStream.println (Ljava/lang/String;)V
   L3
   FRAME SAME
    ICONST_5
    ANEWARRAY Shirt
    ASTORE 2
    ALOAD 2
    ICONST_0
    ALOAD 1
    DUP_X2
    AASTORE
    POP
    RETURN
    MAXSTACK = 4
    MAXLOCALS = 3

  // access flags 0x9
  public static main([Ljava/lang/String;)V
    ALOAD 0
    INVOKESTATIC Main.entrance ([Ljava/lang/String;)V
    RETURN
    MAXSTACK = 1
    MAXLOCALS = 1
}


