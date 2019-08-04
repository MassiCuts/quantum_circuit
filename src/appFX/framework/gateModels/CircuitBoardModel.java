package appFX.framework.gateModels;

import java.io.Serializable;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;

import appFX.appPreferences.AppPreferences;
import appFX.framework.AppStatus;
import appFX.framework.Project;
import appFX.framework.exportGates.RawExportableGateData;
import appFX.framework.exportGates.RawExportableGateData.RawExportControl;
import appFX.framework.exportGates.RawExportableGateData.RawExportLink;
import appFX.framework.exportGates.RawExportableGateData.RawExportOutputLink;
import appFX.framework.exportGates.RawExportableGateData.RawExportRegister;
import appFX.framework.gateModels.BasicGateModel.BasicGateModelType;
import appFX.framework.solderedGates.SolderedControlPin;
import appFX.framework.solderedGates.SolderedGate;
import appFX.framework.solderedGates.SolderedPin;
import appFX.framework.solderedGates.SolderedRegister;
import appFX.framework.solderedGates.SpacePin;
import appFX.framework.solderedGates.SpacePin.OutputLinkType;
import appFX.framework.utils.Action;
import appFX.framework.utils.Action.MultipleAction;
import appFX.framework.utils.InputDefinitions;
import appFX.framework.utils.InputDefinitions.ArgDefinition;
import appFX.framework.utils.InputDefinitions.CheckDefinitionRunnable;
import appFX.framework.utils.InputDefinitions.DefinitionEvaluatorException;
import appFX.framework.utils.InputDefinitions.GroupDefinition;
import appFX.framework.utils.InputDefinitions.MatrixDefinition;
import appFX.framework.utils.InputDefinitions.ScalarDefinition;
import utils.customCollections.Manifest;
import utils.customCollections.Manifest.ManifestElementHandle;
import utils.customCollections.Pair;
import utils.customCollections.Queue;
import utils.customCollections.Stack;
import utils.customCollections.eventTracableCollections.Notifier;
import utils.customCollections.eventTracableCollections.Notifier.ReceivedEvent;

/**
 * This is a 2D grid of gates that represents a quantum protocol within design (often referred to as a sub-circuit or top-level) <br>
 * <p>
 * For a {@link CircuitBoardModel} to be used within a the application through the GUI, it must be added to <br>
 * a {@link Project} instance.
 * <p>
 * A {@link CircuitBoardModel} instance has two tiers: sub-circuit or top-level <br>
 * The top-level board is top-most 'module' of a quantum protocol. <br>
 * The top-level can be composed of other {@link CircuitBoardModel} instances; These instances are called sub-circuits.<br>
 * <p>
 * There can only be one top-level within a single {@link Project} instance, but there is no <br>
 * limit to the amount of sub-circuits within a {@link Project} instance.
 * <p>
 * 
 * For a {@link CircuitBoardModel} to be identified as a 
 * 
 * 
 * 
 * @author quantumresearch
 *
 */
public class CircuitBoardModel extends GateModel implements  Iterable<RawExportableGateData>, CheckDefinitionRunnable, AppPreferences {
	private static final long serialVersionUID = -6921131331890897905L;
	
	public static enum RowType {
		SPACE("Space"),
		CLASSICAL("Classical"),
		QUANTUM("Quantum");
		
		private final String name;
		private RowType(String name) {
			this.name = name;
		}
		
		@Override
		public String toString() {
			return name;
		}
	}
	
	private transient LinkedList<Action> undoList = null;
	private transient Stack<Action> redoStack = null;
	
	private final LinkedList<LinkedList<SolderedPin>> elements;
    private final RowTypeList rowTypes;
	
	public static final String CIRCUIT_BOARD_EXTENSION =  "cb";
	
	
    // Notifies User-Interface of changes
    private final Notifier notifier;
    private final Notifier renderNotifier;
    
    private final Manifest<String> gateModelsUsed;
    
    {
    	if(getComputingType() == GateComputingType.CLASSICAL_OR_QUANTUM)
			throw new IllegalArgumentException("A Circuit board cannot act as both a quantum operation and a classical operation");
    }
    
    private CircuitBoardModel(String locationString, String name, String symbol, String description, GateComputingType computingType, String[] arguments, CircuitBoardModel oldModel) {
    	this(locationString, name, symbol, description, computingType, arguments, oldModel.elements, oldModel.gateModelsUsed, oldModel.rowTypes);
    }
    
    private CircuitBoardModel(String locationString, String name, String symbol, String description, GateComputingType computingType, String[] arguments, LinkedList<LinkedList<SolderedPin>> elements,
    		Manifest<String> gateModelsUsed, RowTypeList rowTypes) {
    	super(locationString, name, symbol, description, computingType, arguments);
    	
    	this.elements = elements;
    	this.rowTypes = rowTypes;
    	
    	this.gateModelsUsed = gateModelsUsed;
    	
    	this.notifier = new Notifier();
    	this.renderNotifier = new Notifier();
    	
    	// adding at least one classical reg (if classical circuitboard)
    	// or adding at least one quantum reg (if quantum circuitboard>
    	// if the circuit board is changed from quantum to classical, 
    	// then all quantum rows are removed
    	if(computingType == GateComputingType.CLASSICAL) {
    		int rows = elements.get(0).size();
    		int amtClassical = rowTypes.countTypeAmtFrom(RowType.CLASSICAL, 0, rows);
    		if(amtClassical <= 0) {
    			Action addRows = addRowsAction(0, 5, RowType.CLASSICAL);
    			addRows.apply();
    		}
    		rows = elements.get(0).size();
    		LinkedList<Pair<Integer, Integer>> ranges = rowTypes.getRangesOfType(RowType.QUANTUM);
    		Iterator<Pair<Integer, Integer>> rangeIterator = ranges.descendingIterator();
    		while(rangeIterator.hasNext()) {
    			Pair<Integer, Integer> range = rangeIterator.next();
    			Action removeRows = removeRowsAction(range.first(), range.second());
    			removeRows.apply();
    		}
    	} else {
    		int rows = elements.get(0).size();
    		int amtQuantum = rowTypes.countTypeAmtFrom(RowType.QUANTUM, 0, rows);
    		if(amtQuantum <= 0) {
    			Action addRows = addRowsAction(0, 5, RowType.QUANTUM);
    			addRows.apply();
    		}
    	}
    }
    
    
    public CircuitBoardModel(String locationString, String name, String symbol, String description, GateComputingType computingType, int rows, int columns, String ... arguments) {
    	super (locationString, name, symbol, description, computingType, arguments);
    	
    	gateModelsUsed = new Manifest<>();
    	
    	if (rows < 1)
			throw new IllegalArgumentException("Rows cannot be less than 1");
		if(columns < 1)
			throw new IllegalArgumentException("Columns cannot be less than 1");
		
		this.notifier = new Notifier();
    	this.renderNotifier = new Notifier();
		this.elements = new LinkedList<>();
		this.rowTypes = new RowTypeList();
		
		RowType rowsToAdd;
		if(computingType == GateComputingType.CLASSICAL)
			rowsToAdd = RowType.CLASSICAL;
		else
			rowsToAdd = RowType.QUANTUM;
		
		
		LinkedList<SolderedPin> column;
		for(int c = 0; c < columns; c++) {
			column = new LinkedList<>();
			for(int r = 0; r < rows; r++)
				column.offerLast(mkIdent());
			elements.offerLast(column);
		}
		rowTypes.add(0, rowsToAdd, rows);
    }
    
    @Override
	public boolean isPreset() {
		return false;
	}
    
    private void checkToInitUndoRedoLists() {
    	if(undoList == null)
    		undoList = new LinkedList<>();
    	if(redoStack == null)
    		redoStack = new Stack<>();
    }
    
    private synchronized void doAction(Action action) {
    	checkToInitUndoRedoLists();
    	redoStack.clear();
    	if(undoList.size() == Integers.UNDO_REDO_HISTORY_REDO.get())
    		undoList.removeLast();
    	undoList.offerFirst(action);
    	action.apply();
    }
    
    public synchronized void undo() {
    	checkToInitUndoRedoLists();
    	if(!undoList.isEmpty()) {
        	notifier.sendChange(this, "undo");
    		Action action = undoList.pop();
    		action.undo();
    		redoStack.add(action);
        	renderNotifier.sendChange(this, "undo");
    	}
    }
    
    public synchronized void redo() {
    	checkToInitUndoRedoLists();
    	if(!redoStack.isEmpty()) {
        	notifier.sendChange(this, "redo");
    		Action action = redoStack.pop();
    		action.apply();
    		undoList.offerFirst(action);
        	renderNotifier.sendChange(this, "redo");
    	}
    }
    
    public int getRows() {
    	return elements.get(0).size();
    }
    
    public int getColumns() {
    	return elements.size();
    }
    
    public Notifier getNotifier() {
    	return notifier;
    }
    
    public Notifier getRenderNotifier() {
    	return renderNotifier;
    }
    
    public void setRenderEventHandler(ReceivedEvent event) {
    	renderNotifier.setReceivedEvent(event);
    }
    
    public int[] getGateBodyBoundsFromSpace(int rowSpace, int columnSpace) {
    	int reg = findRowOfBody(rowSpace, columnSpace);
    	return getGateBodyBounds(reg, columnSpace);
    }
    
    public SolderedGate getGateAt(int row, int column) {
		return elements.get(column).get(row).getSolderedGate();
	}
    
    public SolderedPin getSolderedPinAt(int row, int column) {
		return elements.get(column).get(row);
	}
    
    public int getOccurrences(String gateLocationString) {
    	String[] parts = gateLocationString.split("\\.");
    	
    	if(parts.length == 2)
    		return gateModelsUsed.getOccurrences(gateLocationString);
    	return 0;
    }
    
    @Override
	public Iterator<RawExportableGateData> iterator() {
		return new ExportableGateIterator(0);
	}

	@Override
	public int getNumberOfRegisters() {
		if(getComputingType().isQuantum)
			return rowTypes.countTypeAmtFrom(RowType.QUANTUM, 0, rowTypes.size());
		else
			return rowTypes.countTypeAmtFrom(RowType.CLASSICAL, 0, rowTypes.size());
	}
	
	public RowTypeList getCopyOfRowTypeList() {
		return rowTypes.deepCopy();
	}

	@Override
	public String getExtString() {
		return CIRCUIT_BOARD_EXTENSION;
	}

	@Override
	public GateModel shallowCopyToNewName(String location, String name, String symbol, String description, GateComputingType computingType,
			String... parameters) {
		return new CircuitBoardModel(location, name, symbol, description, computingType, parameters, this);
	}
	
	
	public CircuitBoardModel createDeepCopyToNewName(String locationString, String name, String symbol, String description, GateComputingType computingType, String ... parameters) {
		LinkedList<LinkedList<SolderedPin>> temp = new LinkedList<>();
		
		for(LinkedList<SolderedPin> columns : elements) {
			LinkedList<SolderedPin> tempColumn = new LinkedList<>();
			
			for(SolderedPin sp : columns)
				tempColumn.addLast(sp);
			temp.addLast(tempColumn);
		}
		
		return new CircuitBoardModel(locationString, name, symbol, description, computingType, parameters, temp, gateModelsUsed.deepCopy(), rowTypes.deepCopy());
	}
	
	
//	---------------------------------------------------------------------------------------------------------------------
    
	public synchronized void removeLinksAndControls(int row, int column, boolean removeInputLink, boolean removeControl, boolean removeOutputLink) {
		if(column < 0 || column > getColumns())
			throw new IllegalArgumentException("Column should be greater than 0 and less than circuitboard column size");
    	if(row < 0 || row > getRows())
			throw new IllegalArgumentException("Row should be greater than 0 and less than circuitboard row size");
		SolderedPin sp = getSolderedPinAt(row, column);
		if(sp instanceof SolderedRegister)
			throw new IllegalArgumentException("The chosen location to remove is a gate register and neither controls nor links reside there");
		if(removeControl && !(sp instanceof SolderedControlPin))
			throw new IllegalArgumentException("The chosen location to remove has no controls");
		SpacePin spacePin = (SpacePin) sp;
		if(removeInputLink && !spacePin.isInputLinked())
			throw new IllegalArgumentException("The chosen location to remove has no input Links");
		if(removeOutputLink && !spacePin.isOutputLinked())
			throw new IllegalArgumentException("The chosen location to remove has no output Links");
		
		notifier.sendChange(this, "removeLinksAndControls", row, column, removeControl, removeInputLink, removeOutputLink);
    	Action removeLinksAndControls = removeLinksAndControlsAction(row, column, removeControl, removeInputLink, removeOutputLink);
    	doAction(removeLinksAndControls);
    	renderNotifier.sendChange(this, "removeLinksAndControls", row, column, removeControl, removeInputLink, removeOutputLink);
	}
	
	
	public synchronized void removeAllOccurrences(String oldGateLocationString) {
		String[] parts = oldGateLocationString.split("\\.");
		
		String identityLocation = PresetGateType.IDENTITY.getModel().getLocationString();
		
		if(parts.length == 2 && !oldGateLocationString.equals(identityLocation)) {
        	notifier.sendChange(this, "removeAllOccurrences", oldGateLocationString);
        	Action removeAllOccurrences = removeAllOccurrencesAction(oldGateLocationString);
        	doAction(removeAllOccurrences);
        	renderNotifier.sendChange(this, "removeAllOccurrences", oldGateLocationString);
    	}
	}
	
	public synchronized void changeAllOccurrences(String oldGateLocationString, String newGateLocationString) {
    	String[] parts = oldGateLocationString.split("\\.");
    	
    	if(parts.length == 2 && newGateLocationString.endsWith("." + parts[1])) {
        	notifier.sendChange(this, "changeAllOccurrences", oldGateLocationString, newGateLocationString);
        	Action changeAllOccurances = changeAllOccurrencesAction(oldGateLocationString, newGateLocationString);
        	doAction(changeAllOccurances);
        	renderNotifier.sendChange(this, "changeAllOccurrences", oldGateLocationString, newGateLocationString);
    	}
    }
    
    public synchronized void placeGate(String gateModelLocationString, int column, int[] localToGlobalRegs, String ... parameters) throws DefinitionEvaluatorException {
    	Project p = AppStatus.get().getFocusedProject();
    	GateModel gm = p.getGateModel(gateModelLocationString);
    	if(gm == null)
    		throw new IllegalArgumentException("Gate: \"" + gateModelLocationString + "\"does not exist");
    	
    	GateComputingType computingType = gm.getComputingType();
    	
    	if(column < 0 || column > getColumns())
			throw new IllegalArgumentException("Column should be greater than 0 and less than circuitboard column size");
    	
    	int arbitraryGlobalReg = localToGlobalRegs[0];
    	if(arbitraryGlobalReg < 0 || arbitraryGlobalReg > getRows())
			throw new IllegalArgumentException("Gate register should be greater than 0 and less than circuitboard row size");
    	
    	RowType rowType = rowTypes.getTypeAtRow(arbitraryGlobalReg);
    	RowType chosenType;
    	if(rowType == RowType.SPACE) {
			throw new IllegalArgumentException("Register can not be placed on an empty row");
    	} else if(rowType == RowType.QUANTUM) {
    		if(!computingType.isQuantum())
    			throw new IllegalArgumentException("Gate that is not a quantum operation is placed on quantum registers");
    		chosenType = RowType.QUANTUM;
    	} else {
    		if(!computingType.isClassical())
    			throw new IllegalArgumentException("Gate that is not a classical operation is placed on classical registers");
    		chosenType = RowType.CLASSICAL;
    	}
    	
    	for(int reg : localToGlobalRegs) {
			if(reg < 0 || reg > getRows())
				throw new IllegalArgumentException("Gate register should be greater than 0 and less than circuitboard row size");
			rowType = rowTypes.getTypeAtRow(reg);
			if(rowType == RowType.SPACE)
				throw new IllegalArgumentException("Gate register can not be placed on an empty row");
			else if (rowType != chosenType) 
				throw new IllegalArgumentException("Gate is placed on both quantum registers and classical registers");
		}
    	
		GroupDefinition parameterSet = InputDefinitions.evaluateInput(this, parameters);
    	
		notifier.sendChange(this, "placeGate", gateModelLocationString, column, localToGlobalRegs, parameters);
		Action addGate = addGateAction(gateModelLocationString, column, localToGlobalRegs, parameterSet);
    	doAction(addGate);
    	renderNotifier.sendChange(this, "placeGate", gateModelLocationString, column, localToGlobalRegs, parameters);
    }
    
    
    public synchronized void removeGate(int rowSpace, int columnSpace) {
    	if(columnSpace < 0 || columnSpace > getColumns())
			throw new IllegalArgumentException("Column should be greater than 0 and less than circuitboard column size");
    	if(rowSpace < 0 || rowSpace > getRows())
			throw new IllegalArgumentException("Row should be greater than 0 and less than circuitboard row size");
    	RowType rowType = rowTypes.getTypeAtRow(rowSpace);
    	SolderedPin sp = getSolderedPinAt(rowSpace, columnSpace);
    	SolderedGate sg = sp.getSolderedGate();
    	if(rowType == RowType.SPACE && sg.isIdentity())
			throw new IllegalArgumentException("Cannot remove gate on an empty row");
    	
    	if(!sp.getSolderedGate().isIdentity()) {
    		notifier.sendChange(this, "removeGate", rowSpace, columnSpace);
    		doAction(removeEntireGateAction(rowSpace, columnSpace));
    		renderNotifier.sendChange(this, "removeGate", rowSpace, columnSpace);
    	}
    }
    
    public synchronized void placeControl(int rowControl, int rowSpace, int columnSpace, boolean controlStatus) {
    	if(columnSpace < 0 || columnSpace > getColumns())
			throw new IllegalArgumentException("Column should be greater than 0 and less than circuitboard column size");
    	if(rowControl < 0 || rowControl > getRows() || rowSpace < 0 || rowSpace > getRows())
			throw new IllegalArgumentException("Row should be greater than 0 and less than circuitboard row size");
    	RowType controlRowType = rowTypes.getTypeAtRow(rowControl);
    	if(controlRowType == RowType.SPACE)
			throw new IllegalArgumentException("Control can not be placed on an empty row");
		RowType rowType = rowTypes.getTypeAtRow(rowSpace);
    	SolderedPin sp = getSolderedPinAt(rowSpace, columnSpace);
    	SolderedGate sg = sp.getSolderedGate();
    	if(rowType == RowType.SPACE && sg.isIdentity())
			throw new IllegalArgumentException("Control can not be attached on an empty row");
    	int regRow = getArbitraryGateRegisterLocation(rowSpace, columnSpace);
    	RowType gateType = rowTypes.getTypeAtRow(regRow);
    	if(controlRowType == RowType.QUANTUM && gateType == RowType.CLASSICAL)
    		throw new IllegalArgumentException("A quantum control cannot be attached to a classical gate");
		sp = getSolderedPinAt(rowControl, columnSpace);
    	if(sp.getSolderedGate() == sg && sp instanceof SolderedRegister)
			throw new IllegalArgumentException("Control can not be attached to a register of the attaching gate");
    	String gateLocationString = sg.getGateModelLocationString();
    	GateModel gm = AppStatus.get().getFocusedProject().getGateModel(gateLocationString);
		if(gm == null)
			throw new IllegalArgumentException("Gate: \"" + gateLocationString + "\"does not exist");
		
    	notifier.sendChange(this, "placeControl", rowControl, rowSpace, columnSpace, controlStatus);
    	Action action = addControlAction(rowControl, rowSpace, columnSpace, controlStatus);
    	doAction(action);
    	renderNotifier.sendChange(this, "placeControl", rowControl, rowSpace, columnSpace, controlStatus);
    }
    
    public synchronized void removeControl(int rowControl, int columnControl) {
    	if(columnControl < 0 || columnControl > getColumns())
			throw new IllegalArgumentException("Column should be greater than 0 and less than circuitboard column size");
    	if(rowControl < 0 || rowControl > getRows())
			throw new IllegalArgumentException("Row should be greater than 0 and less than circuitboard row size");
    	
    	SolderedPin sp = getSolderedPinAt(rowControl, columnControl);
    	
    	if(sp instanceof SolderedControlPin) {
	    	notifier.sendChange(this, "removeControl", rowControl, columnControl);
	    	Action action = removeControlPinAction(rowControl, columnControl);
	    	doAction(action);
	    	renderNotifier.sendChange(this, "removeControl", rowControl, columnControl);
    	}
    	
    	throw new IllegalArgumentException("No link is at the specified location");
    }
    
    
    public synchronized void placeInputLink(int rowLink, int rowSpace, int columnSpace, int inputLinkReg) {
    	if(columnSpace < 0 || columnSpace > getColumns())
			throw new IllegalArgumentException("Column should be greater than 0 and less than circuitboard column size");
    	if(rowLink < 0 || rowLink > getRows() || rowSpace < 0 || rowSpace > getRows())
			throw new IllegalArgumentException("Row should be greater than 0 and less than circuitboard row size");
		
    	
    	RowType rowType = rowTypes.getTypeAtRow(rowLink);
    	if(rowType == RowType.SPACE)
			throw new IllegalArgumentException("Input link can not be placed on an empty row");
    	if(rowType == RowType.QUANTUM)
			throw new IllegalArgumentException("Input link can not be placed on an quantum register");
    	rowType = rowTypes.getTypeAtRow(rowSpace);
    	SolderedPin sp = getSolderedPinAt(rowSpace, columnSpace);
    	SolderedGate sg = sp.getSolderedGate();
    	if(rowType == RowType.SPACE && sg.isIdentity())
			throw new IllegalArgumentException("Input link can not be attached on an empty row");
    	int regRow = getArbitraryGateRegisterLocation(rowSpace, columnSpace);
    	RowType gateType = rowTypes.getTypeAtRow(regRow);
    	if(gateType == RowType.CLASSICAL)
    		throw new IllegalArgumentException("Input link can not be attached to an already classical gate");
		sp = getSolderedPinAt(rowLink, columnSpace);
    	if(sp.getSolderedGate() == sg && sp instanceof SolderedRegister)
			throw new IllegalArgumentException("Input link can not be attached on a register of the attaching gate");
		
		AppStatus status = AppStatus.get();
		Project project = status.getFocusedProject();
		String gateModelLocationString = sg.getGateModelLocationString();
		GateModel gm = project.getGateModel(gateModelLocationString);
		
		if(gm == null)
			throw new IllegalArgumentException("Gate Model: \"" + gateModelLocationString + "\" does not exist within the project");
		if(gm instanceof BasicGateModel)
			throw new IllegalArgumentException("Cannot place classical input registers onto non-circuitBoard gates");
		
		
		int outputReg = -1;
		OutputLinkType type = OutputLinkType.CLASSICAL_LINK;
		if(sp instanceof SpacePin && sp.getSolderedGate() == sg) {
			SpacePin spacePin = (SpacePin) sp;
			outputReg = spacePin.getInputReg();
			type = spacePin.getOutputLinkType();
		}
		
    	notifier.sendChange(this, "placeInputLink", rowLink, rowSpace, columnSpace, inputLinkReg);
    	Action action = addLinkAction(rowLink, rowSpace, columnSpace, inputLinkReg, type, outputReg);
    	doAction(action);
    	renderNotifier.sendChange(this, "placeInputLink", rowLink, rowSpace, columnSpace, inputLinkReg);
    }
    
    
    public synchronized void placeOutputLink(int rowLink, int rowSpace, int columnSpace, OutputLinkType outputLinkType, int outputLinkReg) {
    	if(columnSpace < 0 || columnSpace > getColumns())
			throw new IllegalArgumentException("Column should be greater than 0 and less than circuitboard column size");
    	if(rowLink < 0 || rowLink > getRows() || rowSpace < 0 || rowSpace > getRows())
			throw new IllegalArgumentException("Row should be greater than 0 and less than circuitboard row size");
    	RowType rowType = rowTypes.getTypeAtRow(rowLink);
    	if(rowType == RowType.SPACE)
			throw new IllegalArgumentException("Output link can not be placed on an empty row");
    	if(rowType == RowType.QUANTUM)
			throw new IllegalArgumentException("Output link can not be placed on an quantum register");
		rowType = rowTypes.getTypeAtRow(rowSpace);
    	SolderedPin sp = getSolderedPinAt(rowSpace, columnSpace);
		SolderedGate sg = sp.getSolderedGate();
    	if(rowType == RowType.SPACE && sg.isIdentity())
			throw new IllegalArgumentException("Output link can not be attached on an empty row");
    	int regRow = getArbitraryGateRegisterLocation(rowSpace, columnSpace);
    	RowType gateType = rowTypes.getTypeAtRow(regRow);
    	if(gateType == RowType.CLASSICAL)
    		throw new IllegalArgumentException("Output link can not be attached to an already classical gate");
		sp = getSolderedPinAt(rowLink, columnSpace);
    	if(sp.getSolderedGate() == sg && sp instanceof SolderedRegister)
			throw new IllegalArgumentException("Output link can not be attached on a register of the attaching gate");
    	
		AppStatus status = AppStatus.get();
		Project project = status.getFocusedProject();
		String gateModelLocationString = sg.getGateModelLocationString();
		GateModel gm = project.getGateModel(gateModelLocationString);
		
		if(gm == null)
			throw new IllegalArgumentException("Gate Model: \"" + gateModelLocationString + "\" does not exist within the project");
		if(gm instanceof BasicGateModel) {
			BasicGateModel bgm = (BasicGateModel) gm;
			if(bgm.getGateModelType() != BasicGateModelType.POVM && bgm.getGateModelType() != BasicGateModelType.KRAUS_OPERATORS)
				throw new IllegalArgumentException("Cannot place output classical registers from non-circuitboard or non-measurement gates");
		}
		
		int inputReg = -1;
		if(sp instanceof SpacePin && sp.getSolderedGate() == sg) {
			SpacePin spacePin = (SpacePin) sp;
			inputReg = spacePin.getInputReg();
		}
		
		notifier.sendChange(this, "placeOutputLink", rowLink, rowSpace, columnSpace, outputLinkType, outputLinkReg);
    	Action action = addLinkAction(rowLink, rowSpace, columnSpace, inputReg, outputLinkType, outputLinkReg);
    	doAction(action);
    	renderNotifier.sendChange(this, "placeOutputLink", rowLink, rowSpace, columnSpace, outputLinkType, outputLinkReg);
    }
    
    public synchronized void removeLink(int rowLink, int columnLink, boolean output) {
    	if(columnLink < 0 || columnLink > getColumns())
    		throw new IllegalArgumentException("Column should be greater than 0 and less than circuitboard column size");
		if(rowLink < 0 || rowLink > getRows())
			throw new IllegalArgumentException("Row should be greater than 0 and less than circuitboard row size");
    	SolderedPin sp = getSolderedPinAt(rowLink, columnLink);
    	
    	if(!(sp instanceof SolderedRegister)) {
    		SpacePin spacePin = (SpacePin) sp;
    		if(output? spacePin.isOutputLinked() : spacePin.isInputLinked()) {
	    		notifier.sendChange(this, "removeLink", rowLink, columnLink, output);
	        	Action action = removeLinkPinAction(rowLink, columnLink, output);
	        	doAction(action);
	        	renderNotifier.sendChange(this, "removeLink", rowLink, columnLink, output);
    		}
    	}
    	throw new IllegalArgumentException("No link is at the specified location");
    }
    
    public synchronized void removeRows(int rowStartInclusize, int rowEndExclusive) {
    	if(rowStartInclusize < 0 || rowStartInclusize > getRows())
			throw new IllegalArgumentException("Row should be greater than 0 and less than circuitboard row size");
    	if(rowStartInclusize >= rowEndExclusive)
    		throw new IllegalArgumentException("Row range should be greater than 0");
    	GateComputingType computingType = getComputingType();
    	RowType rowType = computingType == GateComputingType.CLASSICAL? RowType.CLASSICAL : RowType.QUANTUM;
    	int typeAmtInRange = rowTypes.countTypeAmtFrom(rowType, rowStartInclusize, rowEndExclusive);
    	int totalAmt = rowTypes.countTypeAmtFrom(rowType, 0, getRows());
    	
    	if(totalAmt - typeAmtInRange <= 0) {
    		if (computingType == GateComputingType.CLASSICAL)
    			throw new IllegalArgumentException("There should be at least one classical register");
    		else 
    			throw new IllegalArgumentException("There should be at least one quantum register");
    	}
    		
    	notifier.sendChange(this, "removeRows", rowStartInclusize, rowEndExclusive);
    	Action action = removeRowsAction(rowStartInclusize, rowEndExclusive);
    	doAction(action);
    	renderNotifier.sendChange(this, "removeRows", rowStartInclusize, rowEndExclusive);
    }
    
    public synchronized void removeColumns(int columnStartInclusize, int columnEndExclusive) {
    	if(columnStartInclusize < 0 || columnEndExclusive > getColumns())
			throw new IllegalArgumentException("Column should be greater than 0 and less than circuitboard column size");
    	if(columnStartInclusize >= columnEndExclusive)
    		throw new IllegalArgumentException("Column range should be greater than 0");
    	
    	int columnsRemoved = columnEndExclusive - columnStartInclusize;
    	
    	if(getColumns() - columnsRemoved < 1 )
    		throw new IllegalArgumentException("There should be at least one column");
    	
    	notifier.sendChange(this, "removeColumns", columnStartInclusize, columnEndExclusive);
    	Action action = removeColumnsAction(columnStartInclusize, columnEndExclusive);
    	doAction(action);
    	renderNotifier.sendChange(this, "removeColumns", columnStartInclusize, columnEndExclusive);
    }
    
    public synchronized void addRows(int rowToAdd, int amt, RowType rowType) {
    	if(rowToAdd < 0 || rowToAdd > getRows())
			throw new IllegalArgumentException("Row should be greater than 0 and less than circuitboard row size");
    	if(amt < 1)
    		throw new IllegalArgumentException("The amt of rows to add must be greater than 0");
    	GateComputingType computingType = getComputingType();
    	if(computingType == GateComputingType.CLASSICAL && rowType == RowType.QUANTUM)
    		throw new IllegalArgumentException("Cannot add quantum registers to a classical circuitboard");
    	
    	notifier.sendChange(this, "addRows", rowToAdd, amt, rowType);
    	Action action = addRowsAction(rowToAdd, amt, rowType);
    	doAction(action);
    	renderNotifier.sendChange(this, "addRows", rowToAdd, amt, rowType);
    }
    
    public synchronized void addColumns(int columnToAdd, int amt) {
    	if(columnToAdd < 0 || columnToAdd > getColumns())
			throw new IllegalArgumentException("Columns should be greater than 0 and less than circuitboard column size");
    	if(amt < 1)
    		throw new IllegalArgumentException("The amt of columns to add must be greater than 0");
    	
    	notifier.sendChange(this, "addColumns", columnToAdd, amt);
    	Action action = addColumnsAction(columnToAdd, amt);
    	doAction(action);
    	renderNotifier.sendChange(this, "addColumns", columnToAdd, amt);
    }
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
//  ---------------------------------------------------------------------------
    
    private Action removeLinksAndControlsAction(int row, int column, boolean removeControl, boolean removeInputLink, boolean removeOutputLink) {
    	Action action = new MultipleAction() {
    		@Override
    		public void initActionQueue(LinkedList<Action> actions) {
    			if(removeControl) {
    				Action removeControl = removeControlPinAction(row, column);
    				actions.add(removeControl);
    			}
    			if(removeInputLink) {
    				Action removeInputLink = removeLinkPinAction(row, column, false);
    				actions.add(removeInputLink);
    			}
    			if(removeOutputLink) {
    				Action removeOutputLink = removeLinkPinAction(row, column, true);
    				actions.add(removeOutputLink);
    			}
    		}
    	};
    	return action;
    }
    
    private Action removeAllOccurrencesAction(String oldGateLocationString) {
    	Action action = new MultipleAction() {
			@Override
			public void initActionQueue(LinkedList<Action> actions) {
				for(RawExportableGateData data : getInstance()) {
					if(data.getSolderedGate().getGateModelLocationString().equals(oldGateLocationString)) {
						Action removeGate = removeEntireGateAction(data.getGateRowBodyStart(), data.getColumn());
						actions.add(removeGate);
					}
				}
			}
		};
		return action;
    }
    
    
    private Action changeAllOccurrencesAction(String oldGateLocationString, String newGateLocationString) {
    	Action action = new Action() {
    		@Override
    		protected void applyActionFunction() {
    			gateModelsUsed.replace(oldGateLocationString, newGateLocationString);
    		}
    		@Override
    		protected void undoActionFunction() {
    			gateModelsUsed.replace(newGateLocationString, oldGateLocationString);
    		}
    	};
    	return action;
    }
    
    
    private Action removeRowsAction(int rowStartInclusize, int rowEndExclusive) {
    	Action action = new MultipleAction() {
			@Override
			public void initActionQueue(LinkedList<Action> actions) {
				for(int i = 0; i < getColumns(); i++) {
					Action clearArea = clearAreaAction(rowStartInclusize, rowEndExclusive - 1, i, false);
					actions.offerLast(clearArea);
				}
				Action removeRows = removeRowsOnClearedAreaAction(rowStartInclusize, rowEndExclusive);
				actions.offerLast(removeRows);
			}
		};
    	return action;
    }
    
    private Action removeColumnsAction(int columnStartInclusize, int columnEndExclusive) {
    	Action action  = new MultipleAction() {
			@Override
			public void initActionQueue(LinkedList<Action> actions) {
				for(int i = columnStartInclusize; i < columnEndExclusive; i++) {
					Action clearColumn = clearAreaAction(0, getRows() - 1, i, true);
					actions.offerLast(clearColumn);
				}
				Action removeColumns = removeColumnsOnClearedAreaAction(columnStartInclusize, columnEndExclusive);
				actions.offerLast(removeColumns);
			}
		};
		return action;
    }
    
    private Action removeRowsOnClearedAreaAction(int rowStartInclusize, int rowEndExclusive) {
    	Action action = new Action() {
    		final LinkedList<RowType> rowTypesRemoved;
    		{
    			rowTypesRemoved = rowTypes.getRowTypes(rowStartInclusize, rowEndExclusive);
    		}
    		
    		@Override
    		protected void applyActionFunction() {
    			ListIterator<LinkedList<SolderedPin>> iterator = elements.listIterator();
    			while(iterator.hasNext()) {
    				LinkedList<SolderedPin> column = iterator.next();
    				ListIterator<SolderedPin> rowIterator = column.listIterator(rowStartInclusize);
    				for(int i = rowStartInclusize; i < rowEndExclusive; i++) {
    					rowIterator.next();
    					rowIterator.remove();
    				}
    			}
    			rowTypes.remove(rowStartInclusize, rowEndExclusive);
    		}
    		@Override
    		protected void undoActionFunction() {
    			ListIterator<LinkedList<SolderedPin>> iterator = elements.listIterator();
    			while(iterator.hasNext()) {
    				LinkedList<SolderedPin> column = iterator.next();
    				Pair<Boolean, Boolean> isWithinBodyOrSpace = isRowInsertedWithinGateBodyOrSpace(rowStartInclusize, iterator.previousIndex());
    				
    				ListIterator<SolderedPin> rowIterator = column.listIterator(rowStartInclusize);
    				if(isWithinBodyOrSpace.first()) {
    					boolean isWithinBody = isWithinBodyOrSpace.second();
    					for(int i = rowStartInclusize; i < rowEndExclusive; i++) {
    						SolderedPin sp = getSolderedPinAt(rowStartInclusize, iterator.previousIndex());
    						SpacePin toAdd = new SpacePin(sp.getSolderedGate(), isWithinBody);
    						rowIterator.add(toAdd);
    					}
    				} else {
    					for(int i = rowStartInclusize; i < rowEndExclusive; i++)
    						rowIterator.add(mkIdent());
    				}
    			}
    			rowTypes.add(rowStartInclusize, rowTypesRemoved);
    		}
    	};
    	return action;
    }
    
    private Action removeColumnsOnClearedAreaAction(int columnStartInclusize, int columnEndExclusive) {
    	Action action = new Action() {
    		protected void applyActionFunction() {
    			ListIterator<LinkedList<SolderedPin>> iterator = elements.listIterator(columnStartInclusize);
    			for(int i = columnStartInclusize; i < columnEndExclusive; i++) {
    				iterator.next();
    				iterator.remove();
    			}
    		}
    		@Override
    		protected void undoActionFunction() {
    			ListIterator<LinkedList<SolderedPin>> iterator = elements.listIterator(columnStartInclusize);
    			for(int i = columnStartInclusize; i < columnEndExclusive; i++) {
    				LinkedList<SolderedPin> column = new LinkedList<>();
    				for(int j = 0; j < getRows(); j++)
    					column.add(mkIdent());
    				iterator.add(column);
    			}
    		}
    	};
    	return action;
    }
    
    private Action addRowsAction(int row, int amt, RowType type) {
    	Action action = new Action() {
    		@Override
    		protected void applyActionFunction() {
    			ListIterator<LinkedList<SolderedPin>> iterator = elements.listIterator();
    			while(iterator.hasNext()) {
    				LinkedList<SolderedPin> column = iterator.next();
    				Pair<Boolean, Boolean> isWithinBodyOrSpace = isRowInsertedWithinGateBodyOrSpace(row, iterator.previousIndex());
					ListIterator<SolderedPin> rowIterator = column.listIterator(row);
    				if(isWithinBodyOrSpace.first()) {
    					boolean isWithinBody = isWithinBodyOrSpace.second();
        				for(int i = 0; i < amt; i++) {
        					SolderedPin sp = getSolderedPinAt(row, iterator.previousIndex());
        					SolderedPin toAdd = new SpacePin(sp.getSolderedGate(), isWithinBody);
        					rowIterator.add(toAdd);
        				}
    					
    				} else {
        				for(int i = 0; i < amt; i++)
        					rowIterator.add(mkIdent());
    				}
    			}
    			rowTypes.add(row, type, amt);
    		}
    		@Override
    		protected void undoActionFunction() {
    			ListIterator<LinkedList<SolderedPin>> iterator = elements.listIterator();
    			while(iterator.hasNext()) {
    				LinkedList<SolderedPin> column = iterator.next();
    				ListIterator<SolderedPin> rowIterator = column.listIterator(row);
    				for(int i = 0; i < amt; i++) {
    					rowIterator.next();
    					rowIterator.remove();
    				}
    			}
    			rowTypes.remove(row, row + amt);
    		}
    	};
    	return action;
    }
    
    private Action addColumnsAction(int column, int amt) {
    	Action action = new Action() {
    		@Override
    		protected void applyActionFunction() {
    			ListIterator<LinkedList<SolderedPin>> iterator = elements.listIterator(column);
    			for(int i = 0; i < amt; i++) {
    				LinkedList<SolderedPin> row = new LinkedList<>();
    				for(int j = 0; j < getRows(); j++)
    					row.offerLast(mkIdent());
    				iterator.add(row);
    			}
    		}
    		@Override
    		protected void undoActionFunction() {
    			ListIterator<LinkedList<SolderedPin>> iterator = elements.listIterator(column);
    			for(int i = 0; i < amt; i++) {
    				iterator.next();
    				iterator.remove();
				}
    		}
    	};
    	return action;
    }
    
    
    
    
    
    
    private Action addControlAction(int rowControl, int rowSpace, int columnSpace, boolean controlStatus) {
    	Action action = new MultipleAction() {
    		@Override
    		public void initActionQueue(LinkedList<Action> actions) {
    			int[] bounds = getGateSpaceBounds(rowSpace, columnSpace);
    			if(rowControl < bounds[0]) {
    				Action clearRows = clearAreaAction(rowControl, bounds[0] - 1, columnSpace, true);
    				actions.offerLast(clearRows);
    			} else if (rowControl > bounds[1]) {
    				Action clearRows = clearAreaAction(bounds[1] + 1, rowControl, columnSpace, true);
    				actions.offerLast(clearRows);
    			}
    			Action addControl = addControlOnClearedAreaAction(rowControl, rowSpace, columnSpace, controlStatus);
    			actions.offerLast(addControl);
    		}
    	};
    	return action;
    }
    
    private Action addControlOnClearedAreaAction(int rowControl, int rowSpace, int columnSpace, boolean controlStatus) {
    	Action action = new Action() {
    		final SolderedPin pinBeforePlacement;
    		
    		{
    			pinBeforePlacement = getSolderedPinAt(rowControl, columnSpace);
    		}
    		
    		@Override
    		protected void applyActionFunction() {
    			SolderedPin spGate = getSolderedPinAt(rowSpace, columnSpace);
    			SolderedGate sg = spGate.getSolderedGate();
    			
    			ListIterator<SolderedPin> iterator = getRowIterator(rowControl, columnSpace);
    			SolderedPin sp = iterator.next();
    			
    			if(sp.getSolderedGate() == sg) {
    				SpacePin spacePin = (SpacePin) sp;
    				iterator.set(new SolderedControlPin(sg, spacePin.isWithinBody(), controlStatus, 
    						spacePin.getInputReg(), spacePin.getOutputLinkType(), spacePin.getOutputReg()));
    			} else {
    				iterator.set(new SolderedControlPin(sg, false, controlStatus));
    				if(rowControl > rowSpace) {
    					iterator.previous();
    					while(iterator.hasPrevious()) {
    						sp = iterator.previous();
    						if(sp.getSolderedGate() == sg)
    							break;
    						iterator.set(new SpacePin(sg, false));
    					}
    				} else {
    					while(iterator.hasNext()) {
    						sp = iterator.next();
    						if(sp.getSolderedGate() == sg)
    							break;
    						iterator.set(new SpacePin(sg, false));
    					}
    				}
    			}
    		}
    		@Override
    		protected void undoActionFunction() {
    			SolderedPin spGate = getSolderedPinAt(rowSpace, columnSpace);
    			SolderedGate sg = spGate.getSolderedGate();
    			
    			ListIterator<SolderedPin> iterator = getRowIterator(rowControl, columnSpace);
    			SolderedPin sp = iterator.next();
    			
    			if(pinBeforePlacement.getSolderedGate() == sg) {
    				if(pinBeforePlacement instanceof SolderedControlPin) {
    					SolderedControlPin controlPinBeforePlacement = (SolderedControlPin) pinBeforePlacement;
        				iterator.set(new SolderedControlPin(sg, controlPinBeforePlacement.isWithinBody(), controlPinBeforePlacement.getControlStatus(), 
        						controlPinBeforePlacement.getInputReg(), controlPinBeforePlacement.getOutputLinkType(), controlPinBeforePlacement.getOutputReg()));
    				} else {
    					SpacePin spacePin = (SpacePin) pinBeforePlacement;
        				iterator.set(new SpacePin(sg, spacePin.isWithinBody(),
        						spacePin.getInputReg(), spacePin.getOutputLinkType(), spacePin.getOutputReg()));
    				}
    			} else {
    				iterator.set(mkIdent());
    				if(rowControl > rowSpace) {
    					iterator.previous();
    					while(iterator.hasPrevious()) {
    						sp = iterator.previous();
    						if(sp.getSolderedGate() == sg)
    							break;
    						iterator.set(mkIdent());
    					}
    				} else {
    					while(iterator.hasNext()) {
    						sp = iterator.next();
    						if(sp.getSolderedGate() == sg)
    							break;
    						iterator.set(mkIdent());
    					}
    				}
    			}
    		}
    	};
    	return action;
    }
    
    private Action addLinkAction(int rowControl, int rowSpace, int columnSpace, int inputLinkReg,
    		OutputLinkType outputLinkType, int outputLinkReg) {
    	Action action = new MultipleAction() {
    		@Override
    		public void initActionQueue(LinkedList<Action> actions) {
    			int[] bounds = getGateSpaceBounds(rowSpace, columnSpace);
    			if(inputLinkReg != -1) {
    				ListIterator<SolderedPin> iterator = getRowIterator(bounds[0], columnSpace);
	    			for(int i = bounds[0]; i <= bounds[1]; i++) {
	    				SolderedPin sp = iterator.next();
	    				if(sp instanceof SpacePin) {
	    					SpacePin spacePin = (SpacePin) sp;
	    					if(spacePin.getInputReg() == inputLinkReg) {
	    						Action removeLink = removeLinkPinAction(i, columnSpace, false);
	    						actions.offerLast(removeLink);
	    						break;
	    					}
	    				}
	    			}
    			}
    			if(rowControl < bounds[0]) {
    				Action clearRows = clearAreaAction(rowControl, bounds[0] - 1, columnSpace, true);
    				actions.offerLast(clearRows);
    			} else if (rowControl > bounds[1]) {
    				Action clearRows = clearAreaAction(bounds[1] + 1, rowControl, columnSpace, true);
    				actions.offerLast(clearRows);
    			}
    			Action addControl = addLinkOnClearedAreaAction(rowControl, rowSpace, columnSpace, inputLinkReg,
    		    		outputLinkType, outputLinkReg);
    			actions.offerLast(addControl);
    		}
    	};
    	return action;
    }
    
    private Action addLinkOnClearedAreaAction(int rowLink, int rowSpace, int columnSpace, int inputLinkReg,
    		OutputLinkType outputLinkType, int outputLinkReg) {
    	Action action = new Action() {
    		final SolderedPin pinBeforePlacement;
    		
    		{
    			pinBeforePlacement = getSolderedPinAt(rowLink, columnSpace);
    		}
    		
    		@Override
    		protected void applyActionFunction() {
    			SolderedPin spGate = getSolderedPinAt(rowSpace, columnSpace);
    			SolderedGate sg = spGate.getSolderedGate();
    			
    			ListIterator<SolderedPin> iterator = getRowIterator(rowLink, columnSpace);
    			SolderedPin sp = iterator.next();
    			
    			if(sp.getSolderedGate() == sg) {
    				if(sp instanceof SolderedControlPin) {
    					SolderedControlPin controlPinBeforePlacement = (SolderedControlPin) pinBeforePlacement;
    					iterator.set(new SolderedControlPin(sg, controlPinBeforePlacement.isWithinBody(), controlPinBeforePlacement.getControlStatus(), 
    							inputLinkReg, outputLinkType, outputLinkReg));
    				} else {
    					iterator.set(new SpacePin(sg, sp.isWithinBody(), inputLinkReg, outputLinkType, outputLinkReg));
    				}
    			} else {
    				iterator.set(new SpacePin(sg, false, inputLinkReg, outputLinkType, outputLinkReg));
    				if(rowLink > rowSpace) {
    					iterator.previous();
    					while(iterator.hasPrevious()) {
    						sp = iterator.previous();
    						if(sp.getSolderedGate() == sg)
    							break;
    						iterator.set(new SpacePin(sg, false));
    					}
    				} else {
    					while(iterator.hasNext()) {
    						sp = iterator.next();
    						if(sp.getSolderedGate() == sg)
    							break;
    						iterator.set(new SpacePin(sg, false));
    					}
    				}
    			}
    		}
    		@Override
    		protected void undoActionFunction() {
    			SolderedPin spGate = getSolderedPinAt(rowSpace, columnSpace);
    			SolderedGate sg = spGate.getSolderedGate();
    			
    			ListIterator<SolderedPin> iterator = getRowIterator(rowLink, columnSpace);
    			SolderedPin sp = iterator.next();
    			
    			if(sp.getSolderedGate() == sg) {
    				if(pinBeforePlacement instanceof SolderedControlPin) {
    					SolderedControlPin controlPinBeforePlacement = (SolderedControlPin) pinBeforePlacement;
        				iterator.set(new SolderedControlPin(sg, controlPinBeforePlacement.isWithinBody(), controlPinBeforePlacement.getControlStatus(), 
        						controlPinBeforePlacement.getInputReg(), controlPinBeforePlacement.getOutputLinkType(), controlPinBeforePlacement.getOutputReg()));
    				} else {
    					SpacePin spacePin = (SpacePin) pinBeforePlacement;
        				iterator.set(new SpacePin(sg, spacePin.isWithinBody(),
        						spacePin.getInputReg(), spacePin.getOutputLinkType(), spacePin.getOutputReg()));
    				}
    			} else {
    				iterator.set(mkIdent());
    				if(rowLink > rowSpace) {
    					iterator.previous();
    					while(iterator.hasPrevious()) {
    						sp = iterator.previous();
    						if(sp.getSolderedGate() == sg)
    							break;
    						iterator.set(mkIdent());
    					}
    				} else {
    					while(iterator.hasNext()) {
    						sp = iterator.next();
    						if(sp.getSolderedGate() == sg)
    							break;
    						iterator.set(mkIdent());
    					}
    				}
    			}
    		}
    	};
    	return action;
    }
    
    private Action addGateAction(String gateModelLocationString, int column, int[] localToGlobalRegs, GroupDefinition parameterSet) {
    	Action action = new MultipleAction() {
    		@Override
    		public void initActionQueue(LinkedList<Action> actions) {
    			int[] regBounds = getRegBounds(localToGlobalRegs);
    			Action clearArea = clearAreaAction(regBounds[0], regBounds[1], column, true);
    			actions.offerLast(clearArea);
    			Action addGateBodyOnClearArea = addGateBodyOnClearedAreaAction(gateModelLocationString, column, localToGlobalRegs, parameterSet);
    			actions.offerLast(addGateBodyOnClearArea);
    		}
    	};
    	return action;
    }
    
    private Action addGateBodyOnClearedAreaAction(String gateModelLocationString, int column, int[] localToGlobalRegs, GroupDefinition parameterSet) {
    	Action action = new Action() {
    		final int rowBodyStart, rowBodyEnd;
    		final SolderedGate sg;
    		{
    			int[] regBounds = getRegBounds(localToGlobalRegs);
    			rowBodyStart = regBounds[0];
    			rowBodyEnd = regBounds[1];
    			sg = new SolderedGate(null, parameterSet);
    		}
    		
			@Override
			protected void applyActionFunction() {
    			sg.setManifestHandle(addToManifest(gateModelLocationString));
    			
    			for(int i = 0; i < localToGlobalRegs.length; i++) {
    				int globalReg = localToGlobalRegs[i];
    				placePin(new SolderedRegister(sg, i), globalReg, column);
    			}
    			
    			ListIterator<SolderedPin> iterator = getRowIterator(rowBodyStart, column);
    			for(int i = rowBodyStart; i <= rowBodyEnd; i++) {
    				SolderedPin sp = iterator.next();
    				if(sp.getSolderedGate() != sg)
    					iterator.set(new SpacePin(sg, true));
    			}
			}
			@Override
			protected void undoActionFunction() {
				ListIterator<SolderedPin> iterator = getRowIterator(rowBodyStart, column);
				SolderedPin sp = null;
    			
    			for(int i = rowBodyStart; i <= rowBodyEnd; i++) {
    				sp = iterator.next();
    				iterator.set(mkIdent());
    			}

				SolderedGate sg = sp.getSolderedGate();
				removeFromManifest(sg);
    			
			}
		};
		return action;
    }
    
    private Action clearAreaAction(int rowStart, int rowEnd, int column, boolean removeWhenInsideGate) {
    	Action action = new MultipleAction() {

			@Override
			public void initActionQueue(LinkedList<Action> actions) {
    			ListIterator<SolderedPin> iterator = getRowIterator(rowStart, column); 
    			
    			SolderedGate currentGate = null;
    			
    			
    			Queue<Integer> controlsToRemoveQueue = new Queue<>();
    			Queue<Integer> inputLinksToRemoveQueue = new Queue<>();
    			Queue<Integer> outputLinksToRemoveQueue = new Queue<>();
    			
    			for(int i = rowStart; i <= rowEnd; i++) {
    				SolderedPin sp = iterator.next();
    				
    				if(currentGate == null)
    					currentGate = sp.getSolderedGate();
    				
    				if(checkIfRemoveGate(sp)) {
    					
    					if(sp.getSolderedGate() != currentGate) {
    						addActions(actions, controlsToRemoveQueue, inputLinksToRemoveQueue, outputLinksToRemoveQueue);
    						currentGate = sp.getSolderedGate();
    					}
    						
    					controlsToRemoveQueue.clear();
    					inputLinksToRemoveQueue.clear();
    					outputLinksToRemoveQueue.clear();
    					
        				if(!sp.getSolderedGate().isIdentity()) { 
    	    				Action removeGate = removeEntireGateAction(iterator.previousIndex(), column);
    	    				actions.offerLast(removeGate);
        				}
        				
        				for(int j = i + 1; j <= rowEnd; j++) {
        					i++;
            				sp = iterator.next();
            				SolderedGate nextGate = sp.getSolderedGate();
        					if(nextGate != currentGate) {
        						currentGate = nextGate;
        						i--;
        						iterator.previous();
        						break;
        					}
        				}
        				
        			} else {
        				
        				if(sp.getSolderedGate() != currentGate) {
    						addActions(actions, controlsToRemoveQueue, inputLinksToRemoveQueue, outputLinksToRemoveQueue);
    						controlsToRemoveQueue.clear();
	    					inputLinksToRemoveQueue.clear();
	    					outputLinksToRemoveQueue.clear();
	    					currentGate = sp.getSolderedGate();
        				}
    					
        				if(sp instanceof SpacePin) {
    						SpacePin spacePin = (SpacePin) sp;
    						if(sp instanceof SolderedControlPin)
    							controlsToRemoveQueue.enqueue(iterator.previousIndex());
    						if(spacePin.isInputLinked())
    							inputLinksToRemoveQueue.enqueue(iterator.previousIndex());
    						if(spacePin.isOutputLinked())
    							outputLinksToRemoveQueue.enqueue(iterator.previousIndex());
    					}
        			}
    			}
    			addActions(actions, controlsToRemoveQueue, inputLinksToRemoveQueue, outputLinksToRemoveQueue);
			}
			
			private boolean checkIfRemoveGate(SolderedPin sp) {
				if(removeWhenInsideGate)
					return sp.isWithinBody();
				else
					return sp instanceof SolderedRegister;
			}
			
			private void addActions(LinkedList<Action> actions, Queue<Integer> controlsQueue, Queue<Integer> inputLinksQueue, Queue<Integer> outputLinksQueue) {
				for(int i : controlsQueue) {
    				Action removeControl = removeControlPinAction(i, column);
					actions.offerLast(removeControl);
    			}
    			for(int i : inputLinksQueue) {
    				Action removeLink = removeLinkPinAction(i, column, false);
					actions.offerLast(removeLink);
    			}
    			for(int i : outputLinksQueue) {
    				Action removeLink =  removeLinkPinAction(i, column, true);
					actions.offerLast(removeLink);
    			}
			}
    	};
    	
    	return action;
    }
    
    private Action removeEntireGateAction(int rowSpace, int columnSpace) {
    	Action action = new MultipleAction() {
    		
    		@Override
    		public void initActionQueue(LinkedList<Action> actions) {
    			int[] rowSpaceBounds = getGateSpaceBounds(rowSpace, columnSpace);
    			
    			ListIterator<SolderedPin> iterator = getRowIterator(rowSpaceBounds[0], columnSpace);
    			for(int i = rowSpaceBounds[0]; i <= rowSpaceBounds[1]; i++) {
    				SolderedPin sp = iterator.next();
    				if(sp instanceof SpacePin) {
    					SpacePin spacePin = (SpacePin) sp;
    					if(spacePin instanceof SolderedControlPin) {
        					Action removeControl = removeControlPinAction(i, columnSpace);
        					actions.offerLast(removeControl);
        				}
    					if(spacePin.isInputLinked()) {
    						Action removeLink = removeLinkPinAction(i, columnSpace, false);
    						actions.offerLast(removeLink);
    					}
    					if(spacePin.isOutputLinked()) {
    						Action removeLink = removeLinkPinAction(i, columnSpace, true);
    						actions.offerLast(removeLink);
    					}
    				}
    			}
    			

    			int rowBody = findRowOfBody(rowSpace, columnSpace);
    			Action removeBody = removeGateBodyAction(rowBody, columnSpace);
    			actions.offerLast(removeBody);
    			
    		}
    	};
    	return action;
    }
    
    private Action removeGateBodyAction(int rowBody, int columnBody) {
    	Action action = new Action() {
    		final SolderedGate sg;
    		final Hashtable<Integer, Integer> globalToLocalRegs;
    		final int bodyStart;
    		final int bodyEnd;
    		
    		{
    			SolderedPin sp = getSolderedPinAt(rowBody, columnBody);
    			sg = sp.getSolderedGate();
    			globalToLocalRegs = getGlobalToLocalRegsFromGate(rowBody, columnBody);
    			
    			int[] bounds = getGateBodyBounds(rowBody, columnBody);
    			bodyStart = bounds[0];
    			bodyEnd = bounds[1];
    		}
    		
    		@Override
    		protected void applyActionFunction() {
    			ListIterator<SolderedPin> iterator = getRowIterator(bodyStart, columnBody);
    			for(int i = bodyStart; i <= bodyEnd; i++) {
    				iterator.next();
    				iterator.set(mkIdent());
    			}
    			removeFromManifest(sg);
    		}
    		
    		@Override
    		protected void undoActionFunction() {
    			ListIterator<SolderedPin> iterator = getRowIterator(bodyStart, columnBody);
    			for(int i = bodyStart; i <= bodyEnd; i++) {
    				iterator.next();
    				Integer localReg = globalToLocalRegs.get(i);
    				if(localReg == null)
    					iterator.set(new SpacePin(sg, true));
    				else
    					iterator.set(new SolderedRegister(sg, localReg));
    			}
    			addToManifest(sg);
    		}
    	};
    	return action;
    }
    
    private Action removeControlPinAction(int rowControl, int columnControl) {
    	Action action = new Action() {
    		final int rowGateBody;
    		final boolean controlStatus;
    		
    		{
    			rowGateBody = findRowOfBody(rowControl, columnControl);
    			SolderedControlPin sp = (SolderedControlPin) getSolderedPinAt(rowControl, columnControl);
    			controlStatus = sp.getControlStatus();
    		}
    		
    		@Override
    		protected void applyActionFunction() {
    			ListIterator<SolderedPin> iterator = getRowIterator(rowControl, columnControl);
    			SolderedControlPin controlPin = (SolderedControlPin) iterator.next();
    			SolderedGate sg = controlPin.getSolderedGate();
    			SolderedPin pinAbove = peakPinAbove(iterator);
    			iterator.previous();
    			SolderedPin pinBelow = peakPinBelow(iterator);
    			boolean isPinBelow = pinBelow == null? false : pinBelow.getSolderedGate() == sg;
    			boolean isPinAbove = pinAbove == null? false : pinAbove.getSolderedGate() == sg;
    			
    			if(isPinBelow && isPinAbove || controlPin.isInputLinked() || controlPin.isOutputLinked()) {
    				iterator.next();
    				iterator.set(new SpacePin(sg, controlPin.isWithinBody(), controlPin.getInputReg(), 
    						controlPin.getOutputLinkType(), controlPin.getOutputReg()));
    			} else {
    				iterator.next();
    				iterator.set(mkIdent());
    				
    				if(isPinBelow) {
    					iterator.previous();
    					while(iterator.hasPrevious()) {
    						SolderedPin sp = iterator.previous();
    						if(sp.isNotEmptySpace())
    							break;
    						iterator.set(mkIdent());
    					}
    				} else if(isPinAbove) {
    					while(iterator.hasNext()) {
    						SolderedPin sp = iterator.next();
    						if(sp.isNotEmptySpace())
    							break;
    						iterator.set(mkIdent());
    					}
    				}
    			}
    		}
    		protected void undoActionFunction() {
    			ListIterator<SolderedPin> iterator = getRowIterator(rowControl, columnControl);
    			SolderedPin pinBeforeControl = iterator.next();
    			SolderedPin gatePin = getSolderedPinAt(rowGateBody, columnControl);
    			SolderedGate sg = gatePin.getSolderedGate();
    			
    			if(sg == pinBeforeControl.getSolderedGate()) {
    				SpacePin sp = (SpacePin) pinBeforeControl;
    				iterator.set(new SolderedControlPin(sg, rowControl == rowGateBody, controlStatus, 
    						sp.getInputReg(), sp.getOutputLinkType(), sp.getOutputReg()));
    			} else {
    				iterator.set(new SolderedControlPin(sg, rowControl == rowGateBody, controlStatus));
    				
    				if(rowControl > rowGateBody) {
        				iterator.previous();
        				while(iterator.hasPrevious()) {
        					SolderedPin sp = iterator.previous();
    						if(sp.getSolderedGate() == sg)
    							break;
    						iterator.set(new SpacePin(sg, false));
        				}
        			} else if(rowControl < rowGateBody) {
        				while(iterator.hasNext()) {
        					SolderedPin sp = iterator.next();
    						if(sp.getSolderedGate() == sg)
    							break;
    						iterator.set(new SpacePin(sg, false));
        				}
        			}
    			}
    			
    		};
    	};
    	return action;
    }
    
    private Action removeLinkPinAction(int rowLink, int columnLink, boolean output) {
    	Action action = new Action() {
    		final int rowGateBody;
    		final SpacePin savedLinkPin;
    		
    		{
    			rowGateBody = findRowOfBody(rowLink, columnLink);
    			savedLinkPin = (SpacePin) getSolderedPinAt(rowLink, columnLink);
    		}
    		
    		@Override
    		protected void applyActionFunction() {
    			ListIterator<SolderedPin> iterator = getRowIterator(rowLink, columnLink);
    			SpacePin linkPin = (SpacePin) iterator.next();
    			SolderedGate sg = linkPin.getSolderedGate();
    			SolderedPin pinAbove = peakPinAbove(iterator);
    			iterator.previous();
    			SolderedPin pinBelow = peakPinBelow(iterator);
    			boolean isPinBelow = pinBelow == null? false : pinBelow.getSolderedGate() == sg;
    			boolean isPinAbove = pinAbove == null? false : pinAbove.getSolderedGate() == sg;
    			boolean willPinStillOccupied = linkPin instanceof SolderedControlPin;
    			willPinStillOccupied |= output?  linkPin.isInputLinked() : linkPin.isOutputLinked();
    			
    			if(isPinBelow && isPinAbove || willPinStillOccupied) {
    				int inputReg = output? linkPin.getInputReg() : -1;
    				int outputReg = output? -1 : linkPin.getOutputReg();
    				OutputLinkType linkType = output? linkPin.getOutputLinkType() : OutputLinkType.CLASSICAL_LINK;
    				
    				iterator.next();
    				if(linkPin instanceof SolderedControlPin) {
    					SolderedControlPin solderedPin = (SolderedControlPin) linkPin;
    					iterator.set(new SolderedControlPin(sg, linkPin.isWithinBody(), solderedPin.getControlStatus(), inputReg, linkType, outputReg));
    				} else {
    					iterator.set(new SpacePin(sg, linkPin.isWithinBody(), inputReg, linkType, outputReg));
    				}
    			} else {
    				iterator.next();
    				iterator.set(mkIdent());
    				
    				if(isPinBelow) {
    					iterator.previous();
    					while(iterator.hasPrevious()) {
    						SolderedPin sp = iterator.previous();
    						if(sp.isNotEmptySpace())
    							break;
    						iterator.set(mkIdent());
    					}
    				} else if(isPinAbove) {
    					while(iterator.hasNext()) {
    						SolderedPin sp = iterator.next();
    						if(sp.isNotEmptySpace())
    							break;
    						iterator.set(mkIdent());
    					}
    				}
    			}
    		}
    		protected void undoActionFunction() {
    			ListIterator<SolderedPin> iterator = getRowIterator(rowLink, columnLink);
    			SolderedPin pinBeforeLink = iterator.next();
    			SolderedPin gatePin = getSolderedPinAt(rowGateBody, columnLink);
    			SolderedGate sg = gatePin.getSolderedGate();
    			
    			int inputLinkReg = savedLinkPin.getInputReg();
    			int outputLinkReg = savedLinkPin.getOutputReg();
    			OutputLinkType linkType = savedLinkPin.getOutputLinkType();
    			
    			
    			if(sg == pinBeforeLink.getSolderedGate() && pinBeforeLink instanceof SolderedControlPin) {
    				SolderedControlPin cp = (SolderedControlPin) pinBeforeLink;
    				iterator.set(new SolderedControlPin(sg, rowLink == rowGateBody, cp.getControlStatus(), inputLinkReg, linkType, outputLinkReg));
    			} else {
    				iterator.set(new SpacePin(sg, rowLink == rowGateBody, inputLinkReg, linkType, outputLinkReg));
    			}
    			
    			if(rowLink > rowGateBody) {
    				iterator.previous();
    				while(iterator.hasPrevious()) {
    					SolderedPin sp = iterator.previous();
						if(sp.getSolderedGate() == sg)
							break;
						iterator.set(new SpacePin(sg, false));
    				}
    			} else if(rowLink < rowGateBody) {
    				while(iterator.hasNext()) {
    					SolderedPin sp = iterator.next();
						if(sp.getSolderedGate() == sg)
							break;
						iterator.set(new SpacePin(sg, false));
    				}
    			}
    			
    		};
    	};
    	return action;
    }
    
    
    
    
    
    
    
//    ---------------------------------------------------------------------------
    
    
    
    private CircuitBoardModel getInstance() {
    	return this;
    }
    
    
    
    
    
    private int[] getLocalToGlobalRegsFromGate(int rowBody, int columnBody) {
    	Hashtable<Integer, Integer> localToGlobal = new Hashtable<>();
    	ListIterator<SolderedPin> iterator = getRowIterator(rowBody, columnBody);
    	SolderedPin sp = iterator.next();
    	SolderedGate sg = sp.getSolderedGate();
    	iterator.previous();
    	
    	while(iterator.hasNext()) {
    		SolderedPin next = iterator.next();
    		if(next.getSolderedGate() != sg)
    			break;
    		if(!next.isWithinBody())
    			break;
    		if(next instanceof SolderedRegister) { 
    			SolderedRegister soldReg = (SolderedRegister) next;
    			localToGlobal.put(soldReg.getSolderedGatePinNumber(), iterator.previousIndex());
    		}
    	}
    	iterator = getRowIterator(rowBody, columnBody);
    	while(iterator.hasPrevious()) {
    		SolderedPin previous = iterator.previous();
    		if(previous.getSolderedGate() != sg)
    			break;
    		if(!previous.isWithinBody())
    			break;
    		if(previous instanceof SolderedRegister) { 
    			SolderedRegister soldReg = (SolderedRegister) previous;
    			localToGlobal.put(soldReg.getSolderedGatePinNumber(), iterator.nextIndex());
    		}
    	}
    	
    	int[] regs = new int[localToGlobal.size()];
    	for(int i = 0; i < regs.length; i++)
    		regs[i] = localToGlobal.get(i);
    	
    	return regs;
    }
    
    private Hashtable<Integer, Integer> getGlobalToLocalRegsFromGate(int rowBody, int columnBody) {
    	Hashtable<Integer, Integer> globalToLocal = new Hashtable<>();
    	ListIterator<SolderedPin> iterator = getRowIterator(rowBody, columnBody);
    	SolderedPin sp = iterator.next();
    	SolderedGate sg = sp.getSolderedGate();
    	iterator.previous();
    	
    	while(iterator.hasNext()) {
    		SolderedPin next = iterator.next();
    		if(next.getSolderedGate() != sg)
    			break;
    		if(!next.isWithinBody())
    			break;
    		if(next instanceof SolderedRegister) { 
    			SolderedRegister soldReg = (SolderedRegister) next;
    			globalToLocal.put(iterator.previousIndex(), soldReg.getSolderedGatePinNumber());
    		}
    	}
    	iterator = getRowIterator(rowBody, columnBody);
    	while(iterator.hasPrevious()) {
    		SolderedPin previous = iterator.previous();
    		if(previous.getSolderedGate() != sg)
    			break;
    		if(!previous.isWithinBody())
    			break;
    		if(previous instanceof SolderedRegister) { 
    			SolderedRegister soldReg = (SolderedRegister) previous;
    			globalToLocal.put(iterator.nextIndex(), soldReg.getSolderedGatePinNumber());
    		}
    	}
    	return globalToLocal;
    }
    
    private int[] getGateSpaceBounds(int rowSpace, int columnSpace) {
    	ListIterator<SolderedPin> iterator = getRowIterator(rowSpace, columnSpace);
    	SolderedPin sp = iterator.next();
    	SolderedGate sg = sp.getSolderedGate();
    	
    	int minimum = rowSpace;
    	int maximum = rowSpace;
    	
    	while(iterator.hasNext()) {
    		SolderedPin next = iterator.next();
    		if(next.getSolderedGate() != sg)
    			break;
    		maximum++;
    	}
    	iterator = getRowIterator(rowSpace, columnSpace);
    	while(iterator.hasPrevious()) {
    		SolderedPin previous = iterator.previous();
    		if(previous.getSolderedGate() != sg)
    			break;
    		minimum--;
    	}
    	return new int[]{minimum, maximum};
    }
    
    private int[] getGateBodyBounds(int rowBody, int columnBody) {
    	ListIterator<SolderedPin> iterator = getRowIterator(rowBody, columnBody);
    	SolderedPin sp = iterator.next();
    	SolderedGate sg = sp.getSolderedGate();
    	
    	int minimum = rowBody;
    	int maximum = rowBody;
    	
    	while(iterator.hasNext()) {
    		SolderedPin next = iterator.next();
    		if(next.getSolderedGate() != sg)
    			break;
    		if(!next.isWithinBody())
    			break;
    		maximum++;
    	}
    	iterator = getRowIterator(rowBody, columnBody);
    	while(iterator.hasPrevious()) {
    		SolderedPin previous = iterator.previous();
    		if(previous.getSolderedGate() != sg)
    			break;
    		if(!previous.isWithinBody())
    			break;
    		minimum--;
    	}
    	return new int[]{minimum, maximum};
    }
    
    private int findRowOfBody(int rowSpace, int columnSpace) {
    	ListIterator<SolderedPin> iterator = getRowIterator(rowSpace, columnSpace);
    	SolderedPin sp = iterator.next();
    	if(sp.isWithinBody())
    		return rowSpace;
    	SolderedGate sg = sp.getSolderedGate();
    	
    	while(iterator.hasNext()) {
    		sp = iterator.next();
    		if(sp.getSolderedGate() != sg)
    			break;
    		if(sp.isWithinBody())
    			return iterator.previousIndex();
    	}
    	iterator = getRowIterator(rowSpace, columnSpace);
    	while(iterator.hasPrevious()) {
    		sp = iterator.previous();
    		if(sp.getSolderedGate() != sg)
    			break;
    		if(sp.isWithinBody())
    			return iterator.nextIndex();
    	}
    	return -1;
    }
    
    private int getArbitraryGateRegisterLocation(int rowSpace, int columnSpace) {
    	ListIterator<SolderedPin> iterator = getRowIterator(rowSpace, columnSpace);
    	SolderedPin sp = iterator.next();
    	SolderedGate sg = sp.getSolderedGate();
    	iterator.previous();
    	
    	while(iterator.hasNext()) {
    		sp = iterator.next();
    		if(sp.getSolderedGate() != sg)
    			break;
    		if(sp instanceof SolderedRegister)
    			return iterator.previousIndex();
    	}
    	iterator = getRowIterator(rowSpace, columnSpace);
    	while(iterator.hasPrevious()) {
    		sp = iterator.previous();
    		if(sp.getSolderedGate() != sg)
    			break;
    		if(sp instanceof SolderedRegister)
    			return iterator.nextIndex();
    	}
    	return -1;
    }
    
    
    private ListIterator<SolderedPin> getRowIterator(int row, int column) {
    	return elements.get(column).listIterator(row);
    }
    
    private Pair<Boolean, Boolean> isRowInsertedWithinGateBodyOrSpace(int row, int column) {
    	ListIterator<SolderedPin> iterator = getRowIterator(row, column);
    	SolderedPin above = peakPinAbove(iterator);
    	SolderedPin below = peakPinBelow(iterator);
    	
    	boolean isWithinSpace = false;
    	boolean isWithinBody = false;
    	if(above != null && below != null && above.getSolderedGate() == below.getSolderedGate()) {
    		isWithinSpace = true;
    		isWithinBody = above.isWithinBody() && below.isWithinBody();
    	}
    	return new Pair<>(isWithinSpace, isWithinBody);
    }
    
    private static SolderedPin peakPinAbove(ListIterator<SolderedPin> iterator) {
    	if(iterator.hasNext()) {
    		SolderedPin next = iterator.next();
    		iterator.previous();
    		return next;
    	} else {
    		return null;
    	}
    }
    
    private static SolderedPin peakPinBelow(ListIterator<SolderedPin> iterator) {
    	if(iterator.hasPrevious()) {
    		SolderedPin previous = iterator.previous();
    		iterator.next();
    		return previous;
    	} else {
    		return null;
    	}
    }
    
    private void placePin(SolderedPin sp, int row, int column) {
    	elements.get(column).set(row, sp);
    }
    
    @SuppressWarnings("rawtypes")
	private SolderedRegister mkIdent() {
		ManifestElementHandle handle = gateModelsUsed.add(PresetGateType.IDENTITY.getModel().getLocationString());
		return new SolderedRegister(new SolderedGate(handle), 0);
	}
    
    
    private void addToManifest(SolderedGate sg) {
    	sg.setManifestHandle(addToManifest(sg.getGateModelLocationString()));
    }
    
    @SuppressWarnings("rawtypes")
	private ManifestElementHandle addToManifest(String gateModelLocationString) {
		return gateModelsUsed.add(gateModelLocationString);
	}
    
    private void removeFromManifest(SolderedGate sg) {
		gateModelsUsed.remove(sg.getGateModelLocationString());
	}
    
    
    private static int[] getRegBounds(int[] localToGlobalRegs) {
    	int minimum = localToGlobalRegs[0];
    	int maximum = localToGlobalRegs[0];
    	
    	for(int i = 0; i < localToGlobalRegs.length; i++) {
    		int globalReg = localToGlobalRegs[i];
    		if(globalReg < minimum)
    			minimum = globalReg;
    		if(globalReg > maximum)
    			maximum = globalReg;
    	}
    	return new int[] {minimum, maximum};
    }
    
	
	@Override
	public void checkScalarDefinition(ScalarDefinition definition, int i) {}

	@Override
	public void checkMatrixDefinition(MatrixDefinition definition, int i) throws DefinitionEvaluatorException {
		throw new DefinitionEvaluatorException("Definition should not define a matrix", i);
	}

	@Override
	public void checkArgDefinition(ArgDefinition definition, int i) throws DefinitionEvaluatorException {
		if(definition.isMatrix())
			throw new DefinitionEvaluatorException("Definition should not define a matrix", i);
	}
	
	
	
	
	public static class RowTypeList implements Serializable, Iterable<RowTypeElement> {
		private static final long serialVersionUID = -64478525067138213L;
		
		private final LinkedList<RowTypeElement> rowTypes;
		
		private RowTypeList() {
			this(new LinkedList<>());
		}
		
		private RowTypeList(LinkedList<RowTypeElement> rowTypes) {
			this.rowTypes = rowTypes;
		}
		
		private void add(int index, LinkedList<RowType> elements) {
			int noneStartAmt = countTypeAmtFrom(RowType.SPACE, 0, index);
			int classicalStartAmt = countTypeAmtFrom(RowType.CLASSICAL, 0, index);
			int quantumStartAmt = countTypeAmtFrom(RowType.QUANTUM, 0, index);
			
			int spaceAddedAmt = 0;
			int classicalAddedAmt = 0;
			int quantumAddedAmt = 0;
			
			ListIterator<RowType> addIterator = elements.listIterator();
			ListIterator<RowTypeElement> listIterator = rowTypes.listIterator(index);
			for(int i = 0; i < elements.size(); i++) {
				RowType rowType = addIterator.next();
				int reg = 0;
				switch(rowType) {
				case CLASSICAL:
					reg = classicalStartAmt + classicalAddedAmt++;
					break;
				case SPACE:
					reg = noneStartAmt + spaceAddedAmt++;
					break;
				case QUANTUM:
					reg = quantumStartAmt + quantumAddedAmt++;
					break;
				default:
					break;
				}
				listIterator.add(new RowTypeElement(reg, rowType));
			}
			while(listIterator.hasNext()) {
				RowTypeElement rowTypeElement = listIterator.next();
				switch(rowTypeElement.type) {
				case CLASSICAL:
					rowTypeElement.reg += classicalAddedAmt;
					break;
				case SPACE:
					rowTypeElement.reg += spaceAddedAmt;
					break;
				case QUANTUM:
					rowTypeElement.reg += quantumAddedAmt;
					break;
				default:
					break;
				}
			}
		}
		
		private void add(int index, RowType type, int amt) {
			if(amt == 0) return;
			
			int amtBefore = countTypeAmtFrom(type, 0, index);
			
			ListIterator<RowTypeElement> iterator = rowTypes.listIterator(index); 
			while(iterator.hasNext()) {
				RowTypeElement element = iterator.next();
				if(element.type == type)
					element.reg += amt;
			}
			
			iterator = rowTypes.listIterator(index);
			for(int i = 0; i < amt; i++)
				iterator.add(new RowTypeElement(amtBefore + i, type));
		}
		
		LinkedList<RowType> getRowTypes(int startIndexInclusive, int endIndexExclusize) {
			LinkedList<RowType> extractedRowTypes = new LinkedList<>();
			ListIterator<RowTypeElement> iterator = rowTypes.listIterator(startIndexInclusive);
			
			for(int i = startIndexInclusive; i < endIndexExclusize; i++) {
				RowTypeElement next = iterator.next();
				extractedRowTypes.offerLast(next.type);
			}
			return extractedRowTypes;
		}
		
		private void remove(int startIndexInclusive, int endIndexExclusize) {
			if(endIndexExclusize - startIndexInclusive == 0) return;
			
			int spaceAmt = countTypeAmtFrom(RowType.SPACE, startIndexInclusive, endIndexExclusize);
			int classicalAmt = countTypeAmtFrom(RowType.CLASSICAL, startIndexInclusive, endIndexExclusize);
			int quantumAmt = countTypeAmtFrom(RowType.QUANTUM, startIndexInclusive, endIndexExclusize);
			

			ListIterator<RowTypeElement> iterator = rowTypes.listIterator(endIndexExclusize); 
			for(int i = endIndexExclusize; i < rowTypes.size(); i++) {
				RowTypeElement elem = iterator.next();
				switch(elem.type) {
				case CLASSICAL:
					elem.reg -= classicalAmt;
					break;
				case SPACE:
					elem.reg -= spaceAmt;
					break;
				case QUANTUM:
					elem.reg -= quantumAmt;
					break;
				default:
					break;
				}
			}
			
			iterator = rowTypes.listIterator(startIndexInclusive);
			
			for(int i = startIndexInclusive; i < endIndexExclusize; i++) {
				iterator.next();
				iterator.remove();
			}
			
		}
		
		public RowType getTypeAtRow(int row) {
			return rowTypes.get(row).type;
		}
		
		public int getRegAtRow(int row) {
			return rowTypes.get(row).reg;
		}
		
		public int countTypeAmtFrom(RowType type, int indexStartInclusize, int indexEndExclusive) {
			int amt = 0;
			
			Iterator<RowTypeElement> iterator = rowTypes.listIterator(indexStartInclusize); 
			for(int i = indexStartInclusize; i < indexEndExclusive; i++)
				if(iterator.next().type == type) amt ++;
			return amt;
		}
		
		public LinkedList<Pair<Integer, Integer>> getRangesOfType(RowType type) {
			LinkedList<Pair<Integer, Integer>> linkedList = new LinkedList<>();
			ListIterator<RowTypeElement> iterator = rowTypes.listIterator(); 
			
			int start = -1;
			while(iterator.hasNext()) {
				RowType current = iterator.next().type;
				if(current == type) {
					start = start == -1? iterator.previousIndex() : start;
				} else {
					if(start != -1) {
						linkedList.add(new Pair<>(start, iterator.previousIndex()));
						start = -1;
					}
				}
			}
			if(start != -1)
				linkedList.add(new Pair<>(start, rowTypes.size()));
			return linkedList;
		}
		
		
		public int size() {
			return rowTypes.size();
		}
		
		public RowTypeList deepCopy() {
			LinkedList<RowTypeElement> newRowTypes = new LinkedList<>();
			for(RowTypeElement elem : rowTypes)
				newRowTypes.offerLast(new RowTypeElement(elem.reg, elem.type));
			return new RowTypeList(newRowTypes);
		}
		
		@Override
		public ListIterator<RowTypeElement> iterator() {
			return rowTypes.listIterator();
		}
		
	}
	
	public static class RowTypeElement implements Serializable {
		private static final long serialVersionUID = -7000406873793219391L;
		
		int reg;
		RowType type;
		
		RowTypeElement(int reg, RowType type) {
			this.reg = reg;
			this.type = type;
		}
		
		public int getReg() {
			return reg;
		}
		
		public RowType getType() {
			return type;
		}
	}
	
	private class ExportableGateIterator implements Iterator<RawExportableGateData> {
		private ListIterator<LinkedList<SolderedPin>> columns;
		private LinkedList<SolderedPin> rowList;
		private ListIterator<SolderedPin> rowIterator;
		private ListIterator<RowTypeElement> rowTypeIterator;
		private int gateRowBodyStart, gateRowBodyEnd;
		SolderedPin currentPin;
		RowTypeElement currentRowType;
		
		
		
		private LinkedList<RawExportControl> quantumControls;
		private LinkedList<RawExportControl> classicalControls;
		private LinkedList<RawExportOutputLink> outputLinks;
		private LinkedList<RawExportLink> inputLinks;
		private LinkedList<RawExportRegister> underneathQuantumIdentityGates;
		private Hashtable<Integer, RawExportRegister> registers;
		private boolean classicalReg = false;
		
		
		public ExportableGateIterator(int index) {
			columns = elements.listIterator(index);
			nextColumn();
		}
		
		@Override
		public boolean hasNext() {
			return rowIterator.hasNext() || columns.hasNext();
		}
		
		@Override
		public RawExportableGateData next() {
			if(!rowIterator.hasNext())
				nextColumn();
			
			incrementRow();
			
			SolderedGate sg = currentPin.getSolderedGate();
			SolderedGate current = sg;
			
			
			quantumControls = new LinkedList<>();
			classicalControls = new LinkedList<>();
			outputLinks = new LinkedList<>();
			inputLinks = new LinkedList<>();
			underneathQuantumIdentityGates = new LinkedList<>();
			registers = new Hashtable<>();
			classicalReg = false;
			
			gateRowBodyStart = -1;
			gateRowBodyEnd = -1;

			int gateRowSpaceStart = getRow();
			
			addPinData();
			
			
			boolean hitNoRowEnd;
			while (hitNoRowEnd = rowIterator.hasNext()) {
				incrementRow();
				if(currentPin.getSolderedGate() != current)
					break;
				addPinData();
			}
			
			
			if(hitNoRowEnd)
				decrementRow();
			
			return new RawExportableGateData(current, quantumControls, classicalControls, outputLinks, inputLinks,
				underneathQuantumIdentityGates, registers, classicalReg, gateRowSpaceStart, getRow(), 
				gateRowBodyStart, gateRowBodyEnd, getColumn());
		}
		
		
		private void incrementRow() {
			currentPin = rowIterator.next();
			currentRowType = rowTypeIterator.next();
		}
		
		private void decrementRow() {
			currentPin = rowIterator.previous();
			currentRowType = rowTypeIterator.previous();
		}
		
		private int getRow() {
			return rowIterator.previousIndex();
		}
		
		private int getColumn() {
			return columns.previousIndex();
		}
		
		private void addPinData() {
			int r = getRow();
			int globalReg = currentRowType.reg;
			RowType type = currentRowType.type;
			
			if(currentPin instanceof SolderedRegister) {
				SolderedRegister registerPin = (SolderedRegister) currentPin;
				classicalReg = type == RowType.CLASSICAL;
				RawExportRegister exportRegister = new RawExportRegister(globalReg, r);
				registers.put(registerPin.getSolderedGatePinNumber(), exportRegister);
				if(gateRowBodyStart == -1)
					gateRowBodyStart = r;
				gateRowBodyEnd = r;
			} else {
				SpacePin sp = (SpacePin) currentPin;
				
				if(sp.isInputLinked()) {
					RawExportLink exportRegister = new RawExportLink(globalReg, r, sp.getInputReg());
					inputLinks.offerLast(exportRegister);
				}
				if(sp.isOutputLinked()) {
					RawExportOutputLink exportRegister = new RawExportOutputLink(globalReg, r, sp.getOutputReg(), sp.getOutputLinkType());
					outputLinks.offerLast(exportRegister);
				}
				
				if(sp instanceof SolderedControlPin) {
					SolderedControlPin scp = (SolderedControlPin) sp;
					RawExportControl exportRegister = new RawExportControl(globalReg, r, scp.getControlStatus());
					if(type == RowType.CLASSICAL)
						classicalControls.offerLast(exportRegister);
					else
						quantumControls.offerLast(exportRegister);
				} else {
					RawExportRegister exportRegister = new RawExportRegister(globalReg, r);
					if(type == RowType.QUANTUM)
						underneathQuantumIdentityGates.offerLast(exportRegister);
				}
				
			}
		}
		
		private void nextColumn() {
			rowList = columns.next();
			rowIterator = rowList.listIterator();
			rowTypeIterator = rowTypes.iterator();
		}
		
	}
}


































