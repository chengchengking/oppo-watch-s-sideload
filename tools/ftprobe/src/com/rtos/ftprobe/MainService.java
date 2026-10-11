package com.rtos.ftprobe;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Parcel;
import android.os.ResultReceiver;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;

/**
 * ftprobe15 —— ★ 把探针从 Activity 改成【前台 Service】。
 *
 * 为什么必须改（这是贯穿前 14 个窗口的隐藏障碍）：
 *   探针是 Activity，靠 `am start` 拉起。但手机上前台常被别的 App 占着（B 站/微信…），
 *   探针的窗口拿不到 focus → ColorOS 判定 Input dispatching timed out →
 *   ANR 杀掉进程。日志就永远停在中间某一行，看起来像"表端没反应"。
 *
 *   实证：ftprobe13 的日志停在 `findPeerAgents(-1,22) -> 10004`；
 *        ftprobe14 停在 `requestServiceConnection(local=40513) -> 0`；
 *        logcat: "Killing com.rtos.ftprobe (adj 0): user request after error:
 *                 Input dispatching timed out ... Waited 5000ms for FocusEvent"
 *   → 前序窗口的"卡住"里，有相当一部分其实是探针自己被系统杀了。
 *
 * 前台 Service 有常驻通知、importance 高、不受 focus 影响，不会被 ANR。
 *
 * 三条已确证的事实（沿用 ftprobe13/14）：
 *   ① urn 必须带 ":<version>"（g3/a.java:46-52）→ profileId 才对 → findPeerAgents 才有回调
 *   ② AccessoryId 取活体值（PeerAgent.getAccessory().mId）
 *   ③ 发 OpCode4 后必须 registerCallbackFacilitator(txId) 才会触发 mk.l.l() 处理队列
 */
public class MainService extends Service {
    static final String TAG = "FTP15";
    static final String CH = "ftp15";

    interface Cb { void on(Bundle b); }

    static class CbBinder extends Binder {
        final String desc;
        final Cb c1, c2;
        CbBinder(String desc, Cb c1, Cb c2) { this.desc = desc; this.c1 = c1; this.c2 = c2; }
        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws android.os.RemoteException {
            if (code >= 1 && code <= 16777215) data.enforceInterface(desc);
            if (code == 1598968902) { reply.writeString(desc); return true; }
            if (code == 1 || code == 2) {
                Bundle b = null;
                try {
                    b = (data.readInt() != 0) ? Bundle.CREATOR.createFromParcel(data) : null;
                    if (b != null && clRef != null) b.setClassLoader(clRef);
                    if (code == 1) { if (c1 != null) c1.on(b); } else { if (c2 != null) c2.on(b); }
                } catch (Throwable t) { try { slog("  [onTransact EX] " + t); } catch (Throwable ig) {} }
                reply.writeNoException();
                return true;
            }
            return super.onTransact(code, data, reply, flags);
        }
    }

    static class DeathBinder extends Binder {
        final String appName;
        DeathBinder(String appName) { this.appName = appName; }
        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws android.os.RemoteException {
            String d = "com.heytap.accessory.api.IDeathCallback";
            if (code >= 1 && code <= 16777215) data.enforceInterface(d);
            if (code == 1598968902) { reply.writeString(d); return true; }
            if (code == 1) { reply.writeNoException(); reply.writeString(appName); return true; }
            return super.onTransact(code, data, reply, flags);
        }
    }

    static volatile ClassLoader clRef;
    static volatile OutputStreamWriter SW;

    OutputStreamWriter w;
    dalvik.system.DexClassLoader cl;
    Object ifm, ifl;
    long clientId = -1;
    int myAgentId = -1;
    int healthFileAgent = -1;
    int healthSvcAgent = -1;
    volatile long liveAccId = -1;
    volatile int livePeerId = -1;
    volatile Object foundPeer = null;
    volatile String connIdSeen = null;
    volatile int peerHits = 0, connHits = 0, chanHits = 0, rrHits = 0;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public int onStartCommand(Intent i, int flags, int startId) {
        startForegroundNow();
        if (w == null) {
            try {
                File dir = getExternalFilesDir(null);
                if (dir != null && !dir.exists()) dir.mkdirs();
                w = new OutputStreamWriter(new FileOutputStream(new File(dir, "ft18_result.txt"), false), "UTF-8");
                SW = w;
            } catch (Throwable t) { Log.e(TAG, "open", t); }
        }
        new Thread(new Runnable() { public void run() {
            try { go(); } catch (Throwable t) { log("FATAL " + t); log(DONE()); }
        } }).start();
        return START_NOT_STICKY;   // 别让系统重启造成风暴
    }

    void startForegroundNow() {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                NotificationChannel c = new NotificationChannel(CH, "FTP", NotificationManager.IMPORTANCE_LOW);
                nm.createNotificationChannel(c);
            }
            Notification n = new Notification.Builder(this, CH)
                    .setContentTitle("FTP15 running")
                    .setContentText("framework probe")
                    .setSmallIcon(android.R.drawable.stat_notify_sync)
                    .build();
            startForeground(1501, n);
        } catch (Throwable t) { Log.e(TAG, "fg", t); }
    }

    static String DONE() { return "=== ftprobe15 done ==="; }

    void log(String s) {
        try { Log.i(TAG, s); } catch (Throwable ig) {}
        try { if (w != null) { w.write(s + "\n"); w.flush(); } } catch (Throwable ig) {}
    }
    static void slog(String s) {
        try { Log.i(TAG, s); } catch (Throwable ig) {}
        try { if (SW != null) { SW.write(s + "\n"); SW.flush(); } } catch (Throwable ig) {}
    }
    static String str(Object o) { return o == null ? "null" : String.valueOf(o); }

    static String bs(Object o) {
        if (!(o instanceof Bundle)) return str(o);
        Bundle bd = (Bundle) o;
        StringBuilder sb = new StringBuilder("{");
        for (String k : bd.keySet()) {
            sb.append(k).append("=");
            try {
                Object v = bd.get(k);
                if (v instanceof ArrayList) sb.append("ArrayList(size=").append(((ArrayList<?>) v).size()).append(")");
                else sb.append(v == null ? "null" : v.getClass().getSimpleName()).append("#").append(str(v));
            } catch (Throwable t) { sb.append("<EX ").append(t.getClass().getSimpleName()).append(">"); }
            sb.append(" ");
        }
        return sb.append("}").toString();
    }

    Object proxy(Class<?> iface, final IBinder binder) {
        return Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[]{iface}, new InvocationHandler() {
            @Override public Object invoke(Object p, Method m, Object[] a) {
                if ("asBinder".equals(m.getName())) return binder;
                Class<?> rt = m.getReturnType();
                if (rt == boolean.class) return Boolean.TRUE;
                if (rt == int.class) return 0;
                if (rt == long.class) return 0L;
                return null;
            }
        });
    }

    Object call(Object t, String m, Class<?>[] sig, Object... args) {
        if (t == null) { log("  ! " + m + " target null"); return null; }
        try { return t.getClass().getMethod(m, sig).invoke(t, args); }
        catch (Throwable e) { Throwable c = e.getCause() != null ? e.getCause() : e; log("  ! " + m + " EX " + c); return null; }
    }

    volatile String pkgUri = null;
    volatile String pkgPath = null;
    volatile long pkgLen = 0;

    /**
     * 把 assets 里的 pkg 落到我们【自己的】external media 目录
     * （/storage/emulated/0/Android/media/com.rtos.ftprobe/），
     * 然后用我们自己的 provider 以 content:// 暴露，并显式授权给 com.heytap.accessory。
     *
     * 为什么不用 /sdcard/Download/：AF 进程 u0_a100 无存储权限，
     * ftprobe15 实测 EACCES(Permission denied) → startSending 失败 → 传输被取消。
     */
    void preparePkg() {
        try {
            File[] med = getExternalMediaDirs();
            File md = new File(med[0], "com.rtos.pkg");
            if (md.getParentFile() != null) md.getParentFile().mkdirs();
            if (!md.exists() || md.length() != 3488) {   // v2 bundle（含 icon）长度；R20b
                java.io.InputStream in = getAssets().open("com.rtos.pkg");
                java.io.FileOutputStream out = new java.io.FileOutputStream(md);
                byte[] buf = new byte[8192]; int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                out.close(); in.close();
            }
            pkgPath = md.getAbsolutePath();
            pkgLen = md.length();
            log("pkg path = " + pkgPath + " len=" + pkgLen);
        } catch (Throwable t) { log("preparePkg EX " + t); }

        pkgUri = PkgProvider.URI.toString();
        try {
            grantUriPermission("com.heytap.accessory", PkgProvider.URI,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            log("grantUriPermission(com.heytap.accessory, " + pkgUri + ") OK");
        } catch (Throwable t) { log("grantUriPermission EX " + t); }

        // 自测：确认 provider 真的能被读到（第 0 列 = _data 绝对路径）
        try {
            android.database.Cursor c = getContentResolver().query(PkgProvider.URI,
                    new String[]{"_data", "_display_name", "_size"}, null, null, null);
            if (c != null) {
                if (c.moveToFirst()) {
                    log("provider self-test OK: _data=" + c.getString(0)
                            + " name=" + c.getString(1) + " size=" + c.getLong(2));
                } else log("provider self-test: empty cursor");
                c.close();
            } else log("provider self-test: null cursor");
        } catch (Throwable t) { log("provider self-test EX " + t); }
    }

    void go() throws Exception {
        log("=== ftprobe18 start (R17 wire contract: TL+MEX headroom) ===");
        cl = new dalvik.system.DexClassLoader(
                getPackageManager().getPackageInfo("com.heytap.accessory", 0).applicationInfo.sourceDir,
                new File(getCacheDir(), "af17").getAbsolutePath(), null, getClassLoader());
        clRef = cl;
        log("dex ok");

        preparePkg();

        Intent it = new Intent("com.heytap.accessory.IAfFtManager");
        it.setComponent(new ComponentName("com.heytap.accessory",
                "com.oplus.pantaconnect.datatrans.platform.services.FileService"));
        log("bind FileService");
        bindService(it, new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName cn, IBinder b) {
                log("FS connected");
                try {
                    Method ai = Class.forName("com.heytap.accessory.core.IFileManager$Stub", false, cl)
                            .getDeclaredMethod("asInterface", IBinder.class);
                    ai.setAccessible(true);
                    ifl = ai.invoke(null, b);
                } catch (Throwable t) { log("FS asInterface EX " + t); }
                bindFw();
            }
            @Override public void onServiceDisconnected(ComponentName cn) { log("FS disconnected"); }
        }, android.content.Context.BIND_AUTO_CREATE);
    }

    void bindFw() {
        try {
            Class<?> iDeath = Class.forName("com.heytap.accessory.api.IDeathCallback", false, cl);
            Class<?> iScInd = Class.forName("com.heytap.accessory.api.IServiceConnectionIndicationCallback", false, cl);
            Class<?> iSc = Class.forName("com.heytap.accessory.api.IServiceConnectionCallback", false, cl);
            Class<?> iCh = Class.forName("com.heytap.accessory.api.IServiceChannelCallback", false, cl);
            Class<?> iPeer = Class.forName("com.heytap.accessory.api.IPeerAgentCallback", false, cl);

            Intent it = new Intent("com.heytap.accessory.action.FRAMEWORK_MANAGER");
            it.setComponent(new ComponentName("com.heytap.accessory",
                    "com.oplus.pantaconnect.datatrans.platform.services.FrameworkService"));
            log("bind FrameworkService");
            bindService(it, new ServiceConnection() {
                @Override public void onServiceConnected(ComponentName cn, IBinder b) {
                    log("FW connected");
                    try {
                        Method ai = Class.forName("com.heytap.accessory.api.IFrameworkManager$Stub", false, cl)
                                .getDeclaredMethod("asInterface", IBinder.class);
                        ai.setAccessible(true);
                        ifm = ai.invoke(null, b);
                        log("asInterface -> " + ifm.getClass().getName());
                        step1(iDeath, iScInd, iSc, iCh, iPeer);
                    } catch (Throwable t) { log("FW EX " + t); log(DONE()); }
                }
                @Override public void onServiceDisconnected(ComponentName cn) { log("FW disconnected"); }
            }, android.content.Context.BIND_AUTO_CREATE);
        } catch (Throwable t) { log("bindFw EX " + t); log(DONE()); }
    }

    void step1(Class<?> iDeath, Class<?> iScInd, Class<?> iSc, Class<?> iCh, Class<?> iPeer) {
        log("handleAuthentication -> " + str(call(ifm, "handleAuthentication", new Class<?>[]{int.class}, 0)));

        IBinder deathB = new DeathBinder(getPackageName());
        CbBinder scIndB = new CbBinder("com.heytap.accessory.api.IServiceConnectionIndicationCallback",
                new Cb() { public void on(Bundle b) { log("[scInd] " + bs(b)); } }, null);

        Object r = call(ifm, "makeFrameworkConnection",
                new Class<?>[]{int.class, String.class, iDeath, int.class, iScInd},
                0, getPackageName(), proxy(iDeath, deathB), 0, proxy(iScInd, scIndB));
        log("makeFrameworkConnection -> " + bs(r));
        if (r instanceof Bundle) {
            Object c = ((Bundle) r).get("clientId");
            if (c instanceof Long) clientId = (Long) c; else if (c instanceof Integer) clientId = (Integer) c;
        }
        log("clientId=" + clientId);
        if (clientId < 0) { log(DONE()); return; }

        Object mineO = call(ifm, "getAgentId",
                new Class<?>[]{long.class, String.class, String.class},
                clientId, getPackageName(), "com.rtos.ftprobe.FtAgent");
        log("ourAgentId -> " + str(mineO));
        try { myAgentId = Integer.parseInt(String.valueOf(mineO)); } catch (Throwable ig) {}
        log("myAgentId=" + myAgentId);

        // ★ 健康 App 的 agent 也要拿一次（进程重启后 id 会变；本机现在是哪个要实测）
        Object hf = call(ifm, "getAgentId", new Class<?>[]{long.class, String.class, String.class},
                clientId, "com.heytap.health", "com.heytap.health.watch.oaf.WatchAppFileAgent");
        log("getAgentId(health, WatchAppFileAgent) -> " + str(hf));
        try { healthFileAgent = Integer.parseInt(String.valueOf(hf)); } catch (Throwable ig) {}
        Object hs = call(ifm, "getAgentId", new Class<?>[]{long.class, String.class, String.class},
                clientId, "com.heytap.health", "com.heytap.health.watch.oaf.WatchAppServiceAgent");
        log("getAgentId(health, WatchAppServiceAgent) -> " + str(hs));
        try { healthSvcAgent = Integer.parseInt(String.valueOf(hs)); } catch (Throwable ig) {}

        final String[] aids = { String.valueOf(myAgentId) };
        for (String aid : aids) {
            final String tag = aid;
            CbBinder peerB = new CbBinder("com.heytap.accessory.api.IPeerAgentCallback",
                    new Cb() {
                        public void on(Bundle b) {
                            peerHits++;
                            log("[peer/" + tag + "] onPeerAgentsFound " + bs(b));
                            if (b == null) return;
                            for (String k : b.keySet()) {
                                Object v = b.get(k);
                                log("    key=" + k + " cls=" + (v == null ? "null" : v.getClass().getName()));
                                if (v instanceof ArrayList) {
                                    ArrayList<?> al = (ArrayList<?>) v;
                                    log("    list size=" + al.size());
                                    if (!al.isEmpty()) {
                                        Object pa = al.get(0);
                                        foundPeer = pa;
                                        log("    PeerAgent@0 = " + str(pa));
                                        try {
                                            Object pid = pa.getClass().getMethod("getId").invoke(pa);
                                            if (pid instanceof Integer) livePeerId = (Integer) pid;
                                            else if (pid instanceof Long) livePeerId = ((Long) pid).intValue();
                                        } catch (Throwable ig) {
                                            try {
                                                java.lang.reflect.Field f = pa.getClass().getDeclaredField("id");
                                                f.setAccessible(true);
                                                Object pid = f.get(pa);
                                                if (pid instanceof Integer) livePeerId = (Integer) pid;
                                                else if (pid instanceof Long) livePeerId = ((Long) pid).intValue();
                                            } catch (Throwable ig2) {}
                                        }
                                        try {
                                            Object acc = pa.getClass().getMethod("getAccessory").invoke(pa);
                                            if (acc != null) {
                                                try {
                                                    Field fm = acc.getClass().getDeclaredField("mId");
                                                    fm.setAccessible(true);
                                                    Object vv = fm.get(acc);
                                                    if (vv instanceof Long) liveAccId = (Long) vv;
                                                    else if (vv instanceof Integer) liveAccId = (Integer) vv;
                                                } catch (Throwable ig2) {}
                                                log("      ★ liveAccId=" + liveAccId + " livePeerId=" + livePeerId);
                                            }
                                        } catch (Throwable t) { log("      acc EX " + t); }
                                    }
                                }
                            }
                        }
                    },
                    new Cb() { public void on(Bundle b) { log("[peer/" + tag + "] updated " + bs(b)); } });
            Object rr = call(ifm, "findPeerAgents",
                    new Class<?>[]{long.class, long.class, String.class, iPeer},
                    clientId, -1L, aid, proxy(iPeer, peerB));
            log("findPeerAgents(-1,\"" + aid + "\") -> " + str(rr));
            try { Thread.sleep(3000); } catch (Throwable ig) {}
        }
        log("peerHits=" + peerHits + " foundPeer=" + (foundPeer != null));

        if (foundPeer != null) {
            Class<?> peerAgentClass = foundPeer.getClass();
            CbBinder connB = new CbBinder("com.heytap.accessory.api.IServiceConnectionCallback",
                    new Cb() {
                        public void on(Bundle b) {
                            connHits++;
                            log("[conn] onConnectionResponse " + bs(b));
                            if (b != null) {
                                for (String k : b.keySet()) {
                                    Object v = b.get(k);
                                    log("    conn key=" + k + " = " + str(v));
                                    if (("connectionId".equals(k) || k.toLowerCase().contains("connection"))
                                            && connIdSeen == null) connIdSeen = str(v);
                                }
                            }
                        }
                    },
                    new Cb() { public void on(Bundle b) { log("[conn] onConnectionLost " + bs(b)); } });
            CbBinder chB = new CbBinder("com.heytap.accessory.api.IServiceChannelCallback",
                    new Cb() { public void on(Bundle b) { chanHits++; log("[chan] onRead " + bs(b)); } },
                    new Cb() { public void on(Bundle b) { chanHits++; log("[chan] onError " + bs(b)); } });

            Object rr = call(ifm, "requestServiceConnection",
                    new Class<?>[]{long.class, String.class, peerAgentClass, iSc, iCh},
                    clientId, String.valueOf(myAgentId), foundPeer, proxy(iSc, connB), proxy(iCh, chB));
            log("requestServiceConnection(local=" + myAgentId + ") -> " + str(rr));
            try { Thread.sleep(6000); } catch (Throwable ig) {}
        }
        log("connHits=" + connHits + " chanHits=" + chanHits + " connIdSeen=" + connIdSeen);

        // ★ 真机安装流程：先 cid=10 控制报文（支起表端接收端），再 OpCode4 文件推送
        //   顺序很重要：真机日志显示 cid=10 → 4秒后 → 文件
        long accNow = liveAccId > 0 ? liveAccId : 0L;
        int peerNow = livePeerId > 0 ? livePeerId : 50;
        if (accNow > 0) {
            try { sendInstallPreCheck(accNow, peerNow); }
            catch (Throwable t) { log("cid=10 EX " + t); }
        }

        // 等 5 秒让控制报文到达表端
        try { Thread.sleep(5000); } catch (Throwable ig) {}

        // 文件推送
        fireOp4AndUnlock();

        // 等 8 秒让表端装完并回报（cid=5），然后发 cid=3 应用列表查询，把回包引到 [chan] onRead
        try { Thread.sleep(8000); } catch (Throwable ig) {}
        if (accNow > 0) {
            try { sendAppListQuery(accNow, peerNow); }
            catch (Throwable t) { log("cid=3 EX " + t); }
        }

        log("rrHits=" + rrHits);
        try { Thread.sleep(12000); } catch (Throwable ig) {}
        log(DONE());
    }

    /** cid=3 AppListInfoMsg：让表端把已装应用列表发回来（安装结果的最终判据）。
     *  手工 wire-format（字段号取自 classes2 类 dump 的 *_FIELD_NUMBER，权威）：
     *  MsgBody{appListInfo=3: AppListInfo{}}、MsgHeader{actionAnchor=1, commandId=2, protocolVersion=4}、
     *  AppCommandMsg{header=1, body=2}。 */
    void sendAppListQuery(long acc, int peer) {
        if (ifm == null) { log("ifm null, skip cid=3"); return; }
        log("--- cid=3 应用列表查询 ---");
        try {
            byte[] anchor = java.util.UUID.randomUUID().toString().replace("-", "").getBytes("UTF-8");
            java.io.ByteArrayOutputStream hd = new java.io.ByteArrayOutputStream();
            hd.write(0x0a); hd.write(varint(anchor.length)); hd.write(anchor);  // header.field1 actionAnchor
            hd.write(0x10); hd.write(varint(3));                                 // header.field2 commandId=3
            hd.write(0x20); hd.write(varint(1));                                 // header.field4 protocolVersion=1
            byte[] headerBytes = hd.toByteArray();
            java.io.ByteArrayOutputStream bd = new java.io.ByteArrayOutputStream();
            bd.write(0x1a); bd.write(0);                                          // body.field3 appListInfo{} 空
            byte[] bodyBytes = bd.toByteArray();
            java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
            o.write(0x0a); o.write(varint(headerBytes.length)); o.write(headerBytes);
            o.write(0x12); o.write(varint(bodyBytes.length)); o.write(bodyBytes);
            byte[] proto = o.toByteArray();
            log("cid=3 proto len=" + proto.length + " hex=" + hex(proto));

            Object svcLocal = call(ifm, "getAgentId",
                    new Class<?>[]{long.class, String.class, String.class},
                    clientId, getPackageName(), "com.rtos.ftprobe.SvcAgent");
            String loc = String.valueOf(svcLocal);
            byte[] payload = new byte[proto.length + 1];
            payload[0] = (byte) 3;
            System.arraycopy(proto, 0, payload, 1, proto.length);
            byte[] buf = new byte[payload.length + 17];
            System.arraycopy(payload, 0, buf, 15, payload.length);
            Object r = call(ifm, "sendMessage",
                    new Class<?>[]{long.class, String.class, String.class, long.class,
                            byte[].class, boolean.class, int.class, int.class},
                    clientId, loc, String.valueOf(peer), acc, buf, false, payload.length, 15);
            log("  [cid=3] local=" + loc + " len=" + payload.length + " -> " + str(r));
        } catch (Throwable t) { log("appList EX " + t); }
    }

    ResultReceiver mkRR() {
        return new ResultReceiver(new Handler()) {
            @Override protected void onReceiveResult(int code, Bundle b) {
                rrHits++;
                log("★★ ResultReceiver code=" + code + "  " + bs(b));
                if (code == 100) log("   → 传输进行中（★ 载荷已被读！）");
                if (code == 101) log("   → ★★★ 传输完成！");
                if (code == 102) log("   → 出错");
            }
        };
    }

    void fireOp4AndUnlock() {
        if (ifl == null) { log("ifl null, skip Op4"); return; }
        long acc = liveAccId > 0 ? liveAccId : 0L;
        if (acc <= 0) { log("liveAccId 未知，跳过 Op4"); return; }
        int peer = livePeerId > 0 ? livePeerId : 50;

        // ★ 用我们自己的 content:// —— AF 无存储权限，读不了裸路径
        String src = pkgUri != null ? pkgUri : ("/sdcard/Download/com.rtos.pkg");
        log("--- OpCode4 矩阵：acc=" + acc + " peer=" + peer + " src=" + src + " ---");

        // ContainerId 候选：我们自己的 agentId，以及实测拿到的健康 App 两个 agent
        String[] conts = { String.valueOf(myAgentId), String.valueOf(healthFileAgent), String.valueOf(healthSvcAgent) };
        java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<String>();
        for (String cont : conts) {
            if (cont == null || cont.equals("-1") || cont.equals("0") || !seen.add(cont)) continue;
            String js = "{\"OpCode\":4,\"Parameters\":{"
                    + "\"SourcePath\":\"" + src + "\",\"DestinationPath\":\"\","
                    + "\"PeerId\":\"" + peer + "\",\"ContainerId\":\"" + cont + "\","
                    + "\"AccessoryId\":" + acc + ",\"FileSize\":" + pkgLen + ","
                    + "\"FileName\":\"pkg_com.rtos.qrwatch\",\"FileURI\":\"" + src + "\","
                    + "\"PackageName\":\"" + getPackageName() + "\","
                    + "\"AgentClassName\":\"com.heytap.health.watch.oaf.WatchAppFileAgent\","
                    + "\"FileInfo\":\"appstore_uri_package\"}}";
            Object res = call(ifl, "sendCommand", new Class<?>[]{String.class}, js);
            log("[Op4 cont=" + cont + "] -> " + bs(res));

            int txId = -1;
            if (res instanceof Bundle) {
                Object id = ((Bundle) res).get("ID");
                if (id instanceof Integer) txId = (Integer) id;
            }
            if (txId <= 0) { log("   cont=" + cont + " 未建任务（-2/-1），换下一个"); continue; }

            Object ok = call(ifl, "registerCallbackFacilitator",
                    new Class<?>[]{int.class, ResultReceiver.class}, txId, mkRR());
            log("★ registerCallbackFacilitator(" + txId + ") -> " + str(ok));
            // ★ 传输 7KB 应该秒级完成；给足 12 秒让 100/101/102 回来
            try { Thread.sleep(12000); } catch (Throwable ig) {}
        }

        // ★ R20b：图标注册推送（商店流程 1:1——pkg 建链后 ~120ms 发 icon_<包名>，FileInfo=appstore_uri_icon）
        // R21c：改为多图标循环——已验证 icon_com.rtos.qrwatch 注册生效（用户目视确认桌面图标变二维码）
        String[][] icons = {
                {"icon_com.rtos.qrwatch", "icon_com.rtos.qrwatch"},
                {"icon_com.xfaith.woodenfish", "icon_com.xfaith.woodenfish"}
        };
        for (String[] ic : icons) {
            fireIconPush(acc, peer, ic[0], ic[1]);
        }
    }

    /** R20b/R21c：图标注册推送。真机商店日志（08:26 木鱼安装）：pkg 传输完成后 ~120ms，
     *  FT_SETUP_REQ fileName=icon_com.xfaith.woodenfish（11294B PNG），uri=appstore_uri_icon，同 sid=272 通道。
     *  实现：把 assets 里的图标字节（assetName）覆盖写到 provider 服务的同一路径（pkgPath），
     *  再 Op4 推送——wire 文件名（fileName=icon_<目标包名>）+ FileInfo（appstore_uri_icon）
     *  就是表端桌面图标注册的路由钥匙。✅ 已验证：推送+重启后桌面图标生效（R21b）。 */
    void fireIconPush(long acc, int peer, String assetName, String wireName) {
        if (ifl == null || pkgPath == null) { log("icon push skip (ifl/pkgPath null)"); return; }
        try {
            java.io.InputStream in = getAssets().open(assetName);
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] ibuf = new byte[8192]; int n;
            while ((n = in.read(ibuf)) > 0) bo.write(ibuf, 0, n);
            in.close();
            byte[] iconBytes = bo.toByteArray();
            java.io.FileOutputStream fo = new java.io.FileOutputStream(pkgPath);
            fo.write(iconBytes); fo.close();
            long iconLen = new java.io.File(pkgPath).length();
            log("--- icon push: len=" + iconLen + " fileName=" + wireName + " ---");

            String[] iconts = { String.valueOf(myAgentId), String.valueOf(healthFileAgent), String.valueOf(healthSvcAgent) };
            java.util.LinkedHashSet<String> seen2 = new java.util.LinkedHashSet<String>();
            for (String cont : iconts) {
                if (cont == null || cont.equals("-1") || cont.equals("0") || !seen2.add(cont)) continue;
                String js = "{\"OpCode\":4,\"Parameters\":{"
                        + "\"SourcePath\":\"" + pkgUri + "\",\"DestinationPath\":\"\","
                        + "\"PeerId\":\"" + peer + "\",\"ContainerId\":\"" + cont + "\","
                        + "\"AccessoryId\":" + acc + ",\"FileSize\":" + iconLen + ","
                        + "\"FileName\":\"" + wireName + "\",\"FileURI\":\"" + pkgUri + "\","
                        + "\"PackageName\":\"" + getPackageName() + "\","
                        + "\"AgentClassName\":\"com.heytap.health.watch.oaf.WatchAppFileAgent\","
                        + "\"FileInfo\":\"appstore_uri_icon\"}}";
                Object res = call(ifl, "sendCommand", new Class<?>[]{String.class}, js);
                log("[iconOp4 cont=" + cont + "] -> " + bs(res));
                int itxId = -1;
                if (res instanceof Bundle) {
                    Object id = ((Bundle) res).get("ID");
                    if (id instanceof Integer) itxId = (Integer) id;
                }
                if (itxId <= 0) { log("   cont=" + cont + " 未建任务，换下一个"); continue; }
                Object iok = call(ifl, "registerCallbackFacilitator",
                        new Class<?>[]{int.class, ResultReceiver.class}, itxId, mkRR());
                log("★ icon registerCallbackFacilitator(" + itxId + ") -> " + str(iok));
                try { Thread.sleep(12000); } catch (Throwable ig) {}
                break; // 每个图标一个成功任务即可
            }
        } catch (Throwable t) { log("fireIconPush EX " + t); }
    }

    // ==================== cid=10 安装预检（R15 还原） ====================

    /**
     * 真机安装是"先控制后文件"两步。控制报文 = `InstallPreCheckMsg`，cid=10：
     *
     *   AbsMsgSendClient.immediateSendMessage（dexdump 逐指令）：
     *     new MessageEvent(267, header.getCommandId(), appCommandMsg.toByteArray())
     *     → cid 就是 protobuf header 里的 commandId
     *
     *   InstallPreCheckMsg.getMsgRequestBody():
     *     MsgBody{ checkRequest: InstallCheckRequest{ fileSize } }
     *
     *   外层：AppCommandMsg{ header{ actionAnchor, protocolVersion, commandId=10, bodyMd5 }, body }
     *
     * 发送（BaseAdapter.java:726-738，权威）：
     *   mServiceProxy.sendMessage(clientId, urn, peerAgent.getAgentId(), peerAgent.getAccessoryId(),
     *                             data, secure, cid, mode)     ← IFrameworkManager 事务码 16
     *
     * 载荷构造：优先用健康 App 的真实 protobuf 类（DexClassLoader 加载 classes2/14），
     *          失败则退回手工 wire-format 编码。
     */
    void sendInstallPreCheck(long acc, int peer) {
        if (ifm == null) { log("ifm null, skip cid=10"); return; }
        log("--- cid=10 安装预检 (acc=" + acc + " peer=" + peer + ") ---");

        // ★ 关键：sendMessage 的第 2 参是【本地 agentId 的十进制字符串】，不是 urn。
        //   FrameworkManagerServiceNative.sendMessage 反汇编实测：
        //     v6  = a.i(-1, param2)   → localAgentId
        //     v14 = a.i(-1, param3)   → remoteAgentId
        //     temp.base.a.a(3, v6, v14, accId) 必须为 true，否则 10018 ACC_DORMANT
        //   传 urn 会解析成 -1 → 本地服务记录为空 → 10018。
        Object svcLocal = call(ifm, "getAgentId",
                new Class<?>[]{long.class, String.class, String.class},
                clientId, getPackageName(), "com.rtos.ftprobe.SvcAgent");
        log("SvcAgent localAgentId -> " + str(svcLocal));
        String svcLocalStr = String.valueOf(svcLocal);

        byte[] proto = buildPreCheckPayload();
        if (proto == null || proto.length == 0) { log("载荷构造失败，跳过"); return; }
        log("protobuf len=" + proto.length + " hex=" + hex(proto));

        // ★★ 最后两个 int 的语义（sendMessage 反汇编 + n.f() 实测）：
        //   日志格式是 "write(boolean) : [" + param8 + ',' + param7 + "] " + len，
        //   守卫是 `param8 + param7 <= bArr.length`，随后
        //     n.f():  i16 = param8 - 1;  i15 = param7 + 1;
        //             bArr[i16] = secure ? 4 : 0;          // ★ 在 param8-1 处写 secure 标志
        //             写 i15 字节，从 i16 开始
        //   ⇒ param7 = 载荷长度，param8 = 载荷在 buffer 里的偏移（≥1）。
        //   之前把两者传反（传了 [3,10]）→ 只发出 10 字节，帧被截断。
        // ★★★ 载荷前缀：AbsMsgAgent.sendMessageSync（dexdump 实测，权威）：
        //     boolean z3 = cid >= 255;
        //     int i5 = z3 ? 3 : 1;                       // 前缀长度
        //     byte[] bArr2 = new byte[bArr.length + i5];
        //     bArr2[0] = (byte) cid;                     // ★ 第 1 字节 = cid
        //     System.arraycopy(bArr, 0, bArr2, i5, bArr.length);
        //   ⇒ 帧载荷 = [cid(1B)] + [protobuf]，真机 cid=10 → 10 + 48 = 49... 
        //   而真机日志是 51 B（len 字段）。差异待下一轮用 D 级日志核对；
        //   本窗口先把 cid 前缀加上（这是结构上必需的一步）。

        // 只用我们自己的 SvcAgent（FtAgent 无服务记录，sendMessage 的 dormant/wake 路径会阻塞 binder）
        java.util.LinkedHashSet<String> locals = new java.util.LinkedHashSet<String>();
        locals.add(svcLocalStr);

        // ★★★ R17b wire 契约（afsrc 实读：FrameworkManagerServiceNative.sendMessage +
        //   qk/b.b→e.c.L + composeMexHeader + ProtocolFrameUtils）：
        //   - 缓冲区 < len+17 → 走 fallback H()：新缓冲头空间 = hk.g.g(accId,3)+4 = 4（会话3不在→iG=0）
        //     → MEX 头 7B 放不下 → "[offset=4; size=7]" → 10110。fallback 是死路。
        //   - 缓冲区 ≥ len+17 → 主路径 e.c.L()：offset<15 时框架把数据原地右移到 15
        //     (=MSG_HEADER_LEN，makeFrameworkConnection Bundle 实证) → MEX i(7):15-7=8 ✓
        //   - 载荷 = [cid(1B)] + [protobuf]，param7 = 载荷长度，param8 = 载荷当前偏移。
        //   - 实弹验证（00:39:31）：(49,15,66) → 返回 22（txId），BT WRITE Len:58 上线。
        byte[] payload = new byte[proto.length + 1];
        payload[0] = (byte) 10;                                // cid=10 前缀（AbsMsgAgent 结构）
        System.arraycopy(proto, 0, payload, 1, proto.length);

        int pLen = payload.length, pOff = 15, bufLen = payload.length + 17;
        byte[] buf = new byte[bufLen];
        System.arraycopy(payload, 0, buf, pOff, payload.length);
        for (String loc : locals) {
            if (loc == null || loc.equals("-1") || loc.equals("0") || loc.equals("null")) continue;
            Object r = call(ifm, "sendMessage",
                    new Class<?>[]{long.class, String.class, String.class, long.class,
                            byte[].class, boolean.class, int.class, int.class},
                    clientId, loc, String.valueOf(peer), acc, buf, false, pLen, pOff);
            log("  [R17b帧] local=" + loc + " len=" + pLen + " off=" + pOff
                    + " buflen=" + bufLen + " -> " + str(r));
            try { Thread.sleep(2000); } catch (Throwable ig) {}
        }
    }

    /** 优先用健康 App 的真实 protobuf 类构造；失败退回手工编码。 */
    byte[] buildPreCheckPayload() {
        // --- 路线 A：DexClassLoader 加载健康 App 的 protobuf 类 ---
        try {
            byte[] a = buildViaRealProto();
            if (a != null && a.length > 0) { log("载荷：用真实 protobuf 类构造成功"); return a; }
        } catch (Throwable t) { log("真实 protobuf 路线失败：" + t); }

        // --- 路线 B：手工 wire-format ---
        try {
            log("载荷：改用手工 wire-format");
            return buildManualProto();
        } catch (Throwable t) { log("手工编码失败：" + t); return null; }
    }

    /** 用健康 App classes2(WatchAppProto) + classes14(protobuf runtime) 真实构造。 */
    byte[] buildViaRealProto() throws Exception {
        String apk = getPackageManager().getPackageInfo("com.heytap.health", 0).applicationInfo.sourceDir;
        // 健康 App 是多 dex：DexClassLoader 会把它们都带上
        dalvik.system.DexClassLoader hcl = new dalvik.system.DexClassLoader(
                apk, new File(getCacheDir(), "h17").getAbsolutePath(), null, getClassLoader());

        Class<?> cMsgBody = Class.forName("com.heytap.health.watch.watchapp.proto.WatchAppProto$MsgBody", false, hcl);
        Class<?> cCheckReq = Class.forName("com.heytap.health.watch.watchapp.proto.WatchAppProto$InstallCheckRequest", false, hcl);
        Class<?> cHeader = Class.forName("com.heytap.health.watch.watchapp.proto.WatchAppProto$MsgHeader", false, hcl);
        Class<?> cCmd = Class.forName("com.heytap.health.watch.watchapp.proto.WatchAppProto$AppCommandMsg", false, hcl);
        log("健康 App protobuf 类加载成功");

        // InstallCheckRequest{ fileSize = pkgLen }
        Object reqB = cCheckReq.getMethod("newBuilder").invoke(null);
        reqB.getClass().getMethod("setFileSize", int.class).invoke(reqB, (int) pkgLen);
        Object req = reqB.getClass().getMethod("build").invoke(reqB);

        // MsgBody{ checkRequest = req }
        Object bodyB = cMsgBody.getMethod("newBuilder").invoke(null);
        bodyB.getClass().getMethod("setCheckRequest", cCheckReq).invoke(bodyB, req);
        Object body = bodyB.getClass().getMethod("build").invoke(bodyB);

        // MsgHeader{ actionAnchor, protocolVersion, commandId=10 }
        Object hB = cHeader.getMethod("newBuilder").invoke(null);
        hB.getClass().getMethod("setCommandId", int.class).invoke(hB, 10);
        hB.getClass().getMethod("setProtocolVersion", int.class).invoke(hB, 1);
        try { hB.getClass().getMethod("setActionAnchor", String.class)
                .invoke(hB, java.util.UUID.randomUUID().toString().replace("-", "")); } catch (Throwable ig) {}
        Object h = hB.getClass().getMethod("build").invoke(hB);

        // AppCommandMsg{ header, body }
        Object cmdB = cCmd.getMethod("newBuilder").invoke(null);
        cmdB.getClass().getMethod("setHeader", cHeader).invoke(cmdB, h);
        cmdB.getClass().getMethod("setBody", cMsgBody).invoke(cmdB, body);
        Object cmd = cmdB.getClass().getMethod("build").invoke(cmdB);

        Object arr = cmd.getClass().getMethod("toByteArray").invoke(cmd);
        return (byte[]) arr;
    }

    /**
     * 手工 protobuf wire-format（备选）。
     * 字段号取自 WatchAppProto 的 *_FIELD_NUMBER（dexdump 实测）：
     *   InstallCheckRequest.fileSize = 1
     *   MsgBody.checkRequest         = 6
     *   MsgHeader: commandId=1? / protocolVersion=2? / actionAnchor=3? / bodyMd5=4?
     *   AppCommandMsg.header=1 / body=2
     * ⚠️ 字段号未逐一验证，故优先走路线 A。
     */
    byte[] buildManualProto() throws Exception {
        java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
        // InstallCheckRequest{ fileSize=1 (varint) }
        byte[] req = new byte[]{0x08};
        byte[] sz = varint(pkgLen);
        java.io.ByteArrayOutputStream rq = new java.io.ByteArrayOutputStream();
        rq.write(0x08); rq.write(sz);
        byte[] reqBytes = rq.toByteArray();
        // MsgBody{ checkRequest=6 (len-delimited) }
        java.io.ByteArrayOutputStream bd = new java.io.ByteArrayOutputStream();
        bd.write(0x32); bd.write(varint(reqBytes.length)); bd.write(reqBytes);
        byte[] bodyBytes = bd.toByteArray();
        // MsgHeader{ commandId=?, protocolVersion=? }
        java.io.ByteArrayOutputStream hd = new java.io.ByteArrayOutputStream();
        hd.write(0x08); hd.write(varint(10));   // commandId (field 1, 待验证)
        hd.write(0x10); hd.write(varint(1));    // protocolVersion (field 2, 待验证)
        byte[] headerBytes = hd.toByteArray();
        // AppCommandMsg{ header=1, body=2 }
        o.write(0x0a); o.write(varint(headerBytes.length)); o.write(headerBytes);
        o.write(0x12); o.write(varint(bodyBytes.length)); o.write(bodyBytes);
        return o.toByteArray();
    }

    static byte[] varint(long v) {
        java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
        while (true) {
            if ((v & ~0x7FL) == 0) { o.write((int) v); return o.toByteArray(); }
            o.write(((int) (v & 0x7F)) | 0x80);
            v >>>= 7;
        }
    }

    static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }
}
