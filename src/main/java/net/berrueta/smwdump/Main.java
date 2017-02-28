package net.berrueta.smwdump;

import java.io.File;
import java.io.FileOutputStream;
import java.util.LinkedList;

import net.sourceforge.jwbf.mediawiki.actions.MediaWiki;
import net.sourceforge.jwbf.mediawiki.actions.queries.AllPageTitles;
import net.sourceforge.jwbf.mediawiki.bots.MediaWikiBot;

import org.apache.log4j.Logger;

import com.hp.hpl.jena.iri.IRIFactory;
import com.hp.hpl.jena.iri.impl.IRIImplException;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.NodeIterator;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.shared.JenaException;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Script to extract a RDF dump from a SemanticMediaWiki instance by means of
 * web requests, i.e., without having local access to the server or the
 * database.
 *
 * Command line syntax: wikiURL [outputfile.rdf]
 *
 * The wikiURL parameter should be the URL of the wiki, i.e.,
 * http://www.crisiswiki.org/
 *
 * The second parameter is optional. If specified, the collected RDF triples
 * will be stored in that file. Otherwise, the file "out.rdf" is assumed.
 *
 * @author Diego Berrueta
 *
 */
public class Main {

    private static final Logger logger = Logger.getLogger(Main.class);

    /**
     * Milliseconds between requests
     */
    private static final long DELAY_MILIS = 0;

    /**
     * @param args
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Please provide the URL to the wiki as the first argument, e.g., http://www.crisiswiki.org/");
        } else {
            String wikiUrl = args[0];
            File outputFile = new File(args.length == 2 ? args[1] : "out.rdf");
            MediaWikiBot b = new MediaWikiBot(wikiUrl);
            int count = 0;
            Model m = ModelFactory.createDefaultModel();
            LinkedList<String> articleNames = new LinkedList<String>();
            for (int namespace : MediaWiki.NS_ALL) {
                logger.info("Geting a list of all pages in namespace " + namespace); // see http://en.wikipedia.org/wiki/Wikipedia:Namespace
                AllPageTitles apt = new AllPageTitles(b, namespace);
                articleNames.addAll(asList(apt));
            }
            logger.info("There are " + articleNames.size() + " pages");
            long startTime = System.currentTimeMillis();
            for (String articleName : articleNames) {
                logger.info("Getting RDF data for article (" + count + " of " + articleNames.size() + "): " + articleName);
                readArticleIntoModel(m, wikiUrl, articleName);
                logger.info("After reading [[" + articleName + "]], the model contains " + m.size() + " triples");
                count++;
                long currentTime = System.currentTimeMillis();
                double progress = (double) count / (double) articleNames.size();
                long elapsedTime = currentTime - startTime;
                long remainingTime = Math.round(elapsedTime * (1 - progress) / progress);
                logger.info("Elapsed time (sec): " + (currentTime - startTime) / 1000 + " -- Progress: " + Math.floor(progress * 100.0) + "% -- Est. remaining time (sec): " + remainingTime / 1000);
                Thread.sleep(DELAY_MILIS);
            }
            removeMalformedURIs(m);
            // save data
            logger.info("Saving " + m.size() + " triples to file " + outputFile + ", " + count + " pages have been retrieved");
            m.write(new FileOutputStream(outputFile)); // avoid FileWriter, see http://jena.sourceforge.net/IO/iohowto.html#encoding
        }
    }

    private static LinkedList<String> asList(AllPageTitles apt) {
        LinkedList<String> articleNames = new LinkedList<String>();
        for (String articleName : apt) {
            articleNames.add(articleName);
        }
        return articleNames;
    }

    /**
     * Fetches the RDF triples about a wiki article and adds them to the model
     *
     * @param m
     * @param articleName
     */
    private static void readArticleIntoModel(Model m, String wikiUrl, String articleName) {
        String rdfUrlString = wikiUrl + "index.php?title=Special:ExportRDF/" + MediaWiki.encode(articleName);
        try {
            URL rdfUrl = new URL(rdfUrlString);
            logger.debug("RDF URL: " + rdfUrl);
            HttpURLConnection urlConnection = (HttpURLConnection) rdfUrl.openConnection();
            urlConnection.setRequestProperty("User-Agent", "net.berrueta.smw-dump");
            m.read(urlConnection.getInputStream(), rdfUrlString);
        }
        catch (Exception e) {
            logger.error("Skipped " + rdfUrlString + " because of parsing errors", e);
        }
    }

    /**
     * Remove buggy resource URIs, i.e., URIs that are not valid
     *
     * @param m
     */
    private static void removeMalformedURIs(Model m) {
        IRIFactory iriFactory = IRIFactory.semanticWebImplementation();
        NodeIterator nodeIterator = m.listObjects();
        while (nodeIterator.hasNext()) {
            RDFNode node = nodeIterator.next();
            if (node.isResource() == true && node.isAnon() == false) {
                Resource resource = node.asResource();
                logger.info("Checking " + resource.getURI());
                try {
                    iriFactory.construct(resource.getURI()); // just try to construct the IRI, check for exceptions
                }
                catch (IRIImplException e) {
                    logger.error("Malformed URI fetched from wiki: " + resource.getURI());
                    logger.info("Removing all triples with object: " + resource.getURI());
                    m.removeAll(null, null, resource);
                }
            }
        }
    }

}
