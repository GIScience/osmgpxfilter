package osmgpxtool.filter.reader;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.URL;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.bind.JAXBException;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import osmgpxtool.filter.gpx.schema10.Gpx;
import osmgpxtool.filter.metadata.schema.GpxFiles.GpxFile;
import osmgpxtool.filter.metadata.schema.GpxFiles.GpxFile.Tags;
import osmgpxtool.filter.writer.Writer;
import osmgpxtool.util.Marshaller;
import osmgpxtool.util.Progress;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;

public class OsmGpxScraper {
	static Logger LOGGER = LoggerFactory.getLogger(OsmGpxScraper.class);

	private Writer writer;
	private int connectionTrails = 0;
	private String baseName;
	private List<Integer> writtenTraces;
	private Marshaller m;

	public OsmGpxScraper(Writer writer) {
		super();
		this.writer = writer;
		Date date = new Date(System.currentTimeMillis());
		DateFormat dformat = new SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH);
		baseName = "gpx-planet-" + dformat.format(date);

		writtenTraces = new ArrayList<Integer>();
		m = new Marshaller();
	}

	public void setBaseName(String baseName) {
		this.baseName = baseName;
	}

	public void scrape() {
		try {
			String osmurl = "http://www.openstreetmap.org/traces/page/";
			Envelope env = writer.getFilter().getEnvelope();
			Envelope extendedEnv = new Envelope(env);
			extendedEnv.expandBy(0.3);
			int page = 1;

			boolean hasNextPage = true;
			Progress p = new Progress();
			p.start(15000);
			int progressPercentPrinted = -1;
			
			while (hasNextPage) {
				try {
					p.increment();
					int currentProgressPercent = (int) (Math.round(p.getProgressPercent()));
					if (currentProgressPercent % 1 == 0 && currentProgressPercent != progressPercentPrinted) {
						LOGGER.info("current page: " + page);
						LOGGER.info(p.getProgressMessage());
						progressPercentPrinted = currentProgressPercent;
					}
					Map<Integer, String> tracks = getTracksFromPage(osmurl + page, extendedEnv);
					if (tracks != null) {
						if (!tracks.isEmpty()) {
							// do everything
							LOGGER.info(tracks.size() +	 " tracks found on page: " + page);
							for (Entry<Integer, String> e : tracks.entrySet()) {
								String user = e.getValue();
								Integer id = e.getKey();
								GpxFile metadata = getMetaData("http://www.openstreetmap.org/user/"
										+ URLEncoder.encode(user, "utf-8") + "/traces/"
										+ URLEncoder.encode(id + "", "utf-8"));
								// set id
								metadata.setId(id);
								// set filename
								metadata.setFilename(id + ".gpx");

								// get content from trkurl
								Gpx gpxTrack = getGpxTrack(
										"http://www.openstreetmap.org/trace/" + URLEncoder.encode(id + "", "UTF-8")
												+ "/data", user);

								// get 9digit id
								String nineDigitId = String.format("%09d", id);
								String filename = baseName + "/" + metadata.getVisibility().toLowerCase() + "/"
										+ nineDigitId.substring(0, 3) + "/" + nineDigitId.substring(3, 6) + "/"
										+ nineDigitId + ".gpx";
								if (gpxTrack != null) {
									if (!writtenTraces.contains(id)) {
										writer.write(gpxTrack, filename, metadata);
										writtenTraces.add(id);
									}

								} else {
									LOGGER.info("could not write gpx-track: id: " + id + " user: " + user + ", page: "
											+ page);
								}
							}

						}
					} else {
						hasNextPage = false;
					}
					page++;
					if (page > 10000) {
						hasNextPage = false;
					}
			

				} catch (SocketException e) {
					LOGGER.info("page: " + page);
					e.printStackTrace();
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	private Gpx getGpxTrack(String url, String user) throws MalformedURLException, IOException, SocketException {

		// String query= "bbox="+bbox+"&page="+page;
		byte[] content = executeQuery(url);
		// unmarchal gpx element
		Gpx gpx = null;
		try {
			gpx = m.unmarshalAndConvertToGpx10(content);
		} catch (JAXBException ex) {
			LOGGER.error("Error in XML, user: (" + user + "): " + url);
			ex.printStackTrace();
		}
		return gpx;
	}

	private Map<Integer, String> getTracksFromPage(String url, Envelope env) throws IOException, SocketException {
		Map<Integer, String> id_users = new HashMap<Integer, String>();
		byte[] html = executeQuery(url);
		if (html != null) {

			Document doc = Jsoup.parse(new String(html));
			// Element table = doc.select("div.content-inner table").first();
			Elements table = doc.select("table#trace_list tbody tr");
			Iterator<Element> ite = table.iterator();
			if (!table.isEmpty()) {
				while (ite.hasNext()) {

					Elements td = ite.next().select("td");
					if (!td.get(0).select("span[class=trace_pending]").text().equals("PENDING")) {

						String traceLink = td.get(1).select("a").first().attr("href");
						String mapLink = td.get(1).select("a").get(2).attr("href");
						Coordinate p = getCoordFromMapLink(mapLink);
						if (env.contains(p)) {
							id_users.put(getIdFromLink(traceLink), getUserFromLink(traceLink));
						}
					}

					/*
					 * <td class="table0"><a
					 * href="/user/madmacz/traces/1897287">
					 * Entrada_n_mero_2_Bicu_2015_03_20_14_05_04.gpx</a> <span
					 * class="trace_summary" title="2015-03-20 21:02:26 UTC">
					 * ... (78 Punkte) ... 2 Minuten her</span> <a
					 * title="Details des GPS-Tracks anzeigen"
					 * href="/user/madmacz/traces/1897287">Details</a> / <a
					 * title="Karte anzeigen"
					 * href="/#map=14/12.0073/-83.7709">Karte</a> / <a
					 * title="Karte bearbeiten"
					 * href="/edit?gpx=1897287">bearbeiten</a> <span
					 * class="trace_identifiable">IDENTIFIZIERBAR</span> <br />
					 * entrada y callejón trasero de BICU Bluefields <br /> von
					 * <a href="/user/madmacz">madmacz</a> </td>
					 */

				}
				return id_users;
			} else {
				return null;
			}

		} else {
			return id_users;
		}
	}

	private byte[] executeQuery(String url) throws MalformedURLException, IOException, SocketException {

		// Set up the initial connection
		HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
		// connection.setRequestProperty("User-Agent", USER_AGENT);
		connection.setRequestProperty("Accept-Charset", "utf-8");
		connection.setRequestProperty("Accept-Encoding", "identity");
		int responseCode = connection.getResponseCode();
		if (responseCode == HttpURLConnection.HTTP_OK) {
			byte[] response = IOUtils.toByteArray(connection.getInputStream());
			// LOGGER.info(connection.getContentType());
			if (connection.getContentType().equals("application/x-zip")) {
				return extractZip2ByteArray(response);
			} else if (connection.getContentType().equals("application/x-bzip2")) {
				return extractBzip2ByteArray(response);
			} else if (connection.getContentType().equals("application/x-gzip")) {
				return extractGzipByteArray(response);
			} else {
				return response;
			}

		} else {
			if (connectionTrails < 3) {

				LOGGER.warn("HTTP response code: " + responseCode + "Could not connect to url: " + connection.getURL());
				LOGGER.info("trying again...");
				try {
					Thread.sleep(2000); // 1000 milliseconds is one second.
				} catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
				}
				connectionTrails++;
				return executeQuery(url);
			} else {
				connectionTrails = 0;
				return null;
			}
		}
	}

	private byte[] extractZip2ByteArray(byte[] data) {
		Gpx gpxAll = new Gpx();
		gpxAll.setVersion("1.0");
		byte[] b = null;
		try {
			ByteArrayInputStream bis = new ByteArrayInputStream(data);
			ZipInputStream zip = new ZipInputStream(bis);
			ZipEntry e = null;
			while ((e = zip.getNextEntry()) != null) {
				if (e.getName().endsWith(".gpx")) {

					byte[] buf = new byte[1024];
					int num = -1;
					ByteArrayOutputStream baos = new ByteArrayOutputStream();
					while ((num = zip.read(buf, 0, buf.length)) != -1) {
						baos.write(buf, 0, num);
					}
					b = baos.toByteArray();
					baos.flush();
					baos.close();

					Gpx gpx = m.unmarshalAndConvertToGpx10(b);
					// take all tracks of all files and put them in a new Gpx
					// elemant

					gpxAll.getTrk().addAll(gpx.getTrk());
				}
			}
			zip.close();
			bis.close();
		} catch (Exception ex) {
			ex.printStackTrace();
		}

		// marchal gpx to String

		byte[] out = m.marshal10(gpxAll);


		return out;
	}

	/**
	 * source http://www.javawebdevelop.com/1870464/
	 * 
	 * @param response
	 * @return
	 */
	private byte[] extractGzipByteArray(byte[] data) {
		byte[] b = null;
		try {
			ByteArrayInputStream bis = new ByteArrayInputStream(data);
			GZIPInputStream gzip = new GZIPInputStream(bis);
			byte[] buf = new byte[1024];
			int num = -1;
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			while ((num = gzip.read(buf, 0, buf.length)) != -1) {
				baos.write(buf, 0, num);
			}
			b = baos.toByteArray();
			baos.flush();
			baos.close();
			gzip.close();
			bis.close();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return b;
	}

	private GpxFile getMetaData(String url) throws MalformedURLException, IOException, SocketException {
		GpxFile metadata = new GpxFile();
		byte[] html = executeQuery(url);
		if (html != null) {

			Document doc = Jsoup.parse(new String(html));
			// Element table = doc.select("div.content-inner table").first();
			Elements table = doc.select("div.content-inner tr");
			Iterator<Element> ite = table.iterator();

			while (ite.hasNext()) {
				Element elem = ite.next();
				String key = elem.select("td").get(0).text();
				Element value = elem.select("td").get(1);

				if (key.equals("Tags:")) {
					Tags tags = new Tags();
					for (int i = 0; i < value.select("a").size(); i++) {
						tags.getTag().add(value.select("a").get(i).text());
					}
					metadata.setTags(tags);
				}
				if (key.equals("Start coordinate:")) {
					metadata.setLat(Float.valueOf(value.select(".latitude").first().text()));
					metadata.setLon(Float.valueOf(value.select(".longitude").first().text()));
				}
				if (key.equals("Owner:")) {
					metadata.setUser(value.select("a").first().text());
				}
				if (key.equals("Points:")) {
					if (Integer.valueOf(value.text().replace(",", "")) > Short.MAX_VALUE) {
						metadata.setPoints(Short.MAX_VALUE);
					} else {
						metadata.setPoints(Short.valueOf(value.text().replace(",", "")));
					}
				}
				if (key.equals("Description:")) {
					metadata.setDescription(value.text());
				}
				if (key.equals("Visibility:")) {
					StringBuffer s = new StringBuffer(value.text());
					metadata.setVisibility(s.substring(0, s.indexOf(" (")));
				}
				if (key.equals("Uploaded:")) {
					try {
						DateFormat format = new SimpleDateFormat("dd MMMM yyyy 'at' kk:mm", Locale.ENGLISH);
						Date date = format.parse(value.text());
						GregorianCalendar c = new GregorianCalendar();
						c.setTime(date);
						XMLGregorianCalendar date2 = DatatypeFactory.newInstance().newXMLGregorianCalendar(c);

						metadata.setTimestamp(date2);
					} catch (DatatypeConfigurationException | ParseException e) {
						e.printStackTrace();
					}
				}
			}

			return metadata;
		} else {
			return null;
		}
	}

	private byte[] extractBzip2ByteArray(byte[] data) {
		byte[] b = null;
		try {
			ByteArrayInputStream bis = new ByteArrayInputStream(data);
			BZip2CompressorInputStream bzip2 = new BZip2CompressorInputStream(bis);
			byte[] buf = new byte[1024];
			int num = -1;
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			while ((num = bzip2.read(buf, 0, buf.length)) != -1) {
				baos.write(buf, 0, num);
			}
			b = baos.toByteArray();
			baos.flush();
			baos.close();
			bzip2.close();
			bis.close();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return b;

	}

	private Coordinate getCoordFromMapLink(String mapLink) {
		// /#map=14/12.0073/-83.7709
		StringBuffer s = new StringBuffer(mapLink);
		double lat = Double.valueOf(s.substring(9, s.lastIndexOf("/")));
		double lon = Double.valueOf(s.substring(s.lastIndexOf("/") + 1));

		return new Coordinate(lon, lat);
	}

	private Integer getIdFromLink(String traceLink) {
		// /user/madmacz/traces/1897287
		StringBuffer s = new StringBuffer(traceLink);
		Integer id = new Integer(s.substring(s.lastIndexOf("/") + 1, s.length()));
		return id;
	}

	private String getUserFromLink(String traceLink) {
		// /user/madmacz/traces/1897287
		StringBuffer s = new StringBuffer(traceLink);
		return s.substring(6, s.indexOf("/traces"));

	}

	public void setWrittenIDs(List<Integer> writtenIDs) {
		this.writtenTraces = writtenIDs;

	}

}
