package framework;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;

import appUI.CircuitBoardSelector;
import appUI.Window;
import preferences.AppPreferencesWindow;

public class Keyboard implements ActionListener, Runnable {
	
	private Thread thread;
	private String actionCommand;
	private Window window;
	
	public Keyboard(Window window) {
		this.window = window;
	}
	
	
	@Override
	public void run() {
		switch(actionCommand){


//		Gates
//        case "Hadamard":
//            Main.cb.edit(DefaultGate.GateType.H);
//            break;
//        case "I":
//            Main.cb.edit(DefaultGate.GateType.I);
//            break;
//        case "X":
//            Main.cb.edit(DefaultGate.GateType.X);
//            break;
//        case "Y":
//            Main.cb.edit(DefaultGate.GateType.Y);
//            break;
//        case "Z":
//            Main.cb.edit(DefaultGate.GateType.Z);
//            break;
//        case "Measure":
//            Main.cb.edit(DefaultGate.GateType.MEASURE);
//            break;
//        case "CNot":
//            Main.cb.edit(DefaultGate.GateType.CNOT);
//            break;
//        case "Swap":
//            Main.cb.edit(DefaultGate.GateType.SWAP);
//            break;
        case "Make Custom Gate...":
        	CustomGate.makeCustom();
            break;

//		Export Types
            
        case "PNG Image":
        	window.getFileSelector().exportPNG(window.getSelectedBoard(), null);
        	break;
        case "QUIL":
            System.out.println(Translator.translateQUIL());
            window.getConsole().println(Translator.translateQUIL());
            break;
        case "QASM":
            System.out.println(Translator.translateQASM());
            window.getConsole().println(Translator.translateQASM());
            break;
        case "Quipper":
            System.out.println(Translator.translateQuipper());
            window.getConsole().println(Translator.translateQuipper());
            break;

//      File Selections
        case "New Circuit":
        	window.setSelectedBoard(window.getFileSelector().createNewBoard(window.getSelectedBoard()));
        	break;
        case "Open Circuit":
        	window.setSelectedBoard(window.getFileSelector().selectBoardFromFileSystem(window.getSelectedBoard()));
        	break;
        case "Save Circuit as":
        	if(window.getFileSelector().saveBoardToFileSystem(window.getSelectedBoard()))
        		window.updateSelectedBoardTitle();
        	break;
        case "Save":
        	if(window.getFileSelector().saveBoard(window.getSelectedBoard()))
        		window.updateSelectedBoardTitle();
        	break;

//      Preferences
        case "Preferences":
        	AppPreferencesWindow apui = new AppPreferencesWindow(window.getFrame());
        	apui.setVisible(true);
        	break;	
        	

//    	Grid Selections
        case "Add Row":
        	window.getSelectedBoard().addRow();
        	window.getRenderContext().paintRerenderedBaseImageOnly();
        	break;
        case "Add Column":
        	window.getSelectedBoard().addColumn();
        	window.getRenderContext().paintRerenderedBaseImageOnly();
        	break;
        case "Remove Last Row":
        	window.getSelectedBoard().removeRow();
        	window.getRenderContext().paintRerenderedBaseImageOnly();
        	break;
        case "Remove Last Column":
        	window.getSelectedBoard().removeColumn();
        	window.getRenderContext().paintRerenderedBaseImageOnly();
        	break;
        case "Run QUIL":
            System.out.println("Running QUIL");
            window.getConsole().println("Running QUIL");
            String quil = Translator.translateQUIL();
            quil.trim();
            try {
                Executor.runQuil(quil);
            } catch (IOException e1) {
                System.err.println("Could not create file!");
                e1.printStackTrace();
            }
            break;
        case "Run QASM":
	        System.out.println("Running QASM");
	        window.getConsole().println("Running QASM");
	        String qasm = Translator.translateQASM();
	        qasm.trim();
	        try {
	            Executor.runQASM(qasm);
	        } catch (IOException e1) {
	            System.err.println("Could not create file!");
	            e1.printStackTrace();
	        }
	        break;
		}
	}
	
	
	@Override
	public void actionPerformed(ActionEvent e) {
		actionCommand = e.getActionCommand();
		thread = new Thread(this);
		thread.start();
	}
}
