package org.example.lambda;

public class Target {

	public static String deduceTarget(String source){
		return source.substring(source.lastIndexOf("/") > 0 ? 
				source.lastIndexOf("/") + 1  : 0, source.lastIndexOf(".") > 0 ? 
						source.lastIndexOf(".") : source.length()-1);
	}
}
