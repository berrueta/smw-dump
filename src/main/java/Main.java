import java.io.File;
import java.io.FileWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedList;

import org.apache.log4j.Logger;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.NodeIterator;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.util.URIref;

import net.sourceforge.jwbf.core.actions.util.ProcessException;
import net.sourceforge.jwbf.core.contentRep.SimpleArticle;
import net.sourceforge.jwbf.mediawiki.actions.MediaWiki;
import net.sourceforge.jwbf.mediawiki.actions.queries.AllPageTitles;
import net.sourceforge.jwbf.mediawiki.bots.MediaWikiBot;


public class Main {
    
    private static final Logger logger = Logger.getLogger(Main.class);

    /**
     * @param args
     */
    public static void main(String[] args) throws Exception {
        String url = "http://www.crisiswiki.org/index.php?title=Main_Page/";
        MediaWikiBot b = new MediaWikiBot (url);
        int count = 0;
        Model m = ModelFactory.createDefaultModel();
        for (int namespace : MediaWiki.NS_ALL) {
            logger.info("Getting all pages in namespace " + namespace); // see http://en.wikipedia.org/wiki/Wikipedia:Namespace
            AllPageTitles apt = new AllPageTitles(b, namespace);
            for (String articleName : apt) {
                logger.info("Getting RDF data for article (" + count + "): " + articleName);
                readArticleIntoModel(m, articleName);
                count++;
            }
        }
        removeMalformedURIs(m);
        // save data
        logger.info("Saving " + m.size() + " triples to the file, " + count + " pages have been retrieved");
        File rdfFileOutput = new File("wiki-dump.rdf");
        m.write(new FileWriter(rdfFileOutput));
    }

    private static void readArticleIntoModel(Model m, String articleName) {
        String rdfUrl = "http://www.crisiswiki.org/Special:ExportRDF/" + articleName;
        logger.debug("RDF URL: " + rdfUrl);
        m.read(rdfUrl);
        logger.info("After reading " + rdfUrl + ", the model contains " + m.size() + " triples");
    }

    private static void removeMalformedURIs(Model m) {
        // remove buggy resources
        NodeIterator nodeIterator = m.listObjects();
        while (nodeIterator.hasNext()) {
            RDFNode node = nodeIterator.next();
            if (node.canAs(Resource.class)) {
                Resource resource = node.asResource();
                try {
                    new URI(resource.getURI());
                } catch (URISyntaxException e) {
                    logger.error("Malformed URI fetched from wiki: " + resource.getURI());
                    logger.info("Removing all triples with object: " + resource.getURI());
                    m.removeAll(null, null, resource);
                }
            }
        }
    }

}
