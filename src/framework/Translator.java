package framework;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import framework.AbstractGate.GateType;
import framework.DefaultGate.LangType;
import mathLib.Complex;
import mathLib.Matrix;

public class Translator {

    static String code = "";
    static int offset = 0;

    public static String exportQUIL() {
        CircuitBoard cb = Main.getWindow().getSelectedBoard();
        offset = cb.getRows();
        ArrayList<String> customGates = new ArrayList<>();
        ExportedGate.exportGates(cb, new ExportGatesRunnable() {
            @Override
            public void gateExported(ExportedGate eg, int x, int y) {
//                SolderedGate.Control[] controls = eg.getControls();
                AbstractGate ag = eg.getAbstractGate();
                Matrix<Complex> m = ag.getMatrix();
                String name = ag.getName();
//                if(isControlled(controls)) {
//                    m = SolderedGate.makeControlledMatrix(m,controls);
//                    name = controls.hashCode() + name;
//                }
//                for(int i = 0; i < y; ++i) {
//                    System.out.println(controls[i]);
//                }
                GateType gt = ag.getType();
                boolean id = ag.getName().equals("I");

                if(!gt.equals(GateType.OTHER) && !id) {
                    name = DefaultGate.typeToString(gt, DefaultGate.LangType.QUIL);
                    code += name + " " + y;
                    if(gt.equals(GateType.CNOT) || gt.equals(GateType.SWAP)) {
                        code += " " + eg.getHeight();
                    }
                } else if(!id){
                    if(!customGates.contains(name)) {
                        customGates.add(name);
                        code += "DEFGATE " + name + ":\n";
                        for (int my = 0; my < m.getRows(); ++my) {
                            code += "\n    ";
                            for (int mx = 0; mx < m.getRows(); ++mx) { //This copies down the matrix into the code
                                code += m.v(mx, my).toString();
                                if (mx + 1 < m.getRows())
                                    code += ", ";
                            }
                        }
                        code += "\n";
                    }
                    code += name;
                    for(int i = 0; i < eg.getRegisters().length; ++i) {
                        code += " " + (eg.getRegisters()[i]);
                    }
                }
                code += id ? "" : "\n";
            }

            @Override
            public void nextColumnEvent(int column) {
                int i = offset;
                try {
                    for (i = 0; cb.getSolderedRegister(column, i).getSolderedGate().getAbstractGate().getName().equals("I"); ++i);
                } catch(IndexOutOfBoundsException e) {}
                offset = Math.min(offset,i);
            }

			@Override
			public void columnEndEvent(int column) {}
        });
        String temp = code;
        code = "";
        return fixQUIL(temp,offset);
    }

    public static String exportQASM() {
        CircuitBoard cb = Main.getWindow().getSelectedBoard();
        offset = cb.getRows();
        ArrayList<String> customGates = new ArrayList<>();
        code += "OPENQASM 2.0;\ninclude \"qelib1.inc\";\nqreg q[" + "FORMATHERE" + "];\ncreg c[" + "FORMATHERE" + "];\n";
        ExportedGate.exportGates(cb, new ExportGatesRunnable() {
            @Override
            public void gateExported(ExportedGate eg, int x, int y) {
                String name = eg.getAbstractGate().getName();
                if(!name.equals("I")) {
                    switch(name) {
                        case "MEASURE":
                            code += "measure q[" + y + "] -> c[" + y + "];\n";
                            break;
                        case "CNOT":
                            code += "cx q[" + y + "],q[" + (y+eg.getHeight()) + "];\n";
                            break;
                        case "SWAP":
                            if(eg.getHeight() > 0) {
                                code += "cx q[" + y + "],q[" + (y + eg.getHeight()) + "];\n";
                                code += "cx q[" + (y + eg.getHeight()) + "],q[" + y + "];\n";
                                code += "cx q[" + y + "],q[" + (y + eg.getHeight()) + "];\n";
                            } else {
                                code += "cx q[" + (y + eg.getHeight()) + "],q[" + y + "];\n";
                                code += "cx q[" + y + "],q[" + (y + eg.getHeight()) + "];\n";
                                code += "cx q[" + (y + eg.getHeight()) + "],q[" + y + "];\n";
                            }
                            break;
                        default:
                            code += DefaultGate.typeToString(eg.getAbstractGate().getType(),LangType.QASM) + " q[" + y + "];\n";
                    }
                }
            }
            @Override
            public void nextColumnEvent(int column) {
                int i = offset;
                try {
                    for (i = 0; cb.getSolderedRegister(column, i).getSolderedGate().getAbstractGate().getName().equals("I"); ++i);
                } catch(IndexOutOfBoundsException e) {}
                offset = Math.min(offset,i);
            }
			@Override
			public void columnEndEvent(int column) {}
        });
        String temp = fixQASM(code,offset).replace("FORMATHERE",""+(cb.getRows()-offset));
        code = "";
        for(int i = 0; i < cb.getRows()-offset; ++i) {
            temp += "measure q[" + i + "] -> c[" + i + "];\n";
        }
        return temp;
    }

    private static String fixQUIL(String code, int offset) { //Subtract offset from all registers
        String[] lines = code.split("\n");
        String output = "";
        for(String line : code.split("\n")){
            //Subtract offset from each number
            String[] components = line.split(" ");
            if(components[0].equals("MEASURE")){
                output += "MEASURE " + (Integer.parseInt(components[1])-offset) + " [" + (Integer.parseInt(components[2].substring(1,2))-offset) + "]";
            } else if(components[0].startsWith("DEFGATE")){
                output += line;
            } else if(isNumber(line.trim())){
                output += line;
            } else {
                output += components[0];
                for(int i = 1; i < components.length; ++i) {
                    if(!components[i].equals(""))
                        output += " " + (Integer.parseInt(components[i])-offset);
                }
            }
            output += "\n";

        }
        return output;
    }

    private static String fixQASM(String code, int offset) { //Subtract offset from all registers
        String[] lines = code.split("\n");
        String output = "";
        for(int i = 0; i < 4; ++i) {
            output += lines[i] + "\n";
        }
        for(String line : Arrays.copyOfRange(lines,4,lines.length)) {
            String[] components = line.split("\\[");
            int idx;
            if(components.length >= 2) {
                output += components[0] + "[";
                idx = Integer.parseInt(components[1].substring(0,components[1].indexOf("]")));
                output += idx-offset;
                output += components[1].substring(components[1].indexOf("]"));
            }
            if(components.length == 3) {
                idx = Integer.parseInt(components[2].substring(0,components[2].indexOf("]")));
                output += "[" + (idx-offset);
                output += components[2].substring(components[2].indexOf("]"));
            }
            output+="\n";
        }
        return output;
    }

    /**
     *
     * @param quil A string containing quil code
     * @return A double arraylist of gates representing a circuit
     */
    public static ArrayList<ArrayList<SolderedRegister>> parseQuil(String quil) { //Parses quil into a circuit diagram
        ArrayList<ArrayList<SolderedRegister>> board = new ArrayList<>();
        int maxLen = 0;
        for(String line : quil.split("\n")) {
            String gate = line.split(" ")[0];
            AbstractGate g = DefaultGate.DEFAULT_GATES.get(gate);
            int register = Integer.parseInt(line.split(" ")[1]);
            while(board.size()-1 < register) {
                board.add(new ArrayList<>());
            }
            if(gate.equals("CNOT") || gate.equals("MEASURE")){
                String otherBit = line.split(" ")[2];
                if(!otherBit.contains("[")){
                    int target = Integer.parseInt(otherBit);
                    while(board.size()-1 < target) {
                        board.add(new ArrayList<>());
                    }
                    SolderedGate sg = new SolderedGate(g, 0, 1);    // from Max to Josh: I added two values to SolderedGate 
                    												// (look in the documentation to see what the extra parameters 
                    												// mean in SolderedRegister and SolderedGate)
                    board.get(register).add(new SolderedRegister(sg,0));
                    board.get(target).add(new SolderedRegister(sg,1));
                }
            }
            board.get(register).add(new SolderedRegister(new SolderedGate(g, 0, 0),0));  // from Max to Josh: added extra parameters here as well
            if(board.get(register).size() > maxLen){
                maxLen = board.get(register).size();
            }
        }
        //Fill
        for(ArrayList<SolderedRegister> a : board) {
            while(a.size() < maxLen) {
                a.add(SolderedRegister.identity());
            }
        }
        //Transpose
        ArrayList<ArrayList<SolderedRegister>> transpose = new ArrayList<>();
        for(int y = 0; y < board.get(0).size(); ++y) {
            ArrayList<SolderedRegister> col = new ArrayList<>();
            for(ArrayList<SolderedRegister> row : board) {
                col.add(row.get(y));
            }
            transpose.add(col);
        }
        return transpose;
    }

    public static ArrayList<ArrayList<SolderedRegister>> loadProgram(LangType lt, String filepath) {
        ArrayList<ArrayList<SolderedRegister>> gates = null;
        String code = "";
        try {
            FileReader fr = new FileReader(new File(filepath));
            BufferedReader br = new BufferedReader(fr);
            code = br.lines().reduce("",(a,c) -> a+"\n"+c);
        } catch (IOException e) {
            System.err.println("ERROR LOADING FILE");
            e.printStackTrace();
        }
        switch(lt){
            case QUIL:
                gates = parseQuil(code);
                break;
            case QASM:
                String unqasm = translateQASMToQuil(code);
                gates = parseQuil(unqasm);
                break;
            case QUIPPER:
                String unquipper = translateQuipperToQuil(code);
                gates = parseQuil(unquipper);
                break;
        }
        return gates;
    }

    /**
     * Outputs quipper ASCII
     * @return Quipper ASCII representing the main circuit
     */
/*
    public static String translateQuipper(){
        String code = "Inputs: None\n";
        ArrayList<ArrayList<DefaultGate>> board = Main.cb.board;
        int numQubits = getQubits(board);
        for(int i = 0; i < numQubits; ++i) {
            code += "QInit0(" + i + ")\n";
        }
        int offset = 20;
        for(int x = 0; x < board.size(); ++x) {
            ArrayList<DefaultGate> instructions = board.get(x);
            for(int y = 0; y < instructions.size(); y++) {
                DefaultGate g = instructions.get(y);
                GateType type = g.getType();
                if(type != GateType.I) {
                    int idx = y;
                    if(idx < offset) offset = idx;
                    code += DefaultGate.typeToString(type,DefaultGate.LangType.QUIPPER);
                    code += "(" + idx + ")";
                    if(type == GateType.CNOT || type == GateType.SWAP) {
                        code += " with controls=[+" + (idx+g.length) + "]";
                    }
                    if(type == GateType.SWAP){
                        code += "\nQGate[\"not\"](" + (idx + g.length) + ") with controls=[+" + idx + "]\n";
                        code += "QGate[\"not\"](" + idx + ") with controls=[+" + (idx + g.length) + "]";
                        //Three CNOTs do a swap
                    }
                    code += "\n";
                }
            }
        }
        for(int i = 0; i < numQubits; ++i) {
            code += "QMeas(" + i + ")\n";
        }
        code += "Outputs: ";
        for(int i = 0; i < numQubits; ++i) {
            code += i + ":Cbit, ";
        }
        code = code.substring(0,code.length()-2);
        return fixQuipper(code,offset);
    }

    private static String fixQuipper(String code, int offset) {
        String newCode = "";
        for(String line : code.split("\n")) {
            if(line.startsWith("QGate")) {
                newCode += line.substring(0,line.indexOf("("));
                int num = Integer.parseInt(line.substring(line.indexOf("(")+1,line.indexOf(")")));
                newCode += "(" + (num-offset) + ")";
                if(line.contains("with controls")) {
                    newCode += " with controls=[+";
                    num = Integer.parseInt(line.substring(line.indexOf("+")+1,line.substring(16).indexOf("]")+16));
                    newCode += (num-offset) + "]";
                }
            } else {
                newCode += line;
            }
            newCode += "\n";
        }
        return newCode;
    }
*/
    /**
     *
     * @param s String possibly being a complex number
     * @return true if it is a complex number
     */
    private static boolean isNumber(String s){
        String[] ss = s.split(",");
        try{
            Complex.parseComplex(ss[0]);
            return true;
        } catch (NumberFormatException nfe) {
            return false;
        }
    }

    public static String translateQuipperToQuil(String quipper) {
        String quil = "";
        String[] tempLines = quipper.split("\n");
        ArrayList<String> quipperLines = new ArrayList<>();
        Collections.addAll(quipperLines,tempLines); //Make list of  lines into arraylist
        quipperLines.remove(0); //Pop off "Inputs: None"
        String line = quipperLines.remove(0); //Grab first line, should be a QInit
        while(line.startsWith("QInit")) {
            line = quipperLines.remove(0); //This is Quil, we do not need to compute the number of qubits
        }
        //At this point, line contains the first actual line of code
        while(!line.startsWith("Outputs:")) {
            if(line.startsWith("QMeas")){
                String num = line.substring(6,line.length()-1);
                quil += "MEASURE " + num + " [" + num +"]\n";
            } else {
                String gateName = line.substring(7); //Cut off QGate["
                gateName = gateName.substring(0, gateName.indexOf("\"")); //Now it is the actual gate name
                String idx = line.substring(line.indexOf("(")+1, line.indexOf(")")); //Target register
                switch (gateName) {
                    case "not":
                        if (line.contains("with controls")) {
                            String controlIdx = line.substring(line.indexOf("+")+1, line.length() - 1); //Control register
                            quil += "CNOT " + idx + " " + controlIdx + "\n";
                        } else {
                            quil += "X " + idx + "\n";
                        }
                        break;
                    default:
                        quil += gateName + " " + idx + "\n"; //hits H,Z,Y gates
                }
            }
            line = quipperLines.remove(0);
        }
        return quil;
    }

    public static String translateQASMToQuil(String qasm) {
        String quil = "";
        String[] tempLines = qasm.split("\n");
        ArrayList<String> qasmLines = new ArrayList<>();
        Collections.addAll(qasmLines,tempLines); //Make list of lines into arraylist
        if(qasmLines.get(0).equals("")){
            qasmLines.remove(0);
        }
        qasmLines.remove(0);
        qasmLines.remove(0);
        qasmLines.remove(0);
        qasmLines.remove(0); //Clear out header lines
        while(qasmLines.size() > 0) {
            String line = qasmLines.remove(0);
            String idx = line.substring(line.indexOf("[")+1,line.indexOf("]"));
            String gateName = line.substring(0,line.indexOf(" "));
            switch(gateName) {
                case "cx":
                    String target = line.substring(line.indexOf(">")+4,line.length()-2);
                    quil += "CNOT " + idx + " " + target;
                    break;
                case "measure":
                    quil += "MEASURE " + idx + " [" + idx + "]";
                    break;
                default:
                    quil += gateName.toUpperCase() + " " + idx + "\n";
            }
        }
        return quil;
    }

//    public static boolean isControlled(SolderedGate.Control[] controls) {
//        for(SolderedGate.Control c : controls) {
//            if(c != null) {
//                return true;
//            }
//        }
//        return false;
//    }
}
