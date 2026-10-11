package com.rtos.ftprobe;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 拉起 MainService 后【自己留在前台】，不再 finish()。
 *
 * R17b 实测（00:45 那轮）：FGS + 亮屏 + deviceidle 白名单 + standby active 全上，
 * ColorOS 依旧把探针 virtualFreeze（CPU 计数 4 秒零增量实锤）——cid=10 发完就冻，
 * Op4 永远执行不到。历代"探针停在中途"的另一半真因就是这个冻结。
 *
 * 有焦点的前台 Activity（TOP 状态）不进 cached freezer，所以本 Activity 留在
 * 屏幕上充当"防冻壳"：keepScreenOn + 常亮文本。任务跑完后服务会自己停。
 */
public class MainActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        try {
            Window w = getWindow();
            w.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            Intent it = new Intent(this, MainService.class);
            if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(it);
            else startService(it);
            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setGravity(Gravity.CENTER);
            TextView tv = new TextView(this);
            tv.setText("ftprobe18 防冻壳运行中\n(Transfer probe keep-alive)\n请勿关闭此页面");
            tv.setTextSize(18);
            tv.setGravity(Gravity.CENTER);
            root.addView(tv);
            setContentView(root);
        } catch (Throwable ig) {}
    }
}
