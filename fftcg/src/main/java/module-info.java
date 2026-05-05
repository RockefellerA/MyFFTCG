/**
 * @author Andrew Rockefeller
 *
 */
module fftcg {
	requires java.desktop;
	requires java.net.http;
	requires java.sql;
	requires org.json;
	requires org.xerial.sqlitejdbc;

	exports fftcg;
	exports scraper;
}