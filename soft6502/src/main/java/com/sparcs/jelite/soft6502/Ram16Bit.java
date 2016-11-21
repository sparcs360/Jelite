package com.sparcs.jelite.soft6502;

import java.io.IOException;
import java.io.InputStream;

public class Ram16Bit {

	public static final int LEN_64K = 64*1024*1024;
	
	private byte[] memory = new byte[LEN_64K];
	
	public void load(InputStream stream, int off, int len) throws IOException {

		stream.read(memory, off, len);
	}

	public int getByte(int addr) {

		return (int)(memory[addr] & 0xFF);
	}

	public void setByte(int addr, int value) {

		memory[addr] = (byte)(value & 0xFF);
	}
}
