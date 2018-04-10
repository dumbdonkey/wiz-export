package com.zuoca.wiznote;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.Data;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

/**
 * @author zuochangan@gmail.com
 * @date 2018/4/10
 * @time 下午21:33
 */
public class Export {

    private static final String TEMPORARY_ZIP_NAME = "__wiznote.zip";
    @Option(required = true, name = "-i", usage = "specify index.db location in full path")
    private String indexLocation;
    @Option(required = true, name = "-d", usage = "specify notes directory")
    private String dataDir;
    @Option(required = true, name = "-t", usage = "specify target directory")
    private String targetDir;

    public static void main(String[] args) {
        Export export = new Export();
        CmdLineParser cmdLineParser = new CmdLineParser(export);
        try {
            cmdLineParser.parseArgument(args);
            export.export();
        } catch (CmdLineException e) {
            cmdLineParser.printUsage(System.err);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private List<WizDocument> buildDocumentList() {

        List<WizDocument> documentList = new ArrayList<>();
        Connection connection = null;
        try {
            Class.forName("org.sqlite.JDBC");
            // create a database connection
            connection = DriverManager.getConnection("jdbc:sqlite:" + indexLocation);
            Statement statement = connection.createStatement();
            statement.setQueryTimeout(30);  // set timeout to 30 sec.
            ResultSet rs = statement.executeQuery("select * from wiz_document");
            while (rs.next()) {

                WizDocument document = new WizDocument();
                document.setGuid(rs.getString("DOCUMENT_GUID"));
                document.setTitle(rs.getString("DOCUMENT_TITLE"));
                document.setLocation(rs.getString("DOCUMENT_Location"));
                documentList.add(document);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (connection != null) {
                    connection.close();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        return documentList;
    }

    private void export() throws IOException {

        List<WizDocument> documentList = this.buildDocumentList();

        for (WizDocument document : documentList) {
            System.out.println("convert " + document.getTitle());
            File zipFile = this.copyAsZipFile(document);
            if (null != zipFile) {
                this.unzip(zipFile);
            }
        }
    }

    private File copyAsZipFile(WizDocument document) throws IOException {
        String dirPath = touchDir(targetDir + document.getLocation());
        File dir = new File(dirPath + "/" + document.getTitle());
        dir.mkdirs();
        File originFile = new File(dataDir + "/{" + document.getGuid() + "}");
        if (!originFile.exists()) {
            System.err.println(originFile.getAbsolutePath() + " not exist!");
            return null;
        }
        File zipFile = new File(dir.getAbsolutePath() + "/" + TEMPORARY_ZIP_NAME);
        Files.copy(originFile.toPath(), zipFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        zipFile.deleteOnExit();
        return zipFile;
    }

    private void unzip(File file) {

        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                System.out.println("Extracting: " + entry);
                String entryName = entry.getName();
                if (entryName.contains("/")) {
                    String prefixDir = entryName.substring(0, entryName.lastIndexOf("/"));
                    touchDir(file.getPath().replace(TEMPORARY_ZIP_NAME, "") + "/" + prefixDir);
                }
                File targetFile = new File(file.getPath().replace(TEMPORARY_ZIP_NAME, "") + "/" + entry.getName());
                Files.copy(zis, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            zis.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String touchDir(String dir) {
        File f = new File(dir);
        f.mkdirs();
        return f.getAbsolutePath();
    }

    @Data
    private static class WizDocument {

        private String guid;
        private String title;
        private String location;
    }
}
