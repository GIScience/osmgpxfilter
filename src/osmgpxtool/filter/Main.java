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

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.compress.compressors.CompressorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import osmgpxtool.filter.reader.OsmGpxDumpReader;
import osmgpxtool.filter.reader.OsmGpxScraper;
import osmgpxtool.filter.writer.DumpWriter;
import osmgpxtool.filter.writer.PGSqlMultilineWriter;
import osmgpxtool.filter.writer.PGSqlWriter;
import osmgpxtool.filter.writer.ShapeFileWriter;
import osmgpxtool.filter.writer.Writer;
import osmgpxtool.util.TimeTools;

public class Main {
	static Logger LOGGER = LoggerFactory.getLogger(Main.class);
	private static String datasource;
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
	private static Options cmdOptions;
	private static CommandLine cmd = null;

	public static void main(String[] args) throws CompressorException, IOException {
		long tStart = System.currentTimeMillis();

		parseArguments(args);

		// init Filter
		GpxFilter filter = new GpxFilter(bboxLeft, bboxRight, bboxBottom, bboxTop, bboxClip, elevationOnly);

		// init writer
		if (cmd.hasOption("wd")) {
			writer = new DumpWriter(filter, outputFileDump);
		} else if (cmd.hasOption("wpg")) {
			if (dbGeometry.equals("point")) {
				writer = new PGSqlWriter(filter, dbName, dbUser, dbPassword, dbHost, dbPort);
			} else if (dbGeometry.equals("linestring")) {
				writer = new PGSqlMultilineWriter(filter, dbName, dbUser, dbPassword, dbHost, dbPort);
			}

		} else if (cmd.hasOption("ws")) {
			writer = new ShapeFileWriter(outputFileShape, filter);
		}
		writer.init();

		if (datasource.equals("dump")) {
			//if dumpwriter is chosen
			OsmGpxDumpReader reader = new OsmGpxDumpReader(writer, tarFile);
			reader.read();
		} else if (datasource.equals("both")) {
			 readFromCombinedSource();
		} else if (datasource.equals("scrape")) {
			 OsmGpxScraper scraper = new OsmGpxScraper(writer);
			 scraper.scrape();
		}

		writer.close();
		filter.printStats();
		long executionTime = (System.currentTimeMillis() - tStart) / 1000; // time
																			// in
																			// seconds
		LOGGER.info("Filter task done... Execution time: " + executionTime + " seconds ("
				+ TimeTools.convertMillisToHourMinuteSecond(executionTime) + ")");
	}






	private static void readFromCombinedSource() throws CompressorException, IOException {
		OsmGpxDumpReader dumpReader = new OsmGpxDumpReader(writer, tarFile);
		List<Integer> writtenIDs = dumpReader.read();
		String baseName = dumpReader.getBaseName();
		OsmGpxScraper scraper = new OsmGpxScraper(writer);
		scraper.setWrittenIDs(writtenIDs);
		scraper.setBaseName(baseName);
		scraper.scrape();
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

	@SuppressWarnings("static-access")
	private static void setupArgumentOptions() {
		// parse command line arguments
		cmdOptions.addOption(new Option("h", "help", false, "displays help"));
		// option for input GPX dump packed and compressed (as *.tar.xz)
		cmdOptions.addOption(OptionBuilder.withLongOpt("input").withDescription("path to gpx-planet.tar.xz").hasArg()
				.create("i"));
		cmdOptions
				.addOption(OptionBuilder
						.withLongOpt("datasource")
						.withDescription(
								"[dump,scrape,both]\n\"dump\": only use specified dump, \n\"scrape\": only scrape OSM public trace list, \n\"both\": use dump and retrieve additional traces from public trace list")
						.hasArg().isRequired().create("ds"));
		cmdOptions.addOption(new Option("e", "elevation", false,
				"only use GPX-files if they have elevation information"));
		cmdOptions.addOption(new Option("c", "Clip", false,
				"Clip GPS traces at bounding box. This option is only applied for PQSql and Shape output."));

		// option for bounding box
		cmdOptions.addOption(OptionBuilder.withLongOpt("bounding-box").withDescription("specifies bounding box")
				.hasArgs(4).withArgName("left=x.x> <right=x.x> <top=x.x> <bottom=x.x").withValueSeparator(' ')
				.create("bbox"));
		// filter option elevation
		// writer options
		cmdOptions.addOption(OptionBuilder.withLongOpt("write-shape").withDescription("path to output shape file")
				.hasArg().withArgName("path to output shape file").create("ws"));
		cmdOptions.addOption(OptionBuilder.withLongOpt("write-dump")
				.withDescription("path to output dump file (gpx-planet.tar.xz").hasArg()
				.withArgName("path to output.tar.xz").create("wd"));
		cmdOptions
				.addOption(OptionBuilder
						.withLongOpt("write-pqsql")
						.withDescription("connection parameters for database. Supported Geometry: linestring, point ")
						.hasArgs(6)
						.withArgName(
								"db=gis> <user=gisuser> <password=xxx> <host=localhost>\n <port=5432> <geometry=[linestring,point]")
						.withValueSeparator(' ').create("wpg"));
	}

	private static void assignArguments(CommandLine cmd) throws ParseException {
		// assign values to variables

		if (cmd.getOptionValue("i") != null && new File(cmd.getOptionValue("i")).exists()) {
			tarFile = cmd.getOptionValue("i");
		} else {
			throw new ParseException("No input file given or it doesn't exist. Check \"-h\" for help ");
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
						bboxMap.put(n.split("=")[0], Double.valueOf(n.split("=")[1]));
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
					throw new ParseException("Bounding Box arguments not valid. Check \"-h\" for help ");
				}
			} else {
				throw new ParseException("Bounding Box arguments not valid. Wrong number of arguments: "
						+ cmd.getOptionValues("bbox").length + " Check \"-h\" for help ");
			}

		}
		// datasource (dump, scrape, both)
		if (cmd.hasOption("ds")) {
			String value = cmd.getOptionValue("ds");
			if (value.equals("dump") || value.equals("both")|| value.equals("scrape")) {
				datasource = value;
			} else {
				throw new ParseException(
						"Given datasource is not valid. The given value must be one of the following: \"dump\", \"scrape\", \"both\": Check \"-h\" for help ");
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
					throw new ParseException("Database arguments not valid. Check \"-h\" for help ");
				}
			} else {
				throw new ParseException("Database arguments not valid.  Wrong number of arguments: "
						+ cmd.getOptionValues("wpg").length + " Check \"-h\" for help ");
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
			if (!dbMap.get("geometry").equals("linestring") && !dbMap.get("geometry").equals("point")) {
				LOGGER.error("Wrong Database parameter: supported geometry types: \"linestring\" and \"point\"");
				return false;
			}
		} else {
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
			LOGGER.error("left box coordinate not within range: " + bbox.get("left"));
			return false;
		}
		if (!bbox.containsKey("right")) {
			LOGGER.error("right bounding box coordinate not in argument list");
			return false;
		}
		if (bbox.get("left") <= -180 || bbox.get("left") >= 180) {
			LOGGER.error("left box coordinate not within range: " + bbox.get("left"));
			return false;
		}

		if (!bbox.containsKey("top")) {
			LOGGER.error("top bounding box coordinate not in argument list");
			return false;
		}
		if (bbox.get("top") <= -90 || bbox.get("top") >= 90) {
			LOGGER.error("top bounding box coordinate not within range: " + bbox.get("top"));
			return false;
		}
		if (!bbox.containsKey("bottom")) {
			LOGGER.error("top bounding box coordinate not in argument list");
			return false;
		}
		if (bbox.get("bottom") <= -90 || bbox.get("top") >= 90) {
			LOGGER.error("bottom bounding box coordinate not within range: " + bbox.get("bottom"));
			return false;
		}
		return true;
	}

	


}