package osmgpxtool.filter.writer;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.opengis.feature.simple.SimpleFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import osmgpxtool.filter.GpxFilter;
import osmgpxtool.filter.gpx.schema.Gpx;
import osmgpxtool.filter.gpx.schema.Gpx.Trk;
import osmgpxtool.filter.gpx.schema.Gpx.Trk.Trkseg;
import osmgpxtool.filter.gpx.schema.Gpx.Trk.Trkseg.Trkpt;
import osmgpxtool.filter.metadata.schema.GpxFiles.GpxFile;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.PrecisionModel;
import com.vividsolutions.jts.io.WKBWriter;

public class PGSqlWriter implements Writer {
	static Logger LOGGER = LoggerFactory.getLogger(PGSqlWriter.class);

	private String dbName;
	private String dbUser;
	private String dbPassword;
	private String dbHost;
	private String dbPort;
	private Connection con = null;
	private PreparedStatement insert_info;
	private PreparedStatement insert_data;

	private GpxFilter filter;

	/**
	 * Writer for Gps traces to PostgresQL/PostGIS database. The writer creates
	 * a new table named "gpx_planet". If the table exists already, the old one
	 * will be overwritten.
	 * 
	 * @param dbName
	 * @param dbUser
	 * @param dbPassword
	 * @param dbHost
	 * @param dbPort
	 */
	public PGSqlWriter(GpxFilter filter, String dbName, String dbUser,
			String dbPassword, String dbHost, String dbPort) {
		super();
		this.dbName = dbName;
		this.dbUser = dbUser;
		this.dbPassword = dbPassword;
		this.dbHost = dbHost;
		this.dbPort = dbPort;
		this.filter = filter;
	}

	/**
	 * this method initializes the writer. It connects to the database, creates
	 * relations and prepares the insert statement.
	 */
	@Override
	public void init() {
		LOGGER.info("connect to database: " + dbName + ", " + dbHost + ":"
				+ dbPort + " with user: " + dbUser);
		connectToDatabase();
		LOGGER.info("Connection successful!");
		createTable();

		// prepare insert statement
		try {
			insert_info = con
					.prepareStatement("INSERT INTO gpx_info(gpx_id,tags,points,uid,\"user\",visibility,description) VALUES(?,?,?,?,?,?,?)");
			insert_data = con
					.prepareStatement("INSERT INTO gpx_data(gpx_id,trk_id,trkseg_id,trkpt_id,timestamp,geom) VALUES(?,?,?,?,?,ST_GeomFromEWKB(?))");

		} catch (SQLException e) {
			e.printStackTrace();
			throw new RuntimeException();
		}
	}

	/**
	 * This method prepares database. It recreates the relations for storing the
	 * Gpx data. Two relation are created. gpx_info and gpx_planet
	 */
	private void createTable() {
		Statement create = null;
		try {
			create = con.createStatement();
			create.addBatch("DROP TABLE IF EXISTS gpx_info;");
			create.addBatch("CREATE TABLE gpx_info(\"gpx_id\" integer CONSTRAINT \"gpx_info_id\" PRIMARY KEY,\"tags\" text[],\"points\" integer,\"uid\" integer,\"user\" text,\"visibility\" text,\"description\" text);");
			create.addBatch("DROP TABLE IF EXISTS gpx_data;");
			create.addBatch("CREATE TABLE gpx_data(\"gpx_id\" integer references gpx_info(gpx_id),\"trk_id\" integer,\"trkseg_id\" integer,\"trkpt_id\" integer,\"timestamp\" text,geom geometry(PointZ,4326),PRIMARY KEY(gpx_id, trk_id, trkseg_id, trkpt_id));");
			create.addBatch("DROP INDEX IF EXISTS gpx_data_geom_index;");
			create.addBatch("CREATE INDEX gpx_data_geom_index ON gpx_data USING gist (geom);");
			create.executeBatch();
			create.close();
		} catch (SQLException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	/**
	 * establish database connection
	 */
	private void connectToDatabase() {

		String url = "jdbc:postgresql://" + dbHost + "/" + dbName;

		try {
			con = DriverManager.getConnection(url, dbUser, dbPassword);
			con.setAutoCommit(true);

		} catch (SQLException ex) {
			LOGGER.error("Could not connect to database");
			ex.printStackTrace();
			System.exit(1);
		}

	}

	/**
	 * This method write the given gps traces to the database relation. If the
	 * metadata is null (which shouldn't occur that often) the gps traces will
	 * not be written.
	 * The metadata is written to relation gpx_info. The geometry is written to relation gpx_data as 3D Points.
	 */
	@Override
	public void write(Gpx gpx, String filename, GpxFile metadata) {
		if (metadata == null) {
			LOGGER.warn("Skipped because of missing metadata: " + filename);
		} else if (filter.check(gpx)) {
			writeMetadata(metadata);
			writeGeometry(gpx, metadata.getId());
		}
	}

	private void writeGeometry(Gpx gpx, int gpx_id) {
		GeometryFactory geomF = new GeometryFactory(new PrecisionModel(), 4326);
		WKBWriter wr = new WKBWriter(3, true);
		try {
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
						// TODO find better way to handle data if -e attribute
						// is
						// not set
						if (trkpt.getEle() != null) {
							ele = trkpt.getEle();
						} else {
							ele = new BigDecimal(-999.0);
						}

						// add data to featureBuilder and create feature
						Coordinate c = new Coordinate(trkpt.getLon()
								.doubleValue(), trkpt.getLat().doubleValue(),
								ele.doubleValue());
						if (filter.isInBbox(c)) {
							Point point = geomF.createPoint(c);
							insert_data.setInt(1, gpx_id);
							insert_data.setInt(2, trk_id);
							insert_data.setInt(3, trkseg_id);
							insert_data.setInt(4, trkpt_id);
							if (trkpt.getTime() != null) {
								insert_data.setString(5, trkpt.getTime()
										.toString());
							} else {
								insert_data.setNull(5, java.sql.Types.INTEGER);
							}

							insert_data.setObject(6, wr.write(point),
									java.sql.Types.BINARY);
							// if parameter -c is set (clip at bounding box)

							insert_data.addBatch();

						}
					}
				}
			}
			insert_data.executeBatch();
		} catch (SQLException e) {
			e.printStackTrace();
			System.exit(1);
		}

	}

	private void writeMetadata(GpxFile metadata) {
		try {
			insert_info.clearParameters();
			insert_info.setInt(1, metadata.getId());
			if (metadata.getTags() != null) {
				insert_info.setArray(
						2,
						con.createArrayOf("text", metadata.getTags().getTag()
								.toArray()));
			} else {
				insert_info.setNull(2, java.sql.Types.ARRAY);
			}
			insert_info.setInt(3, metadata.getPoints());
			if (metadata.getUid() != null) {
				insert_info.setInt(4, metadata.getUid());
			} else {
				insert_info.setNull(4, java.sql.Types.INTEGER);
			}
			insert_info.setString(5, metadata.getUser());
			insert_info.setString(6, metadata.getVisibility());
			if (metadata.getDescription() != null) {
				insert_info.setString(7, metadata.getDescription());
			} else {
				insert_info.setNull(7, java.sql.Types.VARCHAR);
			}
			insert_info.executeUpdate();

		} catch (SQLException e) {
			e.printStackTrace();
			System.exit(1);
		}

	}


	@Override
	public void close() {
		// close connection

		try {

			if (insert_info != null) {
				insert_info.close();
			}
			if (insert_data != null) {
				insert_data.close();
			}
			if (con != null) {
				con.close();
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}

	}
}
