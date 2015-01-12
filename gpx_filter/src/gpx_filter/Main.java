/**
 * 
 */
package gpx_filter;

import gpx_filter.gpx.schema.Gpx;
import gpx_filter.metadata.schema.GpxFiles;
import gpx_filter.metadata.schema.GpxFiles.GpxFile;
import gpx_filter.writer.DumpWriter;
import gpx_filter.writer.PGSqlWriter;
import gpx_filter.writer.ShapeFileWriter;
import gpx_filter.writer.Writer;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.stream.StreamSource;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author steffen
 *
 */
public class Main {
	static Logger LOGGER = LoggerFactory.getLogger(Main.class);
	private static String tarFile;
	private static String dbHost;
	private static String dbPort;
	private static String dbName;
	private static String dbPassword;
	private static String dbUser;
	private static String outputFileDump;
	private static String outputFileShape;
	private static boolean elevationOnly;
	private static boolean bboxClip;
	private static Double bboxLeft;
	private static Double bboxRight;
	private static Double bboxTop;
	private static Double bboxBottom;
	private static Writer writer = null;
	private static TreeMap<Integer, GpxFile> metadata = null;
	private static String metadataFilename;

	private static Options cmdOptions;

	public static void main(String[] args) throws CompressorException,
			IOException {
		// read command line arguments
		CommandLine cmd = null;
		HelpFormatter helpFormater = new HelpFormatter();
		CommandLineParser cmdParser = new BasicParser();
		cmdOptions = new Options();
		setupArgumentOptions();
		// parse arguments
		try {
			cmd = cmdParser.parse(cmdOptions, args);
			if (cmd.hasOption('h')) {
				helpFormater.printHelp("Main.class", cmdOptions);
				return;
			}
			assignArguments(cmd);
		} catch (ParseException parseException) {
			LOGGER.info(parseException.getMessage());
			helpFormater.printHelp("Main.class", cmdOptions);
			return;
		}

		
		// init Filter
		GpxFilter filter = new GpxFilter(bboxLeft, bboxRight, bboxBottom,
				bboxTop,bboxClip, elevationOnly );
		
		// initialize reader
		TarArchiveInputStream tarIn = new TarArchiveInputStream(
				new CompressorStreamFactory().createCompressorInputStream(
						CompressorStreamFactory.XZ, new BufferedInputStream(
								new FileInputStream(tarFile))));
	
		readMetadata();

		// init writer
		if (cmd.hasOption("wd")) {
			writer = new DumpWriter(filter, outputFileDump, metadataFilename);
		} else if (cmd.hasOption("wpg")) {
			writer = new PGSqlWriter(filter, dbName, dbUser, dbPassword, dbHost, dbPort);
		}else if (cmd.hasOption("ws")){
			writer = new ShapeFileWriter(outputFileShape, filter);
		}
		writer.init();

	

		TarArchiveEntry tarEntry;
		int processed = 0;
		int filterPassed = 0;
		int filterRejected = 0;


		while ((tarEntry = tarIn.getNextTarEntry()) != null) {
			if (tarEntry.isFile()) {
				if (isGPX(tarEntry.getName())) {
					byte[] content = new byte[(int) tarEntry.getSize()];
					tarIn.read(content);
					// TODO: parse GPX file
					Gpx gpx = null;
					try {
						JAXBContext jc = JAXBContext
								.newInstance("gpx_filter.gpx.schema");
						Unmarshaller unmarshaller = jc.createUnmarshaller();
						JAXBElement<Gpx> root = (JAXBElement<Gpx>) unmarshaller
								.unmarshal(new StreamSource(
										new ByteArrayInputStream(content)),
										Gpx.class);
						gpx = root.getValue();
						int id = getGpxId(tarEntry);

		
							writer.write(gpx, tarEntry.getName(),
									metadata.get(id));
			
					} catch (JAXBException ex) {
						ex.printStackTrace();
					}
				}
			}

			processed++;
			LOGGER.info(processed+"");
		}
		tarIn.close();
		writer.close();
		filter.printStats();
		LOGGER.info("files processed:       " + processed);


	}

	private static void readMetadata() throws IOException, CompressorException {
		// initialize reader
		TarArchiveInputStream tarIn = new TarArchiveInputStream(
				new CompressorStreamFactory().createCompressorInputStream(
						CompressorStreamFactory.XZ, new BufferedInputStream(
								new FileInputStream(tarFile))));

		TarArchiveEntry tarEntry;
		while ((tarEntry = tarIn.getNextTarEntry()) != null) {
			if (isMetaXML(tarEntry.getName())) {
				//TODO: add argument to constructor of Dumpwriter
				metadataFilename=tarEntry.getName();
				LOGGER.info("save metadata to memory");
				byte[] content = new byte[(int) tarEntry.getSize()];
				tarIn.read(content);
				// parse metadata file
				try {
					JAXBContext jc = JAXBContext
							.newInstance("gpx_filter.metadata.schema");
					Unmarshaller unmarshaller = jc.createUnmarshaller();
					JAXBElement<GpxFiles> root = (JAXBElement<GpxFiles>) unmarshaller
							.unmarshal(new StreamSource(
									new ByteArrayInputStream(content)),
									GpxFiles.class);
					GpxFiles gpxFiles = root.getValue();
					metadata = new TreeMap<Integer, GpxFile>();
					List<GpxFile> gpxFileList = gpxFiles.getGpxFile();
					for (int w = 0; w < gpxFileList.size(); w++) {
						GpxFile meta = gpxFileList.get(w);
						metadata.put(meta.getId(), meta);
					}

				} catch (JAXBException ex) {
					ex.printStackTrace();
				}
			}

		}
		tarIn.close();
		LOGGER.info("metadata successfully stored. GpxFiles: "
				+ metadata.size());
	}

	private static int getGpxId(TarArchiveEntry tarEntry) {
		String n = tarEntry.getName();
		return Integer.valueOf(n.substring(n.lastIndexOf("/") + 1,
				n.lastIndexOf(".")));

	}

	private static void setupArgumentOptions() {
		// parse command line arguments
		cmdOptions.addOption(new Option("h", "help", false, "displays help"));
		// option for input GPX dump packed and compressed (as *.tar.xz)
		cmdOptions.addOption(OptionBuilder.withLongOpt("input")
				.withDescription("path to gpx-planet.tar.xz").hasArg()
				.create("i"));
		// option for bounding box
		cmdOptions.addOption(OptionBuilder.withLongOpt("bounding-box")
				.withDescription("specifies bounding box").hasArgs(4)
				.withArgName("left=3.9> <right=4.5> <top=50.2> <bottom=50.0")
				.withValueSeparator(' ').create("bbox"));
		// filter option elevation
		cmdOptions.addOption(new Option("e", "elevation", false,
				"only use GPX-files if they have elevation information"));
		cmdOptions.addOption(new Option("c", "Clip Bounding Box", false,
				"Clip GPS traces at bounding box. This option is only applied for PQSql and Shape output."));
		// writer options (check if only one writer options of these three are
		// selected)
		cmdOptions.addOption(OptionBuilder.withLongOpt("write-shape")
				.withDescription("path to output shape file").hasArg()
				.create("ws"));
		cmdOptions.addOption(OptionBuilder.withLongOpt("write-dump")
				.withDescription("path to output dump file (gpx-planet.tar.xz")
				.hasArg().create("wd"));
		cmdOptions.addOption(OptionBuilder.withLongOpt("write-pqsql")
				.withDescription("connection parameters for database")
				.hasArgs(5).withArgName("db> <user> <password> <host> <port")
				.withValueSeparator(' ').create("wpg"));

	}

	private static void assignArguments(CommandLine cmd) throws ParseException {
		// assign values to variables
		tarFile = cmd.getOptionValue("i");
		elevationOnly = cmd.hasOption("e");
		bboxClip = cmd.hasOption("c");
		outputFileDump = cmd.getOptionValue("wd");
		outputFileShape = cmd.getOptionValue("ws");

		// parse boundingbox attribute
		if (cmd.hasOption("bbox")) {
			HashMap<String, Double> bboxMap = new HashMap<String, Double>();
			for (String n : cmd.getOptionValues("bbox")) {
				bboxMap.put(n.split("=")[0], Double.valueOf(n.split("=")[1]));
			}
			if (checkBbox(bboxMap)) {
				bboxLeft = bboxMap.get("left");
				bboxRight = bboxMap.get("right");
				bboxBottom = bboxMap.get("bottom");
				bboxTop = bboxMap.get("top");
			} else {
				throw new ParseException(
						"Bounding Box arguments not valid. Check \"-h\" for help ");
			}
		}

		// parse database attributes
		if (cmd.hasOption("wpg")) {
			HashMap<String, String> dbMap = new HashMap<String, String>();
			for (String n : cmd.getOptionValues("wpg")) {
				dbMap.put(n.split("=")[0], n.split("=")[1]);
			}
			if (checkDbParamaters(dbMap)) {
				dbName = dbMap.get("db");
				dbUser = dbMap.get("user");
				dbPassword = dbMap.get("password");
				dbHost = dbMap.get("host");
				dbPort = dbMap.get("port");
			} else {
				throw new ParseException(
						"Database arguments not valid. Check \"-h\" for help ");
			}
		}
	}

	private static boolean checkDbParamaters(HashMap<String, String> dbMap) {
		if (!dbMap.containsKey("host")) {
			LOGGER.error("Database parameter missing: host");
			return false;
		}
		if (!dbMap.containsKey("port")) {
			LOGGER.error("Database parameter missing: port");
			return false;
		}
		if (!dbMap.containsKey("db")) {
			LOGGER.error("Database parameter missing: db");
			return false;
		}
		if (!dbMap.containsKey("user")) {
			LOGGER.error("Database parameter missing: user");
			return false;
		}
		if (!dbMap.containsKey("password")) {
			LOGGER.error("Database parameter missing: password");
			return false;
		}
		return true;
	}

	private static boolean checkBbox(HashMap<String, Double> bbox) {
		if (!bbox.containsKey("left")) {
			LOGGER.error("left bounding box coordinate not in argument list");
			return false;
		}
		if (bbox.get("left") <= -180 || bbox.get("left") >= 180) {
			LOGGER.error("left box coordinate not within range: "
					+ bbox.get("left"));
			return false;
		}
		if (!bbox.containsKey("right")) {
			LOGGER.error("right bounding box coordinate not in argument list");
			return false;
		}
		if (bbox.get("left") <= -180 || bbox.get("left") >= 180) {
			LOGGER.error("left box coordinate not within range: "
					+ bbox.get("left"));
			return false;
		}

		if (!bbox.containsKey("top")) {
			LOGGER.error("top bounding box coordinate not in argument list");
			return false;
		}
		if (bbox.get("top") <= -90 || bbox.get("top") >= 90) {
			LOGGER.error("top bounding box coordinate not within range: "
					+ bbox.get("top"));
			return false;
		}
		if (!bbox.containsKey("bottom")) {
			LOGGER.error("top bounding box coordinate not in argument list");
			return false;
		}
		if (bbox.get("bottom") <= -90 || bbox.get("top") >= 90) {
			LOGGER.error("bottom bounding box coordinate not within range: "
					+ bbox.get("bottom"));
			return false;
		}
		return true;
	}

	private static boolean isMetaXML(String name) {
		if (name.endsWith("metadata.xml")) {
			return true;
		} else
			return false;
	}

	private static boolean isGPX(String name) {
		if (name.endsWith(".gpx")) {
			return true;
		} else
			return false;
	}
}
