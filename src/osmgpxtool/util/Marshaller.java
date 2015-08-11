package osmgpxtool.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.stream.StreamSource;

import osmgpxtool.filter.gpx.schema10.Gpx;
import osmgpxtool.filter.gpx.schema10.Gpx.Trk;
import osmgpxtool.filter.gpx.schema10.Gpx.Trk.Trkseg;
import osmgpxtool.filter.gpx.schema10.Gpx.Trk.Trkseg.Trkpt;
import osmgpxtool.filter.gpx.schema11.GpxType;
import osmgpxtool.filter.gpx.schema11.TrkType;
import osmgpxtool.filter.gpx.schema11.TrksegType;
import osmgpxtool.filter.gpx.schema11.WptType;

public class Marshaller {
	private Unmarshaller unmarshaller10 = null;
	private Unmarshaller unmarshaller11 = null;
	private javax.xml.bind.Marshaller marshaller10 = null;

	public Marshaller() {
		try {
			unmarshaller10 = JAXBContext.newInstance("osmgpxtool.filter.gpx.schema10").createUnmarshaller();
			unmarshaller11 = JAXBContext.newInstance("osmgpxtool.filter.gpx.schema11").createUnmarshaller();

			marshaller10 = (JAXBContext.newInstance(Gpx.class)).createMarshaller();
			// output pretty printed
			marshaller10.setProperty(javax.xml.bind.Marshaller.JAXB_FORMATTED_OUTPUT, true);
		} catch (JAXBException | ClassCastException e) {
			e.printStackTrace();
		}

	}

	public Gpx unmarshalGpx10(byte[] content) throws JAXBException {

		JAXBElement<Gpx> root = (JAXBElement<Gpx>) unmarshaller10.unmarshal(new StreamSource(new ByteArrayInputStream(
				content)), Gpx.class);
		return root.getValue();
	}

	public GpxType unmarshalGpx11(byte[] content) throws JAXBException {

		JAXBElement<GpxType> root = (JAXBElement<GpxType>) unmarshaller11.unmarshal(new StreamSource(
				new ByteArrayInputStream(content)), GpxType.class);
		return root.getValue();
	}

	public Gpx unmarshalAndConvertToGpx10(byte[] content) throws JAXBException {
		Gpx gpx = null;
		gpx = unmarshalGpx10(content);
		if (gpx != null) {
			// if gpx is version 1.1, convert to gps version 1.0
			if (gpx.getVersion().equals("1.0")) {
				return gpx;
			} else if (gpx.getVersion().equals("1.1")) {
				// unmarchal as gpx version 1.1
				GpxType gpx11 = unmarshalGpx11(content);
				Gpx gpx10 = gpx11ToGpx10(gpx11);
				return gpx10;
			} else {
				return null;
			}
		}
		return gpx;
	}

	private Gpx gpx11ToGpx10(GpxType gpx11) {
		Gpx gpx10 = new Gpx();
		gpx10.setVersion("1.0");
		for (TrkType t : gpx11.getTrk()) {
			Trk trk = new Trk();
			for (TrksegType ts : t.getTrkseg()) {
				Trkseg trkseg = new Trkseg();
				// List<Trkpt> trkptList = new ArrayList<Trkpt>();
				for (WptType p : ts.getTrkpt()) {
					Trkpt pt = new Trkpt();
					pt.setLat(p.getLat());
					pt.setLon(p.getLon());
					pt.setEle(p.getEle());
					trkseg.getTrkpt().add(pt);
				}
				trk.getTrkseg().add(trkseg);
			}
			gpx10.getTrk().add(trk);
		}
		return gpx10;
	}

	public byte[] marshal10(Gpx gpx) {
		byte[] out = null;
		try {

			ByteArrayOutputStream os = new ByteArrayOutputStream();
			marshaller10.marshal(gpx, os);
			out = os.toByteArray();
			os.flush();
			os.close();
		} catch (JAXBException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return out;

	}
}
