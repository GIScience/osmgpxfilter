/**
 * 
 */
package osmgpxtool.gpxfilter.writer;

import osmgpxtool.gpxfilter.gpx.schema.Gpx;
import osmgpxtool.gpxfilter.metadata.schema.GpxFiles.GpxFile;

public interface Writer {
	
	/**
	 * @param gpx
	 * @param filename
	 * @param metadata
	 */
	public void write(Gpx gpx, String filename, GpxFile metadata);

	public void init();

	public void close();
}
