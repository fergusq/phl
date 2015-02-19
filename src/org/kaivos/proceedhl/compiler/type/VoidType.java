package org.kaivos.proceedhl.compiler.type;

public class VoidType extends Type {

	public VoidType() {
		super(Class.VOID);
	}

	@Override
	public String toCString() {
		return "void";
	}

	@Override
	public String toCStringWithVariable(String var) {
		return "void " + var;
	}

}
