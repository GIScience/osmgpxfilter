/*|----------------------------------------------------------------------------------------------
 *|														Heidelberg University
 *|	  _____ _____  _____      _                     	Department of Geography		
 *|	 / ____|_   _|/ ____|    (_)                    	Chair of GIScience
 *|	| |  __  | | | (___   ___ _  ___ _ __   ___ ___ 	(C) 2014
 *|	| | |_ | | |  \___ \ / __| |/ _ \ '_ \ / __/ _ \	
 *|	| |__| |_| |_ ____) | (__| |  __/ | | | (_|  __/	Berliner Strasse 48								
 *|	 \_____|_____|_____/ \___|_|\___|_| |_|\___\___|	D-69120 Heidelberg, Germany	
 *|	        	                                       	http://www.giscience.uni-hd.de
 *|								
 *|----------------------------------------------------------------------------------------------*/

package osmgpxtool.filter;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
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

import osmgpxtool.filter.gpx.schema.Gpx;
import osmgpxtool.filter.metadata.schema.GpxFiles;
import osmgpxtool.filter.metadata.schema.GpxFiles.GpxFile;
import osmgpxtool.filter.writer.DumpWriter;
import osmgpxtool.filter.writer.PGSqlMultilineWriter;
import osmgpxtool.filter.writer.PGSqlWriter;
import osmgpxtool.filter.writer.ShapeFileWriter;
import osmgpxtool.filter.writer.Writer;
import osmgpxtool.util.Progress;
import osmgpxtool.util.TimeTools;

public class Main {
	static Logger LOGGER = LoggerFactory.getLogger(Main.class);
	private static String tarFile;
	private static String dbHost;
	private static String dbPort;
	private static String dbName;
	private static String dbPassword;
	private static String dbUser;
	private static String dbGeometry;
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
	private static CommandLine cmd = null;

	public static void main(String[] args) throws CompressorException,
			IOException {
		long tStart= System.currentTimeMillis();

		parseArguments(args);

		// init Filter
		GpxFilter filter = new GpxFilter(bboxLeft, bboxRight, bboxBottom,
				bboxTop, bboxClip, elevationOnly);

		// initialize reader
		TarArchiveInputStream tarIn = new TarArchiveInputStream(
				new CompressorStreamFactory().createCompressorInputStream(
						CompressorStreamFactory.XZ, new BufferedInputStream(
								new FileInputStream(tarFile))));
int gpxFileListSize = readMetadata();

		// init writer
		if (cmd.hasOption("wd")) {
			writer = new DumpWriter(filter, outputFileDump, metadataFilename);
		} else if (cmd.hasOption("wpg")) {
			if (dbGeometry.equals("point")){
				writer = new PGSqlWriter(filter, dbName, dbUser, dbPassword,
						dbHost, dbPort);
			}else if (dbGeometry.equals("linestring")){
				writer = new PGSqlMultilineWriter(filter, dbName, dbUser, dbPassword, dbHost, dbPort);
			}
		
		} else if (cmd.hasOption("ws")) {
			writer = new ShapeFileWriter(outputFileShape, filter);
		}
		writer.init();

		TarArchiveEntry tarEntry;
		LOGGER.info("Start processing "+gpxFileListSize+" gpx files...");
		Progress p = new Progress();
		p.start(gpxFileListSize);
		int progressPercentPrinted = -1;
		while ((tarEntry = tarIn.getNextTarEntry()) != null) {
			if (tarEntry.isFile()) {
				if (isGPX(tarEntry.getName())) {
					p.increment();
					int currentProgressPercent = (int)(Math.round(p.getProgressPercent()));
					if (currentProgressPercent % 5 == 0 && currentProgressPercent != progressPercentPrinted) {
						LOGGER.info(p.getProgressMessage());
						progressPercentPrinted = currentProgressPercent;
					}
					byte[] content = new byte[(int) tarEntry.getSize()];
					tarIn.read(content);
					// write GPX file with specified writer
					Gpx gpx = null;
					try {
						JAXBContext jc = JAXBContext
								.newInstance("osmgpxtool.filter.gpx.schema");
						Unmarshaller unmarshaller = jc.createUnmarshaller();
						JAXBElement<Gpx> root = (JAXBElement<Gpx>) unmarshaller
								.unmarshal(new StreamSource(
										new ByteArrayInputStream(content)),
										Gpx.class);
						gpx = root.getValue();
						int id = getGpxId(tarEntry);

						writer.write(gpx, tarEntry.getName(), metadata.get(id));

					} catch (JAXBException ex) {
						ex.printStackTrace();
					}
				}
			}

		}
		tarIn.close();
		writer.close();
		filter.printStats();
		long executionTime= (System.currentTimeMillis() - tStart)/1000; //time in seconds
		LOGGER.info("Filter task done... Execution time: "+executionTime+" seconds ("+TimeTools.convertMillisToHourMinuteSecond(executionTime)+")");
	}

	private static void parseArguments(String[] args) {
		// read command line arguments
		HelpFormatter helpFormater = new HelpFormatter();
		helpFormater.setWidth(Integer.MAX_VALUE);
		CommandLineParser cmdParser = new BasicParser();
		cmdOptions = new Options();
		setupArgumentOptions();
		// parse arguments
		try {
			cmd = cmdParser.parse(cmdOptions, args);
			if (cmd.hasOption('h')) {
				helpFormater.printHelp("GPX Filter", cmdOptions, true);
				System.exit(0);
			}
			assignArguments(cmd);
		} catch (ParseException parseException) {
			LOGGER.info(parseException.getMessage());
			helpFormater.printHelp("GPX Filter", cmdOptions);
			System.exit(1);
		}
	}

	private static void setupArgumentOptions() {
		// parse command line arguments
		cmdOptions.addOption(new Option("h", "help", false, "displays help"));
		// option for input GPX dump packed and compressed (as *.tar.xz)
		cmdOptions.addOption(OptionBuilder.withLongOpt("input")
				.withDescription("path to gpx-planet.tar.xz").hasArg()
				.create("i"));
		cmdOptions.addOption(new Option("e", "elevation", false,
				"only use GPX-files if they have elevation information"));
		cmdOptions
				.addOption(new Option(
						"c",
						"Clip",
						false,
						"Clip GPS traces at bounding box. This option is only applied for PQSql and Shape output."));

		// option for bounding box
		cmdOptions.addOption(OptionBuilder.withLongOpt("bounding-box")
				.withDescription("specifies bounding box").hasArgs(4)
				.withArgName("left=x.x> <right=x.x> <top=x.x> <bottom=x.x")
				.withValueSeparator(' ').create("bbox"));
		// filter option elevation
		// writer options
		cmdOptions.addOption(OptionBuilder.withLongOpt("write-shape")
				.withDescription("path to output shape file").hasArg()
				.withArgName("path to output shape file").create("ws"));
		cmdOptions.addOption(OptionBuilder.withLongOpt("write-dump")
				.withDescription("path to output dump file (gpx-planet.tar.xz")
				.hasArg().withArgName("path to output.tar.xz").create("wd"));
		cmdOptions
				.addOption(OptionBuilder
						.withLongOpt("write-pqsql")
						.withDescription("connection parameters for database. Supported Geometry: ")
						.hasArgs(6)
						.withArgName(
								"db=gis> <user=gisuser> <password=xxx> <host=localhost> <port=5432> <geometry=[linestring,point]")
						.withValueSeparator(' ').create("wpg"));
	}

	private static void assignArguments(CommandLine cmd) throws ParseException {
		// assign values to variables

		if (cmd.getOptionValue("i") != null
				&& new File(cmd.getOptionValue("i")).exists()) {
			tarFile = cmd.getOptionValue("i");
		} else {
			throw new ParseException(
					"No input file given or it doesn't exist. Check \"-h\" for help ");
		}
		elevationOnly = cmd.hasOption("e");
		bboxClip = cmd.hasOption("c");
		outputFileDump = cmd.getOptionValue("wd");

		outputFileShape = cmd.getOptionValue("ws");

		// parse boundingbox attribute
		if (cmd.hasOption("bbox")) {
			if (cmd.getOptionValues("bbox").length == 4) {
				HashMap<String, Double> bboxMap = new HashMap<String, Double>();
				try {
					for (String n : cmd.getOptionValues("bbox")) {
						bboxMap.put(n.split("=")[0],
								Double.valueOf(n.split("=")[1]));
					}
				} catch (ArrayIndexOutOfBoundsException e) {
					throw new ParseException(
							"Bounding box arguments not valid. Did you use \"=\" to seperate key and value? Check \"-h\" for help ");
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
			} else {
				throw new ParseException(
						"Bounding Box arguments not valid. Wrong number of arguments: "
								+ cmd.getOptionValues("bbox").length
								+ " Check \"-h\" for help ");
			}

		}

		// parse database attributes
		if (cmd.hasOption("wpg")) {
			if (cmd.getOptionValues("wpg").length == 6) {

				HashMap<String, String> dbMap = new HashMap<String, String>();
				try {
					for (String n : cmd.getOptionValues("wpg")) {
						dbMap.put(n.split("=")[0], n.split("=")[1]);
					}
				} catch (ArrayIndexOutOfBoundsException e) {
					throw new ParseException(
							"Database arguments not valid. Did you use \"=\" to seperate key and value? Check \"-h\" for help ");
				}
				if (checkDbParamaters(dbMap)) {
					dbName = dbMap.get("db");
					dbUser = dbMap.get("user");
					dbPassword = dbMap.get("password");
					dbHost = dbMap.get("host");
					dbPort = dbMap.get("port");
					dbGeometry = dbMap.get("geometry");
				} else {
					throw new ParseException(
							"Database arguments not valid. Check \"-h\" for help ");
				}
			} else {
				throw new ParseException(
						"Database arguments not valid.  Wrong number of arguments: "
								+ cmd.getOptionValues("wpg").length
								+ " Check \"-h\" for help ");
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
		if (dbMap.containsKey("geometry")) {
			if (!dbMap.get("geometry").equals("linestring") && !dbMap.get("geometry").equals("point")){
				LOGGER.error("Wrong Database parameter: supported geometry types: \"linestring\" and \"point\"");
				return false;
			}
		}else{
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

	private static int readMetadata() throws IOException, CompressorException {
		
		int gpxFileListSize = 0;
		
		// initialize reader
		TarArchiveInputStream tarIn = new TarArchiveInputStream(
				new CompressorStreamFactory().createCompressorInputStream(
						CompressorStreamFactory.XZ, new BufferedInputStream(
								new FileInputStream(tarFile))));

		TarArchiveEntry tarEntry;
		while ((tarEntry = tarIn.getNextTarEntry()) != null) {
			if (isMetaXML(tarEntry.getName())) {
				metadataFilename = tarEntry.getName();
				// LOGGER.info("Parsing metadata...");
				byte[] content = new byte[(int) tarEntry.getSize()];
				tarIn.read(content);
				// parse metadata file
				try {
					JAXBContext jc = JAXBContext
							.newInstance("osmgpxtool.filter.metadata.schema");
					Unmarshaller unmarshaller = jc.createUnmarshaller();
					JAXBElement<GpxFiles> root = (JAXBElement<GpxFiles>) unmarshaller
							.unmarshal(new StreamSource(
									new ByteArrayInputStream(content)),
									GpxFiles.class);
					GpxFiles gpxFiles = root.getValue();
					metadata = new TreeMap<Integer, GpxFile>();
					List<GpxFile> gpxFileList = gpxFiles.getGpxFile();
					gpxFileListSize = gpxFileList.size();
					LOGGER.info("Parsing "+gpxFileList.size()+" metadata entries...");
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
		LOGGER.info("Metadata successfully parsed. Total number of Gpx-Files in gpx archive: "
				+ metadata.size());
		return gpxFileListSize;
	}

	private static int getGpxId(TarArchiveEntry tarEntry) {
		String n = tarEntry.getName();
		return Integer.valueOf(n.substring(n.lastIndexOf("/") + 1,
				n.lastIndexOf(".")));

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