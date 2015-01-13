package osmgpxtool.gpxfilter.writer;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.geotools.data.DefaultTransaction;
import org.geotools.data.Transaction;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import osmgpxtool.gpxfilter.GpxFilter;
import osmgpxtool.gpxfilter.gpx.schema.Gpx;
import osmgpxtool.gpxfilter.gpx.schema.Gpx.Trk;
import osmgpxtool.gpxfilter.gpx.schema.Gpx.Trk.Trkseg;
import osmgpxtool.gpxfilter.gpx.schema.Gpx.Trk.Trkseg.Trkpt;
import osmgpxtool.gpxfilter.metadata.schema.GpxFiles.GpxFile;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;

public class ShapeFileWriter implements Writer {
	static Logger LOGGER = LoggerFactory.getLogger(ShapeFileWriter.class);
	private String outFile;
	private GpxFilter filter;
	private File file;
	private GeometryFactory geomF;
	private ShapefileDataStore dataStore;
	private SimpleFeatureBuilder featureBuilder;
	private Transaction transaction;
	private SimpleFeatureStore featureStore;

	/**
	 * Writer for gps traces in shapefile as 3D points with corresponding
	 * attributes.
	 * 
	 * @param outFile
	 * @param filter
	 */
	public ShapeFileWriter(String outFile, GpxFilter filter) {
		super();
		this.outFile = outFile;
		this.filter = filter;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gpx_filter.writer.Writer#init()
	 */
	@Override
	public void init() {
		LOGGER.info("Output as shapefile: " + outFile);
		file = new File(outFile);

		try {
			// Define Schema
			final SimpleFeatureType TYPE = createFeatureType();
			featureBuilder = new SimpleFeatureBuilder(TYPE);

			geomF = new GeometryFactory();
			ShapefileDataStoreFactory dataStoreFactory = new ShapefileDataStoreFactory();

			// define parameter for ShapeFileDataStore object
			Map<String, Serializable> params = new HashMap<String, Serializable>();
			params.put("url", file.toURI().toURL());
			params.put("create spatial index", Boolean.TRUE);

			dataStore = (ShapefileDataStore) dataStoreFactory
					.createNewDataStore(params);
			dataStore.createSchema(TYPE);

			// define transaction
			transaction = new DefaultTransaction("create");
			String typeName = dataStore.getTypeNames()[0];
			SimpleFeatureSource featureSource = dataStore
					.getFeatureSource(typeName);
			if (featureSource instanceof SimpleFeatureStore) {
				featureStore = (SimpleFeatureStore) featureSource;

				featureStore.setTransaction(transaction);
			}
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}

	private SimpleFeatureType createFeatureType() {

		SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
		builder.setName("GPX");
		builder.setCRS(DefaultGeographicCRS.WGS84);

		// add attributes in order
		builder.add("the_geom", Point.class);
		builder.add("gpx_id", Integer.class);
		builder.add("trk_id", Integer.class);
		builder.add("seg_id", Integer.class);
		builder.add("trkpt_id", Integer.class);
		builder.add("timestamp", String.class);
		builder.add("ele", Double.class);

		return builder.buildFeatureType();
	}

	@Override
	public void write(Gpx gpx, String filename, GpxFile metadata) {
		if (metadata == null) {
			LOGGER.warn("Skipped because of missing metadata: "+ filename);
		} else {
			if (filter.check(gpx)) {

				List<SimpleFeature> featureList = gpxToFeatureList(gpx,
						metadata.getId());
				SimpleFeatureCollection collection = new ListFeatureCollection(
						featureStore.getSchema(), featureList);
				/*
				 * Write the featurecollection to the shapefile
				 */
				try {

					featureStore.addFeatures(collection);

					// save features to file
					transaction.commit();
				} catch (IOException e) {
					LOGGER.error("Error while writing to shapefile. Last transaction is beeing rolled back.");
					try {
						transaction.rollback();
					} catch (IOException e1) {
						LOGGER.error("Could not roll back transaction.");
						e1.printStackTrace();
					}
					e.printStackTrace();
				}
			}
		}
	}

	private List<SimpleFeature> gpxToFeatureList(Gpx gpx, int gpx_id) {
		List<SimpleFeature> featureList = new ArrayList<SimpleFeature>();
		// loop through tracks
		for (int trk_id = 0; trk_id < gpx.getTrk().size(); trk_id++) {
			Trk trk = gpx.getTrk().get(trk_id);
			// loop through track segements
			for (int trkseg_id = 0; trkseg_id < trk.getTrkseg().size(); trkseg_id++) {
				Trkseg seg = trk.getTrkseg().get(trkseg_id);

				// loop through trackpoints
				for (int trkpt_id = 0; trkpt_id < seg.getTrkpt().size(); trkpt_id++) {
					Trkpt trkpt = seg.getTrkpt().get(trkpt_id);
					BigDecimal ele;
					// TODO find better way to handle data if -e attribute is
					// not set
					if (trkpt.getEle() != null) {
						ele = trkpt.getEle();
					} else {
						ele = new BigDecimal(-999.0);
					}

					// add data to featureBuilder and create feature
					// Coordinate c = new
					// Coordinate(trkpt.getLon().doubleValue(),
					// trkpt.getLat().doubleValue(), ele.doubleValue());
					Coordinate c = new Coordinate(trkpt.getLon().doubleValue(),
							trkpt.getLat().doubleValue(), ele.doubleValue());
					Point point = geomF.createPoint(c);
					featureBuilder.add(point);
					featureBuilder.add(gpx_id);
					featureBuilder.add(trk_id);
					featureBuilder.add(trkseg_id);
					featureBuilder.add(trkpt_id);
					if (trkpt.getTime() != null) {
						featureBuilder.add(trkpt.getTime().toString());
					} else {
						featureBuilder.add(null);
					}

					featureBuilder.add(ele.doubleValue());

					SimpleFeature feature = featureBuilder.buildFeature(null);
					// if parameter -c is set (clip at bounding box)
					if (filter.isInBbox(c)) {
						featureList.add(feature);
					}
				}

			}
		}
		if (featureList.isEmpty()) {
			return null;
		} else {
			return featureList;
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gpx_filter.writer.Writer#close()
	 */
	@Override
	public void close() {
		try {
			if (transaction != null) {
				transaction.close();
			}
		} catch (IOException e) {
			LOGGER.error("could not close transaction");
			e.printStackTrace();
		}

	}

}
