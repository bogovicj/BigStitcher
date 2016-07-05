package input;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import bdv.BigDataViewer;
import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.generic.sequence.ImgLoaderHint;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.registration.ViewTransform;
import mpicbg.spim.data.registration.ViewTransformAffine;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.MissingViews;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.SetupImgLoader;
import mpicbg.spim.data.sequence.Tile;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.TimePoints;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Dimensions;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.algorithm.phasecorrelation.ImgLib2Util;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.numeric.complex.ComplexDoubleType;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;


public class FractalSpimDataGenerator
{
	private RealRandomAccessible< LongType > ra;
	private JuliaRealRandomAccessible fractalRA;
	
	public FractalSpimDataGenerator(AffineGet transform){
		fractalRA = new JuliaRealRandomAccessible(new ComplexDoubleType( -0.4, 0.6 ), 100, 100);
		ra = RealViews.affineReal( fractalRA, transform );
	}
	
	public RandomAccessibleInterval< LongType > getImageAtInterval(Interval interval){
		IterableInterval< LongType > raiT = Views.iterable( Views.zeroMin( Views.interval( Views.raster( ra ), interval) ) );
		Img<LongType> resImg = new ArrayImgFactory<LongType>().create( raiT, new LongType() );
		ImgLib2Util.copyRealImage(raiT, resImg) ;
		return resImg;
	}
		
	/**
	 * 
	 * @param n number of tiles in x
	 * @param m number of tiles in y
	 * @param overlap overlap e (0-1)
	 * @return
	 */
	public static List<Interval> generateTileList(Interval start, int n, int m, double overlap)
	{
		List<Interval> res = new ArrayList<>();
		for (int x = 0; x < n; ++x){
			for (int y = 0; y < m; ++y){
				
				Interval tInterval = new FinalInterval( start );
				tInterval = Intervals.translate( tInterval, (long) ( x * (1 - overlap) * start.dimension( 0 ) ), 0 );
				tInterval = Intervals.translate( tInterval, (long) ( y * (1 - overlap) * start.dimension( 1 ) ), 1 );
				res.add( tInterval );
			}
		}
		return res;
	}
	

	
	public SpimData generateSpimData(final List<Interval> intervals)
	{
		final ArrayList< ViewSetup > setups = new ArrayList< ViewSetup >();
		final ArrayList< ViewRegistration > registrations = new ArrayList< ViewRegistration >();

		final Channel c0 = new Channel( 0 );
		final Angle a0 = new Angle( 0 );
		final Illumination i0 = new Illumination( 0 );
		
		final Dimensions d0 = intervals.get(0);
		final VoxelDimensions vd0 = new FinalVoxelDimensions("px", 1.0, 1.0, 1.0);
		
		for (int i = 0; i < intervals.size(); ++i)
		{
			double[] pos = new double[intervals.get( 0 ).numDimensions()];
			intervals.get( i ).realMin( pos );
			final Tile t = new Tile( i, "Tile " + i, pos );
			setups.add( new ViewSetup( i, "setup " + i, d0, vd0, t, c0, a0, i0 ) );
		}

		final ArrayList< TimePoint > t = new ArrayList< TimePoint >();
		t.add( new TimePoint( 0 ) );
		final TimePoints timepoints = new TimePoints( t );

		final ArrayList< ViewId > missing = new ArrayList< ViewId >();
		final MissingViews missingViews = new MissingViews( missing );

		final ImgLoader imgLoader = new ImgLoader()
		{
			@Override
			public SetupImgLoader< ? > getSetupImgLoader( final int setupId )
			{
				return new SetupImgLoader< LongType >()
				{

					@Override
					public RandomAccessibleInterval< LongType > getImage(int timepointId, ImgLoaderHint... hints)
					{
						return getImageAtInterval( intervals.get( setupId ));
					}

					@Override
					public LongType getImageType() {return new LongType();}
					

					@Override
					public RandomAccessibleInterval< FloatType > getFloatImage(int timepointId, boolean normalize,
							ImgLoaderHint... hints)
					{
						return Converters.convert( getImage( timepointId, hints ), new Converter< LongType, FloatType >()
						{
							@Override
							public void convert(LongType input, FloatType output){output.setReal( input.getRealDouble() );}
						}, new FloatType() );
						
					}

					@Override
					public Dimensions getImageSize(int timepointId)
					{
						return intervals.get( 0 );
					}

					@Override
					public VoxelDimensions getVoxelSize(int timepointId)
					{
						return vd0;
					}
					
				};
			}
		};

		for ( final ViewSetup vs : setups )
		{
			final ViewRegistration vr = new ViewRegistration( t.get( 0 ).getId(), vs.getId() );

			final Tile tile = vs.getTile();

			final AffineTransform3D translation = new AffineTransform3D();

			if ( tile.hasLocation() )
			{
				translation.set( tile.getLocation()[ 0 ], 0, 3 );
				translation.set( tile.getLocation()[ 1 ], 1, 3 );
				translation.set( tile.getLocation()[ 2 ], 2, 3 );
			}

			vr.concatenateTransform( new ViewTransformAffine( "Translation", translation ) );

			final double minResolution = Math.min( Math.min( vs.getVoxelSize().dimension( 0 ), vs.getVoxelSize().dimension( 1 ) ), vs.getVoxelSize().dimension( 2 ) );
			
			final double calX = vs.getVoxelSize().dimension( 0 ) / minResolution;
			final double calY = vs.getVoxelSize().dimension( 1 ) / minResolution;
			final double calZ = vs.getVoxelSize().dimension( 2 ) / minResolution;
			
			final AffineTransform3D m = new AffineTransform3D();
			m.set( calX, 0.0f, 0.0f, 0.0f, 
				   0.0f, calY, 0.0f, 0.0f,
				   0.0f, 0.0f, calZ, 0.0f );
			final ViewTransform vt = new ViewTransformAffine( "Calibration", m );
			vr.preconcatenateTransform( vt );

			vr.updateModel();		
			
			registrations.add( vr );
		}

		final SequenceDescription sd = new SequenceDescription( timepoints, setups, imgLoader, missingViews );
		final SpimData data = new SpimData( new File( "" ), sd, new ViewRegistrations( registrations ) );

		return data;
		
	}
	
	public static void main(String[] args)
	{
		Interval start = new FinalInterval( new long[] {0,0,0},  new long[] {100, 100, 1});
		List<Interval> res = generateTileList( start, 3, 3, 0.2 );
		for (Interval i : res){
			System.out.println("(" + Long.toString( i.min( 0 )) + "," + Long.toString( i.min( 1 )) + ")");
		}
		
		final AffineTransform3D m = new AffineTransform3D();
		double scale = 300;
		m.set( scale, 0.0f, 0.0f, 0.0f, 
			   0.0f, scale, 0.0f, 0.0f,
			   0.0f, 0.0f, scale, 0.0f );
		
		new BigDataViewer( new FractalSpimDataGenerator( m).generateSpimData( res ),
				"", null );
		
		/*
		new ImageJ();
		RandomAccessibleInterval< LongType > rai = new FractalSpimDataGenerator( new AffineTransform2D() ).getImage( res.get( 0 ) );
		ImageJFunctions.show( rai );
		*/
	}
}
