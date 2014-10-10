package org.kaivos.lib;

public class Argument {

	public enum ArgumentType {
		FLAG,
		TEXT
	}
	
	private ArgumentType type;
	private String text;
	private String arg;
	
	public Argument(ArgumentType type, String...args) {
		this.setType(type);
		
		if (type == ArgumentType.TEXT) {
			if (args.length != 1) throw new RuntimeException("Wrong arguments!");
			this.setText(args[0]);
		}
		if (type == ArgumentType.FLAG) {
			if (args.length == 0) throw new RuntimeException("Wrong arguments!");
			this.setText(args[0]);
			if (args.length == 2) this.setFlagArgument(args[1]);
		}
	}

	public ArgumentType getType() {
		return type;
	}

	void setType(ArgumentType type) {
		this.type = type;
	}

	
	/**
	 * Alias for getText()
	 * @return return value of getText()
	 * @see Argument.getText()
	 */
	public String getFlag() {
		return getText();
	}
	
	
	/**
	 * Returns text or flag
	 * @return text argument or flag
	 * @see Argument.getFlag()
	 */
	public String getText() {
		return text;
	}

	void setText(String text) {
		this.text = text;
	}

	public String getFlagArgument() {
		return arg;
	}

	void setFlagArgument(String arg) {
		this.arg = arg;
	}
	
}
