package com.dhz.util;

public class StaticTest {
	public static int a = 1;
	
	public static void main(String[] args) {
		new Thread1().start();
		new Thread2().start();
	}
}

class Thread1 extends Thread {

	@Override
	public void run() {
		// TODO Auto-generated method stub
		while(StaticTest.a == 1) {
			try {
				Thread.sleep(3000);
				System.out.println("wait 3 s in Thread1");
				System.out.println("a = " + StaticTest.a + " in Thread1");
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
	
}

class Thread2 extends Thread {
	@Override
	public void run() {
		// TODO Auto-generated method stub
		super.run();
		try {
			Thread.sleep(10000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println("has waited 10s in Thread2");
		StaticTest.a = 2;
		System.out.println("has set a = 2 in Thread2");
	}
}
