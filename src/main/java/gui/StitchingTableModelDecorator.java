package gui;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.event.TableModelListener;
import javax.swing.table.AbstractTableModel;

import algorithm.TransformTools;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewTransform;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform3D;
import spim.fiji.spimdata.explorer.ExplorerWindow;
import spim.fiji.spimdata.explorer.ISpimDataTableModel;
import spim.fiji.spimdata.stitchingresults.StitchingResults;

public class StitchingTableModelDecorator < AS extends AbstractSpimData< ? > > extends AbstractTableModel implements ISpimDataTableModel<AS>, StitchingResultsSettable{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private ISpimDataTableModel<AS> decorated;
	
	StitchingResults res;
	
	static final List< String > columnNames = Arrays.asList( new String [] {"Location", "Avg. r", "# of links", "Errors (mean/min/max)"} );
	
	public StitchingTableModelDecorator(ISpimDataTableModel<AS> decorated) {
		this.decorated = decorated;
	}
	
	@Override
	public int getRowCount() {
		return decorated.getRowCount();
	}

	@Override
	public int getColumnCount() {
		// TODO implement for real
		return decorated.getColumnCount() + columnNames.size();
	}

	@Override
	public String getColumnName(int columnIndex) {
		// TODO Auto-generated method stub
		if (columnIndex < decorated.getColumnCount())
			return decorated.getColumnName(columnIndex);
		else
			return columnNames.get( columnIndex - decorated.getColumnCount() );
	}

	@Override
	public Class<?> getColumnClass(int columnIndex) {
		// TODO Auto-generated method stub
		return String.class;
	}

	@Override
	public boolean isCellEditable(int rowIndex, int columnIndex) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Object getValueAt(int rowIndex, int columnIndex)
	{

		// pass on to decorated
		if ( columnIndex < decorated.getColumnCount() )
			return decorated.getValueAt( rowIndex, columnIndex );

		// get location
		else if ( columnIndex - decorated.getColumnCount() == 0 )
		{
			ViewRegistration vr = null;
			final int n = decorated.getElements().get( rowIndex ).size();
			int nPresent = 0;
			boolean allTransformsIdentical = true;
			for ( int vi = 0; vi < n; vi++ )
			{
				final ViewId vid = decorated.getElements().get( rowIndex ).get( vi );
				if ( decorated.getPanel().getSpimData().getSequenceDescription().getMissingViews() != null
						&& decorated.getPanel().getSpimData().getSequenceDescription().getMissingViews()
								.getMissingViews().contains( vid ) )
					continue;

				nPresent++;

				final ViewRegistration vrT = decorated.getPanel().getSpimData().getViewRegistrations()
						.getViewRegistration( vid );
				if ( vr == null )
					vr = vrT;

				allTransformsIdentical &= TransformTools.allAlmostEqual( vr.getModel().getRowPackedCopy(),
						vrT.getModel().getRowPackedCopy(), 1e-5 );

			}

			if ( nPresent == 0 )
				return n + " of " + n + " views missing";
			if ( !allTransformsIdentical )
				return "multiple locations (" + nPresent + " of " + n + " views present)";

			final StringBuilder res = new StringBuilder();

			AffineTransform3D tr = vr.getModel(); // getTransformList().get(vr.getTransformList().size()
													// - 1);

			// round to 3 decimal places
			DecimalFormat df = new DecimalFormat( "#.###" );
			df.setRoundingMode( RoundingMode.HALF_UP );

			res.append( df.format( tr.get( 0, 3 ) ) );
			res.append( ", " );
			res.append( df.format( tr.get( 1, 3 ) ) );
			res.append( ", " );
			res.append( df.format( tr.get( 2, 3 ) ) );
			return res.toString();
		}

		// get avg. correlation
		else if ( columnIndex - decorated.getColumnCount() == 1 )
		{
			final Set< ViewId > vid = new HashSet<>( decorated.getElements().get( rowIndex ) );

			DecimalFormat df = new DecimalFormat( "#.###" );
			df.setRoundingMode( RoundingMode.HALF_UP );

			return df.format( res.getAvgCorrelation( vid ) );

		}

		// get no. of links
		else if ( columnIndex - decorated.getColumnCount() == 2 )
		{
			final Set< ViewId > vid = new HashSet<>( decorated.getElements().get( rowIndex ) );
			return ( res.getAllPairwiseResultsForViewId( vid ).size() );
		}

		// get errors
		else if ( columnIndex - decorated.getColumnCount() == 3 )
		{
			final Set< ViewId > vid = new HashSet<>( decorated.getElements().get( rowIndex ) );
			final ArrayList< Double > errors = res.getErrors( vid );
			DecimalFormat df = new DecimalFormat( "#.###" );
			df.setRoundingMode( RoundingMode.HALF_UP );

			if ( errors.size() < 1 )
			{
				return "-";
			}

			Double max = new Double( 0 );
			Double min = Double.MAX_VALUE;
			Double mean = new Double( 0 );
			for ( Double d : errors )
			{
				if ( d > max )
				{
					max = d;
				}
				if ( d < min )
				{
					min = d;
				}
				mean += d;
			}
			mean /= errors.size();

			StringBuilder sb = new StringBuilder();
			sb.append( df.format( mean ) );
			sb.append( ", " );
			sb.append( df.format( min ) );
			sb.append( ", " );
			sb.append( df.format( max ) );

			return sb.toString();
		}

		// should never be reached
		return null;

	}

	@Override
	public void addTableModelListener(TableModelListener l) {decorated.addTableModelListener(l);}

	@Override
	public void removeTableModelListener(TableModelListener l) {decorated.removeTableModelListener(l);}

	@Override
	public void clearSortingFactors() {decorated.clearSortingFactors();}

	@Override
	public void addSortingFactor(Class<? extends Entity> factor) {decorated.addSortingFactor(factor);}

	@Override
	public void clearGroupingFactors() {decorated.clearGroupingFactors();}

	@Override
	public void addGroupingFactor(Class<? extends Entity> factor) {decorated.addGroupingFactor(factor);}

	@Override
	public void clearFilters() {decorated.clearFilters();}

	@Override
	public void addFilter(Class<? extends Entity> cl, List<? extends Entity> instances) {decorated.addFilter(cl, instances);}

	@Override
	public List<List<BasicViewDescription<?>>> getElements() { return decorated.getElements(); }

	@Override
	public void sortByColumn(int column) {
		if (column < decorated.getColumnCount())
			decorated.sortByColumn(column);		
	}

	@Override
	public ExplorerWindow<AS, ?> getPanel() { return decorated.getPanel(); }

	@Override
	public void setStitchingResults(StitchingResults res)
	{
		this.res = res;		
	}

	@Override
	public int getSpecialColumn(SpecialColumnType type)
	{
		return -1;
	}

	@Override
	public void setColumnClasses(List< Class< ? extends Entity > > columnClasses)
	{
		decorated.setColumnClasses( columnClasses );		
	}

	@Override
	public Set< Class< ? extends Entity > > getGroupingFactors(){return decorated.getGroupingFactors();}

	@Override
	public Map< Class< ? extends Entity >, List< ? extends Entity > > getFilters()
	{
		return decorated.getFilters();
	}

}
