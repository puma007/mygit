package my.util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
public class HtmlImageSrcUtils {
	public static String getImg(String s){
		Document doc = Jsoup.parse(s);
		Elements imgs = doc.select("img[src]");
		if(imgs!=null){
			 for (Element src : imgs){
				 return src.attr("src");
			 }
		}
		return null;
		return "";	
	}	
}
