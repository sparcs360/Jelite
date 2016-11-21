package com.sparcs.jelite.soft6502;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.InputStream;

import org.junit.Before;
import org.junit.Test;

public class Soft6502Test {

	private Ram16Bit ram = new Ram16Bit();
	private Soft6502 cpu = new Soft6502();
	
	@Before
	public void beforeTest() throws IOException {

		InputStream data = Ram16Bit.class.getClassLoader().getResourceAsStream("6502_functional_test.bin");
		assertNotNull(data);
		ram.load(data, 0, Ram16Bit.LEN_64K);
	}
	
	@Test
	public void test() {

		cpu.setRam(ram);
		cpu.setPC(0x0400);
		
		while(true) {

			int pcBefore = cpu.getPC();
			cpu.step();
			if( cpu.getPC() == pcBefore ) {
				
				fail();
			}
		}
	}
}
