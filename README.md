# osmgpxfilter
osmgpxfilter is a tool to extract data from the [gpx-planet file](http://wiki.openstreetmap.org/wiki/Planet.gpx) or its [regional extracts](http://zverik.osm.rambler.ru/gps/files/extracts/index.html) and export using one of the following options:
- (clipped) gpx planet-extract (tar.xz archive)
- export Postgresql/PostGIS database (3D Points / 3D MultiLineString) 
- export to as ESRI shapefile (3D Points)
The data can be filtered by bounding box and/or by checking whether an elevation attribute is existing in the track

### Getting started

1. install maven
2. install git
3. clone project `$ git clone https://github.com/GIScience/osmgpxfilter`
4. go into project directory `$ cd osmgpxfilter/`
5. run maven `$ mvn clean package`
6. start application `java -jar target/osmgpxfilter-0.1.jar <args>`

### Usage
```
 -bbox,--bounding-box <left=x.x> <right=x.x> <top=x.x> <bottom=x.x>                       specifies bounding box
 -c,--Clip                                                                                Clip GPS traces at bounding box. This option is only applied for PQSql and Shape output.
 -e,--elevation                                                                           only use GPX-files if they have elevation information
 -h,--help                                                                                displays help
 -i,--input                                                                               path to gpx-planet.tar.xz
 -wd,--write-dump <path to output.tar.xz>                                                 path to output dump file (gpx-planet.tar.xz)
 -wpg,--write-pqsql <db=gis> <user=gisuser> <password=xxx> <host=localhost> <port=5432> <geometry=[point,linestring]>   connection parameters for database
 -ws,--write-shape <path to output shape file>                                            path to output shape file


Example java -jar osmgpxfilter-0.1.jar -bbox top=49.459693 left=8.573179 bottom=49.352565 right=8.794050 -c -e -i D:/osmgpx/baden-wuerttemberg.tar.xz -ws D:/osmgpx/heidelberg.shp

 ```
 
### Citation

When using this software for scientific purposes, please cite:

John, S., Hahmann, S., Zipf, A., Bakillah, M., Mobasheri, A., Rousell, A. (2015): [Towards deriving incline values for street networks from voluntarily collected GPS data] (http://koenigstuhl.geog.uni-heidelberg.de/publications/2015/Hahmann/GI_Forum_GPS.pdf). Poster session, GI Forum. Salzburg, Austria.
 
 
 ```
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
 ```
 
