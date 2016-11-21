package com.sparcs.jelite.soft6502;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Soft6502 {

	private static final Logger log = LoggerFactory.getLogger(Soft6502.class);

	// Registers
	int pc;
	int sp;
	int a;
	int x;
	int y;
	int status = FLAG_CONSTANT;

	// External RAM
	Ram16Bit ram;

	//helper variables
	long instructions = 0; //keep track of total instructions executed
	int clockticks = 0;
	int clockgoal = 0;
	int oldpc;
	int ea;
	int reladdr;
	int value;
	int result;
	int opcode;
	byte oldstatus;

	byte penaltyop;
	byte penaltyaddr;

	void reset() {
		
	    pc = (int)ram.getByte(0xFFFC) | ((int)ram.getByte(0xFFFD) << 8);
	    a = 0;
	    x = 0;
	    y = 0;
	    sp = 0xFD;
	    status |= FLAG_CONSTANT;
	}

	public static int FLAG_CARRY = 0x01;
	public static int FLAG_ZERO = 0x02;
	public static int FLAG_INTERRUPT = 0x04;
	public static int FLAG_DECIMAL = 0x08;
	public static int FLAG_BREAK = 0x10;
	public static int FLAG_CONSTANT = 0x20;
	public static int FLAG_OVERFLOW = 0x40;
	public static int FLAG_SIGN = 0x80;

	//===
	
	public Soft6502(Ram16Bit ram) {

		setRam(ram);
	}

	public Soft6502() {
		
		this(new Ram16Bit());
	}

	public void setRam(Ram16Bit ram) {

		this.ram = ram;
		reset();
	}

	public int getPC() {

		return pc;
	}
	public void setPC(int pc) {

		this.pc = pc;
	}

	public void step() {

		int opcodeByte = ram.getByte(pc);
		Opcode opcode = Opcode.byCode[opcodeByte];
		log.trace(opcode.disassemble(this));
		pc++;
		
	    penaltyop = 0;
	    penaltyaddr = 0;

	    opcode.execute(this);
	    log.trace(dump());

	    clockticks += opcode.getTicks();
	    if (penaltyop > 0 && penaltyaddr > 0) {
	    	clockticks++;
	    }
	    clockgoal = clockticks;

	    instructions++;
	}

	private String dump() {

		StringBuilder sb = new StringBuilder();
		
		sb.append(String.format("A=%02X (%03d), ", (byte)a, (int)(a & 0xFF)))
		  .append(String.format("X=%02X (%03d), ", (byte)x, (int)(x & 0xFF)))
		  .append(String.format("Y=%02X (%03d), ", (byte)y, (int)(y & 0xFF)))
		  .append(String.format("F=%02X (%03d) [", (byte)status, (int)(status & 0xFF)))
//			public static byte FLAG_CARRY = 0x01;
//			public static byte FLAG_ZERO = 0x02;
//			public static byte FLAG_INTERRUPT = 0x04;
//			public static byte FLAG_DECIMAL = 0x08;
//			public static byte FLAG_BREAK = 0x10;
//			public static byte FLAG_CONSTANT = 0x20;
//			public static byte FLAG_OVERFLOW = 0x40;
//			public static byte FLAG_SIGN = (byte)0x80;		  
		  .append((status & Soft6502.FLAG_SIGN) == 0 ? ' ' : 'S')
		  .append((status & Soft6502.FLAG_OVERFLOW) == 0 ? ' ' : 'O')
		  .append((status & Soft6502.FLAG_CONSTANT) == 0 ? ' ' : 'x')
		  .append((status & Soft6502.FLAG_BREAK) == 0 ? ' ' : 'B')
		  .append((status & Soft6502.FLAG_DECIMAL) == 0 ? ' ' : 'D')
		  .append((status & Soft6502.FLAG_INTERRUPT) == 0 ? ' ' : 'I')
		  .append((status & Soft6502.FLAG_ZERO) == 0 ? ' ' : 'Z')
		  .append((status & Soft6502.FLAG_CARRY) == 0 ? ' ' : 'C')
		  .append("], ")
		  .append(String.format("sp=%02X (%03d)", (byte)sp, (int)(sp & 0xFF)))
		  ;
		
		return sb.toString();
	}
}
