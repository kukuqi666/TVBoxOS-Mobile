package com.kukuqi.tvbox.osc.data;

import android.database.Cursor;
import android.database.sqlite.SQLiteException;

import androidx.annotation.NonNull;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.kukuqi.tvbox.osc.base.App;
import com.kukuqi.tvbox.osc.util.FileUtils;

import java.io.File;
import java.io.IOException;


/**
 * 类描述:
 *
 * @author pj567
 * @since 2020/5/15
 */
public class AppDataManager {
    private static final int DB_FILE_VERSION = 3;
    private static final String DB_NAME = "tvbox";
    private static AppDataManager manager;
    private static AppDataBase dbInstance;

    private AppDataManager() {
    }

    public static void init() {
        if (manager == null) {
            synchronized (AppDataManager.class) {
                if (manager == null) {
                    manager = new AppDataManager();
                }
            }
        }
    }

    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            try {
                database.execSQL("ALTER TABLE sourceState ADD COLUMN tidSort TEXT");
            } catch (SQLiteException e) {
                e.printStackTrace();
            }
        }
    };

    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `vodRecordTmp` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `vodId` TEXT, `updateTime` INTEGER NOT NULL, `sourceKey` TEXT, `data` BLOB, `dataJson` TEXT, `testMigration` INTEGER NOT NULL)");

            // Read every thing from the former Expense table
            Cursor cursor = database.query("SELECT * FROM vodRecord");

            int id;
            int vodId;
            long updateTime;
            String sourceKey;
            String dataJson;

            while (cursor.moveToNext()) {
                id = cursor.getInt(cursor.getColumnIndex("id"));
                vodId = cursor.getInt(cursor.getColumnIndex("vodId"));
                updateTime = cursor.getLong(cursor.getColumnIndex("updateTime"));
                sourceKey = cursor.getString(cursor.getColumnIndex("sourceKey"));
                dataJson = cursor.getString(cursor.getColumnIndex("dataJson"));
                database.execSQL("INSERT INTO vodRecordTmp (id, vodId, updateTime, sourceKey, dataJson, testMigration) VALUES" +
                        " ('" + id + "', '" + vodId + "', '" + updateTime + "', '" + sourceKey + "', '" + dataJson + "',0  )");
            }


            // Delete the former table
            database.execSQL("DROP TABLE vodRecord");
            // Rename the current table to the former table name so that all other code continues to work
            database.execSQL("ALTER TABLE vodRecordTmp RENAME TO vodRecord");
        }
    };

    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            try {
                database.execSQL("ALTER TABLE vodRecord ADD COLUMN dataJson TEXT");
            } catch (SQLiteException e) {
                e.printStackTrace();
            }
        }
    };

    static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            try {
                database.execSQL("ALTER TABLE localSource ADD COLUMN type INTEGER NOT NULL DEFAULT 0");
            } catch (SQLiteException e) {
                e.printStackTrace();
            }
        }
    };

    static String dbPath() {
        return DB_NAME + ".v" + DB_FILE_VERSION + ".db";
    }

    public static AppDataBase get() {
        if (manager == null) {
            throw new RuntimeException("AppDataManager is no init");
        }
        if (dbInstance == null)
            dbInstance = Room.databaseBuilder(App.getInstance(), AppDataBase.class, dbPath())
                    .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                    //.addMigrations(MIGRATION_1_2)
                    //.addMigrations(MIGRATION_2_3)
                    //.addMigrations(MIGRATION_3_4)
                    //.addMigrations(MIGRATION_4_5)
                    .addCallback(new RoomDatabase.Callback() {
                        @Override
                        public void onCreate(@NonNull SupportSQLiteDatabase db) {
                            super.onCreate(db);
//                        LOG.i("数据库第一次创建成功");
                        }

                        @Override
                        public void onOpen(@NonNull SupportSQLiteDatabase db) {
                            super.onOpen(db);
//                        LOG.i("数据库打开成功");
                        }
                    }).allowMainThreadQueries()//可以在主线程操作
                    .build();
        return dbInstance;
    }

    public static boolean backup(File path) throws IOException {
        if (dbInstance != null && dbInstance.isOpen()) {
            dbInstance.close();
        }
        dbInstance = null;
        File db = App.getInstance().getDatabasePath(dbPath());
        if (db.exists()) {
            FileUtils.copyFile(db, path);
            return true;
        } else {
            return false;
        }
    }

    public static boolean restore(File path) throws IOException {
        if (!path.isFile() || path.length() < 16) return false;
        try (java.io.FileInputStream input = new java.io.FileInputStream(path)) {
            byte[] header = new byte[16];
            if (input.read(header) != 16 || !new String(header, java.nio.charset.StandardCharsets.US_ASCII).equals("SQLite format 3\u0000")) return false;
        }
        File db = App.getInstance().getDatabasePath(dbPath());
        if (!db.getParentFile().exists()) db.getParentFile().mkdirs();
        // Validate the contents before closing or replacing the working database.
        try (android.database.sqlite.SQLiteDatabase candidate = android.database.sqlite.SQLiteDatabase.openDatabase(path.getAbsolutePath(), null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY);
             android.database.Cursor check = candidate.rawQuery("PRAGMA quick_check", null)) {
            if (!check.moveToFirst() || !"ok".equals(check.getString(0))) return false;
            try (android.database.Cursor identity = candidate.rawQuery("SELECT identity_hash FROM room_master_table WHERE id=42", null);
                 android.database.Cursor current = get().getOpenHelper().getReadableDatabase().query("SELECT identity_hash FROM room_master_table WHERE id=42")) {
                if (!identity.moveToFirst() || !current.moveToFirst() || !identity.getString(0).equals(current.getString(0))) return false;
            }
        } catch (RuntimeException invalid) { return false; }
        File temporary = new File(db.getPath() + ".restore");
        FileUtils.copyFile(path, temporary);
        if (dbInstance != null && dbInstance.isOpen()) {
            dbInstance.close();
        }
        dbInstance = null;
        java.nio.file.Files.move(temporary.toPath(), db.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        new File(db.getPath() + "-wal").delete();
        new File(db.getPath() + "-shm").delete();
        return true;
    }
}
