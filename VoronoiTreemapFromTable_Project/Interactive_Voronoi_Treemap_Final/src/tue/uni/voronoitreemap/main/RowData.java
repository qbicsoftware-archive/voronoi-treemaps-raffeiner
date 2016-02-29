package tue.uni.voronoitreemap.main;

import java.util.ArrayList;
import java.util.List;

public class RowData {

	private List<String> levels;
	private List<Double> ratios;
	
	public RowData(List<String> levels, List<Double> ratios) {
		this.levels = new ArrayList<String>(levels);
		this.ratios = new ArrayList<Double>(ratios);
	}

	public List<String> getLevels() {
		return levels;
	}

	public void setLevels(List<String> levels) {
		this.levels = levels;
	}

	public List<Double> getRatios() {
		return ratios;
	}

	public void setRatios(List<Double> ratios) {
		this.ratios = new ArrayList<Double>(ratios);
	}
	
}
