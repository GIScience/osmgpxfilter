package osmgpxtool.filter.reader;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.stream.StreamSource;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import osmgpxtool.filter.gpx.schema10.Gpx;
import osmgpxtool.filter.metadata.schema.GpxFiles;
import osmgpxtool.filter.metadata.schema.GpxFiles.GpxFile;
import osmgpxtool.filter.writer.Writer;
import osmgpxtool.util.Progress;

public class OsmGpxDumpReader {
	static Logger LOGGER = LoggerFactory.getLogger(OsmGpxDumpReader.class);
	private static TreeMap<Integer, GpxFile> metadata = null;
	private Writer writer;
	private String tarFile;
	private String baseName = null;
	private List<Integer> writtenIDs;

	public OsmGpxDumpReader(Writer writer, String tarFile) {
		this.writer = writer;
		this.tarFile = tarFile;
		writtenIDs = new ArrayList<Integer>();
	}

	public List<Integer> read() throws CompressorException, IOException {
		TarArchiveInputStream tarIn = new TarArchiveInputStream(
				new CompressorStreamFactory().createCompressorInputStream(CompressorStreamFactory.XZ,
						new BufferedInputStream(new FileInputStream(tarFile))));
		int gpxFileListSize = readMetadata();

		TarArchiveEntry tarEntry;
		LOGGER.info("Start processing " + gpxFileListSize + " gpx files...");
		Progress p = new Progress();
		p.start(gpxFileListSize);
		int progressPercentPrinted = -1;
		JAXBContext jc = null;
		Unmarshaller unmarshaller =null;
		try {

			jc = JAXBContext.newInstance("osmgpxtool.filter.gpx.schema10");
			 unmarshaller = jc.createUnmarshaller();
		} catch (JAXBException e) {
			e.printStackTrace();
		}

		while ((tarEntry = tarIn.getNextTarEntry()) != null) {
			if (tarEntry.isFile()) {
				if (isGPX(tarEntry.getName())) {

					p.increment();
					int currentProgressPercent = (int) (Math.round(p.getProgressPercent()));
					if (currentProgressPercent % 5 == 0 && currentProgressPercent != progressPercentPrinted) {
						LOGGER.info(p.getProgressMessage());
						progressPercentPrinted = currentProgressPercent;
					}
					byte[] content = new byte[(int) tarEntry.getSize()];
					tarIn.read(content);

					// write GPX file with specified writer
					Gpx gpx = null;
					try {

						ByteArrayInputStream bis = new ByteArrayInputStream(content);
						StreamSource ss = new StreamSource(bis);
						JAXBElement<Gpx> root = (JAXBElement<Gpx>) unmarshaller.unmarshal(ss, Gpx.class);
						gpx = root.getValue();
						bis.close();
						int id = getGpxId(tarEntry);
						
						GpxFile meta = metadata.get(id);
						writer.write(gpx, tarEntry.getName(),meta);
						writtenIDs.add(id);
					} catch (JAXBException ex) {
						ex.printStackTrace();
					}
				}
			}

		}

		tarIn.close();
		return writtenIDs;
	}

	private int readMetadata() throws IOException, CompressorException {

		int gpxFileListSize = 0;

		// initialize reader
		TarArchiveInputStream tarIn = new TarArchiveInputStream(
				new CompressorStreamFactory().createCompressorInputStream(CompressorStreamFactory.XZ,
						new BufferedInputStream(new FileInputStream(tarFile))));

		TarArchiveEntry tarEntry;
		while ((tarEntry = tarIn.getNextTarEntry()) != null) {
			if (isMetaXML(tarEntry.getName())) {
				baseName = tarEntry.getName().replace("/metadata.xml", "");
				String metadataFilename = tarEntry.getName();
				writer.setMetadataFilename(metadataFilename);
				// LOGGER.info("Parsing metadata...");
				byte[] content = new byte[(int) tarEntry.getSize()];
				tarIn.read(content);
				// parse metadata file
				try {
					JAXBContext jc = JAXBContext.newInstance("osmgpxtool.filter.metadata.schema");
					Unmarshaller unmarshaller = jc.createUnmarshaller();
					JAXBElement<GpxFiles> root = (JAXBElement<GpxFiles>) unmarshaller.unmarshal(new StreamSource(
							new ByteArrayInputStream(content)), GpxFiles.class);
					GpxFiles gpxFiles = root.getValue();
					metadata = new TreeMap<Integer, GpxFile>();
					List<GpxFile> gpxFileList = gpxFiles.getGpxFile();
					gpxFileListSize = gpxFileList.size();
					LOGGER.info("Parsing " + gpxFileList.size() + " metadata entries...");
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
		LOGGER.info("Metadata successfully parsed. Total number of Gpx-Files in gpx archive: " + metadata.size());
		return gpxFileListSize;
	}

	public String getBaseName() {
		return baseName;
	}
	public List<Integer> getWrittenIDs() {
		return writtenIDs;
	}
	private int getGpxId(TarArchiveEntry tarEntry) {
		String n = tarEntry.getName();
		return Integer.valueOf(n.substring(n.lastIndexOf("/") + 1, n.lastIndexOf(".")));

	}

	private boolean isMetaXML(String name) {
		if (name.endsWith("metadata.xml")) {
			return true;
		} else
			return false;
	}

	private boolean isGPX(String name) {
		if (name.endsWith(".gpx")) {
			return true;
		} else
			return false;
	}

}
