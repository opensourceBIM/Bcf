package org.opensourcebim.bcf;

public class ReadOptions {
	public static final ReadOptions DEFAULT = new ReadOptions();
	private boolean readViewPoints;

	public ReadOptions() {
		readViewPoints = true;
	}
	
	public ReadOptions(boolean readViewPoints) {
		this.readViewPoints = readViewPoints;
	}
	
	public boolean isReadViewPoints() {
		return readViewPoints;
	}
}
