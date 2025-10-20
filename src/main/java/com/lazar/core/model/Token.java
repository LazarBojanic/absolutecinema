package com.lazar.core.model;

import com.lazar.core.util.TokenFormatter;

public class Token {
    public final TokenType type;
    public final String lexeme;
    public final Integer literal;
    public final int line, colStart, colEnd;
    public Token(TokenType t, String lx, Integer lit, int line, int cs, int ce) {
        this.type=t; this.lexeme=lx; this.literal=lit; this.line=line; this.colStart=cs; this.colEnd=ce;
    }
    public String toString(){
        return (type+" '"+lexeme+"' at line: "+line+", column: "+colStart).
                replace("\n", "\\n").
                replace("\0", "\\0"); }

    public String formatted() {
        return TokenFormatter.format(this);
    }

}