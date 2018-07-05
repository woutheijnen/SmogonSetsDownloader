/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package smogonsetsdownloader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Wout Heijnen
 */
public class SmogonSetsDownloader {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        String baseUrl = "https://www.smogon.com";
        String urlStructure = "/dex/{gen}/pokemon/{pokemon}";
        String urlStart = "/dex/sm/pokemon";
        boolean shutdown = true;

        String[] generationBlacklist = {};
        String[] formatBlacklist = {};
        int startId = 0;

        Crawler crawler = new Crawler();
        ArrayList<String[]> urls = crawler.getUrlsFromIndex(baseUrl, urlStructure, urlStart, generationBlacklist);
        ArrayList<String> sets = crawler.getSetsFromUrls(urls, formatBlacklist, startId);
        urls.clear();
        sets.clear();

        //Shutdown
        if (shutdown) {
            Runtime runtime = Runtime.getRuntime();
            try {
                Process proc = runtime.exec("shutdown -s -t 300");
            } catch (IOException ex) {
                Logger.getLogger(SmogonSetsDownloader.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

}
