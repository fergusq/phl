package org.kaivos.lib;

import java.util.ArrayList;
import java.util.Map;

import org.kaivos.lib.Argument.ArgumentType;

public class ArgumentParser {
	
	@SuppressWarnings("unused")
	private String[] args;
	private ArrayList<Argument> arguments;
	
	public ArgumentParser(Map<String, Integer> argumentParams, String[] args) {
		this.args = args;
		this.arguments = new ArrayList<>();
		
		for (int i = 0; i < args.length; i++) {
			String arg = args[i];
			if (arg.startsWith("-")) {
				if (arg.length() > 1 && arg.charAt(1) == '-') {
					String name = arg.substring(2);
					if (argumentParams.get(name) == 1) {
						arguments.add(new Argument(ArgumentType.FLAG, name, args[++i]));
						continue;
					} else {
						arguments.add(new Argument(ArgumentType.FLAG, name));
					}
				}
				else for (int j = 1; j < arg.length(); j++) {
					if (argumentParams.get(""+arg.charAt(j)) == 1) {
						arguments.add(new Argument(ArgumentType.FLAG, ""+arg.charAt(j), args[++i]));
					}
					else arguments.add(new Argument(ArgumentType.FLAG, ""+arg.charAt(j)));
				}
				
			} else {
				arguments.add(new Argument(ArgumentType.TEXT, arg));
			}
		}
	}
	
	public Argument getFlag(String flag) {
		for (Argument a : arguments) 
		{
			if (a.getType() == ArgumentType.FLAG) {
				if (a.getFlag().equals(flag)) {
					return a;
				}
			}
		}
		return null;
	}
	
	public String lastText() {
		if (arguments.size() > 0) {
			if (arguments.get(arguments.size()-1).getType() == ArgumentType.TEXT) {
				return arguments.get(arguments.size()-1).getText();
			} else return null;
		} else return null;
	}
	
}
