package appFX.framework;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Random;
import java.util.stream.Stream;

import appFX.framework.exportGates.*;
import appFX.framework.exportGates.ExportedGate;
import appFX.framework.exportGates.GateManager;
import appFX.framework.gateModels.CircuitBoardModel;
import appFX.framework.gateModels.QuantumGateDefinition.QuantumGateType;
import mathLib.Complex;
import mathLib.Matrix;
import mathLib.Vector;

public class Executor {

    /**
     * Executes quil code by writing to a QVM instance and collecting the output
     * @param quil The quil code to be executed.
     * @return The output of the QVM when given the quil code
     */
    static String execute(String quil) {
        //runs qvm -e
        String output = "";
        try {
            Process p = Runtime.getRuntime().exec("qvm -e");
            BufferedReader isr = new BufferedReader(new InputStreamReader(p.getInputStream()));
            p.getOutputStream().write(quil.getBytes());
            p.getOutputStream().close();


            output = isr.lines().map(l -> l + "\n").reduce(String::concat).get();
            System.out.println("Printing output:");
            System.out.println(output);
            System.out.println("Done printing output");
        } catch (IOException e) {
            e.printStackTrace();
        }

        return output;
    }



    /**
     * Executes the current project without relying on any external dependencies such as the QVM or a python install.
     * @param p The project to execute
     * @return A string of the resulting wavefunction after execution
     */
    static String executeInternal(Project p) {
    	boolean debugShow = true;
    	
        Stream<ExportedGate> exps = null;
        try {
            exps = GateManager.exportGates(p);
            System.out.println("Gate stream created = " + exps);
        } catch (GateManager.ExportException e) {
        	e.showExportErrorSource();
            return "";
        }
        
        CircuitBoardModel cb = (CircuitBoardModel) p.getGateModel(p.getTopLevelCircuitLocationString());
        int colHeight = cb.getNumberOfRegisters();
        //System.out.println("Column height = " + colHeight);
        
        int columnIndex = 0;
        ArrayList<Matrix<Complex>> columns = new ArrayList<>();
        addCols: for(Iterator<ExportedGate> itr = exps.iterator(); itr.hasNext();) {
            ArrayList<ExportedGate> column = new ArrayList<>();
            int i = 0;
            while (i < colHeight) {
                /*if(!itr.hasNext()) {
                	System.out.println("itr has no next");
                	break addCols;
                }*/
            	if (itr.hasNext()) {
            		ExportedGate eg = itr.next();
            		if(eg.getQuantumGateType().equals(QuantumGateType.KRAUS_OPERATORS)) {
            			System.out.println("(WARNING) MIXED-SUPPORT: mixed");
            			return executeMixedState(p);
            		}
            		i += eg.getGateRegister().length;
            		column.add(eg);
            	}
            	else break;
            }
            if (debugShow) { System.out.println("executeInternal(): buildColumnMatrix: " + columnIndex++); }
            
            Matrix<Complex> columnMatrix = buildColumnMatrix(column, colHeight);
            
            //System.out.println("exectureInternal(): columnMatrix = ");
            //System.out.println(columnMatrix.toString());
            
            columns.add(columnMatrix);
        } //Columns built
        
        Matrix<Complex> in = getInVector(colHeight);
        System.out.println("executeInternal(): Beginning input vector multiplication");
        for(Matrix<Complex> m : columns) {
        	if (debugShow) {
        		System.out.println("m = "); System.out.println(m);
        		System.out.println("m.columns = " + m.getColumns());
        		System.out.println("in = "); System.out.println(in);
        	}
            in = m.mult(in);
        }
        Matrix<Complex> finalOutput = in;
        if (debugShow) { System.out.println("out = "); System.out.println(finalOutput); }
        
        return finalOutput.toString();
    }

    static Matrix<Complex> getInVector(int numregs) {
        Matrix<Complex> input = new Matrix<Complex>(Complex.ZERO(),1<<numregs,1);
        input.r(Complex.ONE(),0,0);
        for(int i = 1; i < numregs; ++i) {
            input.r(Complex.ZERO(),i,0);
        }
        System.out.println("Input vector: ");
        System.out.println(input);
        return input;
    }

    /**
     * Assumes a pure-quantum state and builds the resulting matrix for a column of gates in the circuit
     * @param column An ArrayList of exportables representing a column in the circuit
     * @param colheight The number of registers (wires) associated with this column
     * @return The matrix (tensor product of all gates and identities for unuses wires) for the column
     */
   static Matrix<Complex> buildColumnMatrix(ArrayList<ExportedGate> column, int colheight) {
	   
	   boolean debugShow = true;
	   
	   // CTT: constant identity 2x2
	   Matrix<Complex> PauliI = Matrix.identity(Complex.ZERO(), 2);
	   
	   // CTT: Pauli-X from scratch.
	   Matrix<Complex> PauliX = Matrix.identity(Complex.ZERO(), 2);
	   PauliX.r(Complex.ZERO(),0,0);
	   PauliX.r(Complex.ONE(),0,1);
	   PauliX.r(Complex.ONE(),1,0);
	   PauliX.r(Complex.ZERO(),1,1);
	   
       // ASSUMPTION I: NO OVERLAPPING CIRCUIT COMPONENTS
       //  perhaps place swap gates to ensure this automatically?
	   // CTT: ASSUMPTION II: GATE INPUT REGISTERS ARE NOT PERMUTED (SHUFFLED)
	   // CTT: ASSUMPTION III: CONTROL REGISTERS APPEAR CONTIGUOUSLY
	   // CTT: ASSUMPTION IV: CONTROL REGISTERS APPEAR IMMEDIATELY BEFORE THE GATE (STANDARD FORM)
	   // CTT: ASSUMPTION V: CONTROL REGISTERS APPEAR IN SORTED ASCENDING ORDER.
	   
	   if (debugShow) { System.out.println("buildColumnMatrix(): colheight = " + colheight); }
	   
       Matrix<Complex> mat = null;		// matrix for entire column
       Matrix<Complex> colmat = null;	// matrix related to current gate (and its controls)
       Matrix<Complex> swapBuffer = Matrix.identity(Complex.ZERO(),1<<colheight);
       
       int itr = 0; //itr is the gate we are processing, i is the register we are processing
       for (int i = 0; i < colheight; itr++) {
    	   if (debugShow) { System.out.println("[itr=" + itr + "; i=" + i + "]"); }
    	   
    	   if (itr >= column.size()) {
    		   	break;
    	   }
    	   
           ExportedGate eg = column.get(itr);	// get current quantum gate
           colmat = eg.getInputMatrixes()[0];	// get its matrix
           
           if (debugShow) { System.out.println("Current colmat = "); System.out.println(colmat.toString()); } 

       	   int minControlIndex = -1;	// where is the first control register (if any).
       	   int minRegIndex = -1; 		// where is the first register for the gate.
       	   int maxRegIndex = -1;		// where is the last register for the gate.
       	   int span = -1;				// the number of registers involved in the (controlled or not) gate.
       	   int startIndex = -1;			// where is first register for the (controlled or not) gate.
       	   								//   this is the minimum of minControlIndex and minRegIndex.
       	   
    	   // determine the registers that the gate depends on (by Assumption I, this is disjoint from neighboring gates)
       	   int numInputs = eg.getGateRegister().length;
    	   minRegIndex = getMinElement(eg.getGateRegister());
    	   maxRegIndex = getMaxElement(eg.getGateRegister());           
    	   
           // check if gate is controlled    	   
           if (eg.getQuantumControls().length > 0) {
       
        	   // base case value for truthAdjuster: constant 1
           	   Matrix<Complex> truthAdjuster = Matrix.identity(Complex.ZERO(), 1);
        	   
        	   // CTT: truthAdjuster is created to reduce to *STANDARD* controls (FALSE-based).
        	   int prevIndex, currIndex;
        	   Control[] myControls = eg.getQuantumControls();
        	   int numControls = myControls.length;
        	   minControlIndex = colheight;	// artificial max
        	   
        	   if (debugShow) { System.out.print("Controls=["); }
        	   // CTT: Assume control registers are sorted but not contiguous.
        	   prevIndex = -1;
        	   for (int j=0; j<numControls; j++) {
        		   currIndex = myControls[j].getRegister();
        		   if (debugShow) { System.out.print(currIndex + ":"); }
        		   // fill gaps between prevIndex and currIndex with identities
        		   /*if (prevIndex > -1) {
        			   for (int k=prevIndex; k<currIndex; k++) {
        				   System.out.println("STRETCHING TRUTH ADJUSTER");
        				   truthAdjuster = truthAdjuster.kronecker(PauliI);
        			   }
        		   }*/
        		   if (myControls[j].getControlStatus()) {
        			   truthAdjuster = truthAdjuster.kronecker(PauliX);
        			   if (debugShow) { System.out.print("T"); }
        		   }
        		   else {
        			   truthAdjuster = truthAdjuster.kronecker(PauliI);
        			   if (debugShow) { System.out.print("F"); }
        		   }
        		   if (currIndex < minControlIndex) { 
        			   // CTT: useful only if myControls are not sorted.
        			   minControlIndex = currIndex;
        		   }
        		   if (debugShow) { System.out.print("|"); }
        		   prevIndex = currIndex;
        	   }
        	   if (debugShow) { System.out.println("]"); }
        	   
        	   truthAdjuster = truthAdjuster.kronecker(Matrix.identity(Complex.ZERO(), 1<<numInputs));
        	   if (debugShow) { System.out.println("truthAdjuster = "); System.out.println(truthAdjuster.toString()); }
        	   
        	   // CTT: Compute nonstandard controlled gate (FALSE based).
        	   // CTT: This is a direct sum of identity (of appropriate size) and colmat (or reversed).
        	   if (debugShow) { System.out.print("numControls[" + numControls); System.out.println("] numInputs[" + numInputs + "]"); }
        	   
        	   Matrix<Complex> directSum = Matrix.identity(Complex.ZERO(), 1<<(numControls+numInputs));
        	   directSum = directSum.setSlice(0, colmat.getRows()-1, 0, colmat.getColumns()-1, colmat);
        	   colmat = directSum;
        	   
        	   //if (debugShow) { System.out.println("colmat = "); System.out.println(colmat.toString()); }
        	   
        	   // conjugate with truthAdjuster to account for Controls on FALSE.
        	   colmat = truthAdjuster.mult(colmat.mult(truthAdjuster));
        	   
        	   //if (debugShow) { System.out.println("truthAdjusted colmat = "); System.out.println(colmat.toString()); }
        	   
        	   startIndex = minControlIndex;
        	   span = 1 + maxRegIndex - minControlIndex;
           }
           else { // CTT: case of uncontrolled gate          
        	   startIndex = minRegIndex;
        	   span = 1 + maxRegIndex - minRegIndex;
           }
           
           if (debugShow) {
        	   System.out.print("span[" + span);
        	   System.out.print("] minRegIndex[" + minRegIndex);
        	   System.out.print("] maxRegIndex[" + maxRegIndex);
        	   System.out.print("] minControlIndex[" + minControlIndex);
        	   System.out.println("] range{" + Arrays.toString(eg.getGateRegister()) + "}");
           }
           
           if (i < startIndex) { // need to pad by tensoring with identities
        	   if (mat == null) { // this is the first gate in this column
        		   mat = Matrix.identity(Complex.ZERO(), 1 << (startIndex-i));
        	   }
        	   else { // there were previous gates in this column
        		   mat = mat.kronecker(Matrix.identity(Complex.ZERO(), 1 << (startIndex-i)));
        	   }
           }
           
           // advance index to first register used by the gate
           i = startIndex;
           

           // CTT: No shuffling supported for now.
           //Shuffle eg to be contiguous and in order, then pad with identity
           /*
           So if there is a gate with registers
           3
           1
           -
           2
           It becomes
           1
           2
           3
           -
           With a swap buffer
            */
           boolean allowShuffle = false;
           if (allowShuffle && eg.getGateRegister().length != 1) { 
        	   /* CTT: is this checking for strictly greater than 1 or just not 1? */
               // DEBUG suppress for now: swapBuffer = swapBuffer.mult(getSwapMat(eg.getGateRegister(),colheight));
               System.out.println("buildColumnMatrix: swapBuffer is of size " + swapBuffer.getRows());
               System.out.println(swapBuffer);
               Matrix<Complex> adjustedColmat = colmat.kronecker(Matrix.identity(Complex.ZERO(),1<<(span-eg.getGateRegister().length)));
               System.out.println("buildColumnMatrix: Adjusted colmat is of size " + adjustedColmat.getRows());
               System.out.println(adjustedColmat);
               // DEBUG suppress for now: colmat = adjustedColmat;
           }
           
           
           if(mat == null) {
        	   // this is the first quantum gate acting on first register
               mat = colmat;
           } else {
        	   // there were previous gates or identities on unaffected registers
               mat = mat.kronecker(colmat);
           }
           
           // CTT: advance to the start of the next possible gate.
           i += span;
           
           // CTT: unclear
           // The following code seems redundant as itr is increment in the FOR loop.
           // itr += span-eg.getGateRegister().length;
       }
       
       if (debugShow) { System.out.println("buildColumnMatrix(): return = "); System.out.println(mat.toString()); }
       
       return swapBuffer.mult(mat).mult(swapBuffer.transpose());
   }
   

   private static int getMaxElement(int[] arr) {
	   if(arr.length==0) {
		   return -1;
	   }
	   int max = arr[0];
	   for(int i = 1; i < arr.length; ++i) {
		   if(max < arr[i]) {
			   max = arr[i];
		   }
	   }
	   return max;
   }
   private static int getMinElement(int[] arr) {
	   if(arr.length==0){
		   return -1;
	   }
	   int min = arr[0];
	   for(int i = 1; i < arr.length; ++i) {
		   if(min > arr[i]) {
			   min = arr[i];
		   }
	   }
	   return min;
   }


    private static Matrix<Complex> getSwapMat(int[] regs, int columnHeight) {
        int len = regs.length;
        /* CTT: swapMat is defined by never used? */
        Matrix<Complex> swapMat = Matrix.identity(Complex.ZERO(),4);
        swapMat.r(Complex.ZERO(),1,1);
        swapMat.r(Complex.ZERO(),2,2);
        swapMat.r(Complex.ONE(),2,1);
        swapMat.r(Complex.ONE(),1,2);
        //System.out.println("Swap Matrix: " + swapMat.toString());
        Matrix<Complex> buffer = Matrix.identity(Complex.ZERO(),1<<columnHeight);
        /*
        Algorithm: Build swap buffer by bubble-sort like process
        Bring 1st register to top, then second register to second place, etc.
         */
        for(int i = 0; i < len; ++i) {
           int ri = regs[i];
           Matrix<Complex> sc = farSwap(i,ri,columnHeight);
           buffer = buffer.mult(sc);
        }
        return buffer;
    }




    private static Matrix<Complex> farSwap(int p1, int p2, int columnHeight) {
        Matrix<Complex> swapMat = Matrix.identity(Complex.ZERO(),4);
        swapMat.r(Complex.ZERO(),1,1);
        swapMat.r(Complex.ZERO(),2,2);
        swapMat.r(Complex.ONE(),2,1);
        swapMat.r(Complex.ONE(),1,2);

        /* CTT: farSwap is the function name and the returned matrix itself? */
        Matrix<Complex> farSwap = Matrix.identity(Complex.ZERO(),1<<columnHeight);
        if(p1 == p2) {
        	System.out.println("farSwap[p1==p2] = " + farSwap);
            return farSwap;
        }
        if(p1 < p2) {
            for(int i = 0; i < p2-p1; ++i) {
                Matrix<Complex> nextLink = identityPad(swapMat,p1+i,columnHeight);
                farSwap = farSwap.mult(nextLink);
            }
            for(int i = 1; i < p2-p1; ++i) {
                Matrix<Complex> nextLink = identityPad(swapMat,p2-i,columnHeight);
                farSwap = farSwap.mult(nextLink);
            }
            return farSwap;
        } else {
             return farSwap(p2,p1,columnHeight);
        }
    }

    /**
     * Takes a gate and returns the matrix for a column consisting of only that gate
     * A call to identityPad with SWAP at 1 and a size of 4 should return
     * ID
     * SWAP1
     * SWAP2
     * ID
     * @param gate The matrix of the gate to be used in the column
     * @param position Where the gate should be in the column; zero indexed
     * @param columnHeight The size of the column
     * @return A matrix representing a column of gates containing identity and the one gate given to the function
     */
    private static Matrix<Complex> identityPad(Matrix<Complex> gate, int position, int columnHeight) {
        int numberOfQubits = 0;
        while(gate.getRows()>>++numberOfQubits > 1);
        Matrix<Complex> column = Matrix.identity(Complex.ZERO(),1);
        for(int i = 0; i < position; ++i) {
            column = column.kronecker(Matrix.identity(Complex.ZERO(),2));
        }
        column = column.kronecker(gate);
        for(int i = position+numberOfQubits; i < columnHeight; ++i) {
            column = column.kronecker(Matrix.identity(Complex.ZERO(),2));
        }
        return column;
    }

    public static String executeMixedState(Project p) {
        CircuitBoardModel cb = (CircuitBoardModel) p.getGateModel(p.getTopLevelCircuitLocationString());
        int colHeight = cb.getNumberOfRegisters();
        System.out.println(colHeight);

        //A state |x> is now |x><x|, and applying an operator A|x> is A|x><x|A*
        //Be sure to normalize by dividing by Tr(A |x><x| A*)

        Vector<Complex> zero = new Vector<Complex>(Complex.ONE(),Complex.ZERO());
        Vector<Complex> inputTemp = zero;
        for(int i = 1; i < colHeight; ++i) {
            inputTemp = inputTemp.kronecker(zero).toVector();
        }
        Matrix<Complex> input = inputTemp.outerProduct(inputTemp);

        System.out.println(input);

        //(A tensor B)* = A* tensor B*
        //Therefore (A tensor B)|x> becomes (A tensor B)|x><x|(A* tensor B*)

        ArrayList<Matrix<Complex>> columns = new ArrayList<>();
        //The strategy remains similar. We will build a column, compute the new state, and loop doing that until we're out of circuit
        //Let's construct the gate stream
        Stream<ExportedGate> gateStream = null;
        try {
            gateStream = GateManager.exportGates(p);
        } catch (GateManager.ExportException e) {
            e.showExportErrorSource();
            return "";
        }
        Iterator<ExportedGate> itr = gateStream.iterator();

        //System.out.println("Matrices:");
        //printArray(itr.next().getInputMatrixes());
        //System.out.println("End Matrices");

        ArrayList<ExportedGate> currentColumn = new ArrayList<>();
        //Loop to grab one column of gate
        while(itr.hasNext()) {
            int columnSpaceTakenUp = 0;
            while (columnSpaceTakenUp < colHeight) {
                ExportedGate eg = itr.next();
                int span = 1 + getMaxElement(eg.getGateRegister()) - getMinElement(eg.getGateRegister());
                columnSpaceTakenUp += span;
                currentColumn.add(eg);
            }

            printColumn(currentColumn);

            ArrayList<Matrix<Complex>> columnMatrix = buildColumnDensityMatrix(currentColumn, colHeight);
            input = applyKrausDontLook(columnMatrix,input);
            currentColumn = new ArrayList<>();
        }

        return input.toString();
    }

    private static <T> void printArray(T[] a) {
        for(T t : a) {
            System.out.println(t);
        }
    }

    private static void printColumn(ArrayList<ExportedGate> column) {
        String toPrint = "";
        for(ExportedGate eg : column) {
            if(eg.getGateModel().getName().equalsIgnoreCase("Identity")) {
                toPrint += "Identity\n";
            } else {
                toPrint += eg.getQuantumGateType().toString() + "\n";
                if(eg.getQuantumGateType().equals(QuantumGateType.KRAUS_OPERATORS))
                    printArray(eg.getInputMatrixes());
            }
        }
        System.out.println(toPrint);
    }



    /**
     * Takes a list of gates in a column, allowing any to be POVMs, and returns the list of kraus matrices defining the action of this column on the state
     * Iff none of the gates are POVMs, the function will return one matrix representing the linear map on density matrices
     * @param egs The exported gates in the column
     * @param columnHeight The size of the column in registers
     * @return The kraus matrices defining the action of this column on the state
     */
    private static ArrayList<Matrix<Complex>> buildColumnDensityMatrix(ArrayList<ExportedGate> egs, int columnHeight) {

        //Tensor Product of two Operators
        //O1 = {A1,A2,...,Ar}
        //O2 = {B1,B2,...,Br}
        //O1 tensor O2 = {Ai tensor Bj | i,j < r}

        //For now, assume all multiqubit structures are contiguous and in order

        egs = removeIdentityUnderGates(egs);

        ArrayList<Matrix<Complex>> kraus = new ArrayList<>();
        Matrix<Complex> column = Matrix.identity(Complex.ZERO(),1);
        kraus.add(column);
        while(!egs.isEmpty()) {
            ExportedGate gate = egs.remove(0);
            switch(gate.getQuantumGateType()) {
                case UNIVERSAL:
                    for(int i = 0; i < kraus.size(); ++i) {
                        kraus.set(i,kraus.get(i).kronecker(gate.getInputMatrixes()[0]));
                    }
                    break;
                case POVM:
                    ArrayList<Matrix<Complex>> newChannel = new ArrayList<>();
                    for(int i = 0; i < kraus.size(); ++i) {
                        Matrix<Complex> k = kraus.get(i);
                        for(Matrix<Complex> kprime : gate.getInputMatrixes()) {
                            newChannel.add(k.kronecker(kprime));
                        }
                        //Rip garbage collector
                    }
                    kraus = newChannel;
                    break;
            }
        }
        return kraus;
    }

    private static Matrix<Complex> applyKrausDontLook(ArrayList<Matrix<Complex>> kraus, Matrix<Complex> density) {
        Matrix<Complex> newDensity = null;
        System.out.println("Density matrix in applyKrausDontLook");
        System.out.println(density);
        for(Matrix<Complex> k : kraus) {
            System.out.println("Kraus matrix");
            System.out.println(k);
            if(newDensity == null) {
                newDensity = k.mult(density);
                Matrix<Complex> kdagger = Matrix.map(Complex.ZERO(),k,Complex::conjugate).transpose();
                newDensity = newDensity.mult(kdagger);
            } else {
                newDensity = newDensity.add(k.mult(density).mult(Matrix.map(Complex.ZERO(),k,Complex::conjugate).transpose()));
            }
        }
        return newDensity;
    }

    private static Matrix<Complex> applyKrausLook(ArrayList<Matrix<Complex>> kraus, Matrix<Complex> density) {
        double rand = (new Random()).nextDouble();
        Matrix<Complex> state = null;
        int i = 0;
        while(rand > 0) {
            Matrix<Complex> k = kraus.get(i++);
            state = k.mult(density).mult(Matrix.map(Complex.ZERO(),k,Complex::conjugate).transpose());
            rand -= state.trace().abs();
        }
        return state;
    }

    private static ArrayList<ExportedGate> removeIdentityUnderGates(ArrayList<ExportedGate> gates) {
        ArrayList<Integer> regsCovered = new ArrayList<>();
        for(ExportedGate g : gates) {
            if(g.getGateRegister().length > 1) {
                for(int i = 0; i < g.getGateRegister().length; ++i) {
                    regsCovered.add(g.getGateRegister()[i]);
                }
            }
        }
        ArrayList<ExportedGate> newGates = new ArrayList<>();
        for(ExportedGate g : gates) {
            boolean redundant = false;
            for(Integer i : regsCovered) {
                if(g.getGateModel().getName().equalsIgnoreCase("Identity") && (g.getGateRegister()[0]==i)) {
                    redundant = true;
                    break;
                }
            }
            if(!redundant) {
                newGates.add(g);
            }
        }
        return newGates;
    }

}
