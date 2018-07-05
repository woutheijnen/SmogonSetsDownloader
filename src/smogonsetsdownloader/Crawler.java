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
        String lastAbilityUsed = "";
        String lastNatureUsed = "";
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
                        //Name
                        String setdata = urls.get(a)[1];

                        //Item
                        if (!moveset.getJSONObject(j).getJSONArray("items").isNull(0)) {
                            setdata += " @ " + moveset.getJSONObject(j).getJSONArray("items").getString(0);
                        }
                        setdata += System.lineSeparator();

                        //Level
                        if (format_name.equals("LC")) {
                            setdata += "Level: 5" + System.lineSeparator();
                        }

                        //Ability
                        if (!moveset.getJSONObject(j).getJSONArray("abilities").isNull(0)) {
                            setdata += "Ability: " + moveset.getJSONObject(j).getJSONArray("abilities").getString(0) + System.lineSeparator();
                            lastAbilityUsed = moveset.getJSONObject(j).getJSONArray("abilities").getString(0);
                        } else {
                            if (!urls.get(a)[2].equals("RB") && !urls.get(a)[2].equals("GS") && !lastAbilityUsed.equals("")) {
                                setdata += "Ability: " + lastAbilityUsed + System.lineSeparator();
                            }
                        }

                        //EVs
                        if (!moveset.getJSONObject(j).getJSONArray("evconfigs").isNull(0)) {
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

                            setdata += "EVs: ";
                            for (int k = 0; k < evs.size(); k++) {
                                setdata += evs.get(k);
                                if (k < evs.size() - 1) {
                                    setdata += " / ";
                                } else {
                                    setdata += System.lineSeparator();
                                }
                            }
                            evs.clear();
                        }

                        //IVs
                        if (!moveset.getJSONObject(j).getJSONArray("ivconfigs").isNull(0)) {
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
                                setdata += "IVs: ";
                                for (int k = 0; k < ivs.size(); k++) {
                                    setdata += ivs.get(k);
                                    if (k < ivs.size() - 1) {
                                        setdata += " / ";
                                    } else {
                                        setdata += System.lineSeparator();
                                    }
                                }
                            }
                            ivs.clear();
                        }

                        //Nature
                        if (!moveset.getJSONObject(j).getJSONArray("natures").isNull(0)) {
                            setdata += moveset.getJSONObject(j).getJSONArray("natures").getString(0) + " Nature" + System.lineSeparator();
                            lastNatureUsed = moveset.getJSONObject(j).getJSONArray("natures").getString(0);
                        } else {
                            if (!urls.get(a)[2].equals("RB") && !urls.get(a)[2].equals("GS") && !lastNatureUsed.equals("")) {
                                setdata += lastNatureUsed + " Nature" + System.lineSeparator();
                            }
                        }

                        //Moves
                        setdata += "- " + moveset.getJSONObject(j).getJSONArray("moveslots").getJSONArray(0).getString(0) + System.lineSeparator();
                        if (!moveset.getJSONObject(j).getJSONArray("moveslots").isNull(1)) {
                            setdata += "- " + moveset.getJSONObject(j).getJSONArray("moveslots").getJSONArray(1).getString(0) + System.lineSeparator();
                        }
                        if (!moveset.getJSONObject(j).getJSONArray("moveslots").isNull(2)) {
                            setdata += "- " + moveset.getJSONObject(j).getJSONArray("moveslots").getJSONArray(2).getString(0) + System.lineSeparator();
                        }
                        if (!moveset.getJSONObject(j).getJSONArray("moveslots").isNull(3)) {
                            setdata += "- " + moveset.getJSONObject(j).getJSONArray("moveslots").getJSONArray(3).getString(0) + System.lineSeparator();
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

    private void keepSet(String setdata, String generation, String format, ArrayList<String> sets, ArrayList<String> db) throws IOException {
        if (!db.contains(bin2hex(getHash(generation + System.lineSeparator() + setdata)))) {
            writeSetToFile(setdata, generation, format);
            sets.add(setdata);
            addToDatabase(setdata, generation, format, db);
            System.out.println(setdata);
        }
    }
}
