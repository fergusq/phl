package org.kaivos.proceedhl.compiler.type;

public class ArrayType extends Type {

	private Type subtype;
	
	public ArrayType(Type subtype) {
		super(Class.ARRAY);
		this.subtype = subtype;
	}
	
	@Override
	public String toCString() {
		return subtype.toCStringWithVariable("*");
	}
	
	@Override
	public String toCStringWithVariable(String var) {
		return subtype.toCStringWithVariable("*" + var);
	}

}
