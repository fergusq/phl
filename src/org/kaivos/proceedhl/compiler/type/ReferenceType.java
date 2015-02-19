package org.kaivos.proceedhl.compiler.type;

public class ReferenceType extends Type {

	private String referenceType;
	
	public ReferenceType(String referenceType) {
		super(Class.REFERENCE);
		this.referenceType = referenceType;
	}
	
	@Override
	public String toCString() {
		return referenceType;
	}
	
	@Override
	public String toCStringWithVariable(String var) {
		return toCString() + " " + var;
	}

}
