/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package smogonsetsdownloader;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import org.json.*;

/**
 *
 * @author Wout Heijnen
 */
public class Crawler {

    public ArrayList<String[]> getUrlsFromIndex(String baseUrl, String structure, String urlStart, String[] genBlacklist) {
        ArrayList<String[]> urls = new ArrayList<>();

        JSONObject obj = getDexSettings(baseUrl + urlStart);
        if (obj == null) {
            System.err.println("Error while getting JSON object from Dex Settings.");
            System.exit(1);
        }
        JSONArray json_mons = obj.getJSONArray("injectRpcs").getJSONArray(1).getJSONObject(1).getJSONArray("pokemon");
        for (int i = 0; i < json_mons.length(); i++) {
            String pokemon = json_mons.getJSONObject(i).getString("name").toLowerCase().replaceAll(" ", "_").replaceAll("'", "").replaceAll("\\.", "").replaceAll("\\%", "").replaceAll(":", "");
            JSONArray genfamily = json_mons.getJSONObject(i).getJSONArray("genfamily");
            for (int j = 0; j < genfamily.length(); j++) {
                String gen = genfamily.getString(j).toLowerCase();
                boolean ok = true;
                for (int k = 0; k < genBlacklist.length; k++) {
                    if (genBlacklist[k].toLowerCase().equals(gen)) {
                        ok = false;
                        break;
                    }
                }
                if (ok) {
                    String crawl_url = baseUrl + structure.replaceAll("\\{gen\\}", gen).replaceAll("\\{pokemon\\}", pokemon);
                    String[] data = {crawl_url, json_mons.getJSONObject(i).getString("name"), genfamily.getString(j)};
                    urls.add(data);
                }
            }
        }

        System.out.println("Added " + urls.size() + " pages to visit.");
        return urls;
    }

    public ArrayList<String> getSetsFromUrls(ArrayList<String[]> urls, String[] formatBlacklist, int start) {
        ArrayList<String> sets = new ArrayList<>();
        ArrayList<String> database = loadDatabase();

        System.out.println("Loaded sets from database:");
        if (database.isEmpty()) {
            System.out.println("None");
        } else {
            for (int i = 0; i < database.size(); i++) {
                System.out.println(database.get(i));
            }
        }
        System.out.println();

        int urls_ammount = urls.size() - start;
        long startTime = System.nanoTime();
        for (int a = start; a < urls_ammount; a++) {
            long passedTime = System.nanoTime() - startTime;
            long averageTime = passedTime / Math.max(a, 1);
            long nanoTimeLeft = averageTime * (urls_ammount - a);
            int secondsLeft = Integer.parseInt(Long.toString(nanoTimeLeft / 1000 / 1000 / 1000));
            int minutesLeft = (secondsLeft / 60) % 60;
            int hoursLeft = secondsLeft / 3600;
            secondsLeft = secondsLeft % 60;
            String ETAString = "";
            if (hoursLeft > 0) {
                ETAString += Integer.toString(hoursLeft);
                ETAString += " Hours, ";
            }
            if (minutesLeft > 0) {
                ETAString += Integer.toString(minutesLeft);
            } else {
                ETAString += "0";
            }
            ETAString += " Minutes and ";
            if (secondsLeft > 0) {
                ETAString += Integer.toString(secondsLeft);
            } else {
                ETAString += "0";
            }
            ETAString += " Seconds";

            if (a == 0) {
                ETAString = "Calculating";
            }

            System.out.println(a + "/" + urls_ammount + " done. ETA: " + ETAString);
            System.out.println("GET " + urls.get(a)[0]);
            JSONObject obj = null;
            do {
                obj = getDexSettings(urls.get(a)[0]);
            } while (obj == null);
            JSONArray json_set = obj.getJSONArray("injectRpcs").getJSONArray(2).getJSONObject(1).getJSONArray("strategies");

            for (int i = 0; i < json_set.length(); i++) {
                JSONArray moveset = json_set.getJSONObject(i).getJSONArray("movesets");
                String format_name = json_set.getJSONObject(i).getString("format");
                boolean ok = true;
                for (int b = 0; b < formatBlacklist.length; b++) {
                    if (formatBlacklist[b].equals(format_name)) {
                        ok = false;
                        break;
                    }
                }
                if (ok) {
                    for (int j = 0; j < moveset.length(); j++) {
                        //Convert to smogon importable set
                        // Init setdata map
                        HashMap<String, ArrayList<String>> setdata = new HashMap<>();

                        //Name
                        ArrayList<String> name = new ArrayList<>();
                        name.add(urls.get(a)[1]);
                        setdata.put("name", name);

                        //Item
                        if (!moveset.getJSONObject(j).getJSONArray("items").isNull(0)) {
                            ArrayList<String> items = new ArrayList<>();
                            for (int k = 0; k < moveset.getJSONObject(j).getJSONArray("items").length(); k++) {
                                items.add(moveset.getJSONObject(j).getJSONArray("items").getString(k));
                            }
                            setdata.put("items", items);
                        }

                        //Level
                        if (format_name.equals("LC")) {
                            ArrayList<String> level = new ArrayList<>();
                            level.add("5");
                            setdata.put("level", level);
                        }

                        //Ability
                        if (!moveset.getJSONObject(j).getJSONArray("abilities").isNull(0)) {
                            ArrayList<String> abilities = new ArrayList<>();
                            for (int k = 0; k < moveset.getJSONObject(j).getJSONArray("abilities").length(); k++) {
                                abilities.add(moveset.getJSONObject(j).getJSONArray("abilities").getString(k));
                            }
                            setdata.put("abilities", abilities);
                        } else {
                            if (!urls.get(a)[2].equals("RB") && !urls.get(a)[2].equals("GS")) {
                                // Find another moveset with abilities
                                int biggestSetIndex = -1;
                                int biggestSetSize = 0;
                                for (int k = 0; k < moveset.length(); k++) {
                                    if (moveset.getJSONObject(k).getJSONArray("abilities").length() > biggestSetSize) {
                                        biggestSetIndex = k;
                                        biggestSetSize = moveset.getJSONObject(k).getJSONArray("abilities").length();
                                    }
                                }
                                if (biggestSetIndex > -1) {
                                    ArrayList<String> abilities = new ArrayList<>();
                                    for (int k = 0; k < moveset.getJSONObject(j).getJSONArray("abilities").length(); k++) {
                                        abilities.add(moveset.getJSONObject(j).getJSONArray("abilities").getString(k));
                                    }
                                    setdata.put("abilities", abilities);
                                }
                            }
                        }

                        //EVs
                        if (!moveset.getJSONObject(j).getJSONArray("evconfigs").isNull(0)) {
                            ArrayList<String> evsList = new ArrayList<>();
                            for (int k = 0; k < moveset.getJSONObject(j).getJSONArray("evconfigs").length(); k++) {
                                ArrayList<String> evs = new ArrayList<>();
                                if (moveset.getJSONObject(j).getJSONArray("evconfigs").getJSONObject(0).getInt("hp") > 0) {
                                    evs.add(moveset.getJSONObject(j).getJSONArray("evconfigs").getJSONObject(0).getInt("hp") + " HP");
                                }
                                if (moveset.getJSONObject(j).getJSONArray("evconfigs").getJSONObject(0).getInt("atk") > 0) {
                                    evs.add(moveset.getJSONObject(j).getJSONArray("evconfigs").getJSONObject(0).getInt("atk") + " Atk");
                                }
                                if (moveset.getJSONObject(j).getJSONArray("evconfigs").getJSONObject(0).getInt("def") > 0) {
                                    evs.add(moveset.getJSONObject(j).getJSONArray("evconfigs").getJSONObject(0).getInt("def") + " Def");
                                }
                                if (moveset.getJSONObject(j).getJSONArray("evconfigs").getJSONObject(0).getInt("spa") > 0) {
                                    evs.add(moveset.getJSONObject(j).getJSONArray("evconfigs").getJSONObject(0).getInt("spa") + " SpA");
                                }
                                if (moveset.getJSONObject(j).getJSONArray("evconfigs").getJSONObject(0).getInt("spd") > 0) {
                                    evs.add(moveset.getJSONObject(j).getJSONArray("evconfigs").getJSONObject(0).getInt("spd") + " SpD");
                                }
                                if (moveset.getJSONObject(j).getJSONArray("evconfigs").getJSONObject(0).getInt("spe") > 0) {
                                    evs.add(moveset.getJSONObject(j).getJSONArray("evconfigs").getJSONObject(0).getInt("spe") + " Spe");
                                }

                                do {
                                } while (!fillEvs(evs));

                                String evString = "";
                                for (int l = 0; l < evs.size(); l++) {
                                    evString += evs.get(l);
                                    if (l < evs.size() - 1) {
                                        evString += " / ";
                                    } else {
                                        // Finished
                                        evsList.add(evString);
                                    }
                                }
                                evs.clear();
                            }
                            setdata.put("evs", evsList);
                        }

                        //IVs
                        if (!moveset.getJSONObject(j).getJSONArray("ivconfigs").isNull(0)) {
                            ArrayList<String> ivsList = new ArrayList<>();
                            for (int k = 0; k < moveset.getJSONObject(j).getJSONArray("ivconfigs").length(); k++) {
                                ArrayList<String> ivs = new ArrayList<>();
                                if (moveset.getJSONObject(j).getJSONArray("ivconfigs").getJSONObject(0).has("hp")) {
                                    ivs.add(moveset.getJSONObject(j).getJSONArray("ivconfigs").getJSONObject(0).getInt("hp") + " HP");
                                }
                                if (moveset.getJSONObject(j).getJSONArray("ivconfigs").getJSONObject(0).has("atk")) {
                                    ivs.add(moveset.getJSONObject(j).getJSONArray("ivconfigs").getJSONObject(0).getInt("atk") + " Atk");
                                }
                                if (moveset.getJSONObject(j).getJSONArray("ivconfigs").getJSONObject(0).has("def")) {
                                    ivs.add(moveset.getJSONObject(j).getJSONArray("ivconfigs").getJSONObject(0).getInt("def") + " Def");
                                }
                                if (moveset.getJSONObject(j).getJSONArray("ivconfigs").getJSONObject(0).has("spa")) {
                                    ivs.add(moveset.getJSONObject(j).getJSONArray("ivconfigs").getJSONObject(0).getInt("spa") + " SpA");
                                }
                                if (moveset.getJSONObject(j).getJSONArray("ivconfigs").getJSONObject(0).has("spd")) {
                                    ivs.add(moveset.getJSONObject(j).getJSONArray("ivconfigs").getJSONObject(0).getInt("spd") + " SpD");
                                }
                                if (moveset.getJSONObject(j).getJSONArray("ivconfigs").getJSONObject(0).has("spe")) {
                                    ivs.add(moveset.getJSONObject(j).getJSONArray("ivconfigs").getJSONObject(0).getInt("spe") + " Spe");
                                }
                                if (!ivs.isEmpty()) {
                                    String ivString = "";
                                    for (int l = 0; l < ivs.size(); l++) {
                                        ivString += ivs.get(l);
                                        if (l < ivs.size() - 1) {
                                            ivString += " / ";
                                        } else {
                                            // Finished
                                            ivsList.add(ivString);
                                        }
                                    }
                                }
                                ivs.clear();
                            }
                            setdata.put("ivs", ivsList);
                        }

                        //Nature
                        if (!moveset.getJSONObject(j).getJSONArray("natures").isNull(0)) {
                            ArrayList<String> natures = new ArrayList<>();
                            for (int k = 0; k < moveset.getJSONObject(j).getJSONArray("natures").length(); k++) {
                                natures.add(moveset.getJSONObject(j).getJSONArray("natures").getString(k));
                            }
                            setdata.put("natures", natures);
                        } else {
                            if (!urls.get(a)[2].equals("RB") && !urls.get(a)[2].equals("GS")) {
                                // Find other movesets with abilities
                                ArrayList<String> natures = new ArrayList<>();
                                for (int k = 0; k < moveset.length(); k++) {
                                    for (int l = 0; l < moveset.getJSONObject(k).getJSONArray("natures").length(); l++) {
                                        if (!natures.contains(moveset.getJSONObject(k).getJSONArray("natures").getString(l))) {
                                            natures.add(moveset.getJSONObject(k).getJSONArray("natures").getString(l));
                                        }
                                    }
                                }
                                if (natures.isEmpty()) {
                                    natures.add("Hardy");
                                }
                                setdata.put("natures", natures);
                            }
                        }

                        //Moves
                        ArrayList<String> moves1 = new ArrayList<>();
                        for (int k = 0; k < moveset.getJSONObject(j).getJSONArray("moveslots").getJSONArray(0).length(); k++) {
                            moves1.add(moveset.getJSONObject(j).getJSONArray("moveslots").getJSONArray(0).getString(k));
                        }
                        setdata.put("moves1", moves1);
                        if (!moveset.getJSONObject(j).getJSONArray("moveslots").isNull(1)) {
                            ArrayList<String> moves2 = new ArrayList<>();
                            for (int k = 0; k < moveset.getJSONObject(j).getJSONArray("moveslots").getJSONArray(1).length(); k++) {
                                moves2.add(moveset.getJSONObject(j).getJSONArray("moveslots").getJSONArray(1).getString(k));
                            }
                            setdata.put("moves2", moves2);
                        }
                        if (!moveset.getJSONObject(j).getJSONArray("moveslots").isNull(2)) {
                            ArrayList<String> moves3 = new ArrayList<>();
                            for (int k = 0; k < moveset.getJSONObject(j).getJSONArray("moveslots").getJSONArray(2).length(); k++) {
                                moves3.add(moveset.getJSONObject(j).getJSONArray("moveslots").getJSONArray(2).getString(k));
                            }
                            setdata.put("moves3", moves3);
                        }
                        if (!moveset.getJSONObject(j).getJSONArray("moveslots").isNull(3)) {
                            ArrayList<String> moves4 = new ArrayList<>();
                            for (int k = 0; k < moveset.getJSONObject(j).getJSONArray("moveslots").getJSONArray(3).length(); k++) {
                                moves4.add(moveset.getJSONObject(j).getJSONArray("moveslots").getJSONArray(3).getString(k));
                            }
                            setdata.put("moves4", moves4);
                        }

                        try {
                            keepSet(setdata, urls.get(a)[2], format_name, sets, database);
                        } catch (IOException ex) {
                            System.err.println("[ERROR] Error while trying to save set data!");
                            System.exit(1);
                        }
                    }
                }
            }
        }
        return sets;
    }

    private JSONObject getDexSettings(String _url) {
        URL url = null;
        try {
            url = new URL(_url);
        } catch (MalformedURLException ex) {
            System.err.println("MalformedURLException in new URL(baseUrl).");
            System.err.println(ex.getMessage());
            return null;
        }
        HttpURLConnection connection;
        try {
            connection = (HttpURLConnection) url.openConnection();
        } catch (IOException ex) {
            System.err.println("IOException in url.openConnection().");
            System.err.println(ex.getMessage());
            return null;
        }
        try {
            connection.setRequestMethod("GET");
        } catch (ProtocolException ex) {
            System.err.println("ProtocolException in connection.setRequestMethod(\"GET\").");
            System.err.println(ex.getMessage());
            return null;
        }
        try {
            connection.connect();
        } catch (IOException ex) {
            System.err.println("IOException in connection.connect().");
            System.err.println(ex.getMessage());
            return null;
        }

        InputStream stream = null;
        try {
            stream = connection.getInputStream();
        } catch (IOException ex) {
            System.err.println("IOException in connection.getInputStream().");
            System.err.println(ex.getMessage());
            return null;
        }

        BufferedReader in = new BufferedReader(new InputStreamReader(stream));
        String line;
        String json = "";
        try {
            while ((line = in.readLine()) != null) {
                if (line.contains("dexSettings")) {
                    json = line.split("exSettings = ")[1];
                    return new JSONObject(json);
                }
            }
        } catch (IOException ex) {
            System.err.println("IOException in in.readLine().");
            System.err.println(ex.getMessage());
            System.err.println(json);
            return null;
        }
        return null;
    }

    private boolean fillEvs(ArrayList<String> evs) {
        int ev_total = 0;
        for (int i = 0; i < evs.size(); i++) {
            ev_total += Integer.parseInt(evs.get(i).split(" ")[0]);
        }
        if (ev_total < 510) {
            int ev_toAdd = 510 - ev_total;
            int stat_toModify = 0;
            int highest_Ev = 0;
            boolean found = false;
            for (int i = 0; i < evs.size(); i++) {
                int ev_amount = Integer.parseInt(evs.get(i).split(" ")[0]);
                if (ev_amount > highest_Ev && ev_amount < 252) {
                    highest_Ev = ev_amount;
                    stat_toModify = i;
                    found = true;
                }
            }
            if (!found) {
                addNewEv(evs, ev_toAdd);
            } else {
                String[] ev_string_array = evs.get(stat_toModify).split(" ");
                int ev_value = Integer.parseInt(ev_string_array[0]);
                ev_value = Math.min((ev_value + ev_toAdd), 252);
                evs.set(stat_toModify, ev_value + " " + ev_string_array[1]);
            }
            return false;
        } else {
            return true;
        }
    }

    private void addNewEv(ArrayList<String> evs, int ev_toAdd) {
        ArrayList<String> stats = new ArrayList<>();
        stats.add("HP");
        stats.add("Atk");
        stats.add("Def");
        stats.add("SpA");
        stats.add("SpD");
        stats.add("Spe");

        for (int i = 0; i < evs.size(); i++) {
            stats.remove(evs.get(i).split(" ")[1]);
        }
        String ev_string_ToAdd = ev_toAdd + " " + stats.get(0);
        evs.add(0, ev_string_ToAdd);
        if (evs.size() > 1) {
            for (int i = 0; i < evs.size() - 1; i++) {
                boolean notMoved = true;
                do {
                    if (EvSort(evs.get(i).split(" ")[1], evs.get(i + 1).split(" ")[1])) {
                        String temp = evs.get(i);
                        evs.set(i, evs.get(i + 1));
                        evs.set(i + 1, temp);
                        notMoved = false;
                    } else {
                        notMoved = true;
                    }
                } while (!notMoved);
            }
        }
    }

    private boolean EvSort(String stat1, String stat2) {
        ArrayList<String> stats = new ArrayList<>();
        stats.add("HP");
        stats.add("Atk");
        stats.add("Def");
        stats.add("SpA");
        stats.add("SpD");
        stats.add("Spe");

        return stats.indexOf(stat1) > stats.indexOf(stat2);
    }

    private ArrayList<String> loadDatabase() {
        try {
            FileInputStream fin = new FileInputStream("database.dat");
            ObjectInputStream ois = new ObjectInputStream(fin);
            ArrayList<String> db = (ArrayList<String>) ois.readObject();
            ois.close();
            fin.close();
            return db;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private void saveDatabase(ArrayList<String> db) throws IOException {
        FileOutputStream fout = new FileOutputStream("database.dat");
        ObjectOutputStream oos = new ObjectOutputStream(fout);
        oos.writeObject(db);
        oos.close();
        fout.close();
    }

    private void addToDatabase(String set, String generation, String format, ArrayList<String> db) throws IOException {
        String hash = bin2hex(getHash(generation + System.lineSeparator() + format + System.lineSeparator() + set));
        db.add(hash);
        System.out.println("Set " + hash);
        saveDatabase(db);
    }

    private byte[] getHash(String string) {
        MessageDigest digest = null;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e1) {
            e1.printStackTrace();
        }
        digest.reset();
        return digest.digest(string.getBytes());
    }

    private String bin2hex(byte[] data) {
        return String.format("%0" + (data.length * 2) + "X", new BigInteger(1, data));
    }

    private void writeSetToFile(String set, String generation, String format) {
        FileWriter fStream;
        try {
            fStream = new FileWriter("_" + generation + "_" + format + ".txt", true);
            fStream.append(set);
            fStream.append(System.lineSeparator());
            fStream.flush();
            fStream.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void keepSet(HashMap<String, ArrayList<String>> setdata, String generation, String format, ArrayList<String> sets, ArrayList<String> db) throws IOException {
        int[] counters = {0, 0, 0, 0, 0, 0, 0, 0, 0};
        String[] counterNames = {"items", "abilities", "evs", "ivs", "natures", "moves1", "moves2", "moves3", "moves4"};
        boolean finished_all = false;
        do {
            // Check if valid moves
            boolean ok = true;
            ArrayList<String> moves = new ArrayList<>();
            ArrayList<String> moves1 = setdata.getOrDefault("moves1", new ArrayList<>());
            ArrayList<String> moves2 = setdata.getOrDefault("moves2", new ArrayList<>());
            ArrayList<String> moves3 = setdata.getOrDefault("moves3", new ArrayList<>());
            ArrayList<String> moves4 = setdata.getOrDefault("moves4", new ArrayList<>());
            if (moves1.size() - 1 >= counters[5]) {
                moves.add(moves1.get(counters[5]));
            }
            if (moves2.size() - 1 >= counters[5]) {
                moves.add(moves2.get(counters[5]));
            }
            if (moves3.size() - 1 >= counters[5]) {
                moves.add(moves3.get(counters[5]));
            }
            if (moves4.size() - 1 >= counters[5]) {
                moves.add(moves4.get(counters[5]));
            }
            for (int i = 0; i < moves.size(); i++) {
                for (int j = 0; j < moves.size(); j++) {
                    if (i != j) {
                        ok = ok && !moves.get(i).equals(moves.get(j));
                    }
                }
            }

            if (ok) {
                String setdataString = getSetdataString(setdata, counters);
                if (!db.contains(bin2hex(getHash(generation + System.lineSeparator() + setdataString)))) {
                    writeSetToFile(setdataString, generation, format);
                    sets.add(setdataString);
                    addToDatabase(setdataString, generation, format, db);
                    System.out.println(setdataString);
                }
            }

            // Increment
            finished_all = true;
            for (int i = 0; i < counters.length; i++) {
                ArrayList<String> list = setdata.getOrDefault(counterNames[i], new ArrayList<>());
                if (list.size() - 1 >= counters[i] + 1) {
                    counters[i]++;
                    finished_all = false;
                    break;
                } else {
                    counters[i] = 0;
                }
            }
        } while (!finished_all);
    }

    private String getSetdataString(HashMap<String, ArrayList<String>> setdata, int[] counters) {
        ArrayList<String> name = setdata.getOrDefault("name", new ArrayList<>());
        String set = name.get(0);
        ArrayList<String> items = setdata.getOrDefault("items", new ArrayList<>());
        if (items.size() - 1 >= counters[0]) {
            set += " @ " + items.get(counters[0]);
        }
        set += System.lineSeparator();
        ArrayList<String> abilities = setdata.getOrDefault("abilities", new ArrayList<>());
        if (abilities.size() - 1 >= counters[1]) {
            set += "Ability: " + abilities.get(counters[1]) + System.lineSeparator();
        }
        ArrayList<String> evs = setdata.getOrDefault("evs", new ArrayList<>());
        if (evs.size() - 1 >= counters[2]) {
            set += "EVs: " + evs.get(counters[2]) + System.lineSeparator();
        }
        ArrayList<String> ivs = setdata.getOrDefault("ivs", new ArrayList<>());
        if (ivs.size() - 1 >= counters[3]) {
            set += "IVs: " + ivs.get(counters[3]) + System.lineSeparator();
        }
        ArrayList<String> natures = setdata.getOrDefault("natures", new ArrayList<>());
        if (natures.size() - 1 >= counters[4]) {
            set += natures.get(counters[4]) + " Nature" + System.lineSeparator();
        }
        ArrayList<String> moves1 = setdata.getOrDefault("moves1", new ArrayList<>());
        if (moves1.size() - 1 >= counters[5]) {
            set += "- " + moves1.get(counters[5]) + System.lineSeparator();
        }
        ArrayList<String> moves2 = setdata.getOrDefault("moves2", new ArrayList<>());
        if (moves2.size() - 1 >= counters[6]) {
            set += "- " + moves2.get(counters[6]) + System.lineSeparator();
        }
        ArrayList<String> moves3 = setdata.getOrDefault("moves3", new ArrayList<>());
        if (moves3.size() - 1 >= counters[7]) {
            set += "- " + moves3.get(counters[7]) + System.lineSeparator();
        }
        ArrayList<String> moves4 = setdata.getOrDefault("moves4", new ArrayList<>());
        if (moves4.size() - 1 >= counters[8]) {
            set += "- " + moves4.get(counters[8]) + System.lineSeparator();
        }
        return set;
    }
}
