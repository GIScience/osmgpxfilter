package osmgpxtool.filter.writer;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;

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
import com.vividsolutions.jts.geom.PrecisionModel;
import com.vividsolutions.jts.io.WKBWriter;

/**
 * 
 * this class writes a multilinestring to Postgis data. it is marked as
 * deprecated, since the program currently imports Points instead of
 * MultiLineString. It is kept in case it needed at later stage.
 *
 */

public class PGSqlMultilineWriter implements Writer {
	static Logger LOGGER = LoggerFactory.getLogger(PGSqlMultilineWriter.class);

	private String dbName;
	private String dbUser;
	private String dbPassword;
	private String dbHost;
	private String dbPort;
	private Connection con = null;
	private PreparedStatement insert;
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
	public PGSqlMultilineWriter(GpxFilter filter, String dbName, String dbUser,
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
			insert = con
					.prepareStatement("INSERT INTO gpx_data_line(gpx_id,tags,points,uid,\"user\",visibility,description,geom) VALUES(?,?,?,?,?,?,?,ST_GeomFromEWKB(?))");
		} catch (SQLException e) {
			e.printStackTrace();
			throw new RuntimeException();
		}
	}

	/**
	 * This method prepares database. It recreates the relations for storing the
	 * Gpx data.
	 */
	private void createTable() {
		Statement create = null;
		try {
			create = con.createStatement();
			create.addBatch("DROP TABLE IF EXISTS gpx_data_line;");
			create.addBatch("CREATE TABLE gpx_data_line(\"gpx_id\" integer CONSTRAINT \"gpx_id\" PRIMARY KEY,\"tags\" text[],\"points\" integer,\"uid\" integer,\"user\" text,\"visibility\" text,\"description\" text,\"geom\" geometry(MultiLineStringZ,4326));");
			create.addBatch("DROP INDEX IF EXISTS gpx_data_line_geom_index;");
			create.addBatch("CREATE INDEX gpx_data_line_geom_index ON gpx_data_line USING gist (geom);");
			create.executeBatch();
			create.close();
		} catch (SQLException e) {
			e.printStackTrace();
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
	 */
	@Override
	public void write(Gpx gpx, String filename, GpxFile metadata) {
		if (metadata == null) {
			LOGGER.warn("Skipped because of missing metadata: " + filename);
		} else if (filter.check(gpx)) {
			// prepare geometry
			MultiLineString geom = prepareGeometry(gpx);
			if (geom != null) {
				WKBWriter wr = new WKBWriter(3, true);
				try {
					insert.clearParameters();
					insert.setInt(1, metadata.getId());
					if (metadata.getTags() != null) {
						insert.setArray(
								2,
								con.createArrayOf("text", metadata.getTags()
										.getTag().toArray()));
					} else {
						insert.setNull(2, java.sql.Types.ARRAY);
					}
					insert.setInt(3, metadata.getPoints());
					if (metadata.getUid() != null) {
						insert.setInt(4, metadata.getUid());
					} else {
						insert.setNull(4, java.sql.Types.INTEGER);
					}
					insert.setString(5, metadata.getUser());
					insert.setString(6, metadata.getVisibility());
					if (metadata.getDescription() != null) {
						insert.setString(7, metadata.getDescription());
					} else {
						insert.setNull(7, java.sql.Types.VARCHAR);
					}
					insert.setObject(8, wr.write(geom), java.sql.Types.BINARY);

					insert.executeUpdate();
					// LOGGER.info("insert: " + metadata.getId());

				} catch (SQLException e) {
					e.printStackTrace();
					System.exit(1);
				}

			}
		}
	}

	/**
	 * This method prepares the geometry of the given gps trace. It returns a
	 * Multilinestring, containing Linestrings for each track segment.
	 * 
	 * @param gpx
	 * @returns null, if MultiLineString is empty
	 */
	private MultiLineString prepareGeometry(Gpx gpx) {
		GeometryFactory geomF = new GeometryFactory(new PrecisionModel(), 4326);
		ArrayList<LineString> linestrings = new ArrayList<LineString>();

		// loop through tracks
		for (int a = 0; a < gpx.getTrk().size(); a++) {
			Trk trk = gpx.getTrk().get(a);
			// loop through track segements
			for (int i = 0; i < trk.getTrkseg().size(); i++) {
				Trkseg seg = trk.getTrkseg().get(i);
				/*
				 * each tracksegment needs at least 2 trackpoints, therefore
				 * skip tracksegments having only one trackpoint
				 */
				ArrayList<Coordinate> coords = new ArrayList<Coordinate>();
				// loop through trackpoints
				for (int u = 0; u < seg.getTrkpt().size(); u++) {
					Trkpt trkpt = seg.getTrkpt().get(u);
					BigDecimal ele;
					// TODO find better way to handle data if -e attribute is
					// not set
					if (trkpt.getEle() != null) {
						ele = trkpt.getEle();
					} else {
						ele = new BigDecimal(-999.0);
					}
					Coordinate c = new Coordinate(trkpt.getLon().doubleValue(),
							trkpt.getLat().doubleValue(), ele.doubleValue());

					if (filter.isInBbox(c)) {
						coords.add(c);
					} else {
						if (coords.size() > 1) {
							linestrings.add(geomF.createLineString(coords
									.toArray(new Coordinate[coords.size()])));
						}
						coords = new ArrayList<Coordinate>();
					}
				}
				// avoid linestring with only one point
				if (coords.size() > 1) {
					linestrings.add(geomF.createLineString(coords
							.toArray(new Coordinate[coords.size()])));
				}

			}
		}
		if (linestrings.size() > 0) {
			return geomF.createMultiLineString(linestrings
					.toArray(new LineString[linestrings.size()]));
		} else {
			return null;
		}

	}

	@Override
	public void close() {
		// close connection

		try {

			if (insert != null) {
				insert.close();
			}
			if (con != null) {
				con.close();
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}

	}
}
