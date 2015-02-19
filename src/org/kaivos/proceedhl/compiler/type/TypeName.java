package org.kaivos.proceedhl.compiler.type;

public class TypeName {

	private String name;
	private Type type;
	
	public TypeName(String name, Type type) {
		this.name = name;
		this.type = type;
	}
	
	public String getName() {
		return name;
	}
	
	public Type getType() {
		return type;
	}
	
	public String toCString() {
		return type.toCStringWithVariable(name);
	}
	
}
