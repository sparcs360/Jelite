package com.sparcs.jelite.soft6502;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Opcode {

	private static final Logger log = LoggerFactory.getLogger(Opcode.class);

	private static int BASE_STACK = 0x0100;

	private byte code;
	private String mnemonic;
	private int operandSize;
	private Consumer<Soft6502> getEffectiveAddress;
	private Consumer<Soft6502> execute;
	private int ticks;
	
	public Opcode(int code, String mnemonic, Consumer<Soft6502> getEffectiveAddress, Consumer<Soft6502> execute, int ticks) {

		this.code = (byte)(code & 0xFF);
		this.mnemonic = mnemonic;
		this.getEffectiveAddress = getEffectiveAddress;
		this.execute = execute;
		this.ticks = ticks;
		
		if( oneOperandList.contains(getEffectiveAddress) ) {
			operandSize = 1;
		} else if( twoOperandList.contains(getEffectiveAddress) ) {
			operandSize = 2;
		} else {
			operandSize = 0;
		}
	}

	public int getTicks() {

		return ticks;
	}

	public String disassemble(Soft6502 cpu) {

		StringBuilder sb = new StringBuilder();
		
		// PC--
		// 0000 4C FE FF  JMP $FFFE
		sb.append(String.format("%04X ", cpu.pc))
		  .append(String.format("%02X ",  code));
		if( operandSize > 0 ) {
			sb.append(String.format("%02X ",  cpu.ram.getByte(cpu.pc+1)));
		} else {
			sb.append("   ");
		}
		if( operandSize > 1 ) {
			sb.append(String.format("%02X ",  cpu.ram.getByte(cpu.pc+2)));
		} else {
			sb.append("   ");
		}
		sb.append(' ')
		  .append(mnemonic)
		  ;

		return sb.toString();
	}

	public void execute(Soft6502 cpu) {

		getEffectiveAddress.accept(cpu);
		execute.accept(cpu);
	}

	//===

	//a few general functions used by various other functions
	private static void push16(Soft6502 cpu, int pushval) {
		
	    cpu.ram.setByte(BASE_STACK + cpu.sp, (byte)((pushval >> 8) & 0xFF));
	    cpu.ram.setByte(BASE_STACK + ((cpu.sp - 1) & 0xFF), (byte)(pushval & 0xFF));
	    cpu.sp -= 2;
	}

	static void push8(Soft6502 cpu, byte value) {
		
		cpu.ram.setByte(BASE_STACK + cpu.sp--, value);
	}

	static byte pop8(Soft6502 cpu) {
		
	    return (byte)(cpu.ram.getByte(BASE_STACK + ++cpu.sp));
	}

	static int pop16(Soft6502 cpu) {
		
	    int temp16 = cpu.ram.getByte(BASE_STACK + ((cpu.sp + 1) & 0xFF)) |
	    			 ((int)cpu.ram.getByte(BASE_STACK + ((cpu.sp + 2) & 0xFF)) << 8);
	    cpu.sp += 2;
	    return temp16;
	}

	//===
	
	// implied
	private static Consumer<Soft6502> imp = (cpu) -> {};

	// accumulator
	private static Consumer<Soft6502> acc = (cpu) -> {
		
		cpu.ea = -1;
	};
	
	// immediate
	private static Consumer<Soft6502> imm = (cpu) -> {

		cpu.ea = cpu.pc++;
	};

	// zero-page
	private static Consumer<Soft6502> zp = (cpu) -> {
		
		cpu.ea = cpu.ram.getByte(cpu.pc++);
	};

	// zero-page,X
	private static Consumer<Soft6502> zpx = (cpu) -> {
		
		cpu.ea = ((int)cpu.ram.getByte(cpu.pc++) + (int)cpu.x) & 0xFF; //zero-page wraparound
	};

	// zero-page,Y
	private static Consumer<Soft6502> zpy = (cpu) -> { 
		cpu.ea = ((int)cpu.ram.getByte((int)cpu.pc++) + (int)cpu.y) & 0xFF; //zero-page wraparound
	};

	// relative for branch ops (8-bit immediate value, sign-extended)
	private static Consumer<Soft6502> rel = (cpu) -> {
		
		cpu.reladdr = (byte)cpu.ram.getByte(cpu.pc++);
	    if ((cpu.reladdr & 0x80) != 0) {
	    	cpu.reladdr |= 0xFF00;
	    }
	};

	// absolute
	private static Consumer<Soft6502> abso = (cpu) -> { 
		
		cpu.ea = (int)cpu.ram.getByte(cpu.pc) | ((int)cpu.ram.getByte(cpu.pc+1) << 8);
	    cpu.pc += 2;
	};

	// absolute,X
	private static Consumer<Soft6502> absx = (cpu) -> {
		
	    int startpage;
	    cpu.ea = ((int)cpu.ram.getByte(cpu.pc) | ((int)cpu.ram.getByte(cpu.pc+1) << 8));
	    startpage = cpu.ea & 0xFF00;
	    cpu.ea += (int)cpu.x;

	    if (startpage != (cpu.ea & 0xFF00)) { //one cycle penlty for page-crossing on some opcodes
	    	cpu.penaltyaddr = 1;
	    }

	    cpu.pc += 2;
	};

	// absolute,Y
	private static Consumer<Soft6502> absy = (cpu) -> {
		
	    int startpage;
	    cpu.ea = ((int)cpu.ram.getByte(cpu.pc) | ((int)cpu.ram.getByte(cpu.pc+1) << 8));
	    startpage = cpu.ea & 0xFF00;
	    cpu.ea += (int)cpu.y;

	    if (startpage != (cpu.ea & 0xFF00)) { //one cycle penlty for page-crossing on some opcodes
	    	cpu.penaltyaddr = 1;
	    }

	    cpu.pc += 2;
	};

	// indirect
	private static Consumer<Soft6502> ind = (cpu) -> {

		int eahelp = (int)cpu.ram.getByte(cpu.pc) | (int)((int)cpu.ram.getByte(cpu.pc+1) << 8);
		int eahelp2 = (eahelp & 0xFF00) | ((eahelp + 1) & 0x00FF); //replicate 6502 page-boundary wraparound bug
		cpu.ea = (int)cpu.ram.getByte(eahelp) | ((int)cpu.ram.getByte(eahelp2) << 8);
	    cpu.pc += 2;
	};

	// (indirect,X)
	private static Consumer<Soft6502> indx = (cpu) -> {

		int eahelp = (int)(((int)cpu.ram.getByte(cpu.pc++) + (int)cpu.x) & 0xFF); //zero-page wraparound for table pointer
		cpu.ea = (int)cpu.ram.getByte(eahelp & 0x00FF) | ((int)cpu.ram.getByte((eahelp+1) & 0x00FF) << 8);
	};

	// (indirect),Y
	private static Consumer<Soft6502> indy = (cpu) -> {

		int eahelp = (int)cpu.ram.getByte(cpu.pc++);
	    int eahelp2 = (eahelp & 0xFF00) | ((eahelp + 1) & 0x00FF); //zero-page wraparound
	    cpu.ea = (int)cpu.ram.getByte(eahelp) | ((int)cpu.ram.getByte(eahelp2) << 8);
	    int startpage = cpu.ea & 0xFF00;
	    cpu.ea += (int)cpu.y;

	    if (startpage != (cpu.ea & 0xFF00)) { //one cycle penlty for page-crossing on some opcodes
	    	cpu.penaltyaddr = 1;
	    }
	};

	private static int getvalue(Soft6502 cpu) {
		
		return (cpu.ea == -1) ? (int)cpu.a : (int)cpu.ram.getByte(cpu.ea);
	}

	private static void putvalue(Soft6502 cpu, int value) {
		
	    if (cpu.ea == -1) {
	    	cpu.a = (byte)(value & 0x00FF);
	    } else {
	    	cpu.ram.setByte(cpu.ea, (byte)(value & 0x00FF));
	    }
	}

	//===

	private static void saveaccum(Soft6502 cpu, int value) {

		cpu.a = (byte)(value & 0xFF);
	}

	//flag modifier macros
	private static void setcarry(Soft6502 cpu) {
		
		cpu.status |= Soft6502.FLAG_CARRY;
	}
	private static void clearcarry(Soft6502 cpu) {
		
		cpu.status &= (~Soft6502.FLAG_CARRY);
	}
	private static void setzero(Soft6502 cpu) {
		
		cpu.status |= Soft6502.FLAG_ZERO;
	}
	private static void clearzero(Soft6502 cpu) {
		
		cpu.status &= (~Soft6502.FLAG_ZERO);
	}
	private static void setinterrupt(Soft6502 cpu) {
		
		cpu.status |= Soft6502.FLAG_INTERRUPT;
	}
	private static void clearinterrupt(Soft6502 cpu) {
		
		cpu.status &= (~Soft6502.FLAG_INTERRUPT);
	}
	private static void setdecimal(Soft6502 cpu) {
		
		cpu.status |= Soft6502.FLAG_DECIMAL;
	}
	private static void cleardecimal(Soft6502 cpu) {
		
		cpu.status &= (~Soft6502.FLAG_DECIMAL);
	}
	private static void setoverflow(Soft6502 cpu) {
		
		cpu.status |= Soft6502.FLAG_OVERFLOW;
	}
	private static void clearoverflow(Soft6502 cpu) {
		
		cpu.status &= (~Soft6502.FLAG_OVERFLOW);
	}
	private static void setsign(Soft6502 cpu) {
		
		cpu.status |= Soft6502.FLAG_SIGN;
	}
	private static void clearsign(Soft6502 cpu) {
		
		cpu.status &= (~Soft6502.FLAG_SIGN);
	}

	// flag calculation macros
	private static void zerocalc(Soft6502 cpu, int n) {
		
	    if ( (n & 0x00FF) == 0) {
	    	setzero(cpu);
	    } else {
	    	clearzero(cpu);
	    }
	}

	private static void signcalc(Soft6502 cpu, int n) {
		
	    if ( (n & 0x0080) == 0) {
	    	clearsign(cpu);
	    } else {
	    	setsign(cpu);
	    }
	}

	private static void carrycalc(Soft6502 cpu, int n) {
		
	    if ( (n & 0xFF00) == 0) {
	    	clearcarry(cpu);
	    } else {
	    	setcarry(cpu);
	    }
	}

	private static void overflowcalc(Soft6502 cpu, int n, int m, int o) { /* n = result, m = accumulator, o = memory */
		
	    if ( ((n ^ m) & (n ^ o) & 0x0080) == 0 ) {
	    	clearoverflow(cpu);
	    } else {
	    	setoverflow(cpu);
	    }
	}

	//===

	//instruction handler functions
	private static Consumer<Soft6502> adc = (cpu) -> {
		
		cpu.penaltyop = 1;
	    int value = getvalue(cpu);
	    int result = (int)cpu.a + value + (int)(cpu.status & Soft6502.FLAG_CARRY);

	    carrycalc(cpu, result);
	    zerocalc(cpu, result);
	    overflowcalc(cpu, result, cpu.a, value);
	    signcalc(cpu, result);

//	    #ifndef NES_CPU
//	    if (status & FLAG_DECIMAL) {
//	        clearcarry();
//
//	        if ((a & 0x0F) > 0x09) {
//	            a += 0x06;
//	        }
//	        if ((a & 0xF0) > 0x90) {
//	            a += 0x60;
//	            setcarry();
//	        }
//
//	        clockticks6502++;
//	    }
//	    #endif

	    saveaccum(cpu, result);
	};

	private static Consumer<Soft6502> and = (cpu) -> {
		
	    cpu.penaltyop = 1;
	    int result = (int)cpu.a & getvalue(cpu);

	    zerocalc(cpu, result);
	    signcalc(cpu, result);

	    saveaccum(cpu, result);
	};

	private static Consumer<Soft6502> asl = (cpu) -> {
		
	    int result = getvalue(cpu) << 1;

	    carrycalc(cpu, result);
	    zerocalc(cpu, result);
	    signcalc(cpu, result);

	    putvalue(cpu, result);
	};

	private static Consumer<Soft6502> bcc = (cpu) -> {
		
	    if ((cpu.status & Soft6502.FLAG_CARRY) == 0) {
	        int oldpc = cpu.pc;
	        cpu.pc += cpu.reladdr;
	        if ((oldpc & 0xFF00) != (cpu.pc & 0xFF00)) {
	        	cpu.clockticks += 2; //check if jump crossed a page boundary
	        } else {
	        	cpu.clockticks++;
	        }
	    }
	};

	private static Consumer<Soft6502> bcs = (cpu) -> {
		
	    if ((cpu.status & Soft6502.FLAG_CARRY) > 0) {
	        int oldpc = cpu.pc;
	        cpu.pc += cpu.reladdr;
	        if ((oldpc & 0xFF00) != (cpu.pc & 0xFF00)) {
	        	cpu.clockticks += 2; //check if jump crossed a page boundary
	        } else {
	        	cpu.clockticks++;
	        }
	    }
	};

	private static Consumer<Soft6502> beq = (cpu) -> {
		
	    if ((cpu.status & Soft6502.FLAG_ZERO) > 0) {
	        int oldpc = cpu.pc;
	        cpu.pc += cpu.reladdr;
	        if ((oldpc & 0xFF00) != (cpu.pc & 0xFF00)) {
	        	cpu.clockticks += 2; //check if jump crossed a page boundary
	        } else {
	        	cpu.clockticks++;
            }
	    }
	};

	private static Consumer<Soft6502> bit = (cpu) -> {
		
	    int value = getvalue(cpu);
	    int result = (int)cpu.a & value;

	    zerocalc(cpu, result);
	    cpu.status = (byte)((cpu.status & 0x3F) | (byte)(value & 0xC0));
	};

	private static Consumer<Soft6502> bmi = (cpu) -> {
		
	    if ((cpu.status & Soft6502.FLAG_SIGN) > 0) {
	        int oldpc = cpu.pc;
	        cpu.pc += cpu.reladdr;
	        if ((oldpc & 0xFF00) != (cpu.pc & 0xFF00)) {
	        	cpu.clockticks += 2; //check if jump crossed a page boundary
	        } else {
	        	cpu.clockticks++;
	        }
	    }
	};

	private static Consumer<Soft6502> bne = (cpu) -> {
		
	    if ((cpu.status & Soft6502.FLAG_ZERO) == 0) {
	        int oldpc = cpu.pc;
	        cpu.pc += cpu.reladdr;
	        if ((oldpc & 0xFF00) != (cpu.pc & 0xFF00)) {
	        	cpu.clockticks += 2; //check if jump crossed a page boundary
	        } else {
	        	cpu.clockticks++;
	        }
	    }
	};

	private static Consumer<Soft6502> bpl = (cpu) -> {
		
	    if ((cpu.status & Soft6502.FLAG_SIGN) > 0) {
	        int oldpc = cpu.pc;
	        cpu.pc += cpu.reladdr;
	        if ((oldpc & 0xFF00) != (cpu.pc & 0xFF00)) {
	        	cpu.clockticks += 2; //check if jump crossed a page boundary
	        } else {
	        	cpu.clockticks++;
	        }
	    }
	};

	private static Consumer<Soft6502> brk = (cpu) -> {
		
		cpu.pc++;
	    push16(cpu, cpu.pc); //push next instruction address onto stack
	    push8(cpu, (byte)(cpu.status | Soft6502.FLAG_BREAK)); //push CPU status to stack
	    setinterrupt(cpu); //set interrupt flag
	    cpu.pc = (int)cpu.ram.getByte(0xFFFE) | ((int)cpu.ram.getByte(0xFFFF) << 8);
	};

	private static Consumer<Soft6502> bvc = (cpu) -> {
		
	    if ((cpu.status & Soft6502.FLAG_OVERFLOW) == 0) {
	        int oldpc = cpu.pc;
	        cpu.pc += cpu.reladdr;
	        if ((oldpc & 0xFF00) != (cpu.pc & 0xFF00)) {
	        	cpu.clockticks += 2; //check if jump crossed a page boundary
	        } else {
	        	cpu.clockticks++;
	        }
	    }
	};

	private static Consumer<Soft6502> bvs = (cpu) -> {
		
	    if ((cpu.status & Soft6502.FLAG_OVERFLOW) == Soft6502.FLAG_OVERFLOW) {
	        int oldpc = cpu.pc;
	        cpu.pc += cpu.reladdr;
	        if ((oldpc & 0xFF00) != (cpu.pc & 0xFF00)) {
	        	cpu.clockticks += 2; //check if jump crossed a page boundary
	        } else {
	        	cpu.clockticks++;
	        }
	    }
	};

	private static Consumer<Soft6502> clc = (cpu) -> {
		
	    clearcarry(cpu);
	};

	private static Consumer<Soft6502> cld = (cpu) -> {
		
	    cleardecimal(cpu);
	};

	private static Consumer<Soft6502> cli = (cpu) -> {
	    clearinterrupt(cpu);
	};

	private static Consumer<Soft6502> clv = (cpu) -> {
	    clearoverflow(cpu);
	};

	private static Consumer<Soft6502> cmp = (cpu) -> {
		
		cpu.penaltyop = 1;
	    int value = getvalue(cpu);
	    int result = (int)cpu.a - value;

	    if (cpu.a >= (byte)(value & 0x00FF)) {
	    	setcarry(cpu);
	    } else {
	    	clearcarry(cpu);
	    }
	    if (cpu.a == (byte)(value & 0x00FF)) {
	    	setzero(cpu);
	    } else {
	    	clearzero(cpu);
	    }
	    signcalc(cpu, result);
	};

	private static Consumer<Soft6502> cpx = (cpu) -> {
		
	    int value = getvalue(cpu);
	    int result = (int)cpu.x - value;

	    if (cpu.x >= (byte)(value & 0x00FF)) {
	    	setcarry(cpu);
	    } else {
	    	clearcarry(cpu);
	    }
	    if (cpu.x == (byte)(value & 0x00FF)) {
	    	setzero(cpu);
	    } else {
	    	clearzero(cpu);
	    }
	    signcalc(cpu, result);
	};

	private static Consumer<Soft6502> cpy = (cpu) -> {
		
	    int value = getvalue(cpu);
	    int result = (int)cpu.y - value;

	    if (cpu.y >= (byte)(value & 0x00FF)) {
	    	setcarry(cpu);
	    } else {
	    	clearcarry(cpu);
	    }
	    if (cpu.y == (byte)(value & 0x00FF)) {
	    	setzero(cpu);
	    } else {
	    	clearzero(cpu);
	    }
	    signcalc(cpu, result);
	};

	private static Consumer<Soft6502> dec = (cpu) -> {
		
	    int result = getvalue(cpu) - 1;

	    zerocalc(cpu, result);
	    signcalc(cpu, result);

	    putvalue(cpu, result);
	};

	private static Consumer<Soft6502> dex = (cpu) -> {
		
		cpu.x--;

	    zerocalc(cpu, cpu.x);
	    signcalc(cpu, cpu.x);
	};

	private static Consumer<Soft6502> dey = (cpu) -> {
		
		cpu.y--;

	    zerocalc(cpu, cpu.y);
	    signcalc(cpu, cpu.y);
	};

	private static Consumer<Soft6502> eor = (cpu) -> {
		
		cpu.penaltyop = 1;
	    int value = getvalue(cpu);
	    int result = (int)cpu.a ^ value;

	    zerocalc(cpu, result);
	    signcalc(cpu, result);

	    saveaccum(cpu, result);
	};

	private static Consumer<Soft6502> inc = (cpu) -> {
		
	    int result = getvalue(cpu) + 1;

	    zerocalc(cpu, result);
	    signcalc(cpu, result);

	    putvalue(cpu, result);
	};

	private static Consumer<Soft6502> inx = (cpu) -> {
		
		cpu.x++;

	    zerocalc(cpu, cpu.x);
	    signcalc(cpu, cpu.x);
	};

	private static Consumer<Soft6502> iny = (cpu) -> {
		
		cpu.y++;

	    zerocalc(cpu, cpu.y);
	    signcalc(cpu, cpu.y);
	};

	private static Consumer<Soft6502> jmp = (cpu) -> {
		
		cpu.pc = cpu.ea;
	};

	private static Consumer<Soft6502> jsr = (cpu) -> {
		
	    push16(cpu, cpu.pc - 1);
	    cpu.pc = cpu.ea;
	};

	private static Consumer<Soft6502> lda = (cpu) -> {
		
		cpu.penaltyop = 1;
	    int value = getvalue(cpu);
	    cpu.a = (byte)(value & 0x00FF);

	    zerocalc(cpu, cpu.a);
	    signcalc(cpu, cpu.a);
	};

	private static Consumer<Soft6502> ldx = (cpu) -> {
		
		cpu.penaltyop = 1;
	    cpu.x = (byte)(getvalue(cpu) & 0x00FF);

	    zerocalc(cpu, cpu.x);
	    signcalc(cpu, cpu.x);
	};

	private static Consumer<Soft6502> ldy = (cpu) -> {
		
		cpu.penaltyop = 1;
	    cpu.y = (byte)(getvalue(cpu) & 0x00FF);

	    zerocalc(cpu, cpu.y);
	    signcalc(cpu, cpu.y);
	};

	private static Consumer<Soft6502> lsr = (cpu) -> {
		
		int value = getvalue(cpu);
	    int result = value >> 1;

	    if ( (value & 1) == 0) {
	    	clearcarry(cpu);
	    } else {
	    	setcarry(cpu);
	    }
	    zerocalc(cpu, result);
	    signcalc(cpu, result);

	    putvalue(cpu, result);
	};

	private static Consumer<Soft6502> nop = (cpu) -> {
		
	    switch (cpu.opcode) {
	        case 0x1C:
	        case 0x3C:
	        case 0x5C:
	        case 0x7C:
	        case (byte)0xDC:
	        case (byte)0xFC:
	            cpu.penaltyop = 1;
	            break;
	    }
	};

	private static Consumer<Soft6502> ora = (cpu) -> {
		
	    cpu.penaltyop = 1;
	    int result = (int)cpu.a | getvalue(cpu);

	    zerocalc(cpu, result);
	    signcalc(cpu, result);

	    saveaccum(cpu, result);
	};

	private static Consumer<Soft6502> pha = (cpu) -> {
		
	    push8(cpu, (byte)(cpu.a & 0xFF));
	};

	private static Consumer<Soft6502> php = (cpu) -> {
		
	    push8(cpu, (byte)(cpu.status | Soft6502.FLAG_BREAK));
	};

	private static Consumer<Soft6502> pla = (cpu) -> {
		
		cpu.a = pop8(cpu);

	    zerocalc(cpu, cpu.a);
	    signcalc(cpu, cpu.a);
	};

	private static Consumer<Soft6502> plp = (cpu) -> {
		
		cpu.status = (byte)(pop8(cpu) | Soft6502.FLAG_CONSTANT);
	};

	private static Consumer<Soft6502> rol = (cpu) -> {
		
	    int result = (getvalue(cpu) << 1) | (cpu.status & Soft6502.FLAG_CARRY);

	    carrycalc(cpu, result);
	    zerocalc(cpu, result);
	    signcalc(cpu, result);

	    putvalue(cpu, result);
	};

	private static Consumer<Soft6502> ror = (cpu) -> {
		
	    int value = getvalue(cpu);
	    int result = (value >> 1) | ((cpu.status & Soft6502.FLAG_CARRY) << 7);

	    if ((value & 1) == 0) {
	    	clearcarry(cpu);
	    } else {
	    	setcarry(cpu);
	    }
	    zerocalc(cpu, result);
	    signcalc(cpu, result);

	    putvalue(cpu, result);
	};

	private static Consumer<Soft6502> rti = (cpu) -> {
		
		cpu.status = pop8(cpu);
	    int value = pop16(cpu);
	    cpu.pc = value;
	};

	private static Consumer<Soft6502> rts = (cpu) -> {
		
	    cpu.pc = pop16(cpu) + 1;
	};

	private static Consumer<Soft6502> sbc = (cpu) -> {
		
	    cpu.penaltyop = 1;
	    int value = getvalue(cpu) ^ 0x00FF;
	    int result = (int)cpu.a + value + (int)(cpu.status & Soft6502.FLAG_CARRY);

	    carrycalc(cpu, result);
	    zerocalc(cpu, result);
	    overflowcalc(cpu, result, cpu.a, value);
	    signcalc(cpu, result);

//	    #ifndef NES_CPU
//	    if (status & FLAG_DECIMAL) {
//	        clearcarry();
//
//	        a -= 0x66;
//	        if ((a & 0x0F) > 0x09) {
//	            a += 0x06;
//	        }
//	        if ((a & 0xF0) > 0x90) {
//	            a += 0x60;
//	            setcarry();
//	        }
//
//	        clockticks6502++;
//	    }
//	    #endif

	    saveaccum(cpu, result);
	};

	private static Consumer<Soft6502> sec = (cpu) -> {
		
	    setcarry(cpu);
	};

	private static Consumer<Soft6502> sed = (cpu) -> {
		
	    setdecimal(cpu);
	};

	private static Consumer<Soft6502> sei = (cpu) -> {
		
	    setinterrupt(cpu);
	};

	private static Consumer<Soft6502> sta = (cpu) -> {
		
	    putvalue(cpu, cpu.a);
	};

	private static Consumer<Soft6502> stx = (cpu) -> {
		
	    putvalue(cpu, cpu.x);
	};

	private static Consumer<Soft6502> sty = (cpu) -> {
		
	    putvalue(cpu, cpu.y);
	};

	private static Consumer<Soft6502> tax = (cpu) -> {
		
		cpu.x = cpu.a;

	    zerocalc(cpu, cpu.x);
	    signcalc(cpu, cpu.x);
	};

	private static Consumer<Soft6502> tay = (cpu) -> {
		
		cpu.y = cpu.a;

	    zerocalc(cpu, cpu.y);
	    signcalc(cpu, cpu.y);
	};

	private static Consumer<Soft6502> tsx = (cpu) -> {
		
		cpu.x = cpu.sp;

	    zerocalc(cpu, cpu.x);
	    signcalc(cpu, cpu.x);
	};

	private static Consumer<Soft6502> txa = (cpu) -> {
		
		cpu.a = cpu.x;

	    zerocalc(cpu, cpu.a);
	    signcalc(cpu, cpu.a);
	};

	private static Consumer<Soft6502> txs = (cpu) -> {
		
		cpu.sp = cpu.x;
	};

	private static Consumer<Soft6502> tya = (cpu) -> {
		
		cpu.a = cpu.y;

	    zerocalc(cpu, cpu.a);
	    signcalc(cpu, cpu.a);
	};

	private static Consumer<Soft6502> lax = nop;
	private static Consumer<Soft6502> sax = nop;
	private static Consumer<Soft6502> dcp = nop;
	private static Consumer<Soft6502> isb = nop;
	private static Consumer<Soft6502> slo = nop;
	private static Consumer<Soft6502> rla = nop;
	private static Consumer<Soft6502> sre = nop;
	private static Consumer<Soft6502> rra = nop;

	//===

	private static List<Consumer<Soft6502>> oneOperandList = Arrays.asList(
		imm, zp, zpx, zpy, rel
	);
	private static List<Consumer<Soft6502>> twoOperandList = Arrays.asList(
		abso, absx, absy, ind, indx, indy
	);

	public static Opcode[] byCode = {

		new Opcode(0x00, "BRK", imp, brk, 7),
		new Opcode(0x01, "ORA", indx, ora, 6),
		new Opcode(0x02, "NOP", imp, nop, 2),
		new Opcode(0x03, "SLO", indx, slo, 8),
		new Opcode(0x04, "NOP", zp, nop, 3),
		new Opcode(0x05, "ORA", zp, ora, 3),
		new Opcode(0x06, "ASL", zp, asl, 5),
		new Opcode(0x07, "SLO", zp, slo, 5),
		new Opcode(0x08, "PHP", imp, php, 3),
		new Opcode(0x09, "ORA", imm, ora, 2),
		new Opcode(0x0A, "ASL", acc, asl, 2),
		new Opcode(0x0B, "NOP", imm, nop, 2),
		new Opcode(0x0C, "NOP", abso, nop, 4),
		new Opcode(0x0D, "ORA", abso, ora, 4),
		new Opcode(0x0E, "ASL", abso, asl, 6),
		new Opcode(0x0F, "SLO", abso, slo, 6),
		new Opcode(0x10, "BPL", rel, bpl, 2),
		new Opcode(0x11, "ORA", indy, ora, 5),
		new Opcode(0x12, "NOP", imp, nop, 2),
		new Opcode(0x13, "SLO", indy, slo, 8),
		new Opcode(0x14, "NOP", zpx, nop, 4),
		new Opcode(0x15, "ORA", zpx, ora, 4),
		new Opcode(0x16, "ASL", zpx, asl, 6),
		new Opcode(0x17, "SLO", zpx, slo, 6),
		new Opcode(0x18, "CLC", imp, clc, 2),
		new Opcode(0x19, "ORA", absy, ora, 4),
		new Opcode(0x1A, "NOP", imp, nop, 2),
		new Opcode(0x1B, "SLO", absy, slo, 7),
		new Opcode(0x1C, "NOP", absx, nop, 4),
		new Opcode(0x1D, "ORA", absx, ora, 4),
		new Opcode(0x1E, "ASL", absx, asl, 7),
		new Opcode(0x1F, "SLO", absx, slo, 7),
		new Opcode(0x20, "JSR", abso, jsr, 6),
		new Opcode(0x21, "AND", indx, and, 6),
		new Opcode(0x22, "NOP", imp, nop, 2),
		new Opcode(0x23, "RLA", indx, rla, 8),
		new Opcode(0x24, "BIT", zp, bit, 3),
		new Opcode(0x25, "AND", zp, and, 3),
		new Opcode(0x26, "ROL", zp, rol, 5),
		new Opcode(0x27, "RLA", zp, rla, 5),
		new Opcode(0x28, "PLP", imp, plp, 4),
		new Opcode(0x29, "AND", imm, and, 2),
		new Opcode(0x2A, "ROL", acc, rol, 2),
		new Opcode(0x2B, "NOP", imm, nop, 2),
		new Opcode(0x2C, "BIT", abso, bit, 4),
		new Opcode(0x2D, "AND", abso, and, 4),
		new Opcode(0x2E, "ROL", abso, rol, 6),
		new Opcode(0x2F, "RLA", abso, rla, 6),
		new Opcode(0x30, "BMI", rel, bmi, 2),
		new Opcode(0x31, "AND", indy, and, 5),
		new Opcode(0x32, "NOP", imp, nop, 2),
		new Opcode(0x33, "RLA", indy, rla, 8),
		new Opcode(0x34, "NOP", zpx, nop, 4),
		new Opcode(0x35, "AND", zpx, and, 4),
		new Opcode(0x36, "ROL", zpx, rol, 6),
		new Opcode(0x37, "RLA", zpx, rla, 6),
		new Opcode(0x38, "SEC", imp, sec, 2),
		new Opcode(0x39, "AND", absy, and, 4),
		new Opcode(0x3A, "NOP", imp, nop, 2),
		new Opcode(0x3B, "RLA", absy, rla, 7),
		new Opcode(0x3C, "NOP", absx, nop, 4),
		new Opcode(0x3D, "AND", absx, and, 4),
		new Opcode(0x3E, "ROL", absx, rol, 7),
		new Opcode(0x3F, "RLA", absx, rla, 7),
		new Opcode(0x40, "RTI", imp, rti, 6),
		new Opcode(0x41, "EOR", indx, eor, 6),
		new Opcode(0x42, "NOP", imp, nop, 2),
		new Opcode(0x43, "SRE", indx, sre, 8),
		new Opcode(0x44, "NOP", zp, nop, 3),
		new Opcode(0x45, "EOR", zp, eor, 3),
		new Opcode(0x46, "LSR", zp, lsr, 5),
		new Opcode(0x47, "SRE", zp, sre, 5),
		new Opcode(0x48, "PHA", imp, pha, 3),
		new Opcode(0x49, "EOR", imm, eor, 2),
		new Opcode(0x4A, "LSR", acc, lsr, 2),
		new Opcode(0x4B, "NOP", imm, nop, 2),
		new Opcode(0x4C, "JMP", abso, jmp, 3),
		new Opcode(0x4D, "EOR", abso, eor, 4),
		new Opcode(0x4E, "LSR", abso, lsr, 6),
		new Opcode(0x4F, "SRE", abso, sre, 6),
		new Opcode(0x50, "BVC", rel, bvc, 2),
		new Opcode(0x51, "EOR", indy, eor, 5),
		new Opcode(0x52, "NOP", imp, nop, 2),
		new Opcode(0x53, "SRE", indy, sre, 8),
		new Opcode(0x54, "NOP", zpx, nop, 4),
		new Opcode(0x55, "EOR", zpx, eor, 4),
		new Opcode(0x56, "LSR", zpx, lsr, 6),
		new Opcode(0x57, "SRE", zpx, sre, 6),
		new Opcode(0x58, "CLI", imp, cli, 2),
		new Opcode(0x59, "EOR", absy, eor, 4),
		new Opcode(0x5A, "NOP", imp, nop, 2),
		new Opcode(0x5B, "SRE", absy, sre, 7),
		new Opcode(0x5C, "NOP", absx, nop, 4),
		new Opcode(0x5D, "EOR", absx, eor, 4),
		new Opcode(0x5E, "LSR", absx, lsr, 7),
		new Opcode(0x5F, "SRE", absx, sre, 7),
		new Opcode(0x60, "RTS", imp, rts, 6),
		new Opcode(0x61, "ADC", indx, adc, 6),
		new Opcode(0x62, "NOP", imp, nop, 2),
		new Opcode(0x63, "RRA", indx, rra, 8),
		new Opcode(0x64, "NOP", zp, nop, 3),
		new Opcode(0x65, "ADC", zp, adc, 3),
		new Opcode(0x66, "ROR", zp, ror, 5),
		new Opcode(0x67, "RRA", zp, rra, 5),
		new Opcode(0x68, "PLA", imp, pla, 4),
		new Opcode(0x69, "ADC", imm, adc, 2),
		new Opcode(0x6A, "ROR", acc, ror, 2),
		new Opcode(0x6B, "NOP", imm, nop, 2),
		new Opcode(0x6C, "JMP", ind, jmp, 5),
		new Opcode(0x6D, "ADC", abso, adc, 4),
		new Opcode(0x6E, "ROR", abso, ror, 6),
		new Opcode(0x6F, "RRA", abso, rra, 6),
		new Opcode(0x70, "BVS", rel, bvs, 2),
		new Opcode(0x71, "ADC", indy, adc, 5),
		new Opcode(0x72, "NOP", imp, nop, 2),
		new Opcode(0x73, "RRA", indy, rra, 8),
		new Opcode(0x74, "NOP", zpx, nop, 4),
		new Opcode(0x75, "ADC", zpx, adc, 4),
		new Opcode(0x76, "ROR", zpx, ror, 6),
		new Opcode(0x77, "RRA", zpx, rra, 6),
		new Opcode(0x78, "SEI", imp, sei, 2),
		new Opcode(0x79, "ADC", absy, adc, 4),
		new Opcode(0x7A, "NOP", imp, nop, 2),
		new Opcode(0x7B, "RRA", absy, rra, 7),
		new Opcode(0x7C, "NOP", absx, nop, 4),
		new Opcode(0x7D, "ADC", absx, adc, 4),
		new Opcode(0x7E, "ROR", absx, ror, 7),
		new Opcode(0x7F, "RRA", absx, rra, 7),
		new Opcode(0x80, "NOP", imm, nop, 2),
		new Opcode(0x81, "STA", indx, sta, 6),
		new Opcode(0x82, "NOP", imm, nop, 2),
		new Opcode(0x83, "SAX", indx, sax, 6),
		new Opcode(0x84, "STY", zp, sty, 3),
		new Opcode(0x85, "STA", zp, sta, 3),
		new Opcode(0x86, "STX", zp, stx, 3),
		new Opcode(0x87, "SAX", zp, sax, 3),
		new Opcode(0x88, "DEY", imp, dey, 2),
		new Opcode(0x89, "NOP", imm, nop, 2),
		new Opcode(0x8A, "TXA", imp, txa, 2),
		new Opcode(0x8B, "NOP", imm, nop, 2),
		new Opcode(0x8C, "STY", abso, sty, 4),
		new Opcode(0x8D, "STA", abso, sta, 4),
		new Opcode(0x8E, "STX", abso, stx, 4),
		new Opcode(0x8F, "SAX", abso, sax, 4),
		new Opcode(0x90, "BCC", rel, bcc, 2),
		new Opcode(0x91, "STA", indy, sta, 6),
		new Opcode(0x92, "NOP", imp, nop, 2),
		new Opcode(0x93, "NOP", indy, nop, 6),
		new Opcode(0x94, "STY", zpx, sty, 4),
		new Opcode(0x95, "STA", zpx, sta, 4),
		new Opcode(0x96, "STX", zpy, stx, 4),
		new Opcode(0x97, "SAX", zpy, sax, 4),
		new Opcode(0x98, "TYA", imp, tya, 2),
		new Opcode(0x99, "STA", absy, sta, 5),
		new Opcode(0x9A, "TXS", imp, txs, 2),
		new Opcode(0x9B, "NOP", absy, nop, 5),
		new Opcode(0x9C, "NOP", absx, nop, 5),
		new Opcode(0x9D, "STA", absx, sta, 5),
		new Opcode(0x9E, "NOP", absy, nop, 5),
		new Opcode(0x9F, "NOP", absy, nop, 5),
		new Opcode(0xA0, "LDY", imm, ldy, 2),
		new Opcode(0xA1, "LDA", indx, lda, 6),
		new Opcode(0xA2, "LDX", imm, ldx, 2),
		new Opcode(0xA3, "LAX", indx, lax, 6),
		new Opcode(0xA4, "LDY", zp, ldy, 3),
		new Opcode(0xA5, "LDA", zp, lda, 3),
		new Opcode(0xA6, "LDX", zp, ldx, 3),
		new Opcode(0xA7, "LAX", zp, lax, 3),
		new Opcode(0xA8, "TAY", imp, tay, 2),
		new Opcode(0xA9, "LDA", imm, lda, 2),
		new Opcode(0xAA, "TAX", imp, tax, 2),
		new Opcode(0xAB, "NOP", imm, nop, 2),
		new Opcode(0xAC, "LDY", abso, ldy, 4),
		new Opcode(0xAD, "LDA", abso, lda, 4),
		new Opcode(0xAE, "LDX", abso, ldx, 4),
		new Opcode(0xAF, "LAX", abso, lax, 4),
		new Opcode(0xB0, "BCS", rel, bcs, 2),
		new Opcode(0xB1, "LDA", indy, lda, 5),
		new Opcode(0xB2, "NOP", imp, nop, 2),
		new Opcode(0xB3, "LAX", indy, lax, 5),
		new Opcode(0xB4, "LDY", zpx, ldy, 4),
		new Opcode(0xB5, "LDA", zpx, lda, 4),
		new Opcode(0xB6, "LDX", zpy, ldx, 4),
		new Opcode(0xB7, "LAX", zpy, lax, 4),
		new Opcode(0xB8, "CLV", imp, clv, 2),
		new Opcode(0xB9, "LDA", absy, lda, 4),
		new Opcode(0xBA, "TSX", imp, tsx, 2),
		new Opcode(0xBB, "LAX", absy, lax, 4),
		new Opcode(0xBC, "LDY", absx, ldy, 4),
		new Opcode(0xBD, "LDA", absx, lda, 4),
		new Opcode(0xBE, "LDX", absy, ldx, 4),
		new Opcode(0xBF, "LAX", absy, lax, 4),
		new Opcode(0xC0, "CPY", imm, cpy, 2),
		new Opcode(0xC1, "CMP", indx, cmp, 6),
		new Opcode(0xC2, "NOP", imm, nop, 2),
		new Opcode(0xC3, "DCP", indx, dcp, 8),
		new Opcode(0xC4, "CPY", zp, cpy, 3),
		new Opcode(0xC5, "CMP", zp, cmp, 3),
		new Opcode(0xC6, "DEC", zp, dec, 5),
		new Opcode(0xC7, "DCP", zp, dcp, 5),
		new Opcode(0xC8, "INY", imp, iny, 2),
		new Opcode(0xC9, "CMP", imm, cmp, 2),
		new Opcode(0xCA, "DEX", imp, dex, 2),
		new Opcode(0xCB, "NOP", imm, nop, 2),
		new Opcode(0xCC, "CPY", abso, cpy, 4),
		new Opcode(0xCD, "CMP", abso, cmp, 4),
		new Opcode(0xCE, "DEC", abso, dec, 6),
		new Opcode(0xCF, "DCP", abso, dcp, 6),
		new Opcode(0xD0, "BNE", rel, bne, 2),
		new Opcode(0xD1, "CMP", indy, cmp, 5),
		new Opcode(0xD2, "NOP", imp, nop, 2),
		new Opcode(0xD3, "DCP", indy, dcp, 8),
		new Opcode(0xD4, "NOP", zpx, nop, 4),
		new Opcode(0xD5, "CMP", zpx, cmp, 4),
		new Opcode(0xD6, "DEC", zpx, dec, 6),
		new Opcode(0xD7, "DCP", zpx, dcp, 6),
		new Opcode(0xD8, "CLD", imp, cld, 2),
		new Opcode(0xD9, "CMP", absy, cmp, 4),
		new Opcode(0xDA, "NOP", imp, nop, 2),
		new Opcode(0xDB, "DCP", absy, dcp, 7),
		new Opcode(0xDC, "NOP", absx, nop, 4),
		new Opcode(0xDD, "CMP", absx, cmp, 4),
		new Opcode(0xDE, "DEC", absx, dec, 7),
		new Opcode(0xDF, "DCP", absx, dcp, 7),
		new Opcode(0xE0, "CPX", imm, cpx, 2),
		new Opcode(0xE1, "SBC", indx, sbc, 6),
		new Opcode(0xE2, "NOP", imm, nop, 2),
		new Opcode(0xE3, "ISB", indx, isb, 8),
		new Opcode(0xE4, "CPX", zp, cpx, 3),
		new Opcode(0xE5, "SBC", zp, sbc, 3),
		new Opcode(0xE6, "INC", zp, inc, 5),
		new Opcode(0xE7, "ISB", zp, isb, 5),
		new Opcode(0xE8, "INX", imp, inx, 2),
		new Opcode(0xE9, "SBC", imm, sbc, 2),
		new Opcode(0xEA, "NOP", imp, nop, 2),
		new Opcode(0xEB, "SBC", imm, sbc, 2),
		new Opcode(0xEC, "CPX", abso, cpx, 4),
		new Opcode(0xED, "SBC", abso, sbc, 4),
		new Opcode(0xEE, "INC", abso, inc, 6),
		new Opcode(0xEF, "ISB", abso, isb, 6),
		new Opcode(0xF0, "BEQ", rel, beq, 2),
		new Opcode(0xF1, "SBC", indy, sbc, 5),
		new Opcode(0xF2, "NOP", imp, nop, 2),
		new Opcode(0xF3, "ISB", indy, isb, 8),
		new Opcode(0xF4, "NOP", zpx, nop, 4),
		new Opcode(0xF5, "SBC", zpx, sbc, 4),
		new Opcode(0xF6, "INC", zpx, inc, 6),
		new Opcode(0xF7, "ISB", zpx, isb, 6),
		new Opcode(0xF8, "SED", imp, sed, 2),
		new Opcode(0xF9, "SBC", absy, sbc, 4),
		new Opcode(0xFA, "NOP", imp, nop, 2),
		new Opcode(0xFB, "ISB", absy, isb, 7),
		new Opcode(0xFC, "NOP", absx, nop, 4),
		new Opcode(0xFD, "SBC", absx, sbc, 4),
		new Opcode(0xFE, "INC", absx, inc, 7),
		new Opcode(0xFF, "ISB", absx, isb, 7),
	};
}
