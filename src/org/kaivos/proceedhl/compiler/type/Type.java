package org.kaivos.proceedhl.compiler.type;

public abstract class Type {
	
	public enum Class {
		INTEGER,
		FLOAT,
		REFERENCE,
		POINTER,
		ARRAY,
		FUNCTION,
		
		VOID
	}
	
	private Class clazz;
	
	public Type(Class typeClass) {
		this.clazz = typeClass;
	}
	
	public Class getTypeClass() {
		return clazz;
	}
	
	public abstract String toCString();
	
	public abstract String toCStringWithVariable(String var);
	
}
