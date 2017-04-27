package sqlitehelper;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.os.IBinder;
import android.text.TextUtils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by sevenshal on 2017/4/26.
 * adb shell am startservice -n {packagename}/sqlitehelper.SqliteHelperService
 * telnet {phone ip} 33060
 */

public class SqliteHelperService extends Service {

    private Thread serverThread;

    private ServerSocket serverSocket;

    @Override

    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (serverThread == null) {
            serverThread = new ServerThread();
            serverThread.start();
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (serverThread != null) {
            serverThread.interrupt();
        }
    }

    private class ServerThread extends Thread {
        @Override
        public void run() {
            try {
                serverSocket = new ServerSocket(33060);
                while (true) {
                    Thread conThread = new ConThread(serverSocket.accept());
                    conThread.start();
                }
            } catch (Exception e) {
                e.printStackTrace();
                stopSelf();
            } finally {
                IOUtils.close(serverSocket);
            }
        }
    }

    private class ConThread extends Thread {
        private Socket con;

        private Map<String, CmdHandler> mapHandlers = new HashMap<>();

        private CmdHandler cmdNotFoundHandler = new CmdNotFoundHandler();

        private CmdHandler sqlCmdHandler = new SqlCmdHandler();

        private CmdHandler noDbHandler = new NoDbSelectHandler();

        private BufferedReader reader;

        private OutputStream os;

        private File databaseDir;

        private ResultRender resultRender;

        private SQLiteDatabase db;

        private boolean exit;

        public ConThread(Socket con) {
            this.con = con;
        }

        @Override
        public void run() {
            try {
                databaseDir = getDatabasePath("sqlite_helper_test.sqlite").getParentFile();
                mapHandlers.put(".help", new HelpCmdHandler());
                mapHandlers.put(".databases", new DatabaseCmdHandler());
                mapHandlers.put(".create", new CreateCmdHandler());
                mapHandlers.put(".using", new UsingCmdHandler());
                mapHandlers.put(".mode", new ModeCmdHandler());
                mapHandlers.put(".master", new MasterCmdHandler());
                mapHandlers.put(".exit", new ExitCmdHandler());
                reader = new BufferedReader(new InputStreamReader(con.getInputStream()));
                os = con.getOutputStream();
                resultRender = new TextRender(os);
                resultRender.render(0, "Press enter to continue");
                resultRender.flush();

                String cmd;
                while ((cmd = reader.readLine()) != null) {
                    if (TextUtils.isEmpty(cmd)) {
                        resultRender.render(0, "Enter \".help\" for usage hints.");
                    } else {
                        try {
                            String[] exec = cmd.split(" ");
                            String keyCmd = cmd;
                            if (exec.length > 0) {
                                keyCmd = exec[0];
                            }
                            CmdHandler handler;
                            boolean isCmd = cmd.startsWith(".");
                            if (isCmd) {
                                CmdHandler cmdHandler = mapHandlers.get(keyCmd.toLowerCase());
                                if (cmdHandler != null) {
                                    handler = cmdHandler;
                                } else {
                                    handler = cmdNotFoundHandler;
                                }
                            } else {
                                handler = db == null ? noDbHandler : sqlCmdHandler;
                            }
                            handler.handleCmd(cmd, exec);
                        } catch (Throwable e) {
                            e.printStackTrace();
                            try {
                                resultRender.render(-1, e.getMessage());
                            } catch (Throwable ex) {
                                ex.printStackTrace();
                                throw e;
                            }
                        }
                    }
                    if (!exit) {
                        resultRender.render(0, "\nsqlite> ");
                        resultRender.flush();
                    }
                }
            } catch (Throwable e) {
                e.printStackTrace();
            } finally {
                exit();
            }
        }

        public void exit() {
            IOUtils.close(reader);
            IOUtils.close(os);
            IOUtils.close(con);
            IOUtils.close(db);
            exit = true;
        }

        public boolean isTextMode() {
            return resultRender instanceof TextRender;
        }

        private class TextRender implements ResultRender {

            private BufferedWriter writer;

            public TextRender(OutputStream os) {
                this.writer = new BufferedWriter(new OutputStreamWriter(os));
            }

            @Override
            public void render(Cursor cursor) throws Throwable {
                int c = cursor.getColumnCount();
                for (int i = 0; i < c; i++) {
                    if (i != 0) {
                        writer.write("|");
                    }
                    writer.write(cursor.getColumnName(i));
                }
                writer.newLine();
                while (cursor.moveToNext()) {
                    for (int i = 0; i < c; i++) {
                        if (i != 0) {
                            writer.write("|");
                        }
                        writer.write(cursor.getString(i));
                    }
                    if (!cursor.isLast()) {
                        writer.newLine();
                    }
                }
            }

            @Override
            public void render(int ok, String msg) throws Throwable {
                writer.write(msg);
            }

            @Override
            public void render(String[] array) throws Throwable {
                for (int i = 0, c = array.length; i < c; ) {
                    writer.write(array[i++]);
                    if (i != c) {
                        writer.newLine();
                    }
                }
            }

            @Override
            public void flush() throws Throwable {
                writer.flush();
            }
        }

        private class JdbcRender implements ResultRender {

            DataOutputStream writer;

            public JdbcRender(OutputStream os) {
                this.writer = new DataOutputStream(os);
            }

            @Override
            public void render(Cursor cursor) throws Throwable {
                int c = cursor.getColumnCount();
                writer.writeInt(c);
                for (int i = 0; i < c; i++) {
                    writer.writeUTF(cursor.getColumnName(i));
                }

                int rows = cursor.getCount();
                writer.writeInt(rows);
                while (cursor.moveToNext()) {
                    for (int i = 0; i < c; i++) {
                        writer.writeUTF(cursor.getString(i));
                    }
                }
            }

            @Override
            public void render(int ok, String msg) throws Throwable {
                writer.writeInt(ok);
            }

            @Override
            public void render(String[] array) throws Throwable {
                for (String str : array) {
                    writer.writeUTF(str);
                }
            }

            @Override
            public void flush() throws Throwable {
                writer.flush();
            }
        }

        private class MasterCmdHandler implements CmdHandler {
            @Override
            public void handleCmd(String cmd, String[] all) throws Throwable {
                if (db == null) {
                    noDbHandler.handleCmd(cmd, all);
                } else {
                    Cursor cursor = db.rawQuery("select * from sqlite_master", null);
                    resultRender.render(cursor);
                    cursor.close();
                }
            }
        }

        private class CreateCmdHandler implements CmdHandler {
            @Override
            public void handleCmd(String cmd, String[] all) throws Throwable {
                String db = all.length > 1 ? all[1] : null;
                if (TextUtils.isEmpty(db)) {
                    resultRender.render(-1, "invalid database name");
                } else {
                    openOrCreateDatabase(db, Context.MODE_PRIVATE, null);
                    resultRender.render(0, "database created");
                }
            }
        }

        private class ExitCmdHandler implements CmdHandler {
            @Override
            public void handleCmd(String cmd, String[] all) throws Throwable {
                exit();
            }
        }

        private class ModeCmdHandler implements CmdHandler {
            @Override
            public void handleCmd(String cmd, String[] all) throws Throwable {
                String mode = all.length > 1 ? all[1] : "text";
                if (TextUtils.equals(mode, "text")) {
                    resultRender = new TextRender(os);
                    resultRender.render(0, "change to text mode");
                } else if (TextUtils.equals(mode, "jdbc")) {
                    resultRender = new JdbcRender(os);
                    resultRender.render(0, "change to jdbc mode");
                } else {
                    resultRender.render(0, "current mode is " + (isTextMode() ? "text" : "jdbc"));
                }
            }
        }

        private class DatabaseCmdHandler implements CmdHandler {

            DatabaseCmdHandler() {
            }

            @Override
            public void handleCmd(String cmd, String[] all) throws Throwable {
                String[] files = databaseDir.list();
                if (files == null || files.length == 0) {
                    resultRender.render(-1, "no databases found");
                } else {
                    resultRender.render(files);
                }
            }
        }

        private class UsingCmdHandler implements CmdHandler {

            private String dbName;

            @Override
            public void handleCmd(String cmd, String[] all) throws Throwable {
                String database = all.length > 1 ? all[1] : null;
                if (TextUtils.isEmpty(database) || !(new File(databaseDir, database)).exists()) {
                    resultRender.render(-1, "please input a correct database file name, current using '"
                            + (dbName == null ? "no database using" : dbName) + "'");
                } else {
                    if (db != null) {
                        db.close();
                    }
                    db = openOrCreateDatabase(database, Context.MODE_PRIVATE, null);
                    resultRender.render(0, "database changed success, current using '" + database + "'");
                    dbName = database;
                }
            }
        }

        private class SqlCmdHandler implements CmdHandler {
            @Override
            public void handleCmd(String cmd, String[] all) throws Throwable {
                String sqlAction = all[0];
                if ("select".equalsIgnoreCase(sqlAction)) {
                    Cursor cursor = db.rawQuery(cmd, null);
                    resultRender.render(cursor);
                    cursor.close();
                } else {
                    SQLiteStatement sqLiteStatement = db.compileStatement(cmd);
                    int changeRows = sqLiteStatement.executeUpdateDelete();
                    resultRender.render(changeRows, "" + changeRows + " rows changed");
                }
            }
        }


        private class CmdNotFoundHandler implements CmdHandler {
            @Override
            public void handleCmd(String cmd, String[] all) throws Throwable {
                resultRender.render(-1, "Error: unknown command or invalid arguments:  \"" + all[0] + "\". Enter \".help\" for help");
            }
        }

        private class NoDbSelectHandler implements CmdHandler {
            @Override
            public void handleCmd(String cmd, String[] all) throws Throwable {
                resultRender.render(-1, "no database select, select one by cmd '.using database'");
            }
        }

        private class HelpCmdHandler implements CmdHandler {
            @Override
            public void handleCmd(String cmd, String[] all) throws Throwable {
                resultRender.render(0, ".help                  Show this message\n" +
                        ".databases             Show all available database files\n" +
                        ".using                 Select one database\n" +
                        ".mode text|jdbc        'text' select result show in text, please do not select jdbc mode for it's used by jdbc driver(undone)\n" +
                        ".master                Show sqlite_master info\n" +
                        ".create                Create an empty sqlite file\n" +
                        ".exit                  Exit this program\n" +
                        "more sqlite language help, see http://www.runoob.com/sqlite/sqlite-tutorial.html");
            }
        }
    }


    interface CmdHandler {
        void handleCmd(String cmd, String[] all) throws Throwable;
    }

    private interface ResultRender {
        void render(Cursor cursor) throws Throwable;

        void render(int ok, String msg) throws Throwable;

        void render(String[] array) throws Throwable;

        void flush() throws Throwable;
    }
}
