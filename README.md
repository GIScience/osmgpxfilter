# osmgpxfilter
osmgpxfilter is a tool to extract data from the [gpx-planet file](http://wiki.openstreetmap.org/wiki/Planet.gpx) or its regional extracts and export it as gpx planet-extract (tar.xz archive), to a Postgresql/PostGIS database or as ESRI shapefile.
The data can be filtered by bounding box or by elevation attribute.

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
 -wd,--write-dump <path to output.tar.xz>                                                 path to output dump file (gpx-planet.tar.xz
 -wpg,--write-pqsql <db=gis> <user=gisuser> <password=xxx> <host=localhost> <port=5432>   connection parameters for database
 -ws,--write-shape <path to output shape file>                                            path to output shape file
 
 ```
