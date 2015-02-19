package org.kaivos.proceedhl.compiler.type;

public class PointerType extends Type {

	private Type sub;
	
	public PointerType(Type subtype) {
		super(Class.POINTER);
		this.sub = subtype;
	}
	
	public Type getSubtype() {
		return sub;
	}
	
	@Override
	public String toCString() {
		return sub.toCString() + "*";
	}

	@Override
	public String toCStringWithVariable(String var) {
		return sub.toCStringWithVariable("*" + var);
	}
	
}
