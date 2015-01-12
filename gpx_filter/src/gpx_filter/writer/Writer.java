/**
 * 
 */
package gpx_filter.writer;

import gpx_filter.gpx.schema.Gpx;
import gpx_filter.metadata.schema.GpxFiles.GpxFile;

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
