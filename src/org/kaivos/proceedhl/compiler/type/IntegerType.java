package org.kaivos.proceedhl.compiler.type;

public class IntegerType extends Type {

	public enum Size {
		I8(8),
		I16(16),
		I32(32),
		I64(64),
		I128(128),
		
		LONG(-1);
		;
		
		int size;
		
		private Size(int size) {
			this.size = size;
		}
		
		public int getSize() {
			return size;
		}
		
		public static Size getSize(int size) {
			for (Size s : Size.values()) {
				if (s.getSize() == size) return s;
			}
			throw new RuntimeException("unacceptable size: " + size);
		}
	}
	
	public Size size;

	public IntegerType(Size size) {
		super(Class.INTEGER);
		this.size = size;
	}
	
	public Size getSize() {
		return size;
	}
	
	@Override
	public String toCString() {
		switch (size) {
		case I8:
			return "char";
		case I16:
			return "short int";
		case I32:
			return "int";
		case I64:
		case LONG:
			return "long int";
		default:
			return "int";
		}
	}
	
	@Override
	public String toCStringWithVariable(String var) {
		return toCString() + " " + var;
	}

}
