package com.lazar.core;

import com.lazar.core.model.Token;
import com.lazar.core.util.TokenFormatter;
import com.lazar.lexer.Lexer;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Scanner;

public class App {
    String pathString;
    String source;
    public App(String[] args) {
        if(args.length == 1){
            pathString = args[0];
        }
        else{
            throw new IllegalArgumentException("Wrong number of arguments");
        }
    }
    public void run() throws IOException {
        scanFile(pathString);
    }
    public void scanFile(String pathString) throws IOException {
        String code = Files.readString(Path.of(pathString), Charset.defaultCharset());
        Lexer lexer = new Lexer(code);
        List<Token> tokenList = lexer.scanTokens();
        System.out.println(TokenFormatter.formatList(tokenList));
    }
}
