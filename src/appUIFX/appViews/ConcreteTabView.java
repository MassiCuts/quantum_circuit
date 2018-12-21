package appUIFX.appViews;

import appPreferencesFX.AppPreferences.Booleans;
import appUIFX.appViews.AppView.ViewListener;
import appUIFX.appViews.gateChooser.CircuitBoardChooser;
import appUIFX.appViews.gateChooser.CustomGateChooser;
import appUIFX.appViews.gateChooser.CustomOracleChooser;
import appUIFX.appViews.gateChooser.PresetGatesChooser;

public enum ConcreteTabView {
	CONSOLE(new Console(), Booleans.CONSOLE_OPEN),
	PRESET_GATES_VIEW(new PresetGatesChooser(), Booleans.PRESET_GATES_OPEN),
	CUSTOM_GATES_VIEW(new CustomGateChooser(), Booleans.CUSTOM_GATES_OPEN),
	CUSTOM_ORACLES_VIEW(new CustomOracleChooser(), Booleans.CUSTOM_ORACLES_OPEN),
	CIRCUITBOARD_VIEW(new CircuitBoardChooser(), Booleans.CIRCUITBOARDS_OPEN),
	PROJECT_HIERARCHY(new ProjectHierarchy(), Booleans.PROJECT_HEIRARCHY_OPEN);
	
	;
	
	private final AppView appView;
	private Booleans wasOpen;
	
	private ConcreteTabView(AppView appView, Booleans wasOpen) {
		this.appView = appView;
		this.wasOpen = wasOpen;
	}
	
	public void setViewListener(ViewListener listener) {
		appView.setViewListener(listener);
	}
	
	public ViewListener getViewListener() {
		return appView.getViewListener();
	}
	
	public AppView getView() {
		return appView;
	}
	
	public Booleans wasOpen() {
		return wasOpen;
	}
}
