package org.kaivos.proceedhl.compiler.type;


public class FloatType extends Type {

	public enum Size {
		SINGLE,
		DOUBLE,
		
		ARCH
	}
	
	public Size size;

	public FloatType(Size size) {
		super(Class.FLOAT);
		this.size = size;
	}
	
	public Size getSize() {
		return size;
	}

	@Override
	public String toCString() {
		switch (size) {
		case SINGLE:
			return "float";
		case DOUBLE:
			return "double";
		case ARCH:
			return "_phl_afloat";
			
		default:
			return "float";
		}
	}
	
	@Override
	public String toCStringWithVariable(String var) {
		return toCString() + " " + var;
	}

}
