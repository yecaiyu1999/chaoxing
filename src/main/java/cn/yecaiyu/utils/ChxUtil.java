package cn.yecaiyu.utils;

import cn.hutool.crypto.digest.MD5;

import java.time.LocalDateTime;
import java.util.Random;

public class ChxUtil {

    public static String[] getEncTime() {
        String[] enc = new String[2];
        String mTime = LocalDateTime.now().toString();
        String mToken = "4faa8662c59590c6f43ae9fe5b002b42";
        String mEncryptStr = "token=" + mToken + "&_time=" + mTime + "&DESKey=Z(AfY@XS";
        String mInfEnc = MD5.create().digestHex(mEncryptStr);
        enc[0] = mTime;
        enc[1] = mInfEnc;
        return enc;
    }

    public static void showProgress(String name, float current, float total) {
        float percent = (current / total * 100);
        float remain = (total - current);
        if (current >= total && remain < 1) {
            System.out.println("当前任务： " + name + " 已完成");
        } else {
            System.out.println("当前任务： " + name + " |" + String.format("%.2f", percent) + "%| ");
        }
    }

    public static String getEnc(String clazzId, String jobId, String objectId, int playingTime, int duration, String userId) {
        String str = String.format("[%s][%s][%s][%s][%s][d_yHJ!$pdA~5][%s][0_%s]", clazzId, userId, jobId, objectId, playingTime * 1000, duration * 1000, duration);
        return MD5.create().digestHex(str);
    }

    public static void pause(int start, int end) {
        Random random = new Random();
        int t = random.nextInt(end - start) + start;
        try {
            Thread.sleep(t * 1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

}