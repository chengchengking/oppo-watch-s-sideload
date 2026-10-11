package com.rtos.ftprobe;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

/**
 * ★ ftprobe16 的关键新增：把 pkg 用我们【自己的】content:// 暴露出去。
 *
 * 为什么必须这样做（ftprobe15 实测到最后一米的报错）：
 *   E PTC.DATATRANS.a: startSending() FileNotFoundException;
 *     /sdcard/Download/com.rtos.pkg: open failed: EACCES (Permission denied)
 *   读文件走的是 ok/a.java:101
 *       this.f22221j = this.f22215c.getContentResolver().openInputStream(uriFromFile);
 *   AF 进程是 u0_a100（priv-app 但【清单未声明任何 STORAGE/MEDIA 权限】），
 *   读不了 /sdcard/Download/（属 u0_a293/media_rw，0660）。
 *
 *   → 换成 content://com.rtos.ftprobe.pkg/pkg：AF 通过我们的 provider 拿流，
 *     我们在自己进程里（有权限）读文件，再把 fd 交给它。
 *
 * 两个必须做对的地方：
 *  ① exported=true + grantUriPermissions=true，并且我们在发 OpCode4 之前
 *     显式 grantUriPermission("com.heytap.accessory", uri, FLAG_GRANT_READ_URI_PERMISSION)。
 *     （健康 App 就是这么干的：它的 FileProvider 是 exported=false + grantUriPermissions=true。）
 *  ② query() 必须把 `_data`（绝对路径）放在第 0 列 ——
 *     com.heytap.accessory.file.FileTransfer.checkSource() 对 content:// 是
 *     query(uri, {"_data"}) → getString(0) 当真实路径 → new File(path).exists()。
 */
public class PkgProvider extends ContentProvider {
    public static final String AUTHORITY = "com.rtos.ftprobe.pkg";
    public static final Uri URI = Uri.parse("content://" + AUTHORITY + "/pkg");

    static File pkgFile(android.content.Context ctx) {
        File[] med = ctx.getExternalMediaDirs();
        if (med != null && med.length > 0 && med[0] != null) {
            File f = new File(med[0], "com.rtos.pkg");
            if (f.exists()) return f;
        }
        // 退路：assets 里那份复制到私有目录
        return new File(ctx.getFilesDir(), "com.rtos.pkg");
    }

    @Override public boolean onCreate() { return true; }

    static void plog(android.content.Context ctx, String s) {
        try {
            File dir = ctx.getExternalFilesDir(null);
            if (dir != null && !dir.exists()) dir.mkdirs();
            FileOutputStream fo = new FileOutputStream(new File(dir, "ft17_provider.txt"), true);
            fo.write((System.currentTimeMillis() + "  " + s + "\n").getBytes("UTF-8"));
            fo.close();
        } catch (Throwable ig) {}
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File f = pkgFile(getContext());
        plog(getContext(), "★ openFile " + uri + " -> " + f.getAbsolutePath() + " len=" + f.length());
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] selectionArgs, String sortOrder) {
        File f = pkgFile(getContext());
        String abs = f.getAbsolutePath();
        plog(getContext(), "query " + uri + " proj=" + java.util.Arrays.toString(projection)
                + " -> _data=" + abs + " len=" + f.length() + " exists=" + f.exists());
        // ★ 第 0 列必须是 _data = 绝对路径
        MatrixCursor c = new MatrixCursor(new String[]{
                "_data", OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        c.addRow(new Object[]{abs, "com.rtos.pkg", f.length()});
        return c;
    }

    @Override public String getType(Uri uri) {
        plog(getContext(), "getType " + uri);
        return "application/octet-stream";
    }

    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
}
