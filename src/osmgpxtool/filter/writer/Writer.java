/**
 * 
 */
package osmgpxtool.filter.writer;

import osmgpxtool.filter.GpxFilter;
import osmgpxtool.filter.gpx.schema10.Gpx;
import osmgpxtool.filter.metadata.schema.GpxFiles.GpxFile;

public interface Writer {
	
	/**
	 * @param gpx
	 * @param filename
	 * @param metadata
	 */
	public void write(Gpx gpx, String filename, GpxFile metadata);

	public void init();

	public void close();
	
	public GpxFilter getFilter();
public void setMetadataFilename(String filename);
}
