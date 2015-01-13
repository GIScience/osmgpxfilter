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

import java.math.BigDecimal;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import osmgpxtool.filter.gpx.schema.Gpx;
import osmgpxtool.filter.gpx.schema.Gpx.Trk;
import osmgpxtool.filter.gpx.schema.Gpx.Trk.Trkseg;
import osmgpxtool.filter.gpx.schema.Gpx.Trk.Trkseg.Trkpt;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;

public class GpxFilter {
	static Logger LOGGER = LoggerFactory.getLogger(GpxFilter.class);

	private Double bboxLeft;
	private Double bboxRight;
	private Double bboxBottom;
	private Double bboxTop;
	private Envelope env;
	private boolean bboxClip;
	private boolean elevationOnly;
	private int rejectedEle;
	private int rejectedBbox;
	private int rejected;
	private int passed;

	public GpxFilter(Double bboxLeft, Double bboxRight, Double bboxBottom,
			Double bboxTop, boolean bboxClip, boolean elevationOnly) {

		super();
		this.bboxLeft = bboxLeft;
		this.bboxRight = bboxRight;
		this.bboxBottom = bboxBottom;
		this.bboxTop = bboxTop;
		this.elevationOnly = elevationOnly;
		this.bboxClip = bboxClip;
		if (bboxLeft != null && bboxRight != null && bboxTop != null
				&& bboxBottom != null) {
			this.env = new Envelope(bboxLeft, bboxRight, bboxTop, bboxBottom);
		} else {
			this.env = null;
		}
		printArgs();
	}

	private void printArgs() {
		LOGGER.info("Filter set with following Arguments:");
		if (bboxLeft != null && bboxRight != null && bboxTop != null
				&& bboxBottom != null) {
			LOGGER.info("Bounding Box: left=" + bboxLeft + " right="
					+ bboxRight + " top=" + bboxTop + " bottom=" + bboxBottom);
			if (bboxClip == true) {
				LOGGER.info("Data will be clipped at bounding box");
			}
		} else {
			LOGGER.info("Bounding box parameter not set. All data will be imported");
		}

		if (elevationOnly == true) {
			LOGGER.info("Only GPS-Tracks with elevation attribute (<ele>) will be exported");
		}
	}

	/**
	 * Applies the filter on the given gps-trace. It returns true, if the
	 * gps-trace passes the filter and false if the trace is rejected. By now,
	 * it is checked if the GPS-Traces lies within or outside a given bounding
	 * box or if it has elevation information or not.
	 * 
	 * 
	 * @param gpx
	 * @return
	 */

	public boolean check(Gpx gpx) {
		boolean isInBbox = false;
		boolean hasEle = false;

		if (bboxLeft != null && bboxBottom != null && bboxTop != null
				&& bboxRight != null) {
			// bounding box is set
			isInBbox = isInBoundingBox(gpx);
		} else {
			isInBbox = true;
		}

		if (elevationOnly == true) {
			// elevationOnly is set. check if trackpoints have elevation
			// attribute
			hasEle = hasElevationAttribute(gpx);
		} else {
			hasEle = true;
		}

		if (hasEle == true && isInBbox == true) {
			passed++;
			return true;
		} else {
			rejected++;
			return false;
		}
	}

	/**
	 * checks if a given coordinate is within the specified bounding box.
	 * Returns true, if Coordinate is within bounding box. If parameter bboxClip
	 * is set to false, this function returns true in any case.
	 * 
	 * @param c
	 * @return
	 */
	public boolean isInBbox(Coordinate c) {

		if (bboxClip && env != null) {
			return env.contains(c);
		} else {
			return true;
		}
	}

	/**
	 * checks if a gpx traces has elevation information. Returns true if ALL
	 * track points of gps trace have elevation information.
	 * 
	 * @param gpx
	 * @return
	 */
	private boolean hasElevationAttribute(Gpx gpx) {
		List<Trk> trkList = gpx.getTrk();
		for (Trk trk : trkList) {
			for (Trkseg trkseg : trk.getTrkseg()) {
				for (Trkpt trkpt : trkseg.getTrkpt()) {
					BigDecimal ele = trkpt.getEle();
					if (ele == null) {
						rejectedEle++;
						return false;
					}
				}
			}
		}
		return true;
	}

	/**
	 * checks whether a Gps-trace is within the specified bounding box. It
	 * returns true, when at least one point intersects the bounding box.
	 * 
	 * @param gpx
	 * @return
	 */
	private boolean isInBoundingBox(Gpx gpx) {

		List<Trk> trkList = gpx.getTrk();
		for (Trk trk : trkList) {
			for (Trkseg trkseg : trk.getTrkseg()) {
				for (Trkpt trkpt : trkseg.getTrkpt()) {
					BigDecimal lon = trkpt.getLon();
					BigDecimal lat = trkpt.getLat();
					Coordinate c = new Coordinate(lon.doubleValue(),
							lat.doubleValue());
					if (env.contains(c)) {
						return true;
					}
				}
			}
		}

		// no point is within bounding box
		rejectedBbox++;
		return false;
	}

	public void printStats() {
		LOGGER.info("Gpx Files passed filter: " + passed);
		LOGGER.info("Gpx Files rejected: " + rejected);
		LOGGER.info("Gpx Files not in Bbox: " + rejectedBbox);
		LOGGER.info("Gpx Files no elevation attribute: " + rejectedEle);
	}

	public boolean isElevationOnly() {
		return elevationOnly;
	}
}
