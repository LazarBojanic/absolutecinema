package com.lazar.absolutecinema;

import com.lazar.absolutecinema.core.App;

class Main {
	public static void main(String[] args) {
		try {
			App.getInstance(args).run();
		}
		catch (Exception e) {
			System.out.println(e.getMessage());
		}

	}
}