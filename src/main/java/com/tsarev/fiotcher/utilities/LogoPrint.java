package com.tsarev.fiotcher.utilities;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Scanner;

public class LogoPrint {
	public static void main(String[] args) {
		InputStream logoStream = Thread.currentThread()
				.getContextClassLoader()
				.getResourceAsStream("logo.txt");
		Scanner scanner = new Scanner(new InputStreamReader(logoStream));
		while (scanner.hasNextLine()) {
			System.out.println(scanner.nextLine());
		}
	}
}
