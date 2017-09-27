package com.Sendarox.HiveJumpPads.update;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

/**
 * @author Gravity
 * @version 2.4
 * 
 * Check for updates on BukkitDev for a given plugin, and download the updates if needed.
 * <p>
 * <b>VERY, VERY IMPORTANT</b>: Because there are no standards for adding auto-update toggles in your plugin's config, this system provides NO CHECK WITH YOUR CONFIG to make sure the user has allowed auto-updating.
 * <br>
 * It is a <b>BUKKIT POLICY</b> that you include a boolean value in your config that prevents the auto-updater from running <b>AT ALL</b>.
 * <br>
 * If you fail to include this option in your config, your plugin will be <b>REJECTED</b> when you attempt to submit it to dev.bukkit.org.
 * </p>
 * An example of a good configuration option would be something similar to 'auto-update: true' - if this value is set to false you may NOT run the auto-updater.
 * <br>
 * If you are unsure about these rules, please read the plugin submission guidelines: http://goo.gl/8iU5l
 * 
 */

public class Updater {

    private static final String TITLE_VALUE = "name";
    private static final String LINK_VALUE = "downloadUrl";
    private static final String TYPE_VALUE = "releaseType";
    private static final String VERSION_VALUE = "gameVersion";
    private static final String QUERY = "/servermods/files?projectIds=";
    private static final String HOST = "https://api.curseforge.com";
    private static final String USER_AGENT = "Updater (by Gravity)";
    private static final String DELIMETER = "^v|[\\s_-]v";
    private static final String[] NO_UPDATE_TAG = { "-DEV", "-PRE", "-SNAPSHOT" };
    private static final int BYTE_SIZE = 1024;
    private static final String API_KEY_DEFAULT = "API_KEY";

    private final Plugin plugin;
    private final UpdateType type;
    private final boolean announce;
    private final File file;
    private final File updateFolder;
    private final UpdateCallback callback;
    private int id = -1;
    private String apiKey = "apikey";

    private String versionName;
    private String versionLink;
    private String versionType;
    private String versionGameVersion;

    private URL url;
    private Thread thread;
    private Updater.UpdateResult result = Updater.UpdateResult.SUCCESS;

    /**
     * Gives the developer the result of the update process. Can be obtained by called {@link #getResult()}
     */
    public enum UpdateResult {
        /**
         * The updater found an update, and has readied it to be loaded the next time the server restarts/reloads.
         */
        SUCCESS,
        /**
         * The updater did not find an update, and nothing was downloaded.
         */
        NO_UPDATE,
        /**
         * The server administrator has disabled the updating system.
         */
        DISABLED,
        /**
         * The updater found an update, but was unable to download it.
         */
        FAIL_DOWNLOAD,
        /**
         * For some reason, the updater was unable to contact dev.bukkit.org to download the file.
         */
        FAIL_DBO,
        /**
         * When running the version check, the file on DBO did not contain a recognizable version.
         */
        FAIL_NOVERSION,
        /**
         * The id provided by the plugin running the updater was invalid and doesn't exist on DBO.
         */
        FAIL_BADID,
        /**
         * The server administrator has improperly configured their API key in the configuration.
         */
        FAIL_APIKEY,
        /**
         * The updater found an update, but because of the UpdateType being set to NO_DOWNLOAD, it wasn't downloaded.
         */
        UPDATE_AVAILABLE
    }

    /**
     * Allows the developer to specify the type of update that will be run.
     */
    public enum UpdateType {
        /**
         * Run a version check, and then if the file is out of date, download the newest version.
         */
        DEFAULT,
        /**
         * Don't run a version check, just find the latest update and download it.
         */
        NO_VERSION_CHECK,
        /**
         * Get information about the version and the download size, but don't actually download anything.
         */
        NO_DOWNLOAD
    }

    /**
     * Represents the various release types of a file on BukkitDev.
     */
    public enum ReleaseType {
        /**
         * An "alpha" file.
         */
        ALPHA,
        /**
         * A "beta" file.
         */
        BETA,
        /**
         * A "release" file.
         */
        RELEASE
    }

    /**
     * Initialize the updater.
     *
     * @param plugin   The plugin that is checking for an update.
     * @param id       The dev.bukkit.org id of the project.
     * @param file     The file that the plugin is running from, get this by doing this.getFile() from within your main class.
     * @param type     Specify the type of update this will be. See {@link UpdateType}
     * @param announce True if the program should announce the progress of new updates in console.
     */
    public Updater(Plugin plugin, int id, File file, UpdateType type, boolean announce) {
        this(plugin, id, file, type, null, announce);
    }

    /**
     * Initialize the updater with the provided callback.
     *
     * @param plugin   The plugin that is checking for an update.
     * @param id       The dev.bukkit.org id of the project.
     * @param file     The file that the plugin is running from, get this by doing this.getFile() from within your main class.
     * @param type     Specify the type of update this will be. See {@link UpdateType}
     * @param callback The callback instance to notify when the Updater has finished
     */
    public Updater(Plugin plugin, int id, File file, UpdateType type, UpdateCallback callback) {
        this(plugin, id, file, type, callback, false);
    }

    /**
     * Initialize the updater with the provided callback.
     *
     * @param plugin   The plugin that is checking for an update.
     * @param id       The dev.bukkit.org id of the project.
     * @param file     The file that the plugin is running from, get this by doing this.getFile() from within your main class.
     * @param type     Specify the type of update this will be. See {@link UpdateType}
     * @param callback The callback instance to notify when the Updater has finished
     * @param announce True if the program should announce the progress of new updates in console.
     */
    public Updater(Plugin plugin, int id, File file, UpdateType type, UpdateCallback callback, boolean announce) {
        this.plugin = plugin;
        this.type = type;
        this.announce = announce;
        this.file = file;
        this.id = id;
        this.updateFolder = this.plugin.getServer().getUpdateFolderFile();
        this.callback = callback;

        String key = API_KEY_DEFAULT;
        if (API_KEY_DEFAULT.equalsIgnoreCase(key) || "".equals(key)) {
            key = null;
        }

        this.apiKey = key;
        
        try {
            this.url = new URL(Updater.HOST + Updater.QUERY + this.id);
        } catch (final MalformedURLException e) {
            this.plugin.getLogger().log(Level.SEVERE, "The project ID provided for updating, " + this.id + " is invalid.", e);
            this.result = UpdateResult.FAIL_BADID;
        }

        if (this.result != UpdateResult.FAIL_BADID) {
            this.thread = new Thread(new UpdateRunnable());
            this.thread.start();
        } else {
            runUpdater();
        }
    }

    /**
     * Get the result of the update process.
     *
     * @return result of the update process.
     * @see UpdateResult
     */
    public Updater.UpdateResult getResult() {
        this.waitForThread();
        return this.result;
    }

    /**
     * Get the latest version's release type.
     *
     * @return latest version's release type.
     * @see ReleaseType
     */
    public ReleaseType getLatestType() {
        this.waitForThread();
        if (this.versionType != null) {
            for (ReleaseType type : ReleaseType.values()) {
                if (this.versionType.equalsIgnoreCase(type.name())) {
                    return type;
                }
            }
        }
        return null;
    }

    /**
     * Get the latest version's game version (such as "CB 1.2.5-R1.0").
     *
     * @return latest version's game version.
     */
    public String getLatestGameVersion() {
        this.waitForThread();
        return this.versionGameVersion;
    }

    /**
     * Get the latest version's name (such as "Project v1.0").
     *
     * @return latest version's name.
     */
    public String getLatestName() {
        this.waitForThread();
        return this.versionName;
    }

    /**
     * Get the latest version's direct file link.
     *
     * @return latest version's file link.
     */
    public String getLatestFileLink() {
        this.waitForThread();
        return this.versionLink;
    }

    /**
     * As the result of Updater output depends on the thread's completion, it is necessary to wait for the thread to finish
     * before allowing anyone to check the result.
     */
    private void waitForThread() {
        if ((this.thread != null) && this.thread.isAlive()) {
            try {
                this.thread.join();
            } catch (final InterruptedException e) {
                this.plugin.getLogger().log(Level.SEVERE, null, e);
            }
        }
    }

    /**
     * Save an update from dev.bukkit.org into the server's update folder.
     *
     * @param file the name of the file to save it as.
     */
    private void saveFile(String file) {
        final File folder = this.updateFolder;

        deleteOldFiles();
        if (!folder.exists()) {
            this.fileIOOrError(folder, folder.mkdir(), true);
        }
        downloadFile();

        final File dFile = new File(folder.getAbsolutePath(), file);
        if (dFile.getName().endsWith(".zip")) {
            this.unzip(dFile.getAbsolutePath());
        }
        if (this.announce) {
            this.plugin.getLogger().info("Finished updating.");
        }
    }

    /**
     * Download a file and save it to the specified folder.
     */
    private void downloadFile() {
        BufferedInputStream in = null;
        FileOutputStream fout = null;
        try {
            URL fileUrl = followRedirects(this.versionLink);
            final int fileLength = fileUrl.openConnection().getContentLength();
            in = new BufferedInputStream(fileUrl.openStream());
            fout = new FileOutputStream(new File(this.updateFolder, file.getName()));

            final byte[] data = new byte[Updater.BYTE_SIZE];
            int count;
            if (this.announce) {
                this.plugin.getLogger().info("About to download a new update: " + this.versionName);
            }
            long downloaded = 0;
            while ((count = in.read(data, 0, Updater.BYTE_SIZE)) != -1) {
                downloaded += count;
                fout.write(data, 0, count);
                final int percent = (int) ((downloaded * 100) / fileLength);
                if (this.announce && ((percent % 10) == 0)) {
                    this.plugin.getLogger().info("Downloading update: " + percent + "% of " + fileLength + " bytes.");
                }
            }
        } catch (Exception ex) {
            this.plugin.getLogger().log(Level.WARNING, "The auto-updater tried to download a new update, but was unsuccessful.", ex);
            this.result = Updater.UpdateResult.FAIL_DOWNLOAD;
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (final IOException ex) {
                this.plugin.getLogger().log(Level.SEVERE, null, ex);
            }
            try {
                if (fout != null) {
                    fout.close();
                }
            } catch (final IOException ex) {
                this.plugin.getLogger().log(Level.SEVERE, null, ex);
            }
        }
    }

    private URL followRedirects(String location) throws IOException {
        URL resourceUrl, base, next;
        HttpURLConnection conn;
        String redLoc;
        while (true) {
            resourceUrl = new URL(location);
            conn = (HttpURLConnection) resourceUrl.openConnection();

            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0...");

            switch (conn.getResponseCode()) {
                case HttpURLConnection.HTTP_MOVED_PERM:
                case HttpURLConnection.HTTP_MOVED_TEMP:
                    redLoc = conn.getHeaderField("Location");
                    base = new URL(location);
                    next = new URL(base, redLoc);  // Deal with relative URLs
                    location = next.toExternalForm();
                    continue;
            }
            break;
        }
        return conn.getURL();
    }

    /**
     * Remove possibly leftover files from the update folder.
     */
    private void deleteOldFiles() {
        File[] list = listFilesOrError(this.updateFolder);
        for (final File xFile : list) {
            if (xFile.getName().endsWith(".zip")) {
                this.fileIOOrError(xFile, xFile.mkdir(), true);
            }
        }
    }

    /**
     * Part of Zip-File-Extractor, modified by Gravity for use with Updater.
     *
     * @param file the location of the file to extract.
     */
    private void unzip(String file) {
        final File fSourceZip = new File(file);
        try {
            final String zipPath = file.substring(0, file.length() - 4);
            ZipFile zipFile = new ZipFile(fSourceZip);
            Enumeration<? extends ZipEntry> e = zipFile.entries();
            while (e.hasMoreElements()) {
                ZipEntry entry = e.nextElement();
                File destinationFilePath = new File(zipPath, entry.getName());
                this.fileIOOrError(destinationFilePath.getParentFile(), destinationFilePath.getParentFile().mkdirs(), true);
                if (!entry.isDirectory()) {
                    final BufferedInputStream bis = new BufferedInputStream(zipFile.getInputStream(entry));
                    int b;
                    final byte[] buffer = new byte[Updater.BYTE_SIZE];
                    final FileOutputStream fos = new FileOutputStream(destinationFilePath);
                    final BufferedOutputStream bos = new BufferedOutputStream(fos, Updater.BYTE_SIZE);
                    while ((b = bis.read(buffer, 0, Updater.BYTE_SIZE)) != -1) {
                        bos.write(buffer, 0, b);
                    }
                    bos.flush();
                    bos.close();
                    bis.close();
                    final String name = destinationFilePath.getName();
                    if (name.endsWith(".jar") && this.pluginExists(name)) {
                        File output = new File(this.updateFolder, name);
                        this.fileIOOrError(output, destinationFilePath.renameTo(output), true);
                    }
                }
            }
            zipFile.close();
            moveNewZipFiles(zipPath);

        } catch (final IOException e) {
            this.plugin.getLogger().log(Level.SEVERE, "The auto-updater tried to unzip a new update file, but was unsuccessful.", e);
            this.result = Updater.UpdateResult.FAIL_DOWNLOAD;
        } finally {
            this.fileIOOrError(fSourceZip, fSourceZip.delete(), false);
        }
    }

    /**
     * Find any new files extracted from an update into the plugin's data directory.
     * @param zipPath path of extracted files.
     */
    private void moveNewZipFiles(String zipPath) {
        File[] list = listFilesOrError(new File(zipPath));
        for (final File dFile : list) {
            if (dFile.isDirectory() && this.pluginExists(dFile.getName())) {
                final File oFile = new File(this.plugin.getDataFolder().getParent(), dFile.getName());
                final File[] dList = listFilesOrError(dFile);
                final File[] oList = listFilesOrError(oFile);
                for (File cFile : dList) {
                    boolean found = false;
                    for (final File xFile : oList) {
                        if (xFile.getName().equals(cFile.getName())) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        File output = new File(oFile, cFile.getName());
                        this.fileIOOrError(output, cFile.renameTo(output), true);
                    } else {
                        this.fileIOOrError(cFile, cFile.delete(), false);
                    }
                }
            }
            this.fileIOOrError(dFile, dFile.delete(), false);
        }
        File zip = new File(zipPath);
        this.fileIOOrError(zip, zip.delete(), false);
    }

    /**
     * Check if the name of a jar is one of the plugins currently installed, used for extracting the correct files out of a zip.
     *
     * @param name a name to check for inside the plugins folder.
     * @return true if a file inside the plugins folder is named this.
     */
    private boolean pluginExists(String name) {
        File[] plugins = listFilesOrError(new File("plugins"));
        for (final File file : plugins) {
            if (file.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check to see if the program should continue by evaluating whether the plugin is already updated, or shouldn't be updated.
     *
     * @return true if the version was located and is not the same as the remote's newest.
     */
    private boolean versionCheck() {
        final String title = this.versionName;
        if (this.type != UpdateType.NO_VERSION_CHECK) {
            final String localVersion = this.plugin.getDescription().getVersion();
            if (title.split(DELIMETER).length >= 2) {
                final String remoteVersion = title.split(DELIMETER)[title.split(DELIMETER).length - 1].split(" ")[0];

                if (this.hasTag(localVersion) || !this.shouldUpdate(localVersion, remoteVersion)) {
                    this.result = Updater.UpdateResult.NO_UPDATE;
                    return false;
                }
            } else {
                final String authorInfo = this.plugin.getDescription().getAuthors().isEmpty() ? "" : " (" + this.plugin.getDescription().getAuthors().get(0) + ")";
                this.plugin.getLogger().warning("The author of this plugin" + authorInfo + " has misconfigured their Auto Update system");
                this.plugin.getLogger().warning("File versions should follow the format 'PluginName vVERSION'");
                this.plugin.getLogger().warning("Please notify the author of this error.");
                this.result = Updater.UpdateResult.FAIL_NOVERSION;
                return false;
            }
        }
        return true;
    }

    /**
     * <b>If you wish to run mathematical versioning checks, edit this method.</b>
     * <p>
     * With default behavior, Updater will NOT verify that a remote version available on BukkitDev
     * which is not this version is indeed an "update".
     * If a version is present on BukkitDev that is not the version that is currently running,
     * Updater will assume that it is a newer version.
     * This is because there is no standard versioning scheme, and creating a calculation that can
     * determine whether a new update is actually an update is sometimes extremely complicated.
     * </p>
     * <p>
     * Updater will call this method from {@link #versionCheck()} before deciding whether
     * the remote version is actually an update.
     * If you have a specific versioning scheme with which a mathematical determination can
     * be reliably made to decide whether one version is higher than another, you may
     * revise this method, using the local and remote version parameters, to execute the
     * appropriate check.
     * </p>
     * <p>
     * Returning a value of <b>false</b> will tell the update process that this is NOT a new version.
     * Without revision, this method will always consider a remote version at all different from
     * that of the local version a new update.
     * </p>
     * @param localVersion the current version
     * @param remoteVersion the remote version
     * @return true if Updater should consider the remote version an update, false if not.
     */
    public boolean shouldUpdate(String localVersion, String remoteVersion) {
        return !localVersion.equalsIgnoreCase(remoteVersion);
    }

    /**
     * Evaluate whether the version number is marked showing that it should not be updated by this program.
     *
     * @param version a version number to check for tags in.
     * @return true if updating should be disabled.
     */
    private boolean hasTag(String version) {
        for (final String string : Updater.NO_UPDATE_TAG) {
            if (version.contains(string)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Make a connection to the BukkitDev API and request the newest file's details.
     *
     * @return true if successful.
     */
    private boolean read() {
        try {
            final URLConnection conn = this.url.openConnection();
            conn.setConnectTimeout(5000);

            if (this.apiKey != null) {
                conn.addRequestProperty("X-API-Key", this.apiKey);
            }
            conn.addRequestProperty("User-Agent", Updater.USER_AGENT);

            conn.setDoOutput(true);

            final BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            final String response = reader.readLine();

            final JSONArray array = (JSONArray) JSONValue.parse(response);

            if (array.isEmpty()) {
                this.plugin.getLogger().warning("The updater could not find any files for the project id " + this.id);
                this.result = UpdateResult.FAIL_BADID;
                return false;
            }

            JSONObject latestUpdate = (JSONObject) array.get(array.size() - 1);
            this.versionName = (String) latestUpdate.get(Updater.TITLE_VALUE);
            this.versionLink = (String) latestUpdate.get(Updater.LINK_VALUE);
            this.versionType = (String) latestUpdate.get(Updater.TYPE_VALUE);
            this.versionGameVersion = (String) latestUpdate.get(Updater.VERSION_VALUE);

            return true;
        } catch (final IOException e) {
            if (e.getMessage().contains("HTTP response code: 403")) {
                this.plugin.getLogger().severe("dev.bukkit.org rejected the API key provided in plugins/Updater/config.yml");
                this.plugin.getLogger().severe("Please double-check your configuration to ensure it is correct.");
                this.result = UpdateResult.FAIL_APIKEY;
            } else {
                this.plugin.getLogger().severe("The updater could not contact dev.bukkit.org for updating.");
                this.plugin.getLogger().severe("If you have not recently modified your configuration and this is the first time you are seeing this message, the site may be experiencing temporary downtime.");
                this.result = UpdateResult.FAIL_DBO;
            }
            this.plugin.getLogger().log(Level.SEVERE, null, e);
            return false;
        }
    }

    /**
     * Perform a file operation and log any errors if it fails.
     * @param file file operation is performed on.
     * @param result result of file operation.
     * @param create true if a file is being created, false if deleted.
     */
    private void fileIOOrError(File file, boolean result, boolean create) {
        if (!result) {
            this.plugin.getLogger().severe("The updater could not " + (create ? "create" : "delete") + " file at: " + file.getAbsolutePath());
        }
    }

    private File[] listFilesOrError(File folder) {
        File[] contents = folder.listFiles();
        if (contents == null) {
            this.plugin.getLogger().severe("The updater could not access files at: " + this.updateFolder.getAbsolutePath());
            return new File[0];
        } else {
            return contents;
        }
    }

    /**
     * Called on main thread when the Updater has finished working, regardless
     * of result.
     */
    public interface UpdateCallback {
        /**
         * Called when the updater has finished working.
         * @param updater The updater instance
         */
        void onFinish(Updater updater);
    }

    private class UpdateRunnable implements Runnable {
        @Override
        public void run() {
            runUpdater();
        }
    }

    private void runUpdater() {
        if (this.url != null && (this.read() && this.versionCheck())) {
            if ((this.versionLink != null) && (this.type != UpdateType.NO_DOWNLOAD)) {
                String name = this.file.getName();
                if (this.versionLink.endsWith(".zip")) {
                    name = this.versionLink.substring(this.versionLink.lastIndexOf("/") + 1);
                }
                this.saveFile(name);
            } else {
                this.result = UpdateResult.UPDATE_AVAILABLE;
            }
        }

        if (this.callback != null) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    runCallback();
                }
            }.runTask(this.plugin);
        }
    }

    private void runCallback() {
        this.callback.onFinish(this);
    }
}