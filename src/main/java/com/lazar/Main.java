package com.lazar;

import com.lazar.core.App;

public class Main {
    public static void main(String[] args) {
        try{
            App app = new App(args);
            app.run();
        }
        catch(Exception e){
            System.err.println(e.getMessage());
        }
    }

}