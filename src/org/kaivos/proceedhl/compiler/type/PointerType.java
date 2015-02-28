package org.kaivos.proceedhl.compiler.type;

public class PointerType extends Type {

	private Type sub;
	private boolean constant = false;
	
	public PointerType(Type subtype) {
		super(Class.POINTER);
		this.sub = subtype;
	}
	
	public PointerType(Type subtype, boolean constant) {
		this(subtype);
		this.constant = true;
	}
	
	public Type getSubtype() {
		return sub;
	}
	
	public boolean isConstant() {
		return constant;
	}
	
	@Override
	public String toCString() {
		return (constant ? "const " : "") + sub.toCStringWithVariable("*");
	}

	@Override
	public String toCStringWithVariable(String var) {
		return (constant ? "const " : "") + sub.toCStringWithVariable("*" + var);
	}
	
}
